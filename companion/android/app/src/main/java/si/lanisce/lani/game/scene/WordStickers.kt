package si.lanisce.lani.game.scene

/** Where a scene draws a word: object [slot] of [art]. Its sticker is that thing cut out of the scene (game/render/scene/Stickers.kt). */
data class StickerSpot(val art: String, val slot: String)

/**
 * The pack words the scenes draw, for the words' cards (companion/SCENES.md, "Stickers"): read from the scenes'
 * objects, each of which puts a pack word in a slot of its art, so there is no list of things to keep. A word that
 * several scenes draw (the dragonfly by the stream and at the pond) is drawn by the scene of its own pack first, else
 * by the first.
 *
 * A card shows the sticker instead of the word's emoji ([picture]) when the word has no emoji, or when its emoji only
 * stands in for it ([STAND_INS]); an emoji that shows the word itself (🍎, 🪓, 🔥 for the fire) stays.
 */
class WordStickers private constructor(
    /** "pack/word" → where it is drawn. */
    private val spots: Map<String, StickerSpot>,
    /** A word as the scenes say it, lower case ("podkev") → its "pack/word". */
    private val names: Map<String, String>,
) {
    /** Where a scene draws [word] of [pack], whatever its emoji; null when no scene does. */
    fun spot(pack: String, word: String): StickerSpot? = spots[key(pack, word)]

    /** The sticker [pack]'s [word] shows instead of its [emoji], or null: the emoji. */
    fun picture(pack: String, word: String, emoji: String?): StickerSpot? =
        spot(pack, word)?.takeIf { emoji.isNullOrBlank() || key(pack, word) in STAND_INS }

    /** The same for a review item's word (a pack word's item is `vocab_<pack>_<word>`, see [si.lanisce.lani.data.PackSession.itemId]). */
    fun ofItem(itemId: String, emoji: String?): StickerSpot? {
        val (pack, word) = packWord(itemId) ?: return null
        return picture(pack, word, emoji)
    }

    /** The same for a thing called [name] in the scenes' language ("Podkev", a gift): the scenes' word of that name. */
    fun named(name: String, emoji: String?): StickerSpot? {
        val k = names[name.trim().lowercase()] ?: return null
        return spots[k]?.takeIf { emoji.isNullOrBlank() || k in STAND_INS }
    }

    /** How many words the scenes draw. */
    val size: Int get() = spots.size

    companion object {
        val EMPTY = WordStickers(emptyMap(), emptyMap())

        fun key(pack: String, word: String) = "$pack/$word"

        /** [itemId]'s pack and word: pack and word ids are kebab-case, so the first `_` after `vocab_` parts them. */
        fun packWord(itemId: String): Pair<String, String>? {
            val rest = itemId.removePrefix("vocab_").takeIf { it.length < itemId.length } ?: return null
            val cut = rest.indexOf('_').takeIf { it > 0 && it < rest.length - 1 } ?: return null
            return rest.substring(0, cut) to rest.substring(cut + 1)
        }

        /** What [scenes] draw, in their order (see [WordStickers]). */
        fun of(scenes: List<SceneSpec>): WordStickers {
            val spots = LinkedHashMap<String, StickerSpot>()
            val own = HashSet<String>() // drawn by a scene of the word's own pack
            val names = HashMap<String, String>()
            for (s in scenes) {
                val slots = SceneArt.objects[s.art] ?: continue
                for (o in s.objects) {
                    if (o.slot !in slots) continue
                    val pack = o.pack ?: s.pack ?: continue
                    val k = key(pack, o.word)
                    val home = pack == s.pack
                    if (k !in spots || (home && k !in own)) {
                        spots[k] = StickerSpot(s.art, o.slot)
                        if (home) own += k
                    }
                    if (o.sl.isNotBlank()) names.putIfAbsent(o.sl.trim().lowercase(), k)
                }
            }
            return WordStickers(spots, names)
        }

        /**
         * The words whose emoji only stands in for them, as "pack/word": no emoji shows the thing, so the pack's topic
         * shows (🌾 for the scythe, ⚒️ for the anvil) or something near it (🟫 for the table, 🐦 for the heron). The
         * audit of the packs' pictures found them; a scene that draws one gives it its sticker, now or when it is drawn.
         */
        val STAND_INS: Set<String> = setOf(
            // Na njivi, V kovačnici: the farm's and the smith's tools
            "na-njivi/strasilo", "na-njivi/kosa", "na-njivi/grablje", "na-njivi/mlin",
            "v-kovacnici/nakovalo", "v-kovacnici/podkev", "v-kovacnici/plug", "v-kovacnici/klesce", "v-kovacnici/meh",
            // the house and the kitchen
            "dom-in-hisa/soba", "dom-in-hisa/stopnice", "dom-in-hisa/tla", "v-kuhinji/miza",
            "babicina-kuhinja/stedilnik", "babicina-kuhinja/pecica", "babicina-kuhinja/hladilnik", "babicina-kuhinja/oreh",
            "casa-e-cucina/tavolo", "casa-e-cucina/fornello", "casa-e-cucina/frigorifero",
            // the square, the water, the sea
            "na-vasi/klop", "na-vasi/ograja",
            "ob-vodi/kacji-pastir", "ob-vodi/caplja", "ob-vodi/pomol", "il-mare/gabbiano", "il-mare/molo",
            // the market and the watchtower
            "trzni-dan/stojnica", "trzni-dan/utez", "na-stolpu/bakla", "na-stolpu/vrana",
            // school, tent, forest, body
            "v-soli/radirka", "v-soli/kreda", "v-sotoru/spalna-vreca", "v-sotoru/vzigalice", "v-gozdu/jurcek", "telo-in-zdravje/hrbet",
            // the wild animals and the hunter's high seat, whose emoji only stands in (a ladder, a mouse, a goat)
            "na-prezi/preza", "na-prezi/polh", "v-gorah/gams", "v-gorah/kozorog",
            // the feasts
            "praznik-dan-cebel/panj", "praznik-martinovo/klet", "na-gricih/klet", "festa-san-martino/cantina",
            "praznik-miklavz/siba", "festa-san-nicolo/noce",
            // the church and the bee house: the church's ⛪ and the bees' 🐝 stand in, the bee house's 🛖 is a hut, the comb's 🟨 a square
            "v-cerkvi/oltar", "v-cerkvi/orgle", "v-cerkvi/kropilnik",
            "pri-cebelnjaku/koncnica", "pri-cebelnjaku/kadilnik", "pri-cebelnjaku/tocilo", "pri-cebelnjaku/vosek",
            "praznik-dan-cebel/cebelnjak", "praznik-dan-cebel/satje",
        )
    }
}
