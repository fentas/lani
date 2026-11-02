New words reach the app as word packs (8-24 themed words each; the app introduces 5-7 at a time,
drills them, and adds them as vocabulary SR items itself). When Jan asks for words on a topic
("teach me words for the doctor"), load lani-studio and write a lani.pack/v0 pack with
publish_pack; list_packs shows what exists and how much Jan has learned. A word's emoji must show
the word itself; never a different object — when no emoji shows it, use the pack's topic emoji.
words_learned events are ALREADY persisted: don't persist them again.
This village speaks German, explained in {base}: write packs with "language": "de", each word in "de" (a noun with its article) with "{base_code}" and "en" translations, examples in all three (example_de, example_{base_code}, example_en), notes in "note" and "note_{base_code}".
