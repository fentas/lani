# Ambient sounds

Every file in this folder is synthesized from code by `companion/android/tools/ambient_sounds.py` in this repository.
The files contain no recordings and no third-party samples.

The files are dedicated to the public domain under CC0 1.0 (https://creativecommons.org/publicdomain/zero/1.0/).

| File | What it is | Period | How it is made |
|---|---|---|---|
| birds.ogg | Daytime birdsong in the countryside: a sparrow, a great tit, two chaffinches, a blackbird and short contact calls, and softer birds far off | 36 s | Sine notes that follow frequency contours, with a weak 2nd harmonic and a slight vibrato. Distance comes from level, low-pass and reverb. |
| crickets.ogg | Field crickets on a warm night, and a faint tree-cricket trill far off | 20 s | Four crickets: chirps of 3–4 short sine pulses at 4.2–5.0 kHz. The trill is a 2.9 kHz tone, pulsed 45 times a second. A short reverb. |
| owl.ogg | A tawny owl at night: the male's hoots and the female's "ke-wick", with silence between | 40 s | Sine notes with weak harmonics and a band of breath noise. A 1.3 s reverb. |
| rain.ogg | Steady moderate rain | 16 s | Band-limited pink noise with slow swells, tiny noise bursts and pings for droplets, damped tones for drops on leaves, and a low band of noise for rain further off. |
| stream.ogg | A babbling brook | 18 s | A few hundred short sine "bubbles" a second that glide up and die away, over band-passed noise. |
| fire.ogg | A small crackling campfire | 20 s | A low band of noise that flickers, groups of tiny clicks (windowed noise), soft pops with a short hiss, and a faint hiss. |
| wind.ogg | Wind in the mountains, soft gusts | 30 s | Pink-brown noise through a slowly moving band-pass, slow gusts, and a narrow whistle band in the strongest gusts. |
| sea.ogg | Small waves on a sandy lagoon shore | 32 s | About four waves of noise under a moving low-pass (swell, soft break, long hiss as the wave runs back), foam grains and a quiet surf. |
| frogs.ogg | Tree frogs and pond frogs on a spring night | 24 s | Three tree frogs take turns with buzzy "kre" notes (1.2–1.9 kHz tones, pulsed 100–150 times a second). Two pond frogs make short rolling croaks (380–580 Hz). A short reverb. |

Each loop is built round a circle, so it repeats exactly: noise is shaped in the frequency domain, slow changes have
whole cycles per loop, events that run past the end continue at the start, and the reverb is a circular convolution.

## One-shots

Short sounds that play once: the thunder after a flash of lightning, and what a tap or a dialog's effect sets off in
a scene. They are synthesized by the same script, from simple tones and filtered noise. No recordings, no samples.

| File | What it is | Length | How it is made |
|---|---|---|---|
| thunder-1.ogg | Thunder far off: a long roll with three swells | 6.5 s | A low body (50–300 Hz) and a grumble (150–850 Hz) of noise that roll at random, under slow swells that rise over half a second or more, a few soft rips of band noise, and a long dark reverb. Low-passed at 2.5 kHz. |
| thunder-2.ogg | Thunder far off: a softer crack, then the roll | 5.5 s | As thunder-1, with a brighter first swell (300–1600 Hz) that rises over 0.3 s. |
| thunder-3.ogg | Thunder far off: deep and short | 4.2 s | As thunder-1, darker (a grumble up to 650 Hz), two swells. |
| bell.ogg | One strike of a village church bell | 2.8 s | The partials of a church bell (hum, prime at 350 Hz, minor third, quint, nominal and upper partials), each doubled a fraction of a Hz apart so it beats; the upper partials die first. A 4 ms rise and the clapper's soft thud. |
| bell-far.ogg | The same bell heard from far away | 3.0 s | The bell low-passed at 1.8 kHz, a 20 ms rise, in a long reverb. |
| anvil.ogg | A hammer's tap on an anvil | 1.3 s | Six inharmonic partials of steel (0.9–4.5 kHz) that ring for about a second, and the hammer's dull tap. |
| cowbell.ogg | One clank of an alpine cowbell | 0.6 s | Inharmonic partials of sheet iron (300 Hz – 2.5 kHz) that die fast, the clapper's second touch 35 ms later, a clack of noise and a slight rattle. |
| hoot.ogg | A tawny owl's call, short: "hu … hu-hu-hooo" | 1.6 s | The owl loop's notes (sines with weak harmonics and breath noise), a little reverb. |
| whistle.ogg | An alpine marmot's alarm whistle | 1.1 s | A sine gliding 2.35 → 2.85 → 2.5 kHz in 0.3 s with a little breath, and two fainter echoes off the slopes. |
| lid.ogg | A pot's lid rattling as the water boils | 1.3 s | Nine small metal clinks, one every 0.125 s (short inharmonic ticks at 2.4–5.6 kHz and a lower tock), and a soft puff of steam (band noise). |
| purr.ogg | A cat purring, two breaths | 2.6 s | Pulses 26 and 23.5 times a second, each a short burst of noise and two short rings (about 160 and 420 Hz), through a band of 130 Hz – 1.1 kHz. |
| whoosh.ogg | A fire catching and flaring up | 1.2 s | A swell of noise that brightens to 2 kHz and darkens again, a low puff of air, and a few crackles as in the fire loop. |

## Format

- Ogg Vorbis (ffmpeg libvorbis, quality 1: `-q:a 1`), mono, 24000 Hz.
- Each loop file holds one loop of P seconds, with 0.25 s (6000 samples) of the loop's end before it and 0.25 s of the
  loop's start after it. The player cuts one period out of the middle.
- Each loop is set to −26 LUFS (EBU R128), with the true peak at −3 dBTP or lower.
- The one-shot files have no pads. Each starts with 8 ms of silence (the codec's pre-echo of a sharp onset falls
  there), then the sound, and ends in silence.
- Each one-shot is set by its loudest momentary loudness (EBU R128 M, 400 ms): −28 LUFS, the thunders −26 LUFS. So
  none is louder than a loop at its full level. The true peak is at −3 dBTP or lower.

## Regenerate

```
uv run companion/android/tools/ambient_sounds.py              # the loops and the one-shots
uv run companion/android/tools/ambient_sounds.py --shots      # only the one-shots
uv run companion/android/tools/ambient_sounds.py --only-shots bell anvil
uv run companion/android/tools/ambient_sounds.py --only rain  # only these loops
```

ffmpeg with libvorbis must be on the PATH. The random seeds are fixed, so a new run makes the same sounds (and, with
the same ffmpeg and libvorbis, the same files). `--preview-shots DIR` also writes 60-second MP3s of the thunder in a
storm and of the one-shots in their scenes, to listen to (ffmpeg needs libmp3lame for them).
