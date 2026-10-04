package si.lanisce.lani.ui.villagers

import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.Chest
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GoodSpec
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.at
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.inBase
import java.time.LocalDate

/** How a villager takes a good (companion/VILLAGERS.md, "Giving a good"): one they like, an ordinary one, a rare one. */
enum class GiftReaction { LIKED, ORDINARY, RARE }

/** Something Jan can say as they hand a gift over: said ([target]), its translation ([base]); [why] a wrong one is wrong. */
data class GiftPhrase(val target: String, val base: String, val right: Boolean, val why: String? = null)

/**
 * The gift moment as it plays (GiftScene): who gets what ([villager], [good]) and how they'll take it ([reaction]); what
 * Jan can say ([choices]; [tried]: the wrong ones they picked, [why]: the last one's why); then what they said ([said]),
 * their answer in their words ([reply]) and how the friendship grew ([gain]: the hearts, a level, a gift of theirs).
 */
data class GiftMoment(
    val villager: String,
    val good: String,
    val reaction: GiftReaction,
    val choices: List<GiftPhrase>,
    val tried: Set<Int> = emptySet(),
    val why: String? = null,
    val wrongPick: Int? = null,
    val mistakes: Int = 0,
    val said: GiftPhrase? = null,
    val reply: Said? = null,
    val gain: FriendGain? = null,
) {
    /** Jan said the right words: the gift is handed over. */
    val handed: Boolean get() = said != null

    /** Jan says choice [k]: the wrong register is crossed out and says why (no harm done); the right one hands it over. */
    fun pick(k: Int): GiftMoment {
        val c = choices.getOrNull(k) ?: return this
        if (handed || k in tried) return this
        if (!c.right) return copy(tried = tried + k, why = c.why, wrongPick = k, mistakes = mistakes + 1)
        return copy(said = c, why = null, wrongPick = null)
    }
}

/**
 * The pure parts of giving a good (GiftScene): whether the moment starts, what Jan can say in the villager's register
 * (two right ways and one in the other register, with a gentle why), and what they answer, in their own `lines.gift`
 * or plain words.
 */
object GiftTalk {
    /** One way to say it, in each language the app knows it in. */
    private class P(val sl: String, val it: String, val en: String, val de: String) {
        fun target(pair: LangPair): String = of(pair.target.code) ?: sl
        /** In the base, else English: never the English line said in an English village. */
        fun base(pair: LangPair): String = of(pair.base.code)?.takeIf { pair.base != pair.target } ?: en.takeIf { pair.target.code != "en" }.orEmpty()

        private fun of(code: String): String? = when (code) {
            "sl" -> sl
            "it" -> it
            "en" -> en
            "de" -> de
            else -> null
        }
    }

    /**
     * Handing it to someone Jan says ti (Italian tu, German du) to. The same thing in the vi (Lei, Sie) form is [VI] at
     * the same place.
     */
    private val TI = listOf(
        P("Izvoli, to je zate.", "Tieni, è per te.", "Here you are, this is for you.", "Hier, bitte. Das ist für dich."),
        P("Nekaj imam zate.", "Ho una cosa per te.", "I have something for you.", "Ich habe etwas für dich."),
        P("Upam, da ti bo všeč.", "Spero che ti piaccia.", "I hope you like it.", "Ich hoffe, es gefällt dir."),
    )

    /** … to someone Jan says vi (Italian Lei, German Sie) to. */
    private val VI = listOf(
        P("Izvolite, to je za vas.", "Tenga, è per Lei.", "Here you are, this is for you.", "Hier, bitte. Das ist für Sie."),
        P("Nekaj imam za vas.", "Ho una cosa per Lei.", "I have something for you.", "Ich habe etwas für Sie."),
        P("Upam, da vam bo všeč.", "Spero che Le piaccia.", "I hope you like it.", "Ich hoffe, es gefällt Ihnen."),
    )

    /** What someone without gift lines of their own says (a tutor's villager, a newcomer): words that fit anyone, ti or vi. */
    private val PLAIN = mapOf(
        GiftReaction.LIKED to VillagerLine(mapOf("sl" to "Oh, to mi je pa res všeč! Hvala!", "it" to "Oh, questo mi piace davvero! Grazie!", "de" to "Oh, das gefällt mir wirklich! Danke!", "en" to "Oh, I really like this! Thank you!")),
        GiftReaction.ORDINARY to VillagerLine(mapOf("sl" to "Hvala lepa, zelo prijazno.", "it" to "Grazie, sei gentile.", "de" to "Danke schön, sehr freundlich.", "en" to "Thank you, that's very kind.")),
        GiftReaction.RARE to VillagerLine(mapOf("sl" to "Joj, kaj takega! Najlepša hvala!", "it" to "Che meraviglia! Grazie mille!", "de" to "Na so was! Vielen, vielen Dank!", "en" to "Oh my, something like this! Thank you so much!")),
    )

    /** How villager [id] takes [good]: a rare one is a special joy, one of their favourites a delight, any other warm thanks. */
    fun reaction(good: GoodSpec, id: String): GiftReaction = when {
        good.rare -> GiftReaction.RARE
        Chest.favourite(id, good.id) -> GiftReaction.LIKED
        else -> GiftReaction.ORDINARY
    }

    /**
     * What Jan can say as they give [name] something, in their [register] (ti or vi): two right ways, and the one of them
     * the day picks in the other register, which is wrong and says why. In an order of the day ([salt] keeps people apart).
     */
    fun phrases(register: String, name: String, day: LocalDate, salt: String = "", pair: LangPair = L10n.pair): List<GiftPhrase> {
        val vi = register == "vi"
        val own = if (vi) VI else TI
        val other = if (vi) TI else VI
        // English has one "you" (its polite register is in the name: Mr Dixon): where the other register says it the same
        // way, nothing is wrong, and all three ways are right
        if (own.indices.all { own[it].target(pair) == other[it].target(pair) }) {
            val all = own.map { GiftPhrase(it.target(pair), it.base(pair), right = true) }
            val turn = Math.floorMod(day.toEpochDay() + name.length, all.size)
            return all.drop(turn) + all.take(turn)
        }
        val left = Math.floorMod(day.toEpochDay() + salt.hashCode(), own.size) // the way not offered today
        val right = own.indices.filter { it != left }
        val mirrored = right[Math.floorMod(day.toEpochDay() / 2 + salt.length, right.size)]
        val wrong = other[mirrored]
        val why = inBase(
            if (vi) "giftScene.whyVi" else "giftScene.whyTi",
            "name" to name, "said" to wrong.target(pair), "right" to own[mirrored].target(pair),
        )
        val all = right.map { GiftPhrase(own[it].target(pair), own[it].base(pair), right = true) } +
            GiftPhrase(wrong.target(pair), wrong.base(pair), right = false, why = why)
        val turn = Math.floorMod(day.toEpochDay() + name.length, all.size)
        return all.drop(turn) + all.take(turn)
    }

    /** Why [good] can't be given to villager [id] today, kindly (once a day each), or null when the moment can start. */
    fun blocker(s: GameState, good: String, id: String, today: LocalDate): String? = Chest.giveBlocker(s, good, id, today)

    /** The gift moment for [good] to [v], or null when it can't be given today ([blocker]): then no moment starts. */
    fun start(s: GameState, v: Villager, good: String, today: LocalDate): GiftMoment? {
        if (blocker(s, good, v.id, today) != null) return null
        val g = Catalog.goods[good] ?: return null
        return GiftMoment(v.id, good, reaction(g, v.id), phrases(v.register, VillagerLogic.shortName(v.name), today, v.id))
    }

    /**
     * What [v] answers to a gift that is [reaction] to them: one of their gift lines of that kind the [level] opens and
     * that fits [time] ("Stasera si fa festa" not at night), by the day; else plain words.
     */
    fun reply(v: Villager?, reaction: GiftReaction, level: Int, day: LocalDate, time: TimeOfDay = TimeOfDay.now()): Said {
        val pool = v?.lines?.gift?.let {
            when (reaction) {
                GiftReaction.LIKED -> it.liked
                GiftReaction.ORDINARY -> it.ordinary
                GiftReaction.RARE -> it.rare
            }
        }.orEmpty()
        val line = VillagerLogic.pick(Bonds.linesFor(pool.at(time), level), day, "${v?.id}:gift:${reaction.name}") ?: PLAIN.getValue(reaction)
        return Said(line.target, line.base)
    }
}
