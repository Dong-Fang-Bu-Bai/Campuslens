import torch


class SAM(torch.optim.Optimizer):
    """
    Sharpness-Aware Minimization optimizer wrapper
    Reference: https://arxiv.org/abs/2010.01412
    
    This optimizer performs two gradient steps:
    1. First step: compute gradient and perturb parameters
    2. Second step: compute gradient at perturbed parameters and update
    
    Usage:
        base_optimizer = torch.optim.Adam(params, lr=1e-4)
        optimizer = SAM(params, base_optimizer, rho=0.05)
        
        # In training loop:
        loss.backward()
        optimizer.first_step(zero_grad=True)
        
        # Second forward pass
        loss.backward()
        optimizer.second_step(zero_grad=True)
    """
    
    def __init__(self, params, base_optimizer, rho=0.05, adaptive=False, **kwargs):
        assert rho >= 0.0, f"Invalid rho, should be non-negative: {rho}"
        
        defaults = dict(rho=rho, adaptive=adaptive, **kwargs)
        super(SAM, self).__init__(params, defaults)
        
        self.base_optimizer = base_optimizer(self.param_groups, **kwargs)
        self.param_groups = self.base_optimizer.param_groups
        self.defaults.update(self.base_optimizer.defaults)
    
    @torch.no_grad()
    def first_step(self, zero_grad=False):
        """First step: compute gradient and perturb parameters"""
        grad_norm = self._grad_norm()
        for group in self.param_groups:
            scale = group["rho"] / (grad_norm + 1e-12)
            
            for p in group["params"]:
                if p.grad is None:
                    continue
                e_w = (torch.pow(p, 2) if group["adaptive"] else 1.0) * p.grad * scale.to(p)
                p.add_(e_w)  # Climb to the local maximum "w + e(w)"
                self.state[p]["e_w"] = e_w
        
        if zero_grad:
            self.zero_grad()
    
    @torch.no_grad()
    def second_step(self, zero_grad=False):
        """Second step: update parameters using gradients at perturbed point"""
        for group in self.param_groups:
            for p in group["params"]:
                if p.grad is None:
                    continue
                p.sub_(self.state[p]["e_w"])  # Get back to "w" from "w + e(w)"
        
        self.base_optimizer.step()  # Do the actual "sharpness-aware" update
        
        if zero_grad:
            self.zero_grad()
    
    @torch.no_grad()
    def step(self, closure=None):
        assert closure is not None, "Sharpness Aware Minimization requires closure"
        closure = torch.enable_grad()(closure)
        
        self.first_step(zero_grad=True)
        closure()
        self.second_step()
    
    def _grad_norm(self):
        """Compute the norm of the gradients"""
        shared_device = self.param_groups[0]["params"][0].device
        norm = torch.norm(
            torch.stack([
                ((torch.abs(p) if group["adaptive"] else 1.0) * p.grad).norm(p=2).to(shared_device)
                for group in self.param_groups
                for p in group["params"]
                if p.grad is not None
            ]),
            p=2
        )
        return norm
    
    def load_state_dict(self, state_dict):
        """Load optimizer state"""
        super().load_state_dict(state_dict)
        self.base_optimizer.param_groups = self.param_groups