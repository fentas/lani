package si.lanisce.lani.game.culture

import kotlinx.serialization.Serializable

/**
 * reactions.json (lani.reactions/v0, optional): what the village's people say while an event is on or just over
 * (companion/GAME.md "People react"), in the pack's language with its translations: the short calls over the map
 * ([ReactionTexts.shout]: "Volkovi!", "Pazi!"), the call to the event's run on a villager's card ([ReactionTexts.call]:
 * "Branimo vas!"), and each one's line by what they do in it (the defenders, the children, the old, the innkeeper, the
 * others). A pack without it has the people react without words of their own: a villager's card says the event's title.
 * The bridge doesn't read it.
 */
@Serializable
data class ReactionsFile(
    val schema: String,
    /** The pack's language: every text has it. */
    val language: String,
    /** Who wrote which of its languages and who checked them: "machine-written (Claude), not reviewed by a native speaker". */
    val review: String? = null,
    val wolves: ReactionTexts,
    val bear: ReactionTexts,
    val storm: ReactionTexts,
    val merchant: ReactionTexts,
    val festival: ReactionTexts,
    /** Right after a won fight (the wolves or the bear driven off): the cheer. */
    val won: ReactionTexts,
    /** Right after a lost one; [ReactionTexts.repair]: whoever patches up the building it damaged. */
    val lost: ReactionTexts,
)

/** What the people say in one event, or right after one (see [ReactionsFile]). Lines are short, A1 where they can be. */
@Serializable
data class ReactionTexts(
    /** Short calls over the map, a word or two each: "Volkovi!", "Hitro noter!". */
    val shout: List<Text> = emptyList(),
    /** The button on a villager's card that starts the event's run: "Branimo vas!". */
    val call: Text? = null,
    /** Who faces the wolves or the bear at a distance (the smith, the shepherd, the hunter) and the one on the watchtower. */
    val defender: List<Text> = emptyList(),
    val child: List<Text> = emptyList(),
    /** The grandparents, the storyteller, the aunt. */
    val elder: List<Text> = emptyList(),
    /** The innkeeper (who keeps a shop). */
    val keeper: List<Text> = emptyList(),
    /** Anyone else, and anyone whose own list is empty. */
    val other: List<Text> = emptyList(),
    /** After a lost fight: whoever patches up the building it damaged. */
    val repair: List<Text> = emptyList(),
)
