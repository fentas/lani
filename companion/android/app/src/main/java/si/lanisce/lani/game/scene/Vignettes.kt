package si.lanisce.lani.game.scene

/**
 * The vignette library of the stories' pictures (companion/SCENES.md, "The story notebook"): what a story's
 * [StoryPicture] can be made of, painted in ink by game/render/book (a woodcut look: black-brown lines on warm paper,
 * hatching for shade, a few flat washes), and in the notebook gone over in pencil (Sketch). A picture is a background,
 * figures and props where they stand, and a caption.
 * The bridge mirrors these lists (bridge/src/stories.ts, `VIGNETTES`) and checks every picture against them; the smoke
 * test parses this file and compares, so the two can't drift.
 */
object Vignettes {
    /** The picture's ground and sky: where it happens. */
    val backgrounds = listOf(
        "mountains", "lake", "castle", "village", "forest", "church", "sea", "cave", "river", "bridge", "meadow", "town",
    )

    /** Who is in it: people, creatures and animals (the chamois gilded is Zlatorog), a boat. */
    val figures = listOf(
        "king", "girl", "woman", "boy", "old-man", "peasant", "hunter", "knight", "soldier", "giant", "fairy", "water-man",
        "devil", "goblin", "dragon", "chamois", "horse", "ox", "boar", "dog", "cat", "bird", "boat", "sheep",
    )

    /** What else: things, trees, the sky's. */
    val props = listOf(
        "crown", "sword", "bell", "tree", "spruce", "cloud", "sun", "moon", "stars", "flower", "rock", "barrel", "chest",
        "sack", "club", "table", "fire", "house", "chapel", "wall",
    )

    /** At most this many figures and props in a picture. */
    const val MAX_FIGURES = 6
    const val MAX_PROPS = 8

    /** What in [p] the library can't draw ("bg \"desert\"", "figures[1] \"unicorn\""); empty when all of it resolves. */
    fun problems(p: StoryPicture): List<String> = buildList {
        if (p.bg !in backgrounds) add("bg \"${p.bg}\"")
        p.figures.forEachIndexed { i, f -> if (f.id !in figures) add("figures[$i] \"${f.id}\"") }
        p.props.forEachIndexed { i, f -> if (f.id !in props) add("props[$i] \"${f.id}\"") }
        if (p.figures.size > MAX_FIGURES) add("${p.figures.size} figures (at most $MAX_FIGURES)")
        if (p.props.size > MAX_PROPS) add("${p.props.size} props (at most $MAX_PROPS)")
    }
}
