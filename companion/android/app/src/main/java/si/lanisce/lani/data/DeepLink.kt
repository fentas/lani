package si.lanisce.lani.data

/** Where a notification tap leads. It travels in the launch intent as the string extra [EXTRA]. */
sealed interface DeepLink {
    data object Chat : DeepLink
    data object Talk : DeepLink
    data object Village : DeepLink
    data object Packs : DeepLink
    /** "📖 Branje · Reading": the reading corner (a reading the tutor wrote). */
    data object Readings : DeepLink
    data class Module(val id: String) : DeepLink
    /** A village scene, optionally at a happening ("scene/happening") or a thing: "scene:<id>" or "scene:<id>@<focus>". */
    data class Scene(val id: String, val focus: String? = null) : DeepLink
    data class Family(val challengeId: String) : DeepLink
    /** A question between towns ([key]): one the learner [asked] another town, or a guest's to them (data/TownQuestions.kt). */
    data class Question(val key: String, val asked: Boolean) : DeepLink

    companion object {
        const val EXTRA = "si.lanisce.lani.open"

        fun encode(link: DeepLink): String = when (link) {
            Chat -> "chat"
            Talk -> "talk"
            Village -> "village"
            Packs -> "packs"
            Readings -> "readings"
            is Module -> "module:${link.id}"
            is Scene -> "scene:${link.id}" + (link.focus?.let { "@$it" } ?: "")
            is Family -> "family:${link.challengeId}"
            is Question -> "question:${if (link.asked) "a" else "r"}:${link.key}"
        }

        fun parse(s: String?): DeepLink? {
            val kind = s?.substringBefore(':') ?: return null
            val arg = s.substringAfter(':', "").takeIf { it.isNotBlank() }
            return when (kind) {
                "chat" -> Chat
                "talk" -> Talk
                "village" -> Village
                "packs" -> Packs
                "readings" -> Readings
                "module" -> arg?.let(::Module)
                "scene" -> arg?.let { Scene(it.substringBefore('@'), it.substringAfter('@', "").ifBlank { null }) }
                "family" -> arg?.let(::Family)
                "question" -> arg?.substringAfter(':', "")?.takeIf { it.isNotBlank() }?.let { Question(it, asked = arg.substringBefore(':') == "a") }
                else -> null
            }
        }

        /** Where a notification about [ev] leads; null just opens the app. */
        fun of(ev: BridgeEvent): DeepLink? = when (ev) {
            is BridgeEvent.Reply -> when {
                ev.conversationId.startsWith("rp-") -> Talk
                ev.conversationId.startsWith("fam_") -> Family(ev.conversationId.removePrefix("fam_"))
                else -> Chat
            }
            is BridgeEvent.ModulePublished -> Module(ev.id)
            is BridgeEvent.PackPublished -> Packs
            is BridgeEvent.ReadingPublished -> Readings
            // the tutor's grammar page: in the village's book
            is BridgeEvent.GrammarPublished -> Village
            is BridgeEvent.PartnerChallenge -> Family(ev.id)
            is BridgeEvent.ScenePublished -> if (ev.removed || ev.id.isEmpty()) Village else Scene(ev.id)
            // A permission prompt shows as a dialog once the app is open; an update as the Home banner.
            is BridgeEvent.PermissionRequest, is BridgeEvent.AppUpdate, BridgeEvent.VoiceUpdated,
            is BridgeEvent.WordsAdded, is BridgeEvent.LexiconUpdated, is BridgeEvent.SentenceExplained -> null
            // a newcomer's introduction: their bubble waits in the village
            is BridgeEvent.ArrivalPublished -> Village
            // a guest's visit: the village applies it once open
            is BridgeEvent.TownGuest -> Village
            // a guest's question to answer, or the answer to the learner's
            is BridgeEvent.TownQuestion -> Question(ev.key, asked = ev.kind == TownQuestions.ANSWER)
        }
    }
}
