Audio is automatic: every Slovene word, example, module line, scenario opener, scene dialog line,
villager line and role-play line is voiced by the bridge (ElevenLabs, or the local Gepard model, cached on the node) and played by the
app, which falls back to phone TTS. Some character clips the Gepard model made are made again in a batch by the local Drama 3 model
(companion/bin/voice-build --drama3, Jan's call; Built with Fish Audio): ElevenLabs' clips come first, then Drama 3's, then
Gepard's. Don't add audio files or TTS notes yourself; voice_status shows
coverage, quota, which engines are up and the voice cast. Generic texts use the narrators (female,
male); each villager speaks with their speaker from the cast (grandma, grandpa, young-man, gruff,
woman, teacher, young-woman, girl, boy), set in the villager's "speaker" field ("voice" stays their
gender). A scenario may set "speaker" the same way.
One voice per person: everyone who speaks has a voice profile (voice_profiles). The cast who live in the
village get a voice of their own, designed from a Slovene sample of their lines, while the ElevenLabs
account has voice slots (the rest speak with their archetype at their own pitch and pace); their lines
are voiced in it as they are first heard. When Jan says a voice doesn't suit someone, voice_redesign
gives them a new one (optionally described: age, timbre, manner).
When Jan says a word or line sounds wrong (English sounds, a bad take), they can have it recorded again themselves: a long
press on its 🔊 in the app (once a clip a day, within the day's re-voice cap; voice_status shows today's count).
A voice that hisses (Stari Janez's own) has its clips filtered: "denoise" in companion/voice-cast.json (voice_status
shows the voices filtered and their clips still waiting).
Every clip is brought to one loudness (−18 LUFS) when it is stored, so no voice is louder than the others; voice_status
shows the clips still waiting (level), which companion/bin/voice-build --level levels.
Every clip's silence at its edges is trimmed when it is stored (0.05 s kept before the sound, 0.1 s after it), so a word
starts at once; voice_status shows the clips still waiting (edges), which companion/bin/voice-build --edges trims. The
local Gepard voice keeps its own pace.
In the Slovene village the narrators (female, male) speak with their native Slovene ElevenLabs voices (Nina, Luka), the
local Gepard voice as their fallback (ana for a lone word, nina for a sentence; marko), never with the English library
voices; a narrator's local clip stays when the ElevenLabs one is made, and the ElevenLabs ones are made as the app asks
for them, within the day's re-voice cap and above the reserve (voice_status: narrators). The villagers keep their voices.
The learner has a voice too: their own lines in a dialog (a choice tapped, a turn typed right, an echo gone on from) are
said in it before the reply comes, the "player" of companion/voice-cast.json by the profile's learner.gender (in the
Slovene village Luka for a man, Nina for a woman, Nina while the profile says none; voice_status: player). A line the
learner said into the microphone isn't said again; their line's 🔊 plays it in their voice.
The school's guided introductions are read out too, in the teacher's voice: each step's explanation in the learner's own
language, the Slovene forms in it, one clip made by ElevenLabs as the app first asks for it (never from the reserve; the app
reads the step with the phone's voice and the Slovene clips meanwhile); voice_status counts them apart (explanations). The
Slovene the steps read (examples, quick questions and their answers, the forms) is voiced ahead like the other content.
