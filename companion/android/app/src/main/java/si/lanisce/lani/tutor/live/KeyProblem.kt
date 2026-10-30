package si.lanisce.lani.tutor.live

/**
 * What went wrong with a Claude key the learner gave ([LiveTutor.setKey]; null there: it works and is kept). Typed, so a
 * screen says it in the learner's words (the setup's and the settings' "🔑 Ta ključ ne deluje · This key doesn't work",
 * app/TutorKey) without reading the English of a message. [text] is that English, for the log and a developer.
 */
sealed interface KeyProblem {
    val text: String

    /** Nothing was typed (the field held only spaces). */
    data object Empty : KeyProblem {
        override val text = "the key is empty"
    }

    /** Anthropic refused it (401): wrong, cut short, or revoked. */
    data object Refused : KeyProblem {
        override val text = "the key was refused (401)"
    }

    /** It couldn't be checked: the network, a rate limit, the service down; [text] is the redacted message. */
    data class Failed(override val text: String) : KeyProblem
}
