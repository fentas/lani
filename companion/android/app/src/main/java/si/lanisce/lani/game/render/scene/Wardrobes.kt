package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Pal
import java.util.concurrent.ConcurrentHashMap

/**
 * What a culture's people wear (companion/SCENES.md "Culture looks", `people.wardrobe`; docs/plans/05-built/map-people.md):
 * the cast's sprites ([cast], by sprite name, where the culture dresses someone otherwise than Primorska), the village's own
 * women and men ([woman], [man], each by their seed, so newcomers don't look alike), and the strangers at the road. The
 * scenes' sprites, the portraits and the map's small figures all read it ([Looks.of]; the map's
 * [si.lanisce.lani.game.render.Effects.lookOf] takes its 14 px and 7 px figures from the sprite's look), so a person looks the
 * same on the map as in a scene. Primorska's people are [Looks.all] as they always were, and a culture without a wardrobe
 * dresses its people as Primorska does.
 */
internal class Wardrobe(
    val id: String,
    val cast: Map<String, Look>,
    val woman: (Int) -> Look,
    val man: (Int) -> Look,
)

internal object Wardrobes {
    private fun pick(seed: Int, shift: Int, n: Int) = ((seed ushr shift) and 0xFFFF) % n

    // shared colours
    private val WHITE = Col.hex(0xF4EEDD)
    private val SHIRT = Col.hex(0xE9E2D0)
    private val DARK = Col.hex(0x3A3246)
    private val SHOES = Col.hex(0x2E2018)
    private val skins = intArrayOf(Pal.SKIN, Pal.SKIN, Col.hex(0xF6CDB0), Col.hex(0xE0A878))

    // ------------------------------------------------------------------ Friuli

    /** Black velvet of the scarpets, Friuli's slippers with a flower embroidered on the toe. */
    private val VELVET = Col.hex(0x1E1A22)

    /**
     * Friuli (Teo's village): Nonna Rosa in the white kerchief tied at the back of the neck (il fazzoletto, fazzùl in
     * Friulian), a dark dress, a blue apron (il grembiule, grimâl) and the scarpets, black velvet with a red flower on the toe; Nonno Bepi in a white shirt, the
     * black waistcoat (il gilet) and a black felt hat, a white moustache; Zia Nives in a navy dress with white dots and a red
     * apron; Marisa of the osteria in a white blouse, a black bodice and skirt, the white apron, a tajut of red wine in her
     * hand; Maestra Elena in a light blue blouse with a white collar and a navy skirt; Tommaso in the school smock (il
     * grembiule), blue with a white collar; Giulia in a red dress with white dots and the
     * scarpets her nonna sewed, a yellow flower on each toe; Lorenzo in a white shirt with blue stripes and a red cap; Davide
     * the shepherd in a brown wool jacket under his cloak; Bruno the smith bare-armed in grey under his leather apron, a dark
     * cap; Franco the miller in a white shirt with braces under his straw hat; Matteo of the vines in a blue work shirt and a
     * brown waistcoat, a brown cap; the pilgrim to Castelmonte in a dark cloak and a black hat. The village's women wear dark
     * skirts and aprons, some a kerchief (white, or coloured), some the scarpets; its men a white shirt and a dark waistcoat, a felt hat or a cap.
     */
    val FRIULI = Wardrobe(
        "friuli",
        cast = mapOf(
            "grandma" to Look(
                torsoW = 13, torsoH = 8, legH = 5, hair = Pal.HAIR[4], hairStyle = Looks.HAIR_BUN,
                top = Col.hex(0x3E3A48), bottom = Col.hex(0x3E3A48), skirt = true, stockings = Col.hex(0x3A3640), apron = Col.hex(0x5A7AB0),
                hat = Looks.HAT_SCARF, hatCol = WHITE, nape = true, prop = Looks.PROP_SPOON, cheeks = true, shoes = VELVET, toes = Pal.GERANIUM,
            ),
            "grandpa" to Look(
                hair = Pal.HAIR[4], hairStyle = Looks.HAIR_BALD,
                top = SHIRT, vest = Col.hex(0x2E2A34), bottom = Col.hex(0x4A4652), hat = Looks.HAT_FELT, hatCol = Col.hex(0x2A2630),
                prop = Looks.PROP_STICK, moustache = Col.hex(0xE6E2DA), cheeks = true,
            ),
            "aunt" to Look(
                legH = 5, hair = Pal.HAIR[1], hairStyle = Looks.HAIR_BUN,
                top = Col.hex(0x2F3E6E), bottom = Col.hex(0x2F3E6E), skirt = true, stockings = DARK, dots = Pal.FLAG_WHITE,
                apron = Col.hex(0xC8463A), prop = Looks.PROP_BASKET, cheeks = true, collar = Pal.FLAG_WHITE,
            ),
            "innkeeper" to Look(
                torsoW = 13, torsoH = 8, legH = 5, hair = Pal.HAIR[0], hairStyle = Looks.HAIR_LONG,
                top = WHITE, vest = Col.hex(0x2E2A34), bottom = Col.hex(0x2E2A34), skirt = true, stockings = DARK, apron = Pal.FLAG_WHITE,
                prop = Looks.PROP_GLASS, cheeks = true,
            ),
            "teacher" to Look(
                hair = Pal.HAIR[1], hairStyle = Looks.HAIR_LONG,
                top = Col.hex(0x8AB0D8), bottom = Col.hex(0x2F3E6E), skirt = true, stockings = Col.hex(0x2F3E6E), glasses = true, prop = Looks.PROP_BOOK,
                collar = Pal.FLAG_WHITE,
            ),
            "child1" to Looks.kid(Look(
                hair = Pal.HAIR[1], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x2E4E9A), bottom = Col.hex(0x3A3246), shorts = true, collar = Pal.FLAG_WHITE,
            )),
            "child2" to Looks.kid(Look(
                hair = Pal.HAIR[0], hairStyle = Looks.HAIR_BRAIDS,
                top = Col.hex(0xC8392F), bottom = Col.hex(0xC8392F), skirt = true, dots = Pal.FLAG_WHITE, stockings = Pal.FLAG_WHITE,
                prop = Looks.PROP_FLOWER, shoes = VELVET, toes = Pal.GOLD,
            )),
            "child3" to Looks.kid(Look(
                hair = Pal.HAIR[0], hairStyle = Looks.HAIR_CURLY,
                top = SHIRT, stripes = Col.hex(0x2E4E9A), bottom = Col.hex(0x2E63B0), shorts = true, hat = Looks.HAT_CAP, hatCol = Col.hex(0xC8392F),
            )),
            "shepherd" to Look(
                hair = Pal.HAIR[1], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x6A4A32), bottom = Col.hex(0x3A3432), cape = Col.hex(0x4A3A30), hat = Looks.HAT_FELT, hatCol = Col.hex(0x4A4038),
                prop = Looks.PROP_CROOK, moustache = Pal.HAIR[1], boots = true,
            ),
            "smith" to Look(
                torsoW = 13, torsoH = 9, hair = Pal.HAIR[1], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x6A6A72), bottom = Col.hex(0x2E2A34), apron = Col.hex(0x8A5A32), hat = Looks.HAT_CAP, hatCol = Col.hex(0x2E2A34),
                prop = Looks.PROP_HAMMER, beard = Col.hex(0x2A2224),
            ),
            "farmer" to Look(
                hair = Pal.HAIR[3], hairStyle = Looks.HAIR_SHORT,
                top = SHIRT, bottom = Col.hex(0x5A5048), hat = Looks.HAT_STRAW, hatCol = Pal.HAY_L, suspenders = true, skin = Col.hex(0xE0A878),
                moustache = Pal.HAIR[3],
            ),
            "winemaker" to Look(
                hair = Pal.HAIR[0], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x4A6A8A), vest = Col.hex(0x5A4232), bottom = Col.hex(0x3A3432), hat = Looks.HAT_CAP, hatCol = Col.hex(0x5A4232),
                prop = Looks.PROP_BOTTLE, moustache = Pal.HAIR[0],
            ),
            "pilgrim" to Look(
                hair = Pal.HAIR[0], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x6A5A4A), bottom = Col.hex(0x3A3432), shoes = Col.hex(0x4E3A2A), boots = true, cape = Col.hex(0x3E3A48),
                hat = Looks.HAT_FELT, hatCol = Col.hex(0x2A2630), shell = true, prop = Looks.PROP_STAFF, satchel = Col.hex(0xB08A56), beard = Pal.HAIR[0],
            ),
        ),
        woman = { seed ->
            val tops = intArrayOf(Col.hex(0x8A3A3A), Col.hex(0x3E5A7A), Col.hex(0x6A7A4A), SHIRT, Col.hex(0xC89A4A), Col.hex(0x5A4A6A))
            val skirts = intArrayOf(Col.hex(0x2E2A34), Col.hex(0x3E3A48), Col.hex(0x2F3E6E), Col.hex(0x5A4232))
            val aprons = intArrayOf(WHITE, Col.hex(0x5A7AB0), Col.hex(0xC8463A), Col.hex(0xE8D8B0))
            val scarves = intArrayOf(WHITE, WHITE, Col.hex(0x2F3E6E), Col.hex(0x8A3A3A))
            val flowers = intArrayOf(Pal.GERANIUM, Pal.GOLD, Col.hex(0x8AB0D8), Col.hex(0xE8A0B0))
            val scarpets = pick(seed, 24, 3) == 0
            Look(
                legH = 5, hair = Pal.HAIR[pick(seed, 2, 4)], hairStyle = intArrayOf(Looks.HAIR_BUN, Looks.HAIR_BRAIDS, Looks.HAIR_LONG, Looks.HAIR_PONYTAIL)[pick(seed, 5, 4)],
                skin = skins[pick(seed, 28, skins.size)], top = tops[pick(seed, 8, tops.size)], bottom = skirts[pick(seed, 11, skirts.size)], skirt = true,
                stockings = DARK, apron = if (pick(seed, 14, 2) == 0) aprons[pick(seed, 15, aprons.size)] else 0,
                hat = if (pick(seed, 16, 4) == 0) Looks.HAT_SCARF else Looks.HAT_NONE, hatCol = scarves[pick(seed, 21, scarves.size)], nape = true,
                cheeks = pick(seed, 18, 2) == 0, collar = if (pick(seed, 19, 3) == 0) Pal.FLAG_WHITE else 0,
                dots = if (pick(seed, 23, 3) == 0) Col.hex(0xE8D8B0) else 0,
                shoes = if (scarpets) VELVET else SHOES, toes = if (scarpets) flowers[pick(seed, 26, flowers.size)] else 0,
            )
        },
        man = { seed ->
            val hair = Pal.HAIR[pick(seed, 2, 4)]
            val vests = intArrayOf(Col.hex(0x2E2A34), Col.hex(0x5A4232), Col.hex(0x3E4A5A))
            val hats = intArrayOf(Col.hex(0x2A2630), Col.hex(0x4A4038), Col.hex(0x3E3A48))
            val waistcoat = pick(seed, 14, 2) == 0
            Look(
                hair = hair, hairStyle = intArrayOf(Looks.HAIR_SHORT, Looks.HAIR_SHORT, Looks.HAIR_CURLY, Looks.HAIR_BALD)[pick(seed, 5, 4)],
                skin = skins[pick(seed, 28, skins.size)],
                top = if (waistcoat) SHIRT else Pal.SHIRTS[pick(seed, 8, Pal.SHIRTS.size)], vest = if (waistcoat) vests[pick(seed, 15, vests.size)] else 0,
                bottom = intArrayOf(DARK, Col.hex(0x4A4652), Col.hex(0x5A4232), Col.hex(0x2E2A34))[pick(seed, 11, 4)],
                hat = intArrayOf(Looks.HAT_NONE, Looks.HAT_FELT, Looks.HAT_CAP, Looks.HAT_NONE)[pick(seed, 16, 4)], hatCol = hats[pick(seed, 21, hats.size)],
                moustache = if (pick(seed, 18, 3) == 0) hair else 0, beard = if (pick(seed, 20, 6) == 0) hair else 0,
            )
        },
    )

    // ------------------------------------------------------------------ Carinthia

    private val LODEN = Col.hex(0x7A7A70)
    /** The Kärntner Anzug's cloth, hazelnut to chestnut brown, its lapels and piping green. */
    private val ANZUG = Col.hex(0x7A5234)
    private val LODEN_GREEN = Col.hex(0x4E6A3E)
    private val COLLAR_GREEN = Col.hex(0x3E6A3A)
    private val HAT_GREEN = Col.hex(0x3E5436)
    /** The Gamsbart in a hat's band: a brush of chamois hair, dark, its tips pale. */
    private val GAMSBART = Col.hex(0x4A3A2E)
    private val LEATHER = Col.hex(0x6A4A2E)
    private val STUTZEN = Col.hex(0xE0DCD0)

    /**
     * Carinthia (Kärnten): the women in the dirndl, a white blouse under a coloured bodice, a skirt and an apron of another
     * colour (Oma Resi's bodice dark green, her skirt dark blue, her apron plum; Tante Mitzi's red over green with a blue
     * apron; Wirtin Kathi's dark blue with a cream apron, her plaits up; Lena's red with a blue apron); Lehrerin Eva in a
     * loden-green Janker over a white blouse; Opa Sepp in the Kärntner Anzug, chestnut brown with green lapels, a green felt hat
     * with a Gamsbart; Maxi and Jakob in short Lederhosen with braces and knee socks (Stutzen), checked shirts, Jakob in a green cap;
     * Florian the herder in a white shirt and a leather waistcoat, the knee-length leather
     * breeches and high boots of the Gailtal, a green hat; Toni the smith bare-headed
     * and bearded under his leather apron; Franz the miller all in white under the miller's white cap; Stefan the Mostbauer
     * in a checked shirt under the farmer's blue working apron (der blaue Schurz), a grey hat; the pilgrim to the Luschariberg in a loden cape
     * and a green hat. The village's women wear the dirndl or a blouse and skirt; its men checked shirts or a loden Janker,
     * Lederhosen or dark trousers, some a green or grey hat with a Gamsbart.
     */
    val KAERNTEN = Wardrobe(
        "kaernten",
        cast = mapOf(
            "grandma" to Look(
                torsoW = 13, torsoH = 8, legH = 5, hair = Pal.HAIR[4], hairStyle = Looks.HAIR_BUN,
                top = WHITE, vest = Col.hex(0x34503A), bottom = Col.hex(0x2E3A5A), skirt = true, stockings = Col.hex(0x6E6A72), apron = Col.hex(0x8A4A6A),
                collar = Pal.FLAG_WHITE, prop = Looks.PROP_SPOON, cheeks = true,
            ),
            "grandpa" to Look(
                hair = Pal.HAIR[4], hairStyle = Looks.HAIR_SHORT,
                top = ANZUG, collar = COLLAR_GREEN, bottom = Col.hex(0x6A4832), hat = Looks.HAT_FELT, hatCol = HAT_GREEN, plume = GAMSBART,
                prop = Looks.PROP_STICK, moustache = Col.hex(0xE6E2DA), cheeks = true,
            ),
            "aunt" to Look(
                legH = 5, hair = Pal.HAIR[0], hairStyle = Looks.HAIR_BUN,
                top = WHITE, vest = Col.hex(0xA8323A), bottom = Col.hex(0x2E5A3A), skirt = true, stockings = Pal.FLAG_WHITE, apron = Col.hex(0x6E8AC8),
                collar = Pal.FLAG_WHITE, prop = Looks.PROP_BASKET, cheeks = true,
            ),
            "innkeeper" to Look(
                torsoW = 13, torsoH = 8, legH = 5, hair = Pal.HAIR[2], hairStyle = Looks.HAIR_BRAIDS,
                top = WHITE, vest = Col.hex(0x2E4A6A), bottom = Col.hex(0x2E4A6A), skirt = true, stockings = Pal.FLAG_WHITE, apron = Col.hex(0xE8D8A8),
                collar = Pal.FLAG_WHITE, prop = Looks.PROP_MUG, cheeks = true,
            ),
            "teacher" to Look(
                hair = Pal.HAIR[2], hairStyle = Looks.HAIR_BUN,
                top = LODEN_GREEN, collar = Pal.FLAG_WHITE, bottom = DARK, skirt = true, stockings = DARK, glasses = true, prop = Looks.PROP_BOOK,
            ),
            "child1" to Looks.kid(Look(
                hair = Pal.HAIR[2], hairStyle = Looks.HAIR_SHORT,
                top = SHIRT, checks = Col.hex(0xC8392F), bottom = LEATHER, shorts = true, suspenders = true, stockings = STUTZEN,
            )),
            "child2" to Looks.kid(Look(
                hair = Pal.HAIR[2], hairStyle = Looks.HAIR_BRAIDS,
                top = WHITE, vest = Col.hex(0xC8392F), bottom = Col.hex(0xC8392F), skirt = true, apron = Col.hex(0x6E8AC8), stockings = Pal.FLAG_WHITE,
                prop = Looks.PROP_FLOWER,
            )),
            "child3" to Looks.kid(Look(
                hair = Pal.HAIR[0], hairStyle = Looks.HAIR_CURLY,
                top = SHIRT, checks = Col.hex(0x2E63B0), bottom = LEATHER, shorts = true, suspenders = true, stockings = STUTZEN,
                hat = Looks.HAT_CAP, hatCol = HAT_GREEN,
            )),
            "shepherd" to Look(
                hair = Pal.HAIR[0], hairStyle = Looks.HAIR_SHORT,
                top = SHIRT, vest = LEATHER, bottom = Col.hex(0x3A2C22), hat = Looks.HAT_FELT, hatCol = HAT_GREEN,
                prop = Looks.PROP_CROOK, moustache = Pal.HAIR[0], boots = true,
            ),
            "smith" to Look(
                torsoW = 13, torsoH = 9, hair = Pal.HAIR[1], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x5A5A62), bottom = Col.hex(0x2E2A34), apron = Col.hex(0x8A5A32), prop = Looks.PROP_HAMMER,
                beard = Col.hex(0x3A2A24), moustache = Col.hex(0x3A2A24),
            ),
            "farmer" to Look(
                hair = Pal.HAIR[3], hairStyle = Looks.HAIR_SHORT,
                top = SHIRT, bottom = Col.hex(0xB8B2A2), hat = Looks.HAT_CAP, hatCol = Col.hex(0xE8E4DA), skin = Col.hex(0xE0A878),
                moustache = Pal.HAIR[3],
            ),
            "winemaker" to Look(
                hair = Pal.HAIR[1], hairStyle = Looks.HAIR_SHORT,
                top = SHIRT, checks = Col.hex(0x3E8A3A), apron = Col.hex(0x2E4E8A), bottom = Col.hex(0x3A3432), hat = Looks.HAT_FELT, hatCol = Col.hex(0x5A5A52),
                prop = Looks.PROP_BOTTLE, moustache = Pal.HAIR[1],
            ),
            "pilgrim" to Look(
                hair = Pal.HAIR[3], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x6A6A60), bottom = Col.hex(0x4A4652), shoes = Col.hex(0x5E4632), boots = true, cape = Col.hex(0x5A6A52),
                hat = Looks.HAT_FELT, hatCol = HAT_GREEN, shell = true, prop = Looks.PROP_STAFF, satchel = Col.hex(0xB08A56), beard = Pal.HAIR[3],
            ),
        ),
        woman = { seed ->
            val bodices = intArrayOf(Col.hex(0x34503A), Col.hex(0xA8323A), Col.hex(0x2E4A6A), Col.hex(0x6A3A5A))
            val skirts = intArrayOf(Col.hex(0x2E3A5A), Col.hex(0x2E5A3A), Col.hex(0xA8323A), Col.hex(0x5A4232), DARK)
            val aprons = intArrayOf(Col.hex(0x6E8AC8), Col.hex(0xD88A9A), Col.hex(0xE8D8A8), Col.hex(0x6A9A5A), Col.hex(0x8A4A6A))
            val dirndl = pick(seed, 14, 2) == 0
            Look(
                legH = 5, hair = Pal.HAIR[pick(seed, 2, 4)], hairStyle = intArrayOf(Looks.HAIR_BUN, Looks.HAIR_BRAIDS, Looks.HAIR_BRAIDS, Looks.HAIR_PONYTAIL)[pick(seed, 5, 4)],
                skin = skins[pick(seed, 28, skins.size)],
                top = if (dirndl) WHITE else Pal.SHIRTS[pick(seed, 8, Pal.SHIRTS.size)], vest = if (dirndl) bodices[pick(seed, 8, bodices.size)] else 0,
                bottom = skirts[pick(seed, 11, skirts.size)], skirt = true, stockings = if (pick(seed, 19, 2) == 0) Pal.FLAG_WHITE else DARK,
                apron = if (dirndl || pick(seed, 15, 3) == 0) aprons[pick(seed, 21, aprons.size)] else 0, collar = if (dirndl) Pal.FLAG_WHITE else 0,
                hat = if (pick(seed, 16, 6) == 0) Looks.HAT_SCARF else Looks.HAT_NONE, hatCol = Col.hex(0x2E2A34),
                cheeks = pick(seed, 18, 2) == 0,
            )
        },
        man = { seed ->
            val hair = Pal.HAIR[pick(seed, 2, 4)]
            val checks = intArrayOf(Col.hex(0xC8392F), Col.hex(0x2E63B0), Col.hex(0x3E8A3A))
            val janker = pick(seed, 8, 3) == 0
            val lederhose = pick(seed, 11, 3) == 0
            val hat = intArrayOf(Looks.HAT_NONE, Looks.HAT_FELT, Looks.HAT_NONE, Looks.HAT_FELT, Looks.HAT_CAP, Looks.HAT_NONE)[pick(seed, 16, 6)]
            Look(
                hair = hair, hairStyle = intArrayOf(Looks.HAIR_SHORT, Looks.HAIR_SHORT, Looks.HAIR_CURLY, Looks.HAIR_BALD)[pick(seed, 5, 4)],
                skin = skins[pick(seed, 28, skins.size)],
                top = if (janker) (if (pick(seed, 9, 2) == 0) LODEN else LODEN_GREEN) else SHIRT, collar = if (janker) COLLAR_GREEN else 0,
                checks = if (janker) 0 else checks[pick(seed, 13, checks.size)],
                bottom = if (lederhose) LEATHER else intArrayOf(DARK, Col.hex(0x4A4652), Col.hex(0x5A5650))[pick(seed, 12, 3)],
                suspenders = lederhose && !janker, stockings = if (lederhose) STUTZEN else 0,
                hat = hat, hatCol = if (pick(seed, 21, 2) == 0) HAT_GREEN else Col.hex(0x5A5A52), plume = if (hat == Looks.HAT_FELT && pick(seed, 22, 2) == 0) GAMSBART else 0,
                moustache = if (pick(seed, 18, 3) == 0) hair else 0, beard = if (pick(seed, 20, 5) == 0) hair else 0,
            )
        },
    )

    // ------------------------------------------------------------------ the Lake District

    private val TWEED = Col.hex(0x6F5A42)
    private val TWEED_GREY = Col.hex(0x5A5650)
    private val WAXED = Col.hex(0x55583A)
    private val CORD = Col.hex(0x6A4A2E)
    private val WELLIES = Col.hex(0x2E4A2E)

    /**
     * The Lake District: the men in flat caps of tweed, Old Wilf in a brown tweed jacket, a white moustache and his stick;
     * Shepherd Jack in a waxed jacket with a cord collar, his crook and green wellies; Mr Tyson in a checked shirt and
     * braces, a tweed cap and green wellies; Mr Dixon the smith in a dark cap over his leather apron; Brewer Sam bare-headed,
     * ginger and bearded, a dark waistcoat over a white shirt under the brewer's apron; Granny Maggie in a lavender cardigan
     * and a tweed skirt under her floral pinny, her grey perm and her glasses; Auntie Edna in a headscarf tied under the
     * chin, a quilted green jacket, a tweed skirt and green wellies; Mrs Birkett of the pub in a red blouse and a white
     * pinny, her blonde perm, a pint in her hand; Mrs Hartley in a blue cardigan over a white blouse, a grey skirt and her
     * glasses; Harry in his maroon school jumper over a white collar, grey shorts and socks; Poppy in a blue gingham dress
     * and red wellies; Alfie in a yellow raincoat and green wellies; the pilgrim to St Herbert's Island in a blue-grey cape.
     * The village's women wear cardigans and tweed or plain skirts, some a headscarf, some wellies; its men waxed jackets,
     * Aran jumpers or checked shirts, many a flat cap, some wellies.
     */
    val LAKELAND = Wardrobe(
        "lakeland",
        cast = mapOf(
            "grandma" to Look(
                torsoW = 13, torsoH = 8, legH = 5, hair = Pal.HAIR[4], hairStyle = Looks.HAIR_CURLY,
                top = Col.hex(0xA88AB8), bottom = Col.hex(0x6A5A4A), skirt = true, stockings = Col.hex(0xC8B4A0), apron = Col.hex(0xE8C8D0),
                prop = Looks.PROP_SPOON, cheeks = true, glasses = true,
            ),
            "grandpa" to Look(
                hair = Pal.HAIR[4], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x7A6A52), collar = SHIRT, bottom = TWEED_GREY, hat = Looks.HAT_CAP, hatCol = TWEED,
                prop = Looks.PROP_STICK, moustache = Col.hex(0xE6E2DA), cheeks = true,
            ),
            "aunt" to Look(
                legH = 5, hair = Pal.HAIR[0], hairStyle = Looks.HAIR_BUN,
                top = Col.hex(0x3E6A5A), bottom = Col.hex(0x6A5A4A), skirt = true, stockings = Col.hex(0xC8B4A0),
                hat = Looks.HAT_SCARF, hatCol = Col.hex(0x6E8AC8), dots = Col.hex(0xF0B8C8), prop = Looks.PROP_BASKET, cheeks = true,
                shoes = WELLIES, boots = true,
            ),
            "innkeeper" to Look(
                torsoW = 13, torsoH = 8, legH = 5, hair = Pal.HAIR[2], hairStyle = Looks.HAIR_CURLY,
                top = Col.hex(0xB8384A), bottom = Col.hex(0x2E2A34), skirt = true, stockings = DARK, apron = Pal.FLAG_WHITE,
                prop = Looks.PROP_MUG, cheeks = true,
            ),
            "teacher" to Look(
                hair = Pal.HAIR[0], hairStyle = Looks.HAIR_BUN,
                top = Col.hex(0x5A7AA8), collar = Pal.FLAG_WHITE, bottom = Col.hex(0x6A6A62), skirt = true, stockings = Col.hex(0xC8B4A0), glasses = true,
                prop = Looks.PROP_BOOK,
            ),
            "child1" to Looks.kid(Look(
                hair = Pal.HAIR[3], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x8A2A3A), collar = Pal.FLAG_WHITE, bottom = Col.hex(0x5A5A62), shorts = true, stockings = Col.hex(0x5A5A62),
            )),
            "child2" to Looks.kid(Look(
                hair = Pal.HAIR[3], hairStyle = Looks.HAIR_BRAIDS,
                top = Col.hex(0x5A8AD0), bottom = Col.hex(0x5A8AD0), skirt = true, checks = Pal.FLAG_WHITE, collar = Pal.FLAG_WHITE,
                prop = Looks.PROP_FLOWER, shoes = Col.hex(0xC8392F), boots = true,
            )),
            "child3" to Looks.kid(Look(
                hair = Pal.HAIR[1], hairStyle = Looks.HAIR_CURLY,
                top = Col.hex(0xE8B830), bottom = Col.hex(0x2E3A5A), shoes = WELLIES, boots = true,
            )),
            "shepherd" to Look(
                hair = Pal.HAIR[0], hairStyle = Looks.HAIR_SHORT,
                top = WAXED, collar = CORD, bottom = Col.hex(0x4A4652), hat = Looks.HAT_CAP, hatCol = Col.hex(0x5A5048),
                prop = Looks.PROP_CROOK, shoes = WELLIES, boots = true,
            ),
            "smith" to Look(
                torsoW = 13, torsoH = 9, hair = Pal.HAIR[1], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x8A8A82), bottom = Col.hex(0x3A3432), apron = Col.hex(0x8A5A32), hat = Looks.HAT_CAP, hatCol = Col.hex(0x3A3A40),
                prop = Looks.PROP_HAMMER, beard = Pal.HAIR[1],
            ),
            "farmer" to Look(
                hair = Pal.HAIR[3], hairStyle = Looks.HAIR_SHORT,
                top = SHIRT, checks = Col.hex(0x8A5A3A), suspenders = true, bottom = Col.hex(0x5A5A4A), hat = Looks.HAT_CAP, hatCol = TWEED,
                skin = Col.hex(0xE0A878), shoes = WELLIES, boots = true,
            ),
            "winemaker" to Look(
                hair = Pal.HAIR[3], hairStyle = Looks.HAIR_SHORT,
                top = SHIRT, vest = Col.hex(0x3E3A4A), apron = Col.hex(0xC8B898), bottom = Col.hex(0x3A3432), prop = Looks.PROP_BOTTLE,
                beard = Pal.HAIR[3], moustache = Pal.HAIR[3],
            ),
            "pilgrim" to Look(
                hair = Pal.HAIR[3], hairStyle = Looks.HAIR_SHORT,
                top = Col.hex(0x7A5E4A), bottom = Col.hex(0x3A3A44), shoes = Col.hex(0x5E4632), boots = true, cape = Col.hex(0x3E5A6A),
                hat = Looks.HAT_FELT, hatCol = Col.hex(0x4A4A44), shell = true, prop = Looks.PROP_STAFF, satchel = Col.hex(0xB08A56), beard = Pal.HAIR[3],
            ),
        ),
        woman = { seed ->
            val tops = intArrayOf(Col.hex(0xA88AB8), Col.hex(0x5A7AA8), Col.hex(0x8A3A4A), Col.hex(0x6A8A5A), Col.hex(0xE8DCC0), Col.hex(0x3E6A5A))
            val skirts = intArrayOf(Col.hex(0x6A5A4A), Col.hex(0x5A6A7A), Col.hex(0x3A3A4A), Col.hex(0x7A6A52))
            val scarves = intArrayOf(Col.hex(0x6E8AC8), Col.hex(0xC8392F), Col.hex(0xE8C8D0), Col.hex(0x3E6A5A))
            val wellies = pick(seed, 24, 3) == 0
            Look(
                legH = 5, hair = Pal.HAIR[pick(seed, 2, 5)], hairStyle = intArrayOf(Looks.HAIR_BUN, Looks.HAIR_CURLY, Looks.HAIR_LONG, Looks.HAIR_PONYTAIL)[pick(seed, 5, 4)],
                skin = skins[pick(seed, 28, skins.size)], top = tops[pick(seed, 8, tops.size)], bottom = skirts[pick(seed, 11, skirts.size)], skirt = true,
                stockings = if (pick(seed, 19, 2) == 0) Col.hex(0xC8B4A0) else DARK,
                apron = if (pick(seed, 14, 3) == 0) Col.hex(0xE8C8D0) else 0,
                hat = if (pick(seed, 16, 4) == 0) Looks.HAT_SCARF else Looks.HAT_NONE, hatCol = scarves[pick(seed, 21, scarves.size)],
                dots = if (pick(seed, 26, 3) == 0) Pal.FLAG_WHITE else 0, cheeks = pick(seed, 18, 2) == 0,
                shoes = if (wellies) intArrayOf(WELLIES, Col.hex(0x22202A), Col.hex(0x2E3A5A))[pick(seed, 22, 3)] else SHOES, boots = wellies,
            )
        },
        man = { seed ->
            val hair = Pal.HAIR[pick(seed, 2, 4)]
            val tops = intArrayOf(WAXED, Col.hex(0xE8DCC0), Col.hex(0x2E3A5A), Col.hex(0x7A6A52), SHIRT)
            val top = pick(seed, 8, tops.size)
            val caps = intArrayOf(TWEED, TWEED_GREY, Col.hex(0x5A5048), Col.hex(0x3A3A40))
            val wellies = pick(seed, 24, 3) == 0
            Look(
                hair = hair, hairStyle = intArrayOf(Looks.HAIR_SHORT, Looks.HAIR_SHORT, Looks.HAIR_CURLY, Looks.HAIR_BALD)[pick(seed, 5, 4)],
                skin = skins[pick(seed, 28, skins.size)], top = tops[top], collar = if (top == 0) CORD else 0, checks = if (top == 4) Col.hex(0x8A5A3A) else 0,
                bottom = intArrayOf(DARK, Col.hex(0x5A5A4A), Col.hex(0x4A4652), CORD)[pick(seed, 11, 4)],
                hat = if (pick(seed, 16, 2) == 0) Looks.HAT_CAP else Looks.HAT_NONE, hatCol = caps[pick(seed, 21, caps.size)],
                moustache = if (pick(seed, 18, 3) == 0) hair else 0, beard = if (pick(seed, 20, 5) == 0) hair else 0,
                shoes = if (wellies) WELLIES else SHOES, boots = wellies,
            )
        },
    )

    private val all: Map<String, Wardrobe> = listOf(FRIULI, KAERNTEN, LAKELAND).associateBy { it.id }

    /** The wardrobes by their ids (the docs, the tests). */
    val ids: Set<String> get() = all.keys

    /** Every sprite some wardrobe dresses otherwise than Primorska (the tests: the rest look the same in every culture). */
    val dressed: Set<String> get() = all.values.flatMap { it.cast.keys }.toSet() + "woman" + "man"

    private val byCulture = ConcurrentHashMap<String, Any>()
    private val NONE = Any()

    /**
     * What [culture]'s people wear: its look's `people.wardrobe` ([si.lanisce.lani.game.scene.CultureLooks]), null for
     * Primorska's (the default culture's) and a culture without one.
     */
    fun of(culture: String): Wardrobe? {
        val w = byCulture.getOrPut(culture) {
            si.lanisce.lani.game.scene.CultureLooks.of(culture).variant("people", "wardrobe")?.let { all[it] } ?: NONE
        }
        return w as? Wardrobe
    }
}
