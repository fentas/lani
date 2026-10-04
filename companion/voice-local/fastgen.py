"""Vectorised frame sampler for gepard_inference.GepardRunner.

The upstream runner samples each of the 32 codebook heads in a Python loop and
builds the repetition-penalty set with one ``.item()`` per head per remembered
frame (about 1000 device syncs per audio frame). On a GPU that sync cost is
larger than the model forward, so the GPU was slower than the CPU.

This subclass keeps the same maths (CFG on raw logits, then repetition
penalty, temperature, top-k, multinomial sampling) but runs all heads as one
padded [heads, max_vocab] tensor, with no host syncs. Padded vocabulary slots
get -inf, so they are never sampled.
"""

from __future__ import annotations

from typing import List, Optional

import torch
import torch.nn.functional as F
from gepard_inference.runner import GepardRunner


class FastGepardRunner(GepardRunner):
    def prepare_fast_heads(self) -> "FastGepardRunner":
        heads = self.model.codebook_heads
        vmax = max(self.model.vocab_sizes)
        d = heads[0].weight.shape[1]
        ref = heads[0].weight
        w = torch.zeros(len(heads), vmax, d, dtype=ref.dtype, device=ref.device)
        b = torch.full((len(heads), vmax), float("-inf"), dtype=torch.float32, device=ref.device)
        for i, h in enumerate(heads):
            v = h.weight.shape[0]
            w[i, :v] = h.weight.data
            b[i, :v] = h.bias.data.float()
        self._head_w = w  # [H, V, d]
        self._head_b = b  # [H, V] (fp32, -inf on padding)
        self._vmax = vmax
        return self

    def _all_logits(self, h: torch.Tensor) -> torch.Tensor:
        # h: (1, d) -> (H, V) fp32; same as head(h).float() per head.
        out = torch.einsum("d,hvd->hv", h[0], self._head_w).float()
        return out + self._head_b

    def _sample_frame(
        self,
        cond_hidden: torch.FloatTensor,
        uncond_hidden: Optional[torch.FloatTensor],
        cfg_scale: float,
        temperature: float,
        top_k: int,
        repetition_penalty: float = 1.0,
        recent_frames: Optional[List[torch.LongTensor]] = None,
    ) -> torch.LongTensor:
        logits = self._all_logits(cond_hidden[:, -1, :])
        if uncond_hidden is not None and cfg_scale != 1.0:
            lu = self._all_logits(uncond_hidden[:, -1, :])
            # -inf padding: keep it -inf (avoid inf - inf = nan).
            logits = torch.where(torch.isinf(logits), logits, lu + cfg_scale * (logits - lu))

        if repetition_penalty != 1.0 and recent_frames:
            recent = torch.stack(recent_frames, dim=0).to(logits.device)  # [W, H]
            seen = F.one_hot(recent, self._vmax).amax(dim=0).bool()       # [H, V]
            pen = torch.where(logits > 0, logits / repetition_penalty, logits * repetition_penalty)
            logits = torch.where(seen, pen, logits)

        if temperature != 1.0:
            logits = logits / temperature
        if top_k > 0:
            k = min(top_k, self._vmax)
            thr = torch.topk(logits, k, dim=-1).values[:, -1:]
            logits = logits.masked_fill(logits < thr, float("-inf"))

        probs = F.softmax(logits, dim=-1)
        return torch.multinomial(probs, num_samples=1).squeeze(-1)  # (H,)


def make_fast(runner: GepardRunner) -> FastGepardRunner:
    runner.__class__ = FastGepardRunner
    return runner.prepare_fast_heads()
