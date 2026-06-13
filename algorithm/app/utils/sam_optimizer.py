import torch


class SAM(torch.optim.Optimizer):
    def __init__(self, params, base_optimizer, rho=0.05, **kwargs):
        if rho < 0.0:
            raise ValueError("rho must be non-negative")
        defaults = dict(rho=rho, **kwargs)
        super().__init__(params, defaults)
        self.base_optimizer = base_optimizer(self.param_groups, **kwargs)
        self.param_groups = self.base_optimizer.param_groups

    @torch.no_grad()
    def first_step(self, zero_grad=False):
        gradients = [p.grad.norm(p=2) for group in self.param_groups for p in group["params"] if p.grad is not None]
        if not gradients:
            return
        norm = torch.norm(torch.stack(gradients), p=2)
        for group in self.param_groups:
            scale = group["rho"] / (norm + 1e-12)
            for parameter in group["params"]:
                if parameter.grad is None:
                    continue
                delta = parameter.grad * scale.to(parameter)
                parameter.add_(delta)
                self.state[parameter]["sar_delta"] = delta
        if zero_grad:
            self.zero_grad()

    @torch.no_grad()
    def second_step(self, zero_grad=False):
        for group in self.param_groups:
            for parameter in group["params"]:
                delta = self.state[parameter].pop("sar_delta", None)
                if delta is not None:
                    parameter.sub_(delta)
        self.base_optimizer.step()
        if zero_grad:
            self.zero_grad()
