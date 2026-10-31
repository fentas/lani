package si.lanisce.lani.game

import si.lanisce.lani.data.TargetNext
import si.lanisce.lani.l10n.bi

/**
 * The learner's aim, the target level the village's ages lead to (companion/GAME.md "Ages", "A target raised later";
 * docs/plans/05-built/target-next-age.md). The profile's `target_level`, and a raise that starts with the village's next
 * age ([TargetNext], `target_level_next`): the step the village is on keeps the words of the target it began with, and
 * from the age [TargetNext.fromAge] on the new target counts. The bridge applies the raise in the profile when the village
 * is saved at that age (src/aim.ts: the same rule, the same cases in bridge/test/fixtures/target-next-age.json); the app
 * counts with it at once, before the bridge has the save (offline too), so whoever reaches the age the target is the same.
 */
object Aims {
    /** The age a pending raise starts with; null for none or one the app doesn't know. */
    fun fromAge(next: TargetNext?): Age? = next?.let { n -> Age.entries.firstOrNull { it.name == n.fromAge } }

    /** The target the village's step from [age] counts with: [next]'s once the village has reached its age, else [target]. */
    fun at(target: String, next: TargetNext?, age: Age): String {
        val from = fromAge(next) ?: return target
        return if (age >= from) next!!.level else target
    }

    /** The raise still waiting while the village is at [age]: [next] before its age, null once it counts (or none). */
    fun waiting(next: TargetNext?, age: Age): TargetNext? {
        val from = fromAge(next) ?: return null
        return next.takeIf { age < from }
    }

    /**
     * The age after the one a waiting raise starts with, and the words it then needs on the way to the raised target:
     * Trg's 500 for B1 from Vas on. Null when nothing waits at [age], or it starts with the last age.
     */
    fun then(next: TargetNext?, age: Age): Pair<Age, Int>? {
        val n = waiting(next, age) ?: return null
        val after = Age.entries.getOrNull(fromAge(n)!!.ordinal + 1) ?: return null
        return after to Catalog.wordsFor(after, n.level)
    }

    /**
     * "🎯 Od naslednje dobe: B1 · From the next age: B1" while a raise waits at [age] (the age card under its words, the
     * progress screen by the target); null when none does.
     */
    fun note(next: TargetNext?, age: Age): String? = waiting(next, age)?.let { "🎯 ${bi("aim.fromNextAge", "level" to it.level)}" }

    /** The line under it on the age card: "🏛️ Trg: 500 besed · Market town: 500 words"; null when none waits. */
    fun thenLine(next: TargetNext?, age: Age): String? =
        then(next, age)?.let { (after, words) -> "${after.emoji} ${bi("aim.ageWords", "age" to after.names, "n" to words)}" }
}
