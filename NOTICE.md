# Notice

**Lani** — a Slovene-first language learning game: an Android app with a pixel-art village, a Bun bridge, and a
Claude Code tutor.

Copyright (C) 2025-2026 Jan Guth

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
later version (SPDX: `AGPL-3.0-or-later`). It is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
[LICENSE](LICENSE) for the full text.

## Where it started

Lani began as a fork of [m98/fluent](https://github.com/m98/fluent), "the AI language learning kit for Claude Code",
by Mohammad Kermani and the Fluent contributors, published under the MIT License. The tutor's methodology and tracking
(`LEARNING_SYSTEM.md`, `PRACTICE.md`, the `.claude/` skills and hooks, `data-examples/`) grew from it. The parts that
come from it remain available under the MIT License as well; its notice is kept in
[LICENSES/MIT-fluent.txt](LICENSES/MIT-fluent.txt). Everything added since, and the work as a whole, is under the AGPL.

## Third-party material

| What | Where | Licence |
|---|---|---|
| Slovene dictionary data from Wiktionary, via [kaikki.org](https://kaikki.org/dictionary/Slovene/) | `companion/lexicon/sl.json` | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); the app and the bridge show the attribution with every lookup |
| Kalam font by the Indian Type Foundry (the story notebook's handwriting) | `companion/android/app/src/main/res/font/` | SIL Open Font License 1.1 (see `assets/fonts/CREDITS.md`) |
| Ambient sounds, synthesized from code by `companion/android/tools/ambient_sounds.py` | `companion/android/app/src/main/assets/ambient/` | CC0 1.0 (see `assets/ambient/CREDITS.md`) |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) by Next-gen Kaldi (the phone's offline voice: its VITS/Piper engine), the AAR `sherpa-onnx-static-link-onnxruntime` 1.13.8, downloaded by the build | linked into the Android app (`libsherpa-onnx-jni.so`) | [Apache-2.0](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE) |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) (linked into sherpa-onnx's library) | the Android app | [MIT](https://github.com/microsoft/onnxruntime/blob/main/LICENSE) |
| [espeak-ng](https://github.com/espeak-ng/espeak-ng) (the phonemes for Piper voices, linked into sherpa-onnx's library) and its data (`espeak-ng-data/`, a language's files downloaded with its offline voice) | the Android app; the bridge's cache and the phone (`files/piper/`) | [GPL-3.0-or-later](https://github.com/espeak-ng/espeak-ng/blob/master/COPYING), compatible with the AGPL-3.0-or-later of the whole |
| [piper-phonemize](https://github.com/rhasspy/piper-phonemize) (linked into sherpa-onnx's library) | the Android app | [MIT](https://github.com/rhasspy/piper-phonemize/blob/master/LICENSE) |
| The offline voices: [Piper](https://github.com/rhasspy/piper) voices of [rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices), downloaded by the bridge when the learner asks (not in this repository nor in the APK): Slovene `sl_SI-artur-medium`, Italian `it_IT-paola-medium`, German `de_DE-thorsten-medium`, English `en_GB-northern_english_male-medium` | the bridge's cache (`~/.cache/lani/piper/`) and the phone | each voice's dataset: Slovene [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) (the [ARTUR studio TTS](https://huggingface.co/datasets/ppisljar/artur_studio_tts/) dataset; the voice trained by ppisljar); Italian CC0 1.0 (paolapersico1/Voice-Dataset-Italian); German CC0 ([Thorsten-Voice](https://github.com/thorstenMueller/Thorsten-Voice)); English [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) ([OpenSLR 83](http://www.openslr.org/83/)). The app shows the voice's credit where it offers it and in its settings |

The pixel art (the village, the scenes, the people) is drawn by code in the app and is part of the program. Voices
are synthesized at run time (ElevenLabs, a self-hosted Gepard voice, or the phone's Piper voice) into the learner's own
data; no recordings are part of this repository.

Content marked `machine_written` (dialogs, stories, grammar pages, word tables) was written with Claude and has not all
been reviewed by a native speaker yet; corrections are welcome.
