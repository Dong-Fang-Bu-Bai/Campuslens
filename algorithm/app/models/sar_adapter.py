from copy import deepcopy
import math

import torch
import torch.nn as nn
import torch.nn.functional as F

from app.config import Config
from app.utils.sam_optimizer import SAM


def collect_adaptation_parameters(model):
    named = []
    for module_name, module in model.named_modules():
        if isinstance(module, (nn.LayerNorm, nn.BatchNorm2d, nn.GroupNorm)):
            for name, parameter in module.named_parameters(recurse=False):
                if name in {"weight", "bias"}:
                    full_name = f"{module_name}.{name}" if module_name else name
                    named.append((full_name, parameter))
    return named


class SARAdapter:
    """Persistent SAR state for one mutable query encoder."""

    def __init__(self, model):
        self.model = model
        self.named_parameters = collect_adaptation_parameters(model)
        if not self.named_parameters:
            raise ValueError("model has no normalization parameters for SAR")
        self.parameters = [parameter for _, parameter in self.named_parameters]
        self.initial_state = self._parameter_state()
        self.optimizer = self._new_optimizer()
        self.ema = None
        self.update_count = 0
        self.generation = 1

    def _new_optimizer(self):
        return SAM(
            self.parameters,
            torch.optim.Adam,
            lr=Config.SAR_LEARNING_RATE,
            rho=Config.SAR_RHO,
        )

    def _parameter_state(self):
        return {name: parameter.detach().cpu().clone() for name, parameter in self.named_parameters}

    def _load_parameter_state(self, state):
        by_name = dict(self.named_parameters)
        for name, value in state.items():
            if name in by_name:
                by_name[name].data.copy_(value.to(by_name[name].device, dtype=by_name[name].dtype))

    def export_state(self):
        return {
            "parameters": self._parameter_state(),
            "optimizer": deepcopy(self.optimizer.base_optimizer.state_dict()),
            "ema": self.ema,
            "updateCount": self.update_count,
            "generation": self.generation,
        }

    def import_state(self, state):
        self._load_parameter_state(state["parameters"])
        self.optimizer = self._new_optimizer()
        self.optimizer.base_optimizer.load_state_dict(state["optimizer"])
        self.ema = state.get("ema")
        self.update_count = int(state.get("updateCount", 0))
        self.generation = int(state.get("generation", 1))
        self.model.eval()

    def reset(self):
        self._load_parameter_state(self.initial_state)
        self.optimizer = self._new_optimizer()
        self.ema = None
        self.update_count = 0
        self.generation += 1
        self.model.eval()

    def adapt_batch(self, input_tensor, landmark_stats):
        self.model.train()
        self.model.requires_grad_(False)
        for parameter in self.parameters:
            parameter.requires_grad_(True)

        with torch.enable_grad():
            first_outputs = self.model.forward_features(input_tensor)
            first_scores = self._scores(first_outputs, landmark_stats)
            first_entropy = self._entropy(first_scores)
            reliable = first_entropy < Config.SAR_ENTROPY_THRESHOLD
            applied = reliable.detach().cpu().tolist()
            updated = bool(reliable.any().item())

            if updated:
                for _ in range(max(1, Config.SAR_STEPS)):
                    outputs = self.model.forward_features(input_tensor)
                    entropy = self._entropy(self._scores(outputs, landmark_stats))
                    entropy[reliable].mean().backward()
                    self.optimizer.first_step(zero_grad=True)

                    perturbed = self.model.forward_features(input_tensor)
                    perturbed_entropy = self._entropy(self._scores(perturbed, landmark_stats))
                    second_loss = perturbed_entropy[reliable].mean()
                    second_loss.backward()
                    self.optimizer.second_step(zero_grad=True)

                loss_value = float(second_loss.detach().item())
                alpha = min(max(Config.SAR_EMA_ALPHA, 0.0), 1.0)
                self.ema = loss_value if self.ema is None else alpha * self.ema + (1.0 - alpha) * loss_value
                self.update_count += 1

        self.model.eval()
        with torch.inference_mode():
            final_outputs = self.model.forward_features(input_tensor)
            features = self._features(final_outputs)
        return features, first_entropy.detach().cpu().tolist(), applied, updated

    def extract(self, input_tensor):
        self.model.eval()
        with torch.inference_mode():
            return self._features(self.model.forward_features(input_tensor))

    def _features(self, outputs):
        values = outputs["x_norm_clstoken"] if isinstance(outputs, dict) else outputs
        if values.ndim == 3:
            values = values[:, 0]
        return F.normalize(values, p=2, dim=-1)

    def _scores(self, outputs, landmark_stats):
        features = self._features(outputs)
        scores = []
        for stats in landmark_stats.values():
            mean = torch.as_tensor(stats["mean"], dtype=features.dtype, device=features.device)
            cov_inv = torch.as_tensor(stats["cov_inv"], dtype=features.dtype, device=features.device)
            diff = features - mean
            distance_sq = torch.einsum("bi,ij,bj->b", diff, cov_inv, diff)
            distance = torch.sqrt(torch.clamp(distance_sq, min=0.0))
            exponent = Config.MATCH_SCORE_SLOPE * (
                torch.log1p(distance) - math.log1p(Config.MATCH_SCORE_CENTER_DISTANCE)
            )
            scores.append(torch.sigmoid(torch.clamp(-exponent, min=-60.0, max=60.0)))
        return torch.stack(scores, dim=1)

    def _entropy(self, scores):
        probabilities = torch.softmax(scores / max(Config.SAR_ENTROPY_TEMPERATURE, 1e-6), dim=1)
        entropy = -(probabilities * torch.log(probabilities.clamp_min(1e-12))).sum(dim=1)
        return entropy / max(math.log(scores.shape[1]), 1.0)
