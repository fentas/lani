#!/usr/bin/env python3
"""Convert a Hugging Face Whisper checkpoint (transformers format) to an openai-whisper .pt.

    convert_hf.py HF_DIR OUT.pt [--fp16]

HF_DIR holds config.json and model*.safetensors (single file or sharded). The result loads
with whisper.load_model(OUT.pt). Used for Slovene fine-tunes of large-v3 (see README.md).
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import torch
from safetensors.torch import load_file

RULES = [
    (r"^model\.encoder\.embed_positions\.weight$", "encoder.positional_embedding"),
    (r"^model\.decoder\.embed_positions\.weight$", "decoder.positional_embedding"),
    (r"^model\.decoder\.embed_tokens\.", "decoder.token_embedding."),
    (r"^model\.encoder\.layer_norm\.", "encoder.ln_post."),
    (r"^model\.decoder\.layer_norm\.", "decoder.ln."),
    (r"^model\.encoder\.conv", "encoder.conv"),
    (r"^model\.(encoder|decoder)\.layers\.(\d+)\.", r"\1.blocks.\2."),
    (r"\.self_attn_layer_norm\.", ".attn_ln."),
    (r"\.encoder_attn_layer_norm\.", ".cross_attn_ln."),
    (r"\.final_layer_norm\.", ".mlp_ln."),
    (r"\.self_attn\.", ".attn."),
    (r"\.encoder_attn\.", ".cross_attn."),
    (r"\.q_proj\.", ".query."),
    (r"\.k_proj\.", ".key."),
    (r"\.v_proj\.", ".value."),
    (r"\.out_proj\.", ".out."),
    (r"\.fc1\.", ".mlp.0."),
    (r"\.fc2\.", ".mlp.2."),
]


def rename(k: str) -> str:
    for pat, rep in RULES:
        k = re.sub(pat, rep, k)
    return k


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("hf_dir")
    ap.add_argument("out")
    ap.add_argument("--fp16", action="store_true")
    a = ap.parse_args()
    d = Path(a.hf_dir)
    cfg = json.loads((d / "config.json").read_text())
    sd: dict[str, torch.Tensor] = {}
    for f in sorted(d.glob("model*.safetensors")):
        sd.update(load_file(str(f)))
    out = {}
    for k, v in sd.items():
        if k == "proj_out.weight":  # tied to the token embedding
            continue
        if a.fp16 and v.is_floating_point():
            v = v.half()
        out[rename(k)] = v
    dims = {
        "n_mels": cfg["num_mel_bins"],
        "n_vocab": cfg["vocab_size"],
        "n_audio_ctx": cfg["max_source_positions"],
        "n_audio_state": cfg["d_model"],
        "n_audio_head": cfg["encoder_attention_heads"],
        "n_audio_layer": cfg["encoder_layers"],
        "n_text_ctx": cfg["max_target_positions"],
        "n_text_state": cfg["d_model"],
        "n_text_head": cfg["decoder_attention_heads"],
        "n_text_layer": cfg["decoder_layers"],
    }
    torch.save({"dims": dims, "model_state_dict": out}, a.out)
    print(f"wrote {a.out}: {len(out)} tensors, dims {dims}")


if __name__ == "__main__":
    main()
