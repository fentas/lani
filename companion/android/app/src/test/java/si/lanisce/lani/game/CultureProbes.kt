package si.lanisce.lani.game

import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.QuestMeta
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.GiftLines
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.VillagerLines
import si.lanisce.lani.game.villagers.Visit
import si.lanisce.lani.game.villagers.parseVillagers
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Every text the game's content makes, for the learner's pair of the moment (the parity test runs it for Jan's sl · en):
 * the data itself (quest templates, festivals, surprises, goods and tools, projects, names) and what the engine builds
 * from it (the chronicle, results, reasons, challenges). Keys name what was probed; the values are the texts.
 *
 * The fixture (src/test/resources/culture/parity.json) was written by these probes from the code as it was before the
 * content moved into the culture pack (companion/cultures/primorska); [CultureParityTest] wants the same texts now.
 */
internal object CultureProbes {
    private val day: LocalDate = LocalDate.of(2026, 9, 1)
    private fun noon(d: LocalDate): Long = d.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val t0 = noon(day)

    private val out = LinkedHashMap<String, String>()
    private fun p(key: String, value: Any?) {
        check(key !in out) { "probe $key twice" }
        // an exercise that names no grammar book page (Exercise.grammar, newer than the fixture) reads as it did, and so does
        // a villager without a day of their own nor a bed elsewhere (Villager.routine, Villager.sleeps, newer too: the
        // strangers at the road)
        out[key] = value?.toString()?.replace(", grammar=null)", ")")?.replace(", sleeps=null)", ")")?.replace(", routine=null)", ")") ?: "null"
    }

    /** [l] as Jan reads it: its Slovene and English, without the translations for other learners (de, it …). */
    private fun jan(ls: List<VillagerLine>): List<VillagerLine> = ls.map { l -> VillagerLine(l.by.filterKeys { it == "sl" || it == "en" }, l.level) }
    private fun jan(l: VillagerLines): VillagerLines = VillagerLines(
        jan(l.greet), jan(l.thanks), jan(l.remember), jan(l.idle), jan(l.cheer), jan(l.comfort), jan(l.listen), jan(l.bye),
        GiftLines(jan(l.gift.liked), jan(l.gift.ordinary), jan(l.gift.rare)),
    )
    private fun jan(v: Villager?): Villager? = v?.copy(lines = jan(v.lines))

    /**
     * The curated cast, from its files (companion/villagers when the fixture was made, the culture pack's now); without the
     * extras (the hunter), who came after the fixture and move in as newcomers do (ResidentsTest has them).
     */
    val cast: List<Villager> by lazy {
        val dir = File("../../cultures/primorska/villagers")
        dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseVillagers("[" + it.readText() + "]").single() }
            .filter { !it.extra }
            .sortedWith(compareBy({ it.order }, { it.id }))
    }

    private val castIds = listOf("micka", "luka", "france", "anton", "ancka", "tone", "janez", "mojca", "vida", "marko", "zala", "nejc", "tine")

    private val cards = listOf(
        ReviewCard("v1", "dober dan", "good day", category = "greetings"),
        ReviewCard("v2", "živjo", "hi", category = "greetings"),
        ReviewCard("v3", "hvala", "thank you", category = "politeness"),
        ReviewCard("v4", "prosim", "please", category = "politeness"),
        ReviewCard("v5", "kruh", "bread", category = "food"),
        ReviewCard("v6", "mleko", "milk", category = "food"),
        ReviewCard("v7", "sir", "cheese", category = "food"),
        ReviewCard("v8", "jabolko", "apple", category = "food"),
        ReviewCard("v9", "ovca", "sheep", category = "animals"),
        ReviewCard("v10", "pes", "dog", category = "animals"),
        ReviewCard("v11", "konj", "horse", category = "animals"),
        ReviewCard("v12", "krava", "cow", category = "animals"),
        ReviewCard("v13", "ena", "one", category = "numbers"),
        ReviewCard("v14", "dva", "two", category = "numbers"),
        ReviewCard("v15", "Jaz sem Jan.", "I am Jan.", category = "grammar_cases"),
        ReviewCard("v16", "Grem v šolo.", "I go to school.", category = "grammar_cases"),
    )
    private fun pool(canSpeak: Boolean = true, present: Set<String>? = null) = ContentPool(cards, canSpeak = canSpeak, present = present)

    private fun all(n: Int) = Res.entries.associateWith { n }

    private fun village(age: Age = Age.VAS, vararg types: BuildingType, res: Map<Res, Int> = all(400), seed: Long = 7) =
        GameEngine.newGame(seed, t0).copy(
            age = age, resources = res, villagers = 6, morale = 60,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i) },
            lastTick = day.toString(), foundedOn = day.minusDays(30).toString(), log = emptyList(),
        )

    private fun GameState.goods(vararg g: Pair<String, Int>) = copy(chest = chest.copy(goods = g.toMap()))
    private fun GameState.tool(giver: String, tier: Int = 1, forged: Int = 0) = copy(chest = chest.copy(tools = chest.tools + (giver to Tool(tier, forged))))
    private fun GameState.lastLog() = log.lastOrNull()?.let { "${it.emoji} ${it.text}" }
    private fun GameState.logs() = log.joinToString("\n") { "${it.emoji} ${it.text}" }

    fun all(): Map<String, String> {
        out.clear()
        world()
        quests()
        festivals()
        surprises()
        chest()
        readings()
        spots()
        projects()
        events()
        residents()
        engine()
        tomorrow()
        return LinkedHashMap(out)
    }

    // --- the names of things ------------------------------------------------------------------------------------

    private fun world() {
        for (r in Res.entries) p("world/res/${r.name}", "${r.emoji} ${r.sl} · ${r.en} (${r.skill})")
        for (a in Age.entries) p("world/age/${a.name}", "${a.emoji} ${ageName(a)}")
        for (t in BuildingType.entries) p("world/building/${t.name}", "${t.emoji} ${buildingName(t)}")
        for (k in EventKind.entries) p("world/event/${k.name}", "${k.emoji} ${k.sl} · ${k.en} (${k.skill})")
        for (n in listOf("hrana", "Les", "kamen", "modrost", "food", "Wood", "stone", "wisdom", "WISDOM", "vino", null)) p("world/resOf/$n", Quests.resOf(n))
    }

    // --- requests -------------------------------------------------------------------------------------------------

    private fun quests() {
        val templates = Cultures.current.quests.requests
        for ((i, g) in templates.withIndex()) {
            p("quest/$i/giver", g.giver)
            p("quest/$i/emoji", g.emoji)
            p("quest/$i/skill", Res.valueOf(g.skill.uppercase()))
            p("quest/$i/title", g.title.bi())
            p("quest/$i/story", g.story.bi())
            p("quest/$i/topics", g.topics.joinToString(","))
        }
        val names = templates.map { it.giver }.distinct()
        for (n in names) p("quest/idOf/$n", Quests.idOf(n))
        // what a refill opens, day by day: the same templates, drawn in the same order
        var s = village(Age.VAS, BuildingType.TENT, BuildingType.FIELD)
        for (i in 0 until 12) {
            val d = day.plusDays(i.toLong())
            val (_, added) = Quests.refill(s.copy(quests = emptyList()), d, noon(d), pool())
            p("quest/refill/$i", added.joinToString("\n") { "${it.giver}|${it.emoji}|${it.title}|${it.story}|${it.skill}|${it.reward}|${it.category}" })
        }
        // only who is here
        val here = setOf("Pastir Luka", "Babica Micka")
        val (_, some) = Quests.refill(s.copy(quests = emptyList()), day, t0, pool(present = here))
        p("quest/refill/present", some.joinToString("\n") { "${it.giver}|${it.title}" })
        s = s.copy(quests = emptyList())
    }

    // --- the calendar ---------------------------------------------------------------------------------------------

    private fun festivals() {
        for (f in Calendar.festivals) {
            val k = "festival/${f.id}"
            p("$k/emoji", f.emoji)
            p("$k/name", f.name)
            p("$k/sl", f.sl)
            p("$k/en", f.en)
            p("$k/ask", f.ask)
            p("$k/about", f.about)
            p("$k/line", f.line)
            p("$k/leaders", f.leaders.joinToString(","))
            p("$k/good", "${f.good} ×${f.goods}")
            p("$k/pack", f.pack)
            p("$k/dates", (2024..2032).joinToString(" ") { f.rule.date(it).toString() })
            for (d in 0L..6L) p("$k/countdown/$d", Calendar.countdown(f, d))
            val on = f.rule.date(2026)
            for (late in 0..2) p("$k/today/$late", Calendar.todayText(FestivalDay(f, on, late, false)))
            for (delta in listOf(0L, 1L, 2L, 3L, -1L, -2L, -3L, -5L, -6L, -40L)) p("$k/when/$delta", Calendar.whenText(f, on.plusDays(delta)))
            Calendar.run(f, 42, on).forEachIndexed { i, (w, ex) -> p("$k/run/$i", "${w.id} $ex") }
            // celebrated, and a try that didn't pass
            val s = village(Age.TABOR, BuildingType.TENT).copy(lastTick = on.toString())
            val (won, r) = Calendar.celebrate(s, f.id, 6, 6, on, noon(on))
            p("$k/celebrate/won", "${r.message} | ${r.thanks?.id} | ${won.lastLog()}")
            val (_, lost) = Calendar.celebrate(s, f.id, 1, 6, on, noon(on))
            p("$k/celebrate/lost", lost.message)
            val late = Calendar.celebrate(s, f.id, 6, 6, on.plusDays(2), noon(on.plusDays(2)))
            p("$k/celebrate/late", late.second.message)
            val ch = GameEngine.festivalChallenge(s, f.id, on)!!
            p("$k/challenge", "${ch.title}\n${ch.intro}")
        }
        for (m in 1..12) for (d in listOf(1, 11, 28)) p("calendar/date/$m-$d", Calendar.dateText(LocalDate.of(2026, m, d)))
        // the tick's line on a festival's day
        val mart = LocalDate.of(2026, 11, 11)
        val s = village(Age.TABOR, BuildingType.TENT).copy(lastTick = mart.minusDays(1).toString())
        p("calendar/tick", GameEngine.tick(s, mart, noon(mart), pool()).news.joinToString("\n") { "${it.emoji} ${it.text}" })
    }

    // --- the day's surprise ---------------------------------------------------------------------------------------

    private fun surprises() {
        Surprises.letters.forEachIndexed { i, l -> p("surprise/letter/$i", l) }
        Surprises.directions.forEachIndexed { i, q -> p("surprise/direction/$i", q) }
        Surprises.lambPlaces.forEachIndexed { i, q -> p("surprise/lamb/$i", q) }
        Surprises.riddles.forEachIndexed { i, q -> p("surprise/riddle/$i", q) }
        for ((k, v) in Surprises.strangers) p("surprise/stranger/$k", jan(v))
        val name = { id: String -> Chest.nameOf(village(), id) }
        val cases = buildList {
            add(Surprise(day.toString(), Surprises.PEDLAR, 3))
            Surprises.letters.forEachIndexed { i, l -> add(Surprise(day.toString(), Surprises.LETTER, i, l.to.takeIf { it != Surprises.JAN })) }
            add(Surprise(day.toString(), Surprises.LAMB, 5, Surprises.SHEPHERD))
            add(Surprise(day.toString(), Surprises.PILGRIM, 2))
            add(Surprise(day.toString(), Surprises.RIDDLE, 4, "nejc"))
            add(Surprise(day.toString(), Surprises.RIDDLE, 1, null))
        }
        for ((i, sp) in cases.withIndex()) {
            val k = "surprise/case/$i/${sp.kind}"
            p("$k/emoji", Surprises.emoji(sp.kind))
            p("$k/title", Surprises.title(sp))
            p("$k/intro", Surprises.intro(sp, name))
            p("$k/ask", Surprises.ask(sp))
            p("$k/skill", Surprises.skill(sp.kind))
            for (speak in listOf(true, false)) Surprises.exercises(sp, 11, speak).forEachIndexed { j, ex -> p("$k/run/$speak/$j", ex) }
            if (sp.kind == Surprises.PEDLAR) continue
            val s = village(Age.TABOR, BuildingType.TENT).copy(surprise = sp)
            for ((c, t) in listOf(3 to 3, 0 to 3)) {
                val (after, r) = Surprises.finish(s, c, t, day, t0)
                p("$k/finish/$c", "${r.message} | ${after.lastLog()}")
            }
            GameEngine.surpriseChallenge(s, pool(), day)?.let { p("$k/challenge", "${it.title}\n${it.intro}") }
        }
    }

    // --- what the chest's things hold to read (added with the readings: their own probes) ---------------------------

    private fun readings() {
        for ((id, _) in Catalog.tools) for (tier in 1..2) {
            val reads = Readings.ofTool(id, tier)
            if (reads.isNotEmpty()) p("chest/read/tool/$id/$tier", reads.joinToString { "${it.item.label} → ${it.reading.id} (${it.by})" })
        }
        for (id in Catalog.goods.keys) Readings.ofGood(id)?.let { p("chest/read/good/$id", "${it.item.label} → ${it.reading.id} (${it.by})") }
        for ((id, r) in Cultures.current.readings) {
            p("reading/$id", "${r.kind} ${r.level} · ${r.title.bi()} · ${si.lanisce.lani.ui.game.ReadingLogic.lines(r).size} lines · ${r.questions.size} questions")
        }
    }

    // --- the landscape's spots as the culture tells them (added with the charcoal pile: their own probes) -------------

    private fun spots() {
        for ((id, s) in Cultures.current.world.spots) {
            val k = "spot/$id"
            val info = si.lanisce.lani.game.scene.TownSpots.info(id)
            p("$k/label", "${info.emoji} ${info.label} | ${info.where} | ${s.about?.bi()}")
            p("$k/pack", "${s.pack} ${s.words}")
            for (lv in s.story.keys.sorted()) {
                si.lanisce.lani.game.scene.Keepers.lore(s, lv, Cultures.current.language)?.let { p("$k/story/$lv", "${it.said}\n${it.meant}") }
            }
            val kp = s.keeper ?: continue
            p("$k/keeper", "${kp.emoji} ${kp.name} (${kp.art}, ${kp.voice}, ${kp.speaker}) ${kp.times} ${kp.chance} ${kp.reward} · ${kp.role?.bi()} · ${kp.title.bi()}")
            val here = si.lanisce.lani.game.scene.ActiveKeeper(id, kp)
            val said = { d: si.lanisce.lani.game.scene.Dialog ->
                d.lines.joinToString("\n") { l ->
                    if (l.choices.isEmpty()) "${l.who}: ${l.sl} | ${l.en}"
                    else l.choices.joinToString(" / ") { c -> (if (c.ok) "✓ " else "✗ ") + "${c.sl} | ${c.en}" + (c.reply?.let { r -> " → ${r.sl} | ${r.en}" } ?: "") }
                }
            }
            for (lv in kp.levels.keys.sorted()) {
                val d = si.lanisce.lani.game.scene.Keepers.talk(here, lv, Cultures.current.manifest.language) ?: continue
                p("$k/keeper/$lv", said(d))
            }
            // the story he tells across visits (added with it: its own probes): each talk, in each of its tellings (round
            // after round), at each level, as the learner meets it there
            kp.talks.forEachIndexed { i, t ->
                for (round in 0..t.again.size) {
                    val at = here.copy(at = 1 + i + round * kp.talks.size)
                    val r = "$k/keeper/talk/${i + 1}/${t.id}/$round"
                    p(r, at.title.bi())
                    for (lv in t.telling(round).keys.sorted()) {
                        val d = si.lanisce.lani.game.scene.Keepers.talk(at, lv, Cultures.current.manifest.language) ?: continue
                        p("$r/$lv", said(d))
                    }
                }
            }
        }
    }

    // --- the chest ------------------------------------------------------------------------------------------------

    private fun chest() {
        for ((id, t) in Catalog.tools) {
            val k = "chest/tool/$id"
            p("$k/name", t.name)
            for ((tier, n) in listOf(1 to t.first, 2 to t.better)) p("$k/$tier", "${n.label} | ${n.emoji} | ${n.sl} | ${n.acc} | ${n.en}")
            p("$k/forge", t.forge)
            for (f in listOf(1f, 1.5f, 2f, 2.5f, 3f)) {
                p("$k/effect/$f", Chest.effectText(t.effect.times(f)))
                p("$k/help/$f", Chest.effectHelp(t.effect.times(f)))
            }
        }
        for ((id, g) in Catalog.goods) p("chest/good/$id", "${g.name.label} | ${g.name.emoji} | ${g.name.sl} | ${g.name.acc} | ${g.name.en} | ${g.value} ${g.food} ${g.rare} ${g.adult}")
        p("chest/thanks", Catalog.thanks.toString() + " default " + Catalog.DEFAULT_THANKS)
        p("chest/smallGifts", Catalog.smallGifts)
        p("chest/likes", Catalog.likes)
        p("chest/defaultLikes", Catalog.defaultLikes)
        p("chest/children", Catalog.children.sorted())
        p("chest/pedlarGoods", Catalog.pedlarGoods)
        val ana = Resident("n-ana-furlan", "2026-08-01", name = "Ana Furlan", voice = "female", role = "Tkalka · Weaver", family = "Furlan")
        for (id in castIds + ana.id + "n-nobody") {
            p("chest/likes/$id", Chest.likes(id).map { it.id })
            p("chest/giftAt/$id", listOf(2, 4).map { Chest.giftAt(id, it).label })
            p("chest/nameOf/$id", Chest.nameOf(village().copy(residents = listOf(ana)), id))
            p("chest/thanksOf/$id", Chest.thanksOf(id).id)
        }
        // the gifts at friendship levels: a tool, the better one; a newcomer's small goods
        for (id in castIds + ana.id) {
            var s = village().copy(residents = if (id == ana.id) listOf(ana) else emptyList())
            s = Bonds.add(s, id, 30, day, now = t0)
            val first = s.lastLog()
            s = Bonds.add(s, id, 120, day, now = t0)
            p("chest/gift/$id", "$first\n${s.lastLog()}")
            p("chest/jump/$id", Bonds.add(village(), id, 160, day, now = t0).logs())
        }
        // giving a good to someone who likes it, and why not (anything else is warm thanks: no wine for a child)
        for (g in Catalog.goods.keys) {
            val who = castIds.firstOrNull { id -> Chest.likes(id).any { it.id == g } } ?: continue
            val s = village().goods(g to 1)
            p("chest/give/$g", "$who: ${Chest.give(s, g, who, day, t0).lastLog()}")
        }
        val s1 = village().goods("med" to 1)
        p("chest/giveBlocker/none", Chest.giveBlocker(village(), "med", "micka", day))
        p("chest/giveBlocker/child", Chest.giveBlocker(village().goods("rebula" to 1), "rebula", "zala", day))
        p("chest/giveBlocker/today", Chest.giveBlocker(s1.copy(chest = s1.chest.copy(given = mapOf("micka" to day.toString()))), "med", "micka", day))
        // selling and buying
        val market = village(Age.TRG, BuildingType.MARKET, res = all(100)).copy(event = GameEvent("ev", EventKind.MERCHANT, 2, t0, t0 + 1000))
        p("chest/market/market", Chest.market(market))
        p("chest/market/merchant", Chest.market(village(res = all(100)).copy(event = GameEvent("ev", EventKind.MERCHANT, 2, t0, t0 + 1000))))
        for (g in Catalog.goods.keys) p("chest/sell/$g", Chest.sell(market.goods(g to 2), g, t0).first.lastLog())
        val shop = market.copy(resources = all(1500), surprise = Surprise(day.toString(), Surprises.PEDLAR, 2))
        for (stall in Chest.stalls(shop, day)) {
            p("chest/stall/${stall.id}", "${stall.emoji} ${stall.label}: " + stall.offers.joinToString { "${it.good.id} ${it.price} ${it.left}" })
            for (o in stall.offers) p("chest/buy/${stall.id}/${o.good.id}", Chest.buy(shop, stall.id, o.good.id, day, t0).lastLog())
        }
        val offer = Chest.stalls(shop, day).first().offers.first()
        p("chest/buyBlocker/soldOut", Chest.buyBlocker(shop, offer.copy(left = 0)))
        p("chest/buyBlocker/short", Chest.buyBlocker(shop.copy(resources = all(1)), offer))
        // the smith
        for ((id, t) in Catalog.tools.filterValues { it.forge }) for (tier in 1..2) for (forged in 0..1) {
            val s = village(Age.VAS, BuildingType.SMITHY, res = all(1500)).tool(id, tier, forged).goods("kruh" to 2)
            p("chest/forge/$id/$tier/$forged", Chest.forge(s, id, t0).lastLog())
        }
        val tone = Resident("tone", day.toString())
        val smith = village(Age.VAS, BuildingType.SMITHY, res = all(1500)).tool("luka").goods("kruh" to 1)
        p("chest/forgeReason/noSmithy", Chest.forgeOption(village(Age.VAS, res = all(1500)).tool("luka").goods("kruh" to 1), "luka")?.reason)
        p("chest/forgeReason/noTone", Chest.forgeOption(smith.copy(residents = listOf(Resident("micka", day.toString()))), "luka")?.reason)
        p("chest/forgeReason/age", Chest.forgeOption(smith.copy(age = Age.TABOR, residents = listOf(tone)), "luka")?.reason)
        p("chest/forgeReason/age2", Chest.forgeOption(smith.copy(age = Age.ZASELEK).tool("luka", 1, 1), "luka")?.reason)
        p("chest/forgeReason/pay", Chest.forgeOption(smith.goods(), "luka")?.reason)
        p("chest/forgeReason/short", Chest.forgeOption(smith.copy(resources = all(10)), "luka")?.reason)
        p("chest/forgeReason/ok", Chest.forgeOption(smith, "luka"))
        // the feast
        val lipa = village(Age.VAS, BuildingType.LIPA, res = all(1500)).goods("kruh" to 3, "med" to 1, "podkev" to 2)
        p("chest/feast/log", Chest.feast(lipa, day, t0).lastLog())
        p("chest/feast/plain", Chest.feast(lipa.goods(), day, t0).lastLog())
        p("chest/feastReason/noLipa", Chest.feastOption(village(Age.VAS, res = all(1500)), day)?.reason)
        for (m in 1..12) {
            val on = LocalDate.of(2026, m, 3)
            p("chest/feastReason/next/$m", Chest.feastOption(lipa.copy(lastFeast = on.minusDays(2).toString()), on)?.reason)
        }
        p("chest/feastReason/short", Chest.feastOption(lipa.copy(resources = all(10)), day)?.reason)
    }

    // --- village projects -----------------------------------------------------------------------------------------

    private fun projects() {
        for (spec in Catalog.projects) {
            val k = "project/${spec.id}"
            p("$k/head", "${spec.emoji} ${spec.landmark} ${spec.age} ${spec.leader} ${spec.skill} ${spec.topics} ${spec.site} ${spec.helpers} ${Projects.bonusText(spec)}")
            p("$k/name", spec.name)
            p("$k/short", spec.short)
            p("$k/about", spec.about)
            spec.steps.forEachIndexed { i, st -> p("$k/step/$i", "${st.kind} ${st.help} | ${st.task} | ${st.line}") }
            p("$k/done", spec.done)
            p("$k/memory", spec.memory)
            // a whole project, step by step, with a fail first
            var s = village(spec.age, res = all(5000)).copy(help = 100).goods("kruh" to 5)
            val fail = Projects.finishStep(s, spec.id, 0, 5, day, t0)
            p("$k/fail", fail.second.message)
            val msgs = ArrayList<String>()
            for (i in spec.steps.indices) {
                val d = day.plusDays(i.toLong())
                val o = Projects.option(s, spec, d)
                msgs += "${Projects.progressText(s, spec)} ${Projects.costLine(o)} ${o.reason}"
                val ch = GameEngine.projectChallenge(s, spec.id, pool(), d)
                if (i == 0) p("$k/challenge", "${ch?.title}\n${ch?.intro}")
                val (n, r) = Projects.finishStep(s, spec.id, 5, 5, d, noon(d))
                msgs += r.message
                s = n
            }
            p("$k/run", msgs.joinToString("\n"))
            p("$k/log", s.logs())
            p("$k/memories", (listOf(spec.leader) + spec.helpers).distinct().map { s.bonds[it]?.memories?.lastOrNull() })
            p("$k/finished", Projects.option(s, spec, day.plusDays(30)).reason)
        }
        val spec = Catalog.projects.first { it.id == "most" }
        val s = village(Age.ZASELEK, res = all(5000)).copy(help = 0).goods()
        p("project/reason/age", Projects.option(s.copy(age = Age.TABOR), spec, day).reason)
        p("project/reason/leader", Projects.option(s.copy(residents = listOf(Resident("micka", day.toString()))), spec, day).reason)
        p("project/reason/today", Projects.option(s.copy(projectDay = day.toString()), spec, day).reason)
        p("project/reason/short", Projects.option(s.copy(resources = all(1)), spec, day).reason)
        p("project/reason/help", Projects.option(s.copy(projects = mapOf("most" to 2)), spec, day).reason)
        p("project/reason/treat", Projects.option(s.copy(projects = mapOf("most" to 5), help = 50), spec, day).reason)
        p("project/reason/unavailable", Projects.finishStep(s.copy(resources = all(1)), "most", 5, 5, day, t0).second.message)
    }

    // --- events ---------------------------------------------------------------------------------------------------

    private fun events() {
        val plain = village(Age.ZASELEK, BuildingType.TENT)
        val walled = village(Age.ZASELEK, BuildingType.PALISADE, BuildingType.WATCHTOWER)
        for (kind in EventKind.entries) for (str in listOf(1, 3)) {
            val e = GameEvent("ev-$kind", kind, str, t0, t0 + 1000)
            val k = "event/$kind/$str"
            p("$k/title", Events.title(plain, e))
            p("$k/titleWalled", Events.title(walled, e))
            p("$k/intro", Events.intro(plain, e, 7, null))
            p("$k/introWalled", Events.intro(walled, e, 8, "🔇 note"))
            for ((won, expired) in listOf(true to false, false to false, false to true)) {
                val (s, r) = Events.outcome(plain.copy(event = e), e, won, expired, t0)
                p("$k/outcome/$won/$expired", "${r.message} | ${s.lastLog()}")
            }
            val ch = GameEngine.eventChallenge(plain.copy(event = e), pool())!!
            p("$k/challenge", "${ch.title}\n${ch.intro}")
        }
        val e = GameEvent("ev-m", EventKind.MERCHANT, 2, t0, t0 + 1000)
        val full = village(Age.ZASELEK, BuildingType.TENT, res = all(100)).copy(event = e)
        p("event/merchant/full", Events.outcome(full, e, won = true, expired = false, now = t0).second.message)
    }

    // --- who lives here -------------------------------------------------------------------------------------------

    private fun residents() {
        for (st in Residents.Stage.entries) p("residents/stage/$st", "${st.label} | ${st.labelFemale}")
        val folk = Cultures.current.people
        p("residents/female", folk.firstNames.female)
        p("residents/male", folk.firstNames.male)
        val surnames: List<String> = folk.surnames
        p("residents/surnames", surnames)
        p("residents/adultLines", jan(folk.lines.adult))
        p("residents/childLines", jan(folk.lines.child))
        for (f in surnames + listOf("Novak", "Kovač", "Mazzer", "Bole", "Kralj")) p("residents/plural/$f", Residents.plural(f))
        // the cast moves in, one by one; then newcomers and children, days on end
        var s = GameState(seed = 11, age = Age.MESTO, villagers = 30, lastTick = day.toString(),
            buildings = BuildingType.entries.mapIndexed { i, t -> Building("b$i", t, i) })
        val news = ArrayList<String>()
        var named = emptyList<Resident>()
        // in their order, as they always came: without a `since` a workplace brings nobody, and in the town every age is
        // reached anyway (what a building brings is residents/brought)
        val inOrder = cast.map { it.copy(since = null) }
        for (i in 0 until 400) {
            val d = day.plusDays(i.toLong())
            val want = if (i < 200) minOf(30, 1 + i / 4) else 30 - (i - 200) / 10
            val (n, lines) = Residents.reconcile(s.copy(villagers = want), inOrder, d)
            news += lines.map { (e, t) -> "$e $t" }
            s = n
            if (i == 199) named = s.residents.filter { it.name != null }
        }
        p("residents/news", news.joinToString("\n"))
        // a resident as the fixture has them: the fields of someone from a friend's town (M5) are none here
        p("residents/people", s.residents.joinToString("\n") { it.toString().replace(", culture=null, language=null, from=null)", ")") })
        for (r in named.take(16)) {
            val from = LocalDate.parse(r.born ?: r.since)
            for (n in listOf(0L, 60L, 300L, 500L)) p("residents/villager/${r.id}/$n", jan(Residents.villagerOf(r, cast, from.plusDays(n))))
        }
        // who leaves: a man and a woman who moved in
        val two = GameState(seed = 2, villagers = 1, residents = listOf(
            Resident("micka", day.toString()),
            Resident("n-rok-humar", day.toString(), name = "Rok Humar", voice = "male", family = "Humar"),
            Resident("n-eva-humar", day.toString(), name = "Eva Humar", voice = "female", family = "Humar"),
        ))
        p("residents/leave", Residents.reconcile(two, cast, day).second)
        p("residents/leaveCast", Residents.reconcile(GameState(seed = 2, villagers = 0, residents = listOf(Resident("luka", day.toString()), Resident("micka", day.toString()))), cast, day).second)
        // the workplaces bring their people, into the free beds (VILLAGERS.md, "A building brings its person")
        val works = GameState(seed = 2, age = Age.VAS, villagers = 1, residents = listOf(Resident("micka", day.toString())),
            buildings = listOf(BuildingType.BEEHIVE, BuildingType.SMITHY, BuildingType.MARKET, BuildingType.SCHOOL, BuildingType.HOUSE, BuildingType.HUT)
                .mapIndexed { i, t -> Building("w$i", t, i) })
        p("residents/brought", Residents.reconcile(works, cast, day).second)
        // every trade a newcomer may bring, with every building standing, and with none (the old probe drew them from
        // the private trade(): "(Drvarka · Woodcutter, woman)")
        for (female in listOf(true, false)) {
            fun shown(t: si.lanisce.lani.game.culture.Trade) = "(${(if (female) t.female else t.male).bi()}, ${if (female) "woman" else "man"})"
            p("residents/trades/$female", folk.trades.map(::shown).toSortedSet())
            p("residents/tradesBare/$female", folk.trades.filter { it.needs == null }.map(::shown).toSortedSet())
        }
        // who comes next, and what they wait for
        val camp = GameState(seed = 3, age = Age.VAS, villagers = 2, buildings = listOf(Building("b0", BuildingType.TENT, 0)),
            residents = listOf(Resident("micka", day.toString()), Resident("luka", day.toString())))
        p("residents/outlook", Residents.outlook(camp, cast, day))
        p("residents/outlookRoom", Residents.outlook(camp.copy(villagers = 20), cast, day))
    }

    // --- the engine's chronicle -----------------------------------------------------------------------------------

    private fun engine() {
        p("engine/newGame", GameEngine.newGame(1, t0).logs())
        // a build, an upgrade, a repair, with and without the neighbours
        val s = village(Age.ZASELEK, res = all(1000)).copy(help = 40, residents = castIds.take(6).map { Resident(it, day.toString()) })
        for (t in listOf(BuildingType.TENT, BuildingType.KOZOLEC, BuildingType.CHURCH, BuildingType.LIPA)) {
            p("engine/build/$t", GameEngine.build(s, t, t0).lastLog())
            p("engine/buildMoba/$t", GameEngine.build(s, t, t0, withMoba = true).lastLog())
        }
        val built = s.copy(buildings = listOf(Building("hut-1", BuildingType.HUT, 0), Building("well-2", BuildingType.WELL, 1, damaged = true)))
        p("engine/upgrade", GameEngine.upgrade(built, "hut-1", t0).lastLog())
        p("engine/upgradeMoba", GameEngine.upgrade(built, "hut-1", t0, withMoba = true).lastLog())
        p("engine/repair", GameEngine.repair(built, "well-2", t0).lastLog())
        p("engine/repairMoba", GameEngine.repair(built, "well-2", t0, withMoba = true).lastLog())
        p("engine/moba", Help.moba(built, Catalog.spec(BuildingType.CHURCH).cost)?.text)
        // every age reached
        for (a in Age.entries.drop(1)) {
            val before = Age.entries[a.ordinal - 1]
            val rule = Catalog.ageRules.getValue(a)
            val types = rule.needs.flatMap { (ts, n) -> List(n) { ts.first() } }
            val v = GameState(seed = 1, age = before, resources = all(5000), lastTick = day.toString(), foundedOn = day.toString(),
                buildings = types.mapIndexed { i, t -> Building("b$i", t, i) },
                bonds = castIds.associateWith { Bond(100) })
            p("engine/advance/$a", GameEngine.advance(v, 5000, t0).logs())
            p("engine/advanceCheck/$a", GameEngine.advanceCheck(v.copy(resources = all(1), buildings = emptyList(), bonds = emptyMap()), 3).let { "${it.missing} ${it.steps.map { st -> st.label }}" })
        }
        // gathering leaves marks
        for (r in Res.entries) for (c in listOf(1, 6)) p("engine/gathered/$r/$c", GameEngine.gathered(village(), r, c, 7, day, t0).lastLog())
        // requests: done, failed, expired, new
        for (g in listOf("Babica Micka", "Pastir Luka", "Kovač Tone", "Vinar Marko", "Soseda Zdenka")) {
            val q = Quest("q1", g, "🙂", "Naslov · Title", "Zgodba · Story", Res.FOOD, mapOf(Res.FOOD to 30, Res.WOOD to 10))
            val v = village().copy(quests = listOf(q))
            val (won, r) = GameEngine.completeQuest(v, "q1", 5, 5, t0)
            p("engine/quest/$g/won", "${r.message} | ${won.logs()}")
            val (lost, r2) = GameEngine.completeQuest(v, "q1", 1, 5, t0)
            p("engine/quest/$g/lost", "${r2.message} | ${lost.logs()}")
            p("engine/quest/$g/challenge", GameEngine.questChallenge(v, "q1", pool())?.let { "${it.title}\n${it.intro}" })
        }
        val expiring = village().copy(quests = listOf(Quest("q-old", "Babica Micka", "👵", "T", "S", Res.FOOD, mapOf(Res.FOOD to 5), expiresAt = t0 - 1)))
        p("engine/tick/expired", GameEngine.tick(expiring, day, t0, pool()).news.joinToString("\n") { "${it.emoji} ${it.text}" })
        val fresh = village(Age.TABOR, BuildingType.TENT).copy(lastTick = day.minusDays(1).toString(), foundedOn = day.minusDays(30).toString())
        for (i in 0 until 20) {
            val d = day.plusDays(i.toLong())
            p("engine/tick/new/$i", GameEngine.tick(fresh.copy(lastTick = d.minusDays(1).toString(), seed = 100L + i), d, noon(d), pool()).news.joinToString("\n") { "${it.emoji} ${it.text}" })
        }
        // days away: hungry, cold, people come and go
        val away = village(Age.ZASELEK, BuildingType.TENT, BuildingType.TENT, BuildingType.HUT, res = mapOf(Res.FOOD to 5, Res.WOOD to 3)).copy(lastTick = day.minusDays(4).toString(), villagers = 3, morale = 20, lowMoraleDays = 2)
        p("engine/tick/away", GameEngine.tick(away, day, t0, pool()).news.joinToString("\n") { "${it.emoji} ${it.text}" })
        val grow = village(Age.ZASELEK, BuildingType.TENT, BuildingType.TENT, BuildingType.HUT, res = all(400)).copy(lastTick = day.minusDays(3).toString(), villagers = 2, morale = 80)
        p("engine/tick/grow", GameEngine.tick(grow, day, t0, pool()).news.joinToString("\n") { "${it.emoji} ${it.text}" })
        // the tutor's requests
        val m = ModuleInfo("dvojina", 2, "Dvojina · The dual", "A1", quest = QuestMeta("Učiteljica Mojca", "👩‍🏫", "Mojca prosi. · Mojca asks.", "stone", mapOf("kamen" to 50)))
        p("engine/tutorQuest", GameEngine.withTutorQuests(village(), listOf(m), t0).logs())
        p("engine/gather", Res.entries.map { GameEngine.gatherChallenge(village(), it, pool()).let { c -> "${c.title} / ${c.intro}" } })
    }

    // --- what's coming --------------------------------------------------------------------------------------------

    private fun tomorrow() {
        for (spec in Catalog.projects) {
            val s = village(spec.age, res = all(5000)).copy(projectDay = day.toString(), projects = mapOf(spec.id to 1), residents = emptyList())
            p("tomorrow/project/${spec.id}", Tomorrow.teasers(s, day).filter { it.kind == Teaser.Kind.PROJECT }.map { it.line })
        }
        for (m in 1..12) {
            val d = LocalDate.of(2026, m, 1)
            p("tomorrow/$m", Tomorrow.teasers(village(Age.TABOR, BuildingType.TENT), d).map { it.line })
        }
        val visitor = village().copy(visitor = Visit("mojca", day.toString()))
        p("tomorrow/visitor", Tomorrow.teasers(visitor, day).map { it.line })
    }
}
