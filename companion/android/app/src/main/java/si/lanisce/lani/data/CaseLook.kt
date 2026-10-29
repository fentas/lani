package si.lanisce.lani.data

import si.lanisce.lani.game.Case

/**
 * The six Slovene cases as the app shows them everywhere (the case trainer, the case lens, the grammar links): the shared
 * definition both keep to (docs/plans/05-built/case-trainer.md, "The contract"), on the app's own [Case] (game/Triggers.kt:
 * the school's number, its question, its English name as [Case.lexicon], its name in the string tables as [Case.key]).
 * Here each case's id (its grammar page, the bank's and the wire's), its forms book slot ("gen"), the question words as
 * the contract writes them and its colour (the light theme's; [darkArgb] the same hue lighter, for the dark theme). Pure.
 */

/** The case's id: its grammar page, as the bank, the wire and the mastery keys name it ("rodilnik"). */
val Case.id: String
    get() = when (this) {
        Case.NOM -> "imenovalnik"
        Case.GEN -> "rodilnik"
        Case.DAT -> "dajalnik"
        Case.ACC -> "tozilnik"
        Case.LOC -> "mestnik"
        Case.INS -> "orodnik"
    }

/** Its slot in the forms book: "gen" (with the number: "gen.sg"). */
val Case.slot: String
    get() = when (this) {
        Case.NOM -> "nom"
        Case.GEN -> "gen"
        Case.DAT -> "dat"
        Case.ACC -> "acc"
        Case.LOC -> "loc"
        Case.INS -> "ins"
    }

/** The school's question words as the contract writes them, lower case: "koga? česa?". */
val Case.ask: String get() = question.lowercase()

/** The second pair of question words, where the school teaches one: the locative's "o kom? o čem?". */
val Case.alsoAsk: String? get() = if (this == Case.LOC) "o kom? o čem?" else null

/** The whole question as the contract writes it: "pri kom? pri čem? (o kom? o čem?)". */
val Case.asks: String get() = alsoAsk?.let { "$ask ($it)" } ?: ask

/** Its name in Slovene as the school writes it (tožilnik with its ž; the id has none), for the tutor's lines. */
val Case.slName: String get() = if (this == Case.ACC) "tožilnik" else id

/** Its colour on the light theme (the contract's six: blue, red, amber, green, violet, teal). */
val Case.lightArgb: Long
    get() = when (this) {
        Case.NOM -> 0xFF4A7FC1
        Case.GEN -> 0xFFC2503C
        Case.DAT -> 0xFFD69A2D
        Case.ACC -> 0xFF3E9B5F
        Case.LOC -> 0xFF8A5CB8
        Case.INS -> 0xFF2C9AA0
    }

/** The same hue for the dark theme: lighter and a little less saturated, readable on a dark surface. */
val Case.darkArgb: Long get() = CaseLook.lighter(lightArgb)

/** The case of [id] ("rodilnik"); null for another word. */
fun Case.Companion.ofId(id: String?): Case? = Case.entries.firstOrNull { it.id == id }

/** By its forms book slot: "gen" → [Case.GEN] ("gen.sg" works too). */
fun Case.Companion.ofSlot(slot: String?): Case? = slot?.substringBefore('.')?.let { s -> Case.entries.firstOrNull { it.slot == s } }

/**
 * The rest of the contract: the rule ids (the grammar pages that explain a rule), and the colour arithmetic. A rule id is
 * what the case trainer's per-rule mastery counts on, and what the case lens's "seen" signal names.
 */
object CaseLook {
    /** The rule ids the contract names (any grammar page may be one; these are the case rules' own). */
    val RULES = listOf(
        "rodilnik-predlogi", "rodilnik-nikalnica", "rodilnik-kolicina", "kam-tozilnik", "kje-mestnik-orodnik",
        "dajalnik", "orodnik", "tozilnik", "mestnik", "skloni",
    )

    /** The pages that are about a case or a case's rule: the trainer opens from them ("🧩 Vadi sklone"). */
    val PAGES: Set<String> = RULES.toSet() + Case.entries.map { it.id } + setOf(
        "stevila-samostalniki", "mnozina-skloni", "dvojina-skloni", "sklanjatev-moski", "sklanjatev-zenski",
        "sklanjatev-srednji", "pridevniki-ujemanje",
    )

    /** The number's label key in the string tables: "cases.number.pl" (množina · plural). */
    fun numberKey(n: String): String = when (n) {
        "du" -> "cases.number.du"
        "pl" -> "cases.number.pl"
        else -> "cases.number.sg"
    }

    /** [argb] with its lightness raised (HSL) for a dark background, the hue kept. */
    fun lighter(argb: Long): Long {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        val d = max - min
        val s = if (d == 0.0) 0.0 else d / (1 - kotlin.math.abs(2 * l - 1))
        val h = when {
            d == 0.0 -> 0.0
            max == r -> 60 * (((g - b) / d).mod(6.0))
            max == g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        val l2 = (l + (1 - l) * 0.38).coerceAtMost(0.80)
        val s2 = s * 0.85
        val c = (1 - kotlin.math.abs(2 * l2 - 1)) * s2
        val x = c * (1 - kotlin.math.abs((h / 60).mod(2.0) - 1))
        val m = l2 - c / 2
        val (r1, g1, b1) = when {
            h < 60 -> Triple(c, x, 0.0)
            h < 120 -> Triple(x, c, 0.0)
            h < 180 -> Triple(0.0, c, x)
            h < 240 -> Triple(0.0, x, c)
            h < 300 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun ch(v: Double) = ((v + m) * 255).toLong().coerceIn(0, 255)
        return (0xFFL shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}
