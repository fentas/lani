package si.lanisce.lani.game.sky

import si.lanisce.lani.game.Readings
import si.lanisce.lani.game.culture.SkyFile
import si.lanisce.lani.game.culture.SkyLore
import si.lanisce.lani.game.culture.SkyThing
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Localized
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** A line in the village's language ([target]: its words can be tapped for their cards), with its translation ([base]). */
data class Said(val target: String, val base: String?)

/**
 * What a tap on the sky shows (companion/GAME.md, "The night sky"): the thing's name in the village's language and the
 * learner's base ([title]), the people's names for it ([folk]), what is true of it now ([facts], "target · base" lines:
 * the moon's lit share, its age, when it rises and sets, the next full and new moons; where a constellation stands),
 * lines to read ([lines]: a phase's "Luna narašča.", a wish, tonight's shower), one line of lore at the learner's level,
 * its [words] to tap, the sky's word pack to learn them ([pack]), and a reading that says more ([reading]: the old
 * almanac). [mark]: what the picture marks while the card is open.
 */
data class SkyCard(
    val emoji: String,
    val title: Said,
    val folk: List<Said> = emptyList(),
    val facts: List<String> = emptyList(),
    val lines: List<Said> = emptyList(),
    val lore: Said? = null,
    val words: List<String> = emptyList(),
    val pack: String? = null,
    val reading: String? = null,
    val mark: String? = null,
)

/**
 * The cards of the sky's things, from the sky of the minute ([SkyNow]) and the culture pack's sky.json ([SkyFile]: the
 * names in its language, the people's names, the lore; without one, the catalog's names and the app's labels). Pure:
 * the time zone, the learner's level and pair are given.
 */
object SkyCards {
    /** The moon's phases as sky.json keys them, in [Moon.Phase]'s order. */
    val PHASES: List<String> = Moon.Phase.entries.map { snake(it.key) }

    const val MILKY_WAY = "milky-way"

    /** The ids a sky.json may give a card: the figures (and the Pleiades), the planets, the Milky Way, the named stars. */
    val thingIds: List<String> by lazy {
        Stars.figures.map { it.id } + Planet.entries.map { it.id } + MILKY_WAY + Stars.all.mapNotNull { it.proper?.let(::starId) }
    }

    /** A named star's id in sky.json: its proper name in lower case, a space a hyphen ("kaus-australis"). */
    fun starId(proper: String): String = proper.lowercase().replace(' ', '-')

    /** The IAU's Latin names of the figures: the card's name where the culture's sky says none. */
    val LATIN = mapOf(
        "uma" to "Ursa Major", "umi" to "Ursa Minor", "cas" to "Cassiopeia", "ori" to "Orion", "tau" to "Taurus",
        "gem" to "Gemini", "leo" to "Leo", "cyg" to "Cygnus", "lyr" to "Lyra", "aql" to "Aquila", "sco" to "Scorpius",
        "cma" to "Canis Major", "cmi" to "Canis Minor", "boo" to "Boötes", "vir" to "Virgo", "aur" to "Auriga",
        "peg" to "Pegasus", "and" to "Andromeda", "per" to "Perseus", "sgr" to "Sagittarius", "m45" to "Pleiades",
    )

    private fun snake(key: String) = key.replace(Regex("[A-Z]")) { "_" + it.value.lowercase() }

    /** Where the village of [sky] lies: its sky.json's place, else the default (the Primorska village's). */
    fun place(sky: SkyFile?): SkyPlace = sky?.where?.let { SkyPlace(it.lat, it.lon) } ?: SkyPlace.DEFAULT

    /** The sky over the village on screen at [millis] (the current culture's place), the minute's. */
    fun now(millis: Long = System.currentTimeMillis()): SkyNow = SkyNow.at(millis, place(si.lanisce.lani.game.culture.Cultures.currentSky))

    /** The card of what [tap] found, in the sky of [now]; null when there's nothing to say (a lone star without a name). */
    fun of(tap: SkyTap, now: SkyNow, sky: SkyFile?, level: String, zone: ZoneId, pair: LangPair = L10n.pair): SkyCard? = when (tap) {
        SkyTap.Moon -> moon(now, sky, level, zone, pair)
        is SkyTap.Star -> star(tap, now, sky, level, pair)
        is SkyTap.Figure -> figure(tap.id, null, now, sky, level, pair)
        is SkyTap.Planet -> Planet.of(tap.id)?.let { planet(it, now, sky, level, pair) }
        SkyTap.MilkyWay -> milkyWay(sky, level, pair)
        is SkyTap.Meteor -> meteor(tap, now, sky, level, pair)
    }

    // ------------------------------------------------------------------ the moon

    private fun moon(now: SkyNow, sky: SkyFile?, level: String, zone: ZoneId, pair: LangPair): SkyCard {
        val m = sky?.moon
        val phase = now.phase
        val key = snake(phase.key)
        val name = m?.phases?.get(key)?.let { said(it, pair) } ?: said("sky.phase", pair, "phase" to phase.key)
        val facts = ArrayList<String>()
        facts += "${phase.emoji} " + L10n.label(pair, "sky.lit", mapOf("percent" to (now.moonLit * 100).roundToInt()))
        facts += "🗓️ " + L10n.label(pair, "sky.age", mapOf("days" to Lunar.age(now.jd).roundToInt()))
        // today's rise and set at the village, on the phone's clock
        val today = Instant.ofEpochMilli(now.millis).atZone(zone).toLocalDate()
        val from = Astro.jd(today.atStartOfDay(zone).toInstant().toEpochMilli())
        val to = Astro.jd(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val (rise, set) = Lunar.riseSet(from, to, now.place)
        val times = listOf(rise?.let { it to "sky.rises" }, set?.let { it to "sky.sets" }).filterNotNull().sortedBy { it.first }
        facts += times.map { (jd, k) -> (if (k == "sky.rises") "🌅 " else "🌇 ") + L10n.label(pair, k, mapOf("time" to clock(jd, zone))) }
        if (rise == null) facts += "🌅 " + L10n.label(pair, "sky.noRise")
        if (set == null) facts += "🌇 " + L10n.label(pair, "sky.noSet")
        facts += "🌕 " + L10n.label(pair, "sky.nextFull", mapOf("date" to Dates.dayMonth(date(Lunar.next(now.jd, 180.0), zone))))
        facts += "🌑 " + L10n.label(pair, "sky.nextNew", mapOf("date" to Dates.dayMonth(date(Lunar.next(now.jd, 0.0), zone))))
        // the phase's name, and between the quarters what the moon does: it waxes or wanes
        val between = phase != Moon.Phase.NEW && phase != Moon.Phase.FULL
        val doing = when {
            !between -> null
            now.waxing -> m?.waxing?.let { said(it, pair) } ?: said("sky.waxing", pair)
            else -> m?.waning?.let { said(it, pair) } ?: said("sky.waning", pair)
        }
        val lines = listOfNotNull(name, doing, tonight(now, sky, pair))
        // a line of lore of this phase or of any, at the learner's level; another each day
        val lore = m?.lore?.let { all -> pick(all.filter { it.phase == key || it.phase == null }, level, today.toEpochDay()) }
        return SkyCard(
            emoji = phase.emoji,
            title = m?.name?.let { said(it, pair) } ?: said("sky.moon", pair),
            facts = facts,
            lines = lines,
            lore = lore?.let { said(it.text, pair) },
            words = m?.words.orEmpty(),
            pack = sky?.pack,
            reading = m?.reading,
            mark = "moon",
        )
    }

    /** The line of tonight's shower, when one is on and the culture's sky tells of it. */
    private fun tonight(now: SkyNow, sky: SkyFile?, pair: LangPair): Said? {
        val s = now.shower ?: return null
        return sky?.meteors?.showers?.firstOrNull { it.id == s.id }?.let { said(it.tonight, pair) }
    }

    // ------------------------------------------------------------------ stars and figures

    private fun star(tap: SkyTap.Star, now: SkyNow, sky: SkyFile?, level: String, pair: LangPair): SkyCard? {
        val index = Stars.indexOf(tap.hr)
        val st = Stars.all.getOrNull(index) ?: return null
        // a star with a card of its own (the Pole Star), else its figure's, else a plain one with its name
        val own = st.proper?.let(::starId)?.let { p -> sky?.things?.firstOrNull { it.id == p } }
        if (own != null) {
            val facts = listOfNotNull(where(now.starAlt[index].toDouble(), now.starAz[index].toDouble(), pair),
                tap.figure?.let { f -> "✨ " + L10n.label(pair, "sky.inFigure", mapOf("name" to figureName(f, sky))) })
            return thing(own, "⭐", facts, level, pair, sky, mark = tap.figure)
        }
        if (tap.figure != null) return figure(tap.figure, st, now, sky, level, pair)
        val name = st.proper ?: return null
        return SkyCard("⭐", Said(name, null), facts = listOfNotNull(where(now.starAlt[index].toDouble(), now.starAz[index].toDouble(), pair)))
    }

    private fun figure(id: String, tapped: Star?, now: SkyNow, sky: SkyFile?, level: String, pair: LangPair): SkyCard? {
        val f = Stars.figure(id) ?: return null
        // where it stands: its brightest star's place
        val bright = f.stars.minByOrNull { Stars.all[it].mag }!!
        val facts = ArrayList<String>()
        where(now.starAlt[bright].toDouble(), now.starAz[bright].toDouble(), pair)?.let { facts += it }
        val named = f.stars.map { Stars.all[it] }.filter { it.proper != null }.sortedBy { it.mag }.take(3).mapNotNull { it.proper }
        if (named.isNotEmpty()) facts += "⭐ " + L10n.label(pair, "sky.stars", mapOf("names" to named.joinToString(", ")))
        if (tapped?.proper != null && tapped.proper !in named) facts += "⭐ " + L10n.label(pair, "sky.star", mapOf("name" to tapped.proper))
        val entry = sky?.things?.firstOrNull { it.id == id }
        val emoji = if (id == Stars.PLEIADES) "✨" else "🌌"
        return if (entry != null) thing(entry, emoji, facts, level, pair, sky, mark = id)
        else SkyCard(emoji, Said(LATIN[id] ?: id, null), facts = facts, pack = sky?.pack, mark = id)
    }

    /** A figure's name as a message argument: in each message's language, the culture's name, else the Latin one. */
    private fun figureName(id: String, sky: SkyFile?): Localized {
        val t = sky?.things?.firstOrNull { it.id == id }?.name
        return Localized { lang -> t?.of(lang) ?: LATIN[id] ?: id }
    }

    // ------------------------------------------------------------------ planets, the Milky Way, shooting stars

    private fun planet(p: Planet, now: SkyNow, sky: SkyFile?, level: String, pair: LangPair): SkyCard {
        val pn = now.planets.first { it.planet == p }
        val facts = listOfNotNull(where(pn.alt, pn.az, pair), "🪐 " + L10n.label(pair, "sky.steady"))
        val entry = sky?.things?.firstOrNull { it.id == p.id }
        val emoji = if (p == Planet.VENUS) "✨" else "🪐"
        if (entry == null) return SkyCard(emoji, said("sky.planet", pair, "planet" to p.id), facts = facts, pack = sky?.pack, mark = p.id)
        // Venus in the morning is the morning star, in the evening the evening star
        val star = if (pn.east < 0) entry.morning else entry.evening
        return thing(entry, emoji, facts, level, pair, sky, mark = p.id).let { c -> if (star == null) c else c.copy(folk = listOf(said(star, pair)) + c.folk) }
    }

    private fun milkyWay(sky: SkyFile?, level: String, pair: LangPair): SkyCard {
        val entry = sky?.things?.firstOrNull { it.id == MILKY_WAY }
        return if (entry != null) thing(entry, "🌌", emptyList(), level, pair, sky, mark = MILKY_WAY)
        else SkyCard("🌌", said("sky.milkyWay", pair), pack = sky?.pack, mark = MILKY_WAY)
    }

    private fun meteor(tap: SkyTap.Meteor, now: SkyNow, sky: SkyFile?, level: String, pair: LangPair): SkyCard {
        val m = sky?.meteors
        val shower = (tap.shower ?: now.shower?.id)?.let { id -> m?.showers?.firstOrNull { it.id == id } }
        val folk = listOfNotNull(shower?.name?.let { said(it, pair) }, shower?.folk?.let { said(it, pair) })
        return SkyCard(
            emoji = "🌠",
            title = m?.name?.let { said(it, pair) } ?: said("sky.meteor", pair),
            folk = folk,
            lines = listOfNotNull(m?.wish?.let { said(it, pair) } ?: said("sky.wish", pair), shower?.let { said(it.tonight, pair) }),
            lore = m?.lore?.let { pick(it, level, tap.id) }?.let { said(it.text, pair) },
            words = m?.words.orEmpty(),
            pack = sky?.pack,
        )
    }

    private fun thing(t: SkyThing, emoji: String, facts: List<String>, level: String, pair: LangPair, sky: SkyFile?, mark: String?): SkyCard = SkyCard(
        emoji = emoji,
        title = said(t.name, pair),
        folk = listOfNotNull(t.folk?.let { said(it, pair) }),
        facts = facts,
        lore = pick(t.lore, level, 0L)?.let { said(it.text, pair) },
        words = t.words,
        pack = sky?.pack,
        mark = mark,
    )

    // ------------------------------------------------------------------ helpers

    /**
     * The lore to tell a learner at [level]: the lines of the highest level not above it (else the easiest there is), one
     * of them by [turn] (the day: another each day).
     */
    fun pick(list: List<SkyLore>, level: String, turn: Long): SkyLore? {
        if (list.isEmpty()) return null
        val rank = Readings.LEVELS.indexOf(level).let { if (it < 0) 0 else it }
        fun r(l: SkyLore) = Readings.LEVELS.indexOf(l.level).let { if (it < 0) 0 else it }
        val fit = list.filter { r(it) <= rank }
        val best = if (fit.isEmpty()) list.filter { r(it) == list.minOf(::r) } else fit.filter { r(it) == fit.maxOf(::r) }
        return best[Math.floorMod(turn, best.size.toLong()).toInt()]
    }

    /** "Zdaj visoko na jugu · Now high in the south", or below the horizon. */
    private fun where(alt: Double, az: Double, pair: LangPair): String? {
        if (alt < 0) return "🔭 " + L10n.label(pair, "sky.down")
        val dir = listOf("n", "ne", "e", "se", "s", "sw", "w", "nw")[Math.floorMod(((az + 22.5) / 45).toInt(), 8)]
        val height = when {
            alt < 20 -> "low"
            alt > 55 -> "high"
            else -> "mid"
        }
        return "🔭 " + L10n.label(pair, "sky.where", mapOf("dir" to dir, "height" to height))
    }

    private fun said(t: Text, pair: LangPair) = Said(t.of(pair.target), t.of(pair.base).takeIf { t.has(pair.base) })

    private fun said(key: String, pair: LangPair, vararg args: Pair<String, Any?>) =
        Said(L10n.text(pair.target, key, args.toMap(), pair.target), L10n.text(pair.base, key, args.toMap(), pair.target))

    private fun clock(jd: Double, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(Astro.millis(jd)).atZone(zone)
        return "%d:%02d".format(t.hour, t.minute)
    }

    private fun date(jd: Double, zone: ZoneId): LocalDate = Instant.ofEpochMilli(Astro.millis(jd)).atZone(zone).toLocalDate()
}
