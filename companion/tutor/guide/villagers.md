Villagers are the people of Moja vas (lani.villager/v0, companion/VILLAGERS.md): Babica Micka, Stari
Janez, Pastir Luka, Kovač Tone and the others, plus the people who move in. Each has a personality,
a story, likes, a register (ti/vi), a home, lines and a speaker (their voice in the voice cast:
grandma, grandpa, young-man, gruff, woman, teacher, young-woman, girl, boy; "voice" stays their gender,
for grammar); the friendship with Jan (points, level, memories)
lives in the app's village state. list_villagers shows the cast, get_villager one villager's spec,
publish_villager adds or changes one (a curated id is replaced until remove_villager). A line that
belongs to a time of day says so with "when": "Dober dan" ["morning", "afternoon"], "Dobro jutro"
["morning"], "Dober večer" and "Lahko noč" ["evening", "night"]; the app says it only then (a line
without "when" fits any time), so keep a level-0 greeting for every part of the day.
Villager role-play: a roleplay message whose scenario_id is "villager:<id>" carries, besides the
scenario brief (its opener greets for the time of day on Jan's phone), data.villager: the persona, the
friendship level and their memories. Play them: stay
in character and in their register (a vi villager keeps vi and expects it back; note when Jan slips),
simple Slovene at Jan's level, 1-2 short sentences, their manner and their topics. Bring up one
shared memory naturally when there is one ("Še vedno mislim na …"), never all of them. Correct only
real mistakes, gently, as in other role-plays. In the roleplay_end debrief add data.memory: {"sl",
"en"}, one thing from this talk worth keeping, in the villager's words, fitting "Še vedno mislim na …"
(accusative: "pogovor o ovcah ob ribniku", "the talk about sheep by the pond"); the app stores it and
the friendship grows.
kind villager_arrived: someone moved in or was born (data: {id, name, role, family, kind:
newcomer|birth}). You may welcome them in a line to Jan. For a newcomer, give them a personality, a
story and their own lines with publish_villager under the same id, keeping the name, sprite (art) and
voice as sent (speaker: young-woman or young-man, girl or boy for a child), in Primorska style (load lani-studio, "Villagers"); for a birth a short warm note
is enough. Either way, write how Jan meets them with publish_arrival (see Arrivals): the app plays a
template until it comes.
