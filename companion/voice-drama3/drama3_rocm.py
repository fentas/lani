"""Fish Audio Drama 3 on AMD ROCm (RX 9070, gfx1201), sharing a 16 GB card with other services.

Built with Fish Audio. The model and the upstream fish-speech code (fishaudio/fish-speech at
541566932abbaebcb0a40e562a1f846738383f38) are under the Fish Audio Community License v1.0
(LICENSES/Fish-Audio-Community-License-1.0.md); this module changes how that code runs, at run
time, without editing its files (LICENSES/fish-audio-NOTICE.txt):

  * Routed FP8 experts are streamed. The 30 GiB of routed experts stay in the safetensors shards
    and are read on demand (threaded preadv into a small pinned staging buffer) into a per-layer
    LRU cache in VRAM. Nothing of them is held in RAM ("safe mode": about 2.4 GiB resident; the
    kernel may cache the files' pages and drop them under pressure). Each expert is one 3 MiB
    span [down | gate | up]; two cross a shard boundary and are read in parts.
  * FP8 on RDNA4. Upstream turns its Triton W8A8 MoE kernel off for every HIP device; gfx12 has
    e4m3fn and the kernel compiles for it, so it is on here (LANI_DRAMA3_TRITON_FP8=0: a W8A16
    path that dequantises the cached experts to bf16 instead). The kernel's "expert ids" are cache
    slots, so the block scales are kept per slot.
  * The triangular solve. ROCm's batched solve_triangular takes ~80 ms per call at the gated
    delta rule's shapes (60 calls per prefill); an exact fp32 forward substitution replaces the
    unit-lower case, in fish_omni/layers.py only (LANI_DRAMA3_FAST_SOLVE=0: off).
  * Memory. The 1 GB token embedding stays on the CPU (one row per step); lm_head keeps only the
    4,097 rows the sampler can draw (EOS and the semantic band): the same logits for every
    candidate, -inf elsewhere, which the sampler never reads. The codec loads without its two
    unused 32768^2 masks (akashzeno/ComfyUI-FishAudioDrama3's method).
  * No CUDA/HIP graphs: the expert routing needs a host round trip in every layer.
  * Host protection: oom_score_adj 1000, and a watchdog exits the process when MemAvailable falls
    under a floor.

Checked against a CPU reference in the spike (2026-10-19): the experts on every path, conv1d,
SDPA, both gated-delta-rule paths and bmm; a 30-row prefill plus single decode steps agrees with
one full prefill.
"""

from __future__ import annotations

import json
import os
import sys
import threading
import time
import warnings
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

os.environ.setdefault("FISH_DRAMA3_CUDA_GRAPH", "0")

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

FISH_SPEECH = Path(os.environ.get("LANI_DRAMA3_SRC", Path.home() / ".local/share/lani-drama3/fish-speech")).expanduser()
sys.path.insert(0, str(FISH_SPEECH))

from fish_speech.models.text2semantic.fish_omni.backbone import (  # noqa: E402
    CHECKPOINT_BACKBONE_PREFIX,
    CHECKPOINT_LM_HEAD_KEY,
    EXPERT_KEY_PATTERN,
    FishOmniBackbone,
    backbone_name,
)
from fish_speech.models.text2semantic.fish_omni.config import FishOmniConfig  # noqa: E402
from fish_speech.models.text2semantic.fish_omni.model import (  # noqa: E402
    AUDIO_DECODER_PREFIXES,
    FishOmniModel,
    iter_checkpoint_tensors,
)
from fish_speech.models.text2semantic.fish_omni.moe import FP8_BLOCK_SIZE, FP8_DTYPE  # noqa: E402
from fish_speech.models.text2semantic.fish_omni.fp8_moe import fp8_moe_forward  # noqa: E402
from fish_speech.models.text2semantic.fish_omni import layers as fish_layers  # noqa: E402

USE_TRITON_FP8 = os.environ.get("LANI_DRAMA3_TRITON_FP8", "1") == "1"


def unit_lower_solve(A: torch.Tensor, B: torch.Tensor) -> torch.Tensor:
    """X with A X = B for unit lower-triangular A [..., n, n], by forward substitution in fp32."""
    X = B.clone()
    for i in range(1, A.shape[-1]):
        X[..., i, :] -= (A[..., i:i + 1, :i] @ X[..., :i, :]).squeeze(-2)
    return X


class _LinalgShim:
    """torch.linalg for fish_omni.layers, with the unit-lower case solved as above."""

    def __getattr__(self, name):
        return getattr(torch.linalg, name)

    @staticmethod
    def solve_triangular(A, B, *, upper, left=True, unitriangular=False, out=None):
        if A.is_cuda and not upper and left and unitriangular and out is None:
            return unit_lower_solve(A, B)
        return torch.linalg.solve_triangular(A, B, upper=upper, left=left, unitriangular=unitriangular, out=out)


class _TorchShim:
    def __getattr__(self, name):
        return _LinalgShim() if name == "linalg" else getattr(torch, name)


if os.environ.get("LANI_DRAMA3_FAST_SOLVE", "1") == "1":
    fish_layers.torch = _TorchShim()  # only fish_omni/layers.py sees the shim
from fish_speech.models.text2semantic.fish_omni.tower import (  # noqa: E402
    FishOmniAudioDecoder,
    load_audio_decoder_weights,
)
from tokenizers import Tokenizer  # noqa: E402

warnings.filterwarnings("ignore", message="The given buffer is not writable")

GIB = 2**30


def mem_available(meminfo: str = "/proc/meminfo") -> int:
    """MemAvailable in bytes (what `free` shows as "available")."""
    with open(meminfo) as f:
        for line in f:
            if line.startswith("MemAvailable:"):
                return int(line.split()[1]) * 1024
    raise RuntimeError("no MemAvailable")


def protect_host(min_available_gib: float = 4.0, log=print) -> None:
    """Be the OOM killer's first choice, and exit before the host runs short of memory."""
    try:
        with open("/proc/self/oom_score_adj", "w") as f:
            f.write("1000")
    except OSError:
        pass

    def watch() -> None:
        while True:
            if mem_available() < min_available_gib * GIB:
                log(f"MemAvailable below {min_available_gib} GiB: exiting to protect the host")
                os._exit(3)
            time.sleep(1.0)

    threading.Thread(target=watch, name="memwatch", daemon=True).start()


def shard_header(path: Path) -> tuple[int, dict]:
    with open(path, "rb") as f:
        size = int.from_bytes(f.read(8), "little")
        header = json.loads(f.read(size))
    header.pop("__metadata__", None)
    return 8 + size, header


class Stats:
    def __init__(self) -> None:
        self.reset()

    def reset(self) -> None:
        self.hits = self.misses = 0
        self.bytes = 0
        self.copy_s = 0.0
        self.calls = 0


class ExpertStore:
    """Every routed expert as one byte span [down | gate | up] in a shard, read with preadv."""

    def __init__(self, checkpoint: Path, layers: int, experts: int) -> None:
        self.spans: dict[tuple[int, int], list[tuple[int, int, int]]] = {}
        self.scales: dict[tuple[int, int, str], torch.Tensor] = {}
        self.row_bytes = 0
        parts: dict[tuple[int, int], dict[str, tuple[int, int, int, list]]] = {}
        weight_map = json.loads((checkpoint / "model.safetensors.index.json").read_text())["weight_map"]
        shards = sorted(set(weight_map.values()))
        for shard_index, shard in enumerate(shards):
            base, header = shard_header(checkpoint / shard)
            with open(checkpoint / shard, "rb") as f:
                for key, info in header.items():
                    if key.startswith(AUDIO_DECODER_PREFIXES):
                        continue
                    match = EXPERT_KEY_PATTERN.fullmatch(backbone_name(key))
                    if match is None:
                        continue
                    layer, expert = int(match["layer"]), int(match["expert"])
                    start, end = info["data_offsets"]
                    if match["kind"] == "weight_scale_inv":
                        f.seek(base + start)
                        arr = np.frombuffer(f.read(end - start), dtype=np.float32)
                        self.scales[(layer, expert, match["proj"])] = torch.from_numpy(arr.copy()).view(info["shape"])
                    else:
                        if info["dtype"] != "F8_E4M3":
                            raise ValueError(f"{key}: expected F8_E4M3, got {info['dtype']}")
                        parts.setdefault((layer, expert), {})[match["proj"]] = (
                            shard_index, base + start, base + end, info["shape"])
        for (layer, expert), p in parts.items():
            segments: list[tuple[int, int, int]] = []  # (shard, offset, length), merged where contiguous
            for proj in ("down_proj", "gate_proj", "up_proj"):
                shard, start, end, _ = p[proj]
                if segments and segments[-1][0] == shard and segments[-1][1] + segments[-1][2] == start:
                    segments[-1] = (shard, segments[-1][1], segments[-1][2] + end - start)
                else:
                    segments.append((shard, start, end - start))
            self.spans[(layer, expert)] = segments
            self.row_bytes = sum(length for _, _, length in segments)
        if len(self.spans) != layers * experts:
            raise ValueError(f"{len(self.spans)} experts found, {layers * experts} expected")
        self.down_shape = tuple(parts[(0, 0)]["down_proj"][3])  # [H, I]
        self.fds = [os.open(checkpoint / shard, os.O_RDONLY) for shard in shards]
        self.pool = ThreadPoolExecutor(12)

    def read_row(self, layer: int, expert: int, out: torch.Tensor) -> None:
        """preadv the expert's [down | gate | up] bytes into the (pinned) uint8 row out."""
        view = memoryview(out.numpy())
        at = 0
        for shard, offset, length in self.spans[(layer, expert)]:
            done = 0
            while done < length:
                n = os.preadv(self.fds[shard], [view[at + done:at + length]], offset + done)
                if n <= 0:
                    raise OSError(f"short read for expert {layer}.{expert}")
                done += n
            at += length

    def read_rows(self, layer: int, experts: list[int], out: torch.Tensor) -> None:
        list(self.pool.map(lambda a: self.read_row(layer, a[1], out[a[0]]), enumerate(experts)))

    def layer_scales(self, layer: int, experts: int, device) -> tuple[torch.Tensor, torch.Tensor]:
        w13 = torch.stack([torch.cat((self.scales[(layer, e, "gate_proj")], self.scales[(layer, e, "up_proj")]))
                           for e in range(experts)])
        w2 = torch.stack([self.scales[(layer, e, "down_proj")] for e in range(experts)])
        return w13.to(device), w2.to(device)


class Staging:
    """A pinned host buffer for the misses of one step, copied to the GPU in one go."""

    def __init__(self, rows: int, row_bytes: int, device) -> None:
        self.rows = rows
        self.host = torch.empty((rows, row_bytes), dtype=torch.uint8, pin_memory=True)
        self.gpu = torch.empty((rows, row_bytes), dtype=torch.uint8, device=device)


class StreamedExperts(nn.Module):
    """Drop-in for MoEExperts: weights stream from ExpertStore into a VRAM LRU cache."""

    def __init__(self, layer: int, store: ExpertStore, num_experts: int, capacity: int,
                 staging: Staging, stats: Stats, device) -> None:
        super().__init__()
        self.layer = layer
        self.store = store
        self.num_experts = num_experts
        self.capacity = capacity
        self.staging = staging
        self.stats = stats
        self.device = torch.device(device)
        self.hidden, self.intermediate = store.down_shape
        self.down_bytes = self.hidden * self.intermediate
        self.packed = torch.empty((capacity, store.row_bytes), dtype=torch.uint8, device=device)
        self.slots: OrderedDict[int, int] = OrderedDict()
        self.free = list(range(capacity - 1, -1, -1))
        self.w13_scale, self.w2_scale = store.layer_scales(layer, num_experts, device)
        self.use_triton = USE_TRITON_FP8
        self.w2_view = self.packed[:, :self.down_bytes].view(FP8_DTYPE).view(capacity, self.hidden, self.intermediate)
        self.w13_view = self.packed[:, self.down_bytes:].view(FP8_DTYPE).view(capacity, 2 * self.intermediate, self.hidden)
        self.slot_s13 = torch.ones((capacity, *self.w13_scale.shape[1:]), dtype=torch.float32, device=device)
        self.slot_s2 = torch.ones((capacity, *self.w2_scale.shape[1:]), dtype=torch.float32, device=device)

    def place(self, wanted: list[int]) -> list[int]:
        """Slots of the distinct experts wanted; copies the missing ones in."""
        needed = set(wanted)
        missing = []
        for e in wanted:
            if e in self.slots:
                self.slots.move_to_end(e)
            else:
                missing.append(e)
        self.stats.calls += 1
        self.stats.hits += len(wanted) - len(missing)
        self.stats.misses += len(missing)
        if missing:
            t = time.perf_counter()
            new_slots = []
            for e in missing:
                if self.free:
                    slot = self.free.pop()
                else:
                    victim = next(k for k in self.slots if k not in needed)
                    slot = self.slots.pop(victim)
                self.slots[e] = slot
                new_slots.append(slot)
            st = self.staging
            for start in range(0, len(missing), st.rows):
                group = missing[start:start + st.rows]
                torch.cuda.current_stream(self.device).synchronize()  # the staging buffer is free again
                self.store.read_rows(self.layer, group, st.host)
                n = len(group)
                st.gpu[:n].copy_(st.host[:n], non_blocking=True)
                index = torch.tensor(new_slots[start:start + n], dtype=torch.long).to(self.device, non_blocking=True)
                self.packed.index_copy_(0, index, st.gpu[:n])
            src = torch.tensor(missing, dtype=torch.long).to(self.device)
            dst = torch.tensor(new_slots, dtype=torch.long).to(self.device)
            self.slot_s13.index_copy_(0, dst, self.w13_scale.index_select(0, src))
            self.slot_s2.index_copy_(0, dst, self.w2_scale.index_select(0, src))
            self.stats.bytes += len(missing) * self.store.row_bytes
            self.stats.copy_s += time.perf_counter() - t
        return [self.slots[e] for e in wanted]

    def dequant(self, slots: torch.Tensor, experts: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        """bf16 w13 [n, 2I, H] and w2 [n, H, I] of the cached experts in slots."""
        rows = self.packed.index_select(0, slots)
        n = rows.shape[0]
        w2 = rows[:, :self.down_bytes].view(FP8_DTYPE).view(n, self.hidden, self.intermediate)
        w13 = rows[:, self.down_bytes:].view(FP8_DTYPE).view(n, 2 * self.intermediate, self.hidden)
        return (self._scale(w13, self.w13_scale[experts]), self._scale(w2, self.w2_scale[experts]))

    @staticmethod
    def _scale(weight: torch.Tensor, scale: torch.Tensor) -> torch.Tensor:
        n, r, c = weight.shape
        b = FP8_BLOCK_SIZE
        blocks = weight.view(n, r // b, b, c // b, b).to(torch.float32)
        return (blocks * scale[:, :, None, :, None]).view(n, r, c).to(torch.bfloat16)

    def forward(self, x: torch.Tensor, topk_ids: torch.Tensor, topk_weights: torch.Tensor) -> torch.Tensor:
        tokens, top_k = topk_ids.shape
        ids_cpu = topk_ids.cpu()
        unique = torch.unique(ids_cpu).tolist()
        if self.use_triton:
            return self._triton(x, ids_cpu, unique, topk_weights)
        if len(unique) <= self.capacity and tokens * top_k <= 64:
            return self._gathered(x, ids_cpu, unique, topk_weights)
        return self._grouped(x, ids_cpu, unique, topk_weights)

    def _triton(self, x, ids_cpu, unique, topk_weights):
        """Upstream fp8_moe_forward over the cache; a cache-full of experts at a time when more are hit."""
        out = None
        for g in range(0, len(unique), self.capacity):
            group = unique[g:g + self.capacity]
            slots = self.place(group)
            slot_of = torch.zeros(self.num_experts, dtype=torch.long)
            slot_of[torch.tensor(group)] = torch.tensor(slots)
            remap = slot_of[ids_cpu].to(self.device)
            weights = topk_weights
            if len(group) < len(unique):  # pairs routed outside this group count zero here
                inside = torch.zeros(self.num_experts, dtype=torch.bool)
                inside[torch.tensor(group)] = True
                weights = topk_weights * inside[ids_cpu].to(self.device, topk_weights.dtype)
            y = fp8_moe_forward(x, remap, weights, self.w13_view, self.slot_s13, self.w2_view, self.slot_s2)
            out = y.float() if out is None else out + y.float()
        return out.to(x.dtype)

    def _gathered(self, x, ids_cpu, unique, topk_weights):
        """Decode, W8A16: every (token, expert) pair in one batched matmul."""
        tokens, top_k = ids_cpu.shape
        slots = self.place(unique)
        slot_of = torch.empty(self.num_experts, dtype=torch.long)
        slot_of[torch.tensor(unique)] = torch.tensor(slots)
        flat = ids_cpu.reshape(-1)
        w13, w2 = self.dequant(slot_of[flat].to(self.device, non_blocking=True), flat.to(self.device, non_blocking=True))
        rows = x.repeat_interleave(top_k, dim=0).unsqueeze(-1)
        gate, up = torch.bmm(w13, rows).squeeze(-1).chunk(2, dim=-1)
        out = torch.bmm(w2, (F.silu(gate) * up).unsqueeze(-1)).squeeze(-1)
        out = out.float() * topk_weights.reshape(-1, 1).float()
        return out.view(tokens, top_k, self.hidden).sum(dim=1).to(x.dtype)

    def _grouped(self, x, ids_cpu, unique, topk_weights):
        """Prefill, W8A16: loop over the experts hit, a cache-full of them at a time."""
        tokens, top_k = ids_cpu.shape
        flat = ids_cpu.reshape(-1)
        order = torch.argsort(flat, stable=True)
        counts = torch.bincount(flat, minlength=self.num_experts)
        starts = torch.cumsum(counts, 0) - counts
        token_rows = (order // top_k).to(self.device)
        pair_weights = topk_weights.reshape(-1)[order.to(topk_weights.device)]
        out = torch.zeros(tokens, self.hidden, dtype=torch.float32, device=x.device)
        for g in range(0, len(unique), self.capacity):
            group = unique[g:g + self.capacity]
            slots = self.place(group)
            for j, e in enumerate(group):
                s, c = int(starts[e]), int(counts[e])
                rows = token_rows[s:s + c]
                w13, w2 = self.dequant(torch.tensor([slots[j]]).to(self.device), torch.tensor([e]).to(self.device))
                gate, up = F.linear(x[rows], w13[0]).chunk(2, dim=-1)
                y = F.linear(F.silu(gate) * up, w2[0])
                out.index_add_(0, rows, y.float() * pair_weights[s:s + c, None].float())
        return out.to(x.dtype)


class SlicedHead(nn.Module):
    """lm_head rows of the main-token candidates only; other logits are -inf."""

    def __init__(self, full_weight: torch.Tensor, candidates: torch.Tensor, vocab: int, device) -> None:
        super().__init__()
        self.weight = nn.Parameter(full_weight[candidates].to(device), requires_grad=False)
        self.register_buffer("candidates", candidates.to(device))
        self.vocab = vocab

    def forward(self, hidden: torch.Tensor) -> torch.Tensor:
        out = hidden.new_full((*hidden.shape[:-1], self.vocab), float("-inf"))
        out[..., self.candidates] = F.linear(hidden, self.weight)
        return out


def dense_tensors(checkpoint: Path):
    """Backbone tensors except the routed experts, read without touching expert bytes."""
    from safetensors import safe_open
    weight_map = json.loads((checkpoint / "model.safetensors.index.json").read_text())["weight_map"]
    for shard in sorted(set(weight_map.values())):
        with safe_open(str(checkpoint / shard), framework="pt", device="cpu") as f:
            for key in f.keys():
                if key.startswith(AUDIO_DECODER_PREFIXES) or EXPERT_KEY_PATTERN.fullmatch(backbone_name(key)):
                    continue
                yield key, f.get_tensor(key)


def gpu_mem() -> dict:
    free, _total = torch.cuda.mem_get_info()
    return {"free_GiB": round(free / GIB, 2), "torch_allocated_GiB": round(torch.cuda.memory_allocated() / GIB, 2),
            "torch_reserved_GiB": round(torch.cuda.memory_reserved() / GIB, 2)}


def rss_gib() -> float:
    with open("/proc/self/statm") as f:
        return round(int(f.read().split()[1]) * os.sysconf("SC_PAGE_SIZE") / GIB, 2)


@torch.inference_mode()
def load_model(checkpoint: str | Path, device: str = "cuda:0", cache_slots: int | None = None,
               reserve_gib: float = 1.6, max_slots: int = 32, staging_rows: int = 32,
               cap_gib: float | None = None, workspace_gib: float = 0.6):
    """The streamed model. cache_slots None: as many as fit, leaving reserve_gib of the device free
    and workspace_gib of this process's cap (cap_gib) for activations."""
    checkpoint = Path(checkpoint)
    t0 = time.perf_counter()
    config = FishOmniConfig.from_dict(json.loads((checkpoint / "config.json").read_text()))
    tc = config.text
    with torch.device("meta"):
        backbone = FishOmniBackbone(tc, fp8_experts=True, dtype=torch.bfloat16)
        tower = FishOmniAudioDecoder(config, dtype=torch.bfloat16)
    backbone.model.embed_tokens.to_empty(device="cpu")
    backbone.model.norm.to_empty(device=device)
    for layer in backbone.model.layers:
        for name, child in layer.named_children():
            if name != "mlp":
                child.to_empty(device=device)
        for name, child in layer.mlp.named_children():
            if name != "experts":
                child.to_empty(device=device)
    tower.to_empty(device=device)

    # Dense backbone weights, and the lm_head rows the sampler can draw.
    candidates = torch.cat((torch.tensor([config.eos_token_id]),
                            torch.arange(config.semantic_start_token_id, config.semantic_end_token_id + 1))).sort().values
    targets = {n: p for n, p in backbone.named_parameters() if ".mlp.experts." not in n and n != "lm_head.weight"}
    targets.update({n: b for n, b in backbone.named_buffers() if ".mlp.experts." not in n})
    loaded = set()
    head = None
    for name_ck, tensor in dense_tensors(checkpoint):
        name = backbone_name(name_ck)
        if name == "lm_head.weight":
            head = SlicedHead(tensor, candidates, tc.vocab_size, device)
            continue
        if name not in targets:
            raise ValueError(f"unexpected checkpoint tensor {name_ck}")
        if tuple(targets[name].shape) != tuple(tensor.shape):
            raise ValueError(f"{name}: shape {tuple(tensor.shape)} != {tuple(targets[name].shape)}")
        targets[name].copy_(tensor)
        loaded.add(name)
    if missing := set(targets) - loaded:
        raise ValueError(f"not loaded: {sorted(missing)[:6]}")
    if head is None:
        raise ValueError("lm_head.weight missing")
    backbone.lm_head = head
    load_audio_decoder_weights(tower, iter_checkpoint_tensors(
        checkpoint, skip_prefixes=(CHECKPOINT_BACKBONE_PREFIX, CHECKPOINT_LM_HEAD_KEY)))
    embed = backbone.model.embed_tokens
    backbone.embed = lambda ids: embed(ids.to("cpu")).to(device)  # type: ignore[method-assign]
    t_dense = time.perf_counter() - t0

    # Routed experts: read from the shards, scales on the GPU, per-layer caches in what VRAM is left.
    store = ExpertStore(checkpoint, tc.num_hidden_layers, tc.num_experts)
    torch.cuda.synchronize()
    free = torch.cuda.mem_get_info()[0]
    staging_bytes = staging_rows * store.row_bytes
    per_slot = store.row_bytes * tc.num_hidden_layers
    if cache_slots is None:
        room = free - reserve_gib * GIB
        if cap_gib is not None:
            room = min(room, cap_gib * GIB - torch.cuda.memory_allocated() - workspace_gib * GIB)
        cache_slots = int((room - staging_bytes) // per_slot)
        cache_slots = max(8, min(max_slots, cache_slots))
    if cache_slots < tc.num_experts_per_tok:
        raise ValueError("a decode step needs 8 cached experts per layer")
    stats = Stats()
    staging = Staging(staging_rows, store.row_bytes, device)
    for index, layer in enumerate(backbone.model.layers):
        layer.mlp.experts = StreamedExperts(index, store, tc.num_experts, cache_slots, staging, stats, device)
    backbone.inverse_frequencies.clear()
    tokenizer = Tokenizer.from_file(str(checkpoint / "tokenizer.json"))
    model = FishOmniModel(config, backbone, tower, tokenizer).eval()
    model.expert_stats = stats
    model.expert_store = store
    torch.cuda.synchronize()
    info = {
        "load_s": round(time.perf_counter() - t0, 1), "dense_load_s": round(t_dense, 1),
        "mem_available_GiB": round(mem_available() / GIB, 1),
        "cache_slots_per_layer": cache_slots, "cache_GiB": round(cache_slots * per_slot / GIB, 2),
        "expert_row_MiB": store.row_bytes / 2**20, **gpu_mem(), "rss_GiB": rss_gib(),
    }
    return model, info


def load_codec(checkpoint: str | Path, device: str = "cpu"):
    """Upstream modded DAC without its two unused 32768^2 causal masks."""
    from fish_speech.models.dac.inference import load_model as load_dac
    from fish_speech.models.dac.modded_dac import WindowLimitedTransformer
    real_tril = torch.tril

    def tril(input, *args, **kwargs):
        if not args and not kwargs and input.dtype == torch.bool and input.shape == (32768, 32768):
            return input.new_empty(0)
        return real_tril(input, *args, **kwargs)

    torch.tril = tril
    try:
        codec = load_dac("modded_dac_vq", str(Path(checkpoint) / "codec.pth"), device="cpu")
    finally:
        torch.tril = real_tril
    for module in codec.modules():
        if isinstance(module, WindowLimitedTransformer):
            module.causal_mask = None
    return codec.to(device).eval()
