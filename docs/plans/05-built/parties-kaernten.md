# Built: Kärnten's five parties (parties-kaernten, 2026-11-02)

Wave D, followups-10 §3–5. Only Primorska had `celebrations/<age>.json`; Kärnten had the generic gathering with no dialog
(`AgeUps.generic`). Now each of its five ages has a Carinthian celebration, with a party at A1, A2, B1 and B2 and its own word
pack, at the lead's request above the format's A1 and A2 (B2 is the default aim). All of it is machine-written (Claude) and
flagged so in each file's `review`.

## What the learner gets

| Age | Celebration | Host, guests | Decor, music, place | Pack (words) | Tested in every level |
|---|---|---|---|---|---|
| Lager | 🔥 Der erste Abend am Feuer | Opa Sepp; Oma Resi, Hirte Florian, Lena | torches; `valcek`; fire | `feier-erster-abend` (9) | die Fackel, singen |
| Weiler | 🌲 Die Firstfeier | Müller Franz; Oma Resi, Hirte Florian, Tante Mitzi, Jakob | spruce, bunting; `valcek`; fire | `feier-firstfeier` (9) | der Firstbaum, das Band |
| Dorf | 🪗 Das Dorffest | Wirtin Kathi; Mostbauer Stefan, Hirte Florian, Maxi, Oma Resi | bunting, floor; `harmonika`; square | `feier-dorffest` (10) | das Glas, tanzen |
| Markt | 🎪 Der Wiesenmarkt | Mostbauer Stefan; Tante Mitzi, Oma Resi, Lena, Jakob | stalls, bunting; `harmonika`; square | `feier-wiesenmarkt` (10) | das Lebkuchenherz, kaufen |
| Stadt | 🎺 Das Stadtfest | Lehrerin Eva; Opa Sepp, Schmied Toni, Wirtin Kathi, Maxi | banners, bunting; `godba`; square | `feier-stadtfest` (10) | die Musikkapelle, die Fahne |

20 parties, 177 lines, 3 turns each (a word, a word, a toast). A few wrong choices at A1 to B1 name a grammar page
(`artikel-genus`, `akkusativ`, `modalverben`, `nebensatz`); the German book has no page above A2 yet, so B2 names none. Registers
as the cast has them: everyone says du to the learner, and what the learner says to Franz, Kathi, Toni and Eva avoids du. Every
word a tap meets is in `companion/lexicon/de.extra.json` (90 new entries, more forms on 27 others), with Slovene and Italian
meanings. What the stage looks like is `4dbd6960` (parties-friuli): the banners, garlands and awning are Austria's.

A village already past an age: the chronicle's "Watch again" shows the new celebration and offers its party. Nothing comes up by
itself (no party tonight, no decorations), an earlier age's party pays nothing, and the party of the village's own age pays once
when watched the first time, as in Primorska (`KaerntenCelebrationsTest`). An older app bundles the packs it was built with and an
older bridge never reads `celebrations/`, so Teo's tablet and a village in the middle of an age aren't touched.

## Why these, and not the Kirchtag

The brief names the Firstfeier, the Kirchtag and the Kufenstechen, "without repeating what the festivals already have as if new".
Kärnten's calendar already has `kirchtag` (the last Sunday of August, its soup) and `kufenstechen` (the Lindentanz with it). A party
called Kirchtag would be a second one in the year, on any date, and a Kirchtag is the church's day, while a village may reach the
age before it has built its church. So the Dorf's party is the **Dorffest**: the word the Gailtal uses for what follows the Kirchtag.
It is an ordinary word for a village party, not a named custom: this is the weakest of the five, and the one for Jan to overrule.
One B2 line says so aloud ("Bei uns im Gailtal gehört das Dorffest sonst zum Kirchtag").

## The sources

Read for this round, 2026-11-02. A claim the sources don't carry isn't in the content.

**The first evening.** The Kärntnerlied in several voices, the lead singer who leads and isn't a soloist, Koschat's quartets:
- https://austria-forum.org/af/AEIOU/Koschat,_Thomas (Vorsänger, "der Führer einer harmonisch-melodischen Einheit")
- https://austria-forum.org/web-books/koroska02de2016isds/000136 (the Carinthian song, its Slovene melodies)
- https://www.5min.at/villach/5202511011325/is-schon-still-uman-see-wuerdigung-fuer-kaerntens-heimliche-hymne/ (Is schon still uman See, "heimliche Hymne"). Its melody and text are in copyright (Mittergradnegger, Glawischnig): only the title is named.

That a first evening by a fire is itself a custom isn't claimed, as with Primorska's: the custom is the singing.

**The Firstfeier.**
- https://de.wikipedia.org/wiki/Richtfest ("in Österreich Gleichenfeier, Firstfeier/-fest oder Dachgleiche", the verse, the glass)
- https://sbg.lko.at/wenn-der-firstbaum-gestohlen-wird+2400+3654198 (Salzburg: the Firstbaum with colourful ribbons from the carpenters, only the roof covering missing, everyone who worked invited, the meal; the stolen tree)
- https://www.meinbezirk.at/tag/firstfeier and https://www.meinbezirk.at/tag/firstspruch (Tyrol and Salzburg: the Firstfeier, the Firstspruch)

**Not shown for Carinthia:** I found no Carinthian report that says Firstfeier. Carinthian reports found say Gleichenfeier
(https://www.meinbezirk.at/tag/gleichenfeier/6). The name is the brief's. A line says both exist, and it's first on the native list.

**The Dorffest.**
- https://austria-forum.org/af/AustriaWiki/Kufenstechen (the Kirchtag with "die Kirchtagssuppe und das anschließende Dorffest")
- https://www.unesco.at/en/culture/intangible-cultural-heritage/inventory/element/article/traditions-of-the-untergailtaler-kirchtag-and-traditional-costumes-in-the-untergailtal-zilski-zegen-in-ziljska-nosa (music and dance on the village square or at an inn, the local unmarried young people organising it)
- https://www.meinbezirk.at/villach/c-regionauten-community/villacher-kirchtag-start-in-die-79-brauchtumswoche_a6823029 ("der große Tanzboden aufgestellt", Villach)
- https://www.kaernten.at/kulinarik/typisch-kaerntnerisch/typische-kaerntner-gerichte/ (Kasnudeln)
- https://en.wikipedia.org/wiki/Austrian_folk_dance (the Styrian harmonica, polka and waltz: not Carinthia in particular)

**The Wiesenmarkt.**
- https://www.weekend.at/bundesland/kaernten/wissenswertes-659-st-veiter-wiesenmarkt ("Kärntens ältestes Volksfest", the Freyung two weeks before, "Symbol für freies Handeln", a gingerbread heart in a caption)
- https://www.5min.at/kaernten/5202609090530/zehn-tage-ausnahmezustand-das-erwartet-dich-heuer-am-st-veiter-wiesenmarkt/ (the Freyung "Rechtsdenkmal", the opening on the Hauptplatz, about 160 traders from Austria and neighbouring countries)
- https://www.meinbezirk.at/tag/freyung ("Symbol für freies Handeln und ehrliches Wirtschaften")
- https://www.visitcarinthia.at/destinations/places/bleiburg/ (Bleiburg's, every September since 1393)
- https://kaernten.orf.at/stories/3226306/ (the parade ends on the Hauptplatz, "wo der Herold seine Proklamation verlas")
- https://sv.or.at/fileadmin/user_upload/_Laufwerk_Stadt_SanktVeit/News-Upload/2023/Wiesenmarkt_Pressemappe_2023.pdf (what he reads, the Marktordnung): read only as a search result quoted it; the file has since moved and returned 404 when fetched. ORF confirms the herald and a proclamation, not that it is the Marktordnung.

**The Stadtfest.** One paper carries it, on the town elevations of the 20th century:
- https://www.historischerverein-stmk.at/wp-content/uploads/Z_SB26_Alfred-OGRIS-Stadterhebungen-in-K%C3%A4rnten-im-20.-Jahrhundert.pdf (Spittal 1930: bells for an hour, the houses flagged, a torch procession, the banquet; Hermagor: celebrated nine months later, "umso ausgiebiger"; St. Ruprecht: the band's marches; Feldkirchen: the formal session; charters, arms and flags)

A band *leading* a procession at an elevation isn't in that paper: it is from the Wiesenmarkt's and Bleiburg's parades
(https://www.meinbezirk.at/voelkermarkt/c-leute/624-bleiburger-wiesenmarkt-i_a2233372), and the decor draws it. "Das Stadtfest"
is a plain name for it: the sources say Stadterhebung and Stadterhebungsfeier.

## Checks

- `bun test/check-celebrations.ts kaernten`, `bun test/check-culture.ts kaernten`, `bun test/prompt-clashes.ts` (one new clash met and settled: das Standl has its own prompt beside der Marktstand).
- `KaerntenCelebrationsTest` (7) and the smoke's three new checks.
- `companion/bin/lexicon-build --coverage --lang de --all`: none of these words is left unknown.
- QA: `companion/bin/qa --steps party` (the shared step, 4e8e7bdd). See the report for what was played.

## Shared commits

`c40cc47c` (the validator counts a word with its article), `4e8e7bdd` (QA's `party`), both this branch's and on their own for the
others; `4dbd6960` (parties-friuli: the stage in the culture's colours) and `c572dc3a` (parties-lakeland: check-celebrations keeps
the dictionary's flags) cherry-picked.

## For a native check

Everything: all five files and packs, German, Slovene, English and Italian. First these:
- die Firstfeier against die Gleichenfeier in Carinthia, der Firstbaum, der Firstspruch, die Dachdeckung
- das Dorffest as this party's name; der Tanzboden; die Burschen; die Kasnudel against die Käsnudel
- das Standl, der Kramer, das Marktrecht, die Freyung as a common noun; sl "travniški sejem" and "znamenje Freyung"
- Hoch die Stadt! and Hoch soll sie leben! as what a crowd says; die Festtafel
- Stoßen wir an with tea or apple juice; sl "Na zdravje" for both Prost and Zum Wohl
- Mitzi's line that Bleiburg's market dates from 1393, Sepp's lines on Spittal and Hermagor: true, but do they sound like someone speaking?
- `de.extra.json`'s new meanings: sl šepav (holprig), it "poter andare" (infrage)

The Slovene is first-person neutral on the guests' lines so that a stand-in fits, and marked `{m:…|f:…}` for the learner: worth a read for stiffness.

## Voice

Not built. No ElevenLabs call was made. 360 new texts, **14,641 characters** for the parties' lines in their speakers' voices (one
line, "Prost!" or similar, is already voiced), and about 1,300 more for the 32 new pack words and their examples. About 16,000 in all.

## What's left

- Jan's call on the Dorffest's name, and a native check of the list above.
- A second source for what the herald reads being the Marktordnung (ORF says his proclamation).
- The German grammar book has no B1 or B2 page, so those parties' form turns can name only A2's. When `outlines-2` brings pages, a few B2 wrong choices could name them.
