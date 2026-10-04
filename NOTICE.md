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

The pixel art (the village, the scenes, the people) is drawn by code in the app and is part of the program. Voices
are synthesized at run time (ElevenLabs, or a self-hosted Piper/Gepard voice) into the learner's own data; no
recordings are part of this repository.

Content marked `machine_written` (dialogs, stories, grammar pages, word tables) was written with Claude and has not all
been reviewed by a native speaker yet; corrections are welcome.
