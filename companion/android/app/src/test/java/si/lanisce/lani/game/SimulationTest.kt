package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.QuestMeta
import si.lanisce.lani.game.BuildingType.*
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Resident
import kotlin.random.Random

/**
 * Sixty days of learner profiles against the real engine. Guards the pacing targets in GAME.md ("Pacing").
 *
 * Practice outside the village is modelled on the app: reviews and word packs (ReviewPlanner variants:
 * mostly 🌾 with some 🪵), the curated grammar modules (12 exercises: 4 🌾, 7 🪨, 1 📜, plus their tutor
 * quest), talk role-play with a villager (one 📜 entry per line, corrected lines pay like an almost), family
 * answers (3 correct 📜). A hinted right answer pays like an almost, so it sits in the "almost" share.
 * Village runs (events, local quests, gathering) are played through the engine. Words are words learned
 * (a pack's six are answered right in its session); the target is B2, as Jan's. A review answered wrong leaves its
 * word rusty (it doesn't count, and the next age waits for it): the rusty words come back first in the next day's
 * review, and when they are all that holds the next age back, the player polishes them at once (a short review of
 * just them, the age step's button).
 *
 * The people are modelled on the app too: the cast moves in by its order once its age and its home are there
 * (Kovač Tone with the smithy, Mojca with the school), only who lives here asks for help (a tutor quest waits for
 * its giver), and friendship grows as in VILLAGERS.md: +10 for a request passed, +6 for a talk, +1 a session
 * on the training stage with today's companion (the friend seen longest ago) or the worker of the run.
 *
 * The player is sensible, not optimal: it repairs first, advances when it can, builds a fixed plan (what
 * the next age needs, then extras), then buys the cheapest upgrade, then has the smith forge a tool, then does
 * the day's project step (a short practice with its leader), then hosts a feast. It calls a moba whenever the
 * neighbours can help. From halfway to the next word milestone it saves a growing share of that age's price, but
 * spends from a store that is full anyway. It gathers what it is shortest of, weighted towards what its other
 * practice brings little of; it talks with the villager closest to becoming a friend, and gives goods to those who
 * like them. Every session it plays the day's surprise at the road and the festival that is on, and with full
 * stores it buys goods for the people who'd like them (and the pedlar's rare one).
 *
 * Printed per day: age, words (and 🔩 rusty ones), resources/caps, villagers, morale, fire, events won/lost, friends
 * and 🤝, buildings and what was done (or what the player saves for and how much is missing).
 */
class SimulationTest {
    // --- the learner --------------------------------------------------------------------------

    /** One day of practice. A day with nothing in it is a day off: the app stays closed. */
    private data class Plan(
        val reviews: Int = 0,
        val packs: Int = 0,
        val module: Boolean = false,
        val talk: Boolean = false,
        val family: Boolean = false,
        /** Village runs: the active event first, then a quest paying what is needed, else gathering it. */
        val runs: Int = 0,
    ) {
        val active get() = reviews + packs + runs > 0 || module || talk || family
    }

    private class Profile(val name: String, val plan: (day: Int) -> Plan)

    /** 10 min: reviews every day, a word pack every other day, one village run on the days between. */
    private val casual = Profile("casual") { d ->
        if (d % 2 == 1) Plan(reviews = 12, packs = 1) else Plan(reviews = 12, runs = 1, family = d % 7 == 0)
    }

    /** 30–40 min: reviews, a pack, a grammar module or a talk scene, family every third day, three runs. */
    private val committed = Profile("committed") { d ->
        Plan(reviews = 20, packs = 1, module = d % 2 == 1, talk = d % 2 == 0, family = d % 3 == 0, runs = 3)
    }

    /** Five intense days (an hour each), ten days away, again. */
    private val binge = Profile("binge+gap") { d ->
        if ((d - 1) % 15 < 5) Plan(reviews = 25, packs = 2, module = true, talk = true, family = true, runs = 5) else Plan()
    }

    /** Committed, but never talks and has no family challenges: 📜 only from modules and the linden. */
    private val noTalk = Profile("no talk") { Plan(reviews = 20, packs = 1, module = true, runs = 3) }

    private class Learner(seed: Long) {
        private val r = Random(seed)

        fun verdict(correct: Double, almost: Double): Verdict {
            val x = r.nextDouble()
            return if (x < correct) Verdict.CORRECT else if (x < correct + almost) Verdict.ALMOST else Verdict.WRONG
        }

        fun res(mix: List<Pair<Res, Double>>): Res {
            var x = r.nextDouble()
            for ((k, p) in mix) {
                x -= p
                if (x < 0) return k
            }
            return mix.last().first
        }
    }

    /** Someone of the cast: moves in (by list order) from [since], once one of [home] stands (none: a spot). */
    private data class Person(val id: String, val name: String, val since: Age, val home: List<BuildingType> = emptyList())

    private companion object {
        const val DAYS = 60
        const val START_WORDS = 23 // Jan's learned words today (answered right at least once)
        /** Jan's target level: Zaselek at 60 learned words, Vas at 300, Trg at 1000, Mesto at 2000. */
        const val TARGET = "B2"
        const val PACK_WORDS = 6
        /** The cast in the order they move in (the villager files' `order`, `since` and `home`). */
        val CAST = listOf(
            Person("micka", "Babica Micka", Age.OGENJ), Person("luka", "Pastir Luka", Age.OGENJ), Person("zala", "Zala", Age.OGENJ),
            Person("janez", "Stari Janez", Age.OGENJ), Person("france", "Mlinar France", Age.OGENJ, listOf(FIELD, KOZOLEC)),
            Person("tine", "Tine", Age.OGENJ, listOf(FIELD, KOZOLEC)), Person("ancka", "Teta Ančka", Age.TABOR),
            Person("anton", "Čebelar Anton", Age.TABOR), Person("tone", "Kovač Tone", Age.ZASELEK, listOf(SMITHY)),
            Person("marko", "Vinar Marko", Age.ZASELEK), Person("vida", "Gostilničarka Vida", Age.ZASELEK, listOf(MARKET, HOUSE)),
            Person("nejc", "Nejc", Age.ZASELEK, listOf(MARKET, HOUSE)), Person("mojca", "Učiteljica Mojca", Age.VAS, listOf(SCHOOL)),
        )
        /** Who works where a gathering run goes (the training stage). */
        val WORKERS = mapOf(Res.FOOD to "france", Res.WOOD to "luka", Res.STONE to "tone", Res.WISDOM to "janez")
        /** Reviews: ReviewPlanner variants for single words (recognize, pick, type → 🌾; listen, dictation → 🪵; tiles → 🪨). */
        val REVIEW_MIX = listOf(Res.FOOD to 0.64, Res.WOOD to 0.32, Res.STONE to 0.04)
        val PACK_INTRO_MIX = listOf(Res.FOOD to 0.73, Res.WOOD to 0.27)
        val PACK_RECALL_MIX = listOf(Res.FOOD to 0.58, Res.WOOD to 0.38, Res.STONE to 0.04)
        /** A curated grammar module: 3 choice + 1 translate (🌾), 4 cloze + 2 reorder + 1 multi (🪨), 1 speak (📜). */
        val MODULE_SKILLS = List(4) { Res.FOOD } + List(7) { Res.STONE } + Res.WISDOM
        const val TALK_LINES = 8
        const val FAMILY_ENTRIES = 3
        val TUTOR_REWARD = mapOf("stone" to 60, "wisdom" to 10)
        const val FIRST_MODULES = 6
        /** The tutor publishes a new module (with a quest) every few days. */
        const val NEW_MODULE_EVERY = 5
        /** Saving for the next age starts this many words before its milestone (about one pack). */
        const val NEAR_WORDS = 6
    }

    /** Cards for village runs plus drills from the curated modules (cloze, reorder, multi, speak). */
    private val cards = ContentPool(
        Fixtures.cards,
        (1..FIRST_MODULES).flatMap { i ->
            listOf(
                "m$i" to Exercise.Cloze("Jaz ___ Jan.", listOf("sem")),
                "m$i" to Exercise.Cloze("Midva ___ doma.", listOf("sva")),
                "m$i" to Exercise.Reorder("I am Jan", listOf("sem", "Jan"), listOf(listOf("Sem", "Jan"))),
                "m$i" to Exercise.Multi("p", listOf("a", "b"), listOf(0)),
                "m$i" to Exercise.Speak("Dober dan", "Say good day"),
            )
        },
        canSpeak = true,
    )

    private fun modules(day: Int) = (1..FIRST_MODULES + (day - 1) / NEW_MODULE_EVERY).map {
        ModuleInfo("m$it", 1, "Module $it", "A1", quest = QuestMeta("Kovač Tone", "⚒️", "story", "stone", TUTOR_REWARD))
    }

    // --- one run --------------------------------------------------------------------------------

    private data class Row(
        val day: Int, val active: Boolean, val age: Age, val words: Int, val res: Map<Res, Int>, val caps: Map<Res, Int>,
        val buildings: List<Building>, val villagers: Int, val morale: Int, val fire: Int, val won: Int, val lost: Int,
        val acts: List<String>,
        /** What the player saves for at the end of the day, how much is still missing, and how much is covered. */
        val target: String?, val shortfall: Int, val covered: Int,
        /** Friends (friendship level 2 or more), 🤝, goods in the chest, tools. */
        val friends: Int = 0, val help: Int = 0, val goods: Int = 0, val tools: Int = 0,
        /** Nothing is left to buy for this age (every plot built, every upgrade and forging done): waiting for words. */
        val complete: Boolean = false,
        /** Something new today: the day's surprise (played, or the pedlar seen), a festival, a project step. */
        val news: Boolean = false,
        /** Project steps done, festivals celebrated, surprises played, goods bought (so far). */
        val steps: Int = 0,
        /** Rusty words at the end of the day (their last review failed). */
        val rusty: Int = 0,
    ) {
        val capped get() = Res.entries.any { r -> res.getValue(r) >= caps.getValue(r) * 0.95 }
        /** Every store (nearly) full: nothing the village earns now is kept. */
        val full get() = Res.entries.all { r -> res.getValue(r) >= caps.getValue(r) * 0.95 }
        val starved get() = Res.entries.any { r -> res.getValue(r) < caps.getValue(r) * 0.25 }
    }

    private inner class Sim(val profile: Profile, val days: Int = DAYS, seed: Long = 7, gameSeed: Long = 42) {
        val learner = Learner(seed)
        /** The answers when polishing rusty words, on their own dice: the rest of the day plays as it would. */
        private val polisher = Learner(seed + 1)
        /** The answers to Luka's request to move the tent (TentMove), on their own dice too. */
        private val mover = Learner(seed + 2)
        var s: GameState = GameEngine.newGame(gameSeed, Fixtures.noon(Fixtures.day0)).copy(
            buildings = listOf(Building("tent-1", TENT, 0)), // Jan has built their first tent
            resources = mapOf(Res.FOOD to 5, Res.WOOD to 5),
        )
        var words = START_WORDS
        /** Words whose last review failed: not in [words] until a review gets them right. */
        var rusty = 0
        /** Rusty words polished at once (not left for the next day's review), and the sessions that did. */
        var polished = 0
        var polishes = 0
        var now = 0L
        var today = Fixtures.day0
        val rows = mutableListOf<Row>()
        /** 📜 (and everything else) credited per source. */
        val income = mutableMapOf<String, MutableMap<Res, Int>>()
        val gapReturns = mutableListOf<Pair<Int, Int>>() // (return day, villagers before the gap)
        var surprises = 0
        var festivals = 0
        var bought = 0
        /** Something new in today's session (see [Row.news]); tries at today's project step. */
        private var news = false
        private var projectTries = 0

        private fun credit(source: String, results: List<Pair<Res, Verdict>>) {
            val (n, got) = GameEngine.earn(s, results)
            s = n
            val m = income.getOrPut(source) { mutableMapOf() }
            got.forEach { (r, v) -> m[r] = (m[r] ?: 0) + v }
        }

        private fun bonus(source: String, before: GameState) {
            val m = income.getOrPut(source) { mutableMapOf() }
            Res.entries.forEach { r -> (s.res(r) - before.res(r)).takeIf { it > 0 }?.let { m[r] = (m[r] ?: 0) + it } }
        }

        // --- the people ------------------------------------------------------------------------

        /** Who lives here: the cast by its order, once its age and its home are there, as many as the population. */
        fun present(): List<Person> =
            CAST.filter { p -> s.age >= p.since && (p.home.isEmpty() || s.buildings.any { it.type in p.home }) }.take(s.villagers)

        private fun pool() = cards.copy(present = present().map { it.name }.toSet())

        /**
         * Who lives here, as the engine sees it ([GameState.residents]): the leader of a project, who a surprise is
         * about, the friends an age counts and who a feast or a festival brings closer.
         */
        private fun settle() {
            val ids = present().map { it.id }
            if (s.residents.map { it.id } != ids) s = s.copy(residents = ids.map { Resident(it, Fixtures.day0.toString()) })
        }

        private fun here(name: String) = present().any { it.name == name }

        private fun points(id: String) = s.bonds[id]?.points ?: 0

        /** A session on the training stage with [id]: +1, at most three a day. */
        private fun train(id: String?) {
            if (id != null) s = Bonds.add(s, id, Bonds.TRAINING, today, training = true, now = now)
        }

        /** Today's companion: the friend met longest ago (not seen since), else Micka. */
        private fun companion(): String {
            val met = present().filter { s.bonds[it.id]?.met != null }
            return met.minWithOrNull(compareBy({ s.bonds[it.id]?.seen ?: "" }, { CAST.indexOf(it) }))?.id ?: "micka"
        }

        /** Who to talk to: the one closest to becoming a friend, else closest to the next gift. */
        private fun talkPartner(): String? {
            val people = present()
            return people.filter { points(it.id) < Bonds.THRESHOLDS[Bonds.FRIEND] }.maxByOrNull { points(it.id) }?.id
                ?: people.filter { points(it.id) < Bonds.THRESHOLDS.last() }.maxByOrNull { points(it.id) }?.id
        }

        /** A quest of [q]'s giver was passed: +10 ♥ with them (and maybe their gift). */
        private fun thanked(q: Quest, passed: Boolean) {
            if (passed) s = Bonds.add(s, Quests.idOf(q.giver), Bonds.QUEST, today, now = now)
        }

        /**
         * Gives goods to who likes them, one a day each, the closest to a new level first; keeps two treats for the
         * feast once there is a linden.
         */
        private fun giveGoods() {
            for (p in present().sortedByDescending { points(it.id) }) {
                if (Bonds.level(points(p.id)) >= Bonds.THRESHOLDS.lastIndex) continue
                val keep = if (s.buildings.any { it.type == LIPA }) Catalog.FEAST_GOODS else 0
                val g = Chest.likes(p.id).firstOrNull { Chest.count(s, it.id) > 0 } ?: continue
                if (g.food && s.chest.goods.filterKeys { Catalog.goods[it]?.food == true }.values.sum() <= keep) continue
                s = Chest.give(s, g.id, p.id, today, now)
            }
        }

        fun run(): Sim {
            var lastActive = 0
            var villagersBefore = s.villagers
            for (day in 1..days) {
                val plan = profile.plan(day)
                today = Fixtures.day0.plusDays(day - 1L)
                now = Fixtures.noon(today)
                if (!plan.active) {
                    rows += row(day, false, emptyList())
                    continue
                }
                if (lastActive > 0 && day - lastActive > 3) gapReturns += day to villagersBefore
                lastActive = day
                s = GameEngine.tick(s, today, now, pool()).state
                settle()
                s = GameEngine.withTutorQuests(s, modules(day), now)
                val acts = mutableListOf<String>()
                news = false
                projectTries = 0

                if (plan.reviews > 0) train(companion())
                review(plan.reviews)
                repeat(plan.packs) {
                    train(companion())
                    credit("packs", List(PACK_WORDS) { learner.res(PACK_INTRO_MIX) to learner.verdict(0.85, 0.05) } +
                        List(PACK_WORDS) { learner.res(PACK_RECALL_MIX) to learner.verdict(0.65, 0.15) })
                    words += PACK_WORDS
                }
                if (plan.module) playModule()
                if (plan.talk) {
                    credit("talk", List(TALK_LINES) { Res.WISDOM to learner.verdict(0.6, 0.4) })
                    talkPartner()?.let { id -> s = GameEngine.helped(Bonds.add(s, id, Bonds.TALK, today, now = now), Catalog.HELP_TALK) }
                }
                if (plan.family) {
                    credit("family", List(FAMILY_ENTRIES) { Res.WISDOM to Verdict.CORRECT })
                    s = GameEngine.helped(s, Catalog.HELP_FAMILY)
                }

                acts += surprise()
                acts += festival()
                acts += moveTent()
                acts += spend()
                repeat(plan.runs) {
                    villageRun()
                    acts += spend()
                }
                acts += buyGoods()
                giveGoods()
                acts += spend()
                if (!news) acts += surprise() // the first surprise comes with the camp (Tabor), in the session
                villagersBefore = s.villagers
                rows += row(day, true, acts)
            }
            return this
        }

        /**
         * Luka's request to move the tent to the pond (TentMove): played the day he asks (and again the next day, not passed),
         * besides the day's runs, on its own dice; it pays like a request.
         */
        private fun moveTent(): List<String> {
            val q = s.quests.firstOrNull { it.id == TentMove.ID && !it.done && here(it.giver) } ?: return emptyList()
            val c = GameEngine.questChallenge(s, q.id, pool()) ?: return emptyList()
            val results = c.skills.map { it to mover.verdict(0.75, 0.10) }
            credit("quests", results)
            val before = s
            val (after, r) = GameEngine.completeQuest(s, q.id, results.count { it.second == Verdict.CORRECT }, results.size, now)
            s = after
            thanked(q, r.won)
            bonus("quests", before)
            return if (r.won) listOf("⛺→🪷") else emptyList()
        }

        /** The day's surprise at the road: played when it's a practice, seen when it's the pedlar (see [buyGoods]). */
        private fun surprise(): List<String> {
            val sp = Surprises.today(s, today) ?: return emptyList()
            if (sp.kind == Surprises.PEDLAR) {
                news = true
                return emptyList()
            }
            val c = GameEngine.surpriseChallenge(s, pool(), today) ?: return emptyList()
            train(sp.who ?: companion())
            val correct = play("surprises", c)
            val before = s
            s = Surprises.finish(s, correct, c.exercises.size, today, now).first
            bonus("surprises", before)
            surprises++
            news = true
            return listOf(Surprises.emoji(sp.kind))
        }

        /** The festival that is on: its run with the festival's words. */
        private fun festival(): List<String> {
            val d = Calendar.open(s, today).firstOrNull() ?: return emptyList()
            val c = GameEngine.festivalChallenge(s, d.festival.id, today) ?: return emptyList()
            train(Calendar.leader(s, d.festival, today) ?: companion())
            val correct = play("festivals", c)
            val before = s
            val (after, r) = Calendar.celebrate(s, d.festival.id, correct, c.exercises.size, today, now)
            s = after
            bonus("festivals", before)
            news = true
            if (!r.won) return emptyList()
            festivals++
            return listOf(d.festival.emoji)
        }

        /**
         * A step the village can do today and still keep what it saves, the one that spends most from the fullest
         * stores (two tries a day).
         */
        private fun projectStep(reserve: Map<Res, Int>): String? {
            if (projectTries >= 2) return null
            val caps = GameEngine.attributes(s).caps
            // the step that spends most from the fullest stores (what the village earns next would be lost there)
            val o = Projects.today(s, today).filter { keeps(it.cost, reserve) }.maxByOrNull { o ->
                o.cost.entries.sumOf { (r, n) -> n * s.res(r).toDouble() / caps.getValue(r) } / o.cost.values.sum().coerceAtLeast(1)
            } ?: return null
            val c = GameEngine.projectChallenge(s, o.spec.id, pool(), today) ?: return null
            projectTries++
            train(o.spec.leader)
            val correct = play("projects", c)
            val (after, r) = Projects.finishStep(s, o.spec.id, correct, c.exercises.size, today, now)
            s = after
            if (!r.won) return null
            news = true
            return "🏗${o.spec.id}${o.done + 1}"
        }

        /**
         * With full stores: buys what the stalls offer for the people who'd like it and haven't had a gift today (the
         * pedlar's rare good too), keeping what the player saves for.
         */
        private fun buyGoods(): List<String> {
            val acts = mutableListOf<String>()
            val caps = GameEngine.attributes(s).caps
            fun plenty(cost: Map<Res, Int>) = cost.all { (r, n) -> s.res(r) - n >= caps.getValue(r) * 0.6 }
            for (stall in Chest.stalls(s, today)) for (o in stall.offers) {
                if (o.left <= 0 || !plenty(o.price) || !keeps(o.price, reserve())) continue
                val wanted = o.good.rare || present().any { p ->
                    Bonds.level(points(p.id)) < Bonds.THRESHOLDS.lastIndex && s.chest.given[p.id] != today.toString() &&
                        Chest.likes(p.id).any { it.id == o.good.id } && Chest.count(s, o.good.id) == 0
                }
                if (!wanted) continue
                val b = Chest.buy(s, stall.id, o.good.id, today, now)
                if (b !== s) {
                    s = b
                    bought++
                    acts += "🛒${o.good.id}"
                }
            }
            return acts
        }

        /**
         * The day's review of [n] cards: the rusty words come back first (the honest judge, a day or more later), and a
         * right or almost-right answer polishes one; a wrong answer on another word leaves it rusty.
         */
        private fun review(n: Int) {
            val back = rusty
            repeat(n) { i ->
                val answer = learner.res(REVIEW_MIX) to learner.verdict(0.75, 0.10)
                credit("reviews", listOf(answer))
                val right = answer.second != Verdict.WRONG
                if (i < back) {
                    if (right) { rusty--; words++ }
                } else if (!right) { rusty++; words-- }
            }
        }

        /**
         * Rusty words are all that holds the next age back: the player polishes them now, a short review of just them
         * (the age step's button), again while one is still rusty, three times at most. True when it did.
         */
        private fun polish(): Boolean {
            if (rusty == 0 || !GameEngine.advanceCheck(s, planned(), TARGET).ok) return false
            polishes++
            repeat(3) {
                if (rusty == 0) return@repeat
                val answers = List(rusty) { polisher.res(REVIEW_MIX) to polisher.verdict(0.75, 0.10) }
                credit("reviews", answers)
                val right = answers.count { it.second != Verdict.WRONG }
                rusty -= right
                words += right
                polished += right
            }
            return true
        }

        private fun playModule() {
            // a tutor's request waits for its giver to live here
            val quest = s.quests.firstOrNull { it.source == QuestSource.TUTOR && !it.done && here(it.giver) }
            train(quest?.let { Quests.idOf(it.giver) } ?: companion())
            val results = MODULE_SKILLS.map { it to learner.verdict(0.65, 0.10) }
            credit("modules", results)
            if (quest != null) {
                val before = s
                val (after, r) = GameEngine.completeQuest(s, quest.id, results.count { it.second == Verdict.CORRECT }, results.size, now)
                s = after
                thanked(quest, r.won)
                bonus("module quests", before)
            }
        }

        private fun play(source: String, c: Challenge): Int {
            val results = c.skills.map { it to learner.verdict(0.75, 0.10) }
            credit(source, results)
            return results.count { it.second == Verdict.CORRECT }
        }

        private fun villageRun() {
            GameEngine.eventChallenge(s, pool())?.let { c ->
                train(companion())
                val correct = play("events", c)
                val before = s
                s = GameEngine.resolveEvent(s, correct, c.exercises.size, now).first
                bonus("events", before)
                return
            }
            val need = need()
            val open = s.quests.filter { it.source == QuestSource.LOCAL && !it.done && it.id != TentMove.ID }
            // a request paying what's needed; while friends are missing for the next age, the friend-to-be's first
            val friendsShort = nextRule()?.let { Bonds.friends(s) < it.friends } == true
            val friendToBe = open.filter { points(Quests.idOf(it.giver)) < Bonds.THRESHOLDS[Bonds.FRIEND] }.maxByOrNull { points(Quests.idOf(it.giver)) }
            val quest = (if (friendsShort) friendToBe else null) ?: open.firstOrNull { it.skill == need }
            quest?.let { q ->
                train(Quests.idOf(q.giver))
                val c = GameEngine.questChallenge(s, q.id, pool())!!
                val correct = play("quests", c)
                val before = s
                val (after, r) = GameEngine.completeQuest(s, q.id, correct, c.exercises.size, now)
                s = after
                thanked(q, r.won)
                bonus("quests", before)
                return
            }
            train(WORKERS[need]?.takeIf { id -> present().any { it.id == id } } ?: companion())
            play("gathering", GameEngine.gatherChallenge(s, need, pool()))
        }

        // --- the player's choices ------------------------------------------------------------

        /**
         * The buildings this player wants by the end of the current age: what the next age needs, then extras, as many
         * as the age has plots (the palisade, round the clearing, takes none; nor the tent that moved to the pond, so its
         * plot takes the next one of the plan).
         */
        fun wanted(): List<BuildingType> {
            val plan = listOf(TENT, TENT) + // Ogenj
                listOf(FIELD, PALISADE, HUT, KOZOLEC) + // Tabor
                listOf(LIPA, HUT, CHURCH, SMITHY) + // Zaselek: the smithy brings Tone (his requests, forging)
                listOf(WELL, SCHOOL, MARKET, HOUSE, KOZOLEC) + // Vas
                listOf(WATCHTOWER, HOUSE, HOUSE, HOUSE, HOUSE, KOZOLEC) // Trg
            val n = PLOTS_PER_AGE[s.age.ordinal]
            var plots = 0
            var pond = if (TentMove.moved(s)) 1 else 0
            val mine = plan.takeWhile { t -> if (t.onPlot && !(t == TENT && pond-- > 0)) plots++ < n else plots < n }
            val have = s.buildings.groupingBy { it.type }.eachCount().toMutableMap()
            return mine.filter { t -> (have[t] ?: 0).let { if (it > 0) { have[t] = it - 1; false } else true } }
        }

        private fun nextAge() = Age.entries.getOrNull(s.age.ordinal + 1)

        private fun nextRule() = nextAge()?.let { Catalog.ageRules.getValue(it) }

        /** The words the player plans with: the rusty ones are a minute's polish away when the age needs them. */
        private fun planned() = words + rusty

        private fun ageNear() = nextAge()?.let { planned() + NEAR_WORDS >= Catalog.wordsFor(it, TARGET) } == true

        /** Share of the way from this age's word milestone to the next one. */
        private fun wordProgress(): Float {
            val next = nextAge() ?: return 0f
            val from = Catalog.wordsFor(s.age, TARGET)
            return ((planned() - from).toFloat() / (Catalog.wordsFor(next, TARGET) - from)).coerceIn(0f, 1f)
        }

        /**
         * What the player keeps: two days of upkeep, the next planned building, and a growing share of the next
         * age (its cost and the buildings it needs) from halfway to its words; all of it once they are near.
         */
        fun reserve(): Map<Res, Int> {
            val a = GameEngine.attributes(s)
            var r: Map<Res, Int> = mapOf(Res.FOOD to a.foodUpkeep * 2, Res.WOOD to a.woodUpkeep * 2)
            val w = wanted()
            w.firstOrNull()?.let { r = r.sumWith(Catalog.spec(it).cost) }
            nextRule()?.let { rule ->
                val share = if (ageNear()) 1f else ((wordProgress() - 0.5f) * 2).coerceAtLeast(0f)
                val needs = rule.needs.flatMap { (types, n) -> List((n - s.buildings.count { it.type in types }).coerceAtLeast(0)) { types.first() } }
                val later = (needs.map { Catalog.spec(it).cost } + rule.cost).fold(emptyMap<Res, Int>()) { acc, c -> acc.sumWith(c) }
                r = r.sumWith(later.mapValues { (it.value * share).toInt() })
            }
            return r.mapValues { (k, v) -> v.coerceAtMost(a.caps.getValue(k)) }
        }

        private fun affords(cost: Map<Res, Int>) = cost.all { (r, n) -> s.res(r) >= n }

        /** A purchase keeps the reserve, except from a store that is full anyway (what it earns next would be lost). */
        private fun keeps(cost: Map<Res, Int>, reserve: Map<Res, Int>): Boolean {
            val caps = GameEngine.attributes(s).caps
            return cost.all { (r, n) -> s.res(r) - n >= (reserve[r] ?: 0) || s.res(r) >= caps.getValue(r) * 0.95 }
        }

        /**
         * What the player saves for: the next planned building, the next age once near, else the upgrade it can
         * afford soonest (what's missing, weighted like [need] towards what its other practice brings little of).
         */
        fun target(): Pair<String, Map<Res, Int>>? {
            wanted().firstOrNull()?.let { return it.name to Catalog.spec(it).cost }
            val passive = passiveShare()
            val upgrade = s.buildings.mapNotNull { b -> GameEngine.upgradeOption(s, b.id)?.let { b to it } }
                .filter { (_, o) -> o.reason == null || o.reason.startsWith("Manjka") }
                .minWithOrNull(compareBy({ (_, o) ->
                    o.cost.entries.sumOf { (r, n) -> ((n - s.res(r)).coerceAtLeast(0) / (passive.getValue(r) + 0.2f)).toDouble() }
                }, { (_, o) -> o.cost.values.sum() }))
            val next = Age.entries.getOrNull(s.age.ordinal + 1)
            val project = openProject()
            if (next != null && (ageNear() || upgrade == null && project == null)) return next.name to nextRule()!!.cost
            return upgrade?.let { (b, o) -> "${b.id}→${o.toLevel}" to o.cost } ?: project?.let { "🏗${it.spec.id}" to it.cost }
        }

        private fun shortfall(cost: Map<Res, Int>) = cost.entries.sumOf { (r, n) -> (n - s.res(r)).coerceAtLeast(0) }

        /** Everything the player is saving for: the target on top of the reserve, as much as the stores hold. */
        fun goal(): Map<Res, Int> {
            val caps = GameEngine.attributes(s).caps
            val t = target()?.second.orEmpty()
            val r = if (target()?.first == wanted().firstOrNull()?.name) reserve() else reserve().sumWith(t)
            return Res.entries.associateWith { maxOf(r[it] ?: 0, t[it] ?: 0) }.mapValues { (k, n) -> n.coerceAtMost(caps.getValue(k)) }
        }

        /** Share of each resource in the practice outside the village so far (what comes in anyway). */
        private fun passiveShare(): Map<Res, Float> {
            val outside = listOf("reviews", "packs", "modules", "talk", "family").mapNotNull { income[it] }
            val total = outside.sumOf { it.values.sum() }.coerceAtLeast(1)
            return Res.entries.associateWith { r -> outside.sumOf { it[r] ?: 0 }.toFloat() / total }
        }

        /**
         * The resource to gather next: the largest shortfall for what the player saves for, weighted towards what
         * the other practice brings little of (a learner who only does words knows stone won't come by itself).
         */
        fun need(): Res {
            val a = GameEngine.attributes(s)
            if (s.res(Res.FOOD) < a.foodUpkeep * 2) return Res.FOOD
            if (s.res(Res.WOOD) < a.woodUpkeep * 2) return Res.WOOD
            val passive = passiveShare()
            return goal().mapValues { (r, n) -> n - s.res(r) }.filterValues { it > 0 }
                .maxByOrNull { (r, short) -> short / (passive.getValue(r) + 0.2f) }?.key
                ?: Res.entries.minBy { s.res(it).toFloat() / a.caps.getValue(it) }
        }

        /** What a moba leaves to pay, or the whole cost without one. */
        private fun due(cost: Map<Res, Int>, moba: Moba?) = if (moba?.affordable == true) moba.rest else cost

        /**
         * Repairs, advances, builds the plan, then upgrades with what is left over, then forges a tool, then does the
         * day's project step, then hosts a feast; with a moba whenever the neighbours can help. Returns what was done.
         */
        fun spend(): List<String> {
            val acts = mutableListOf<String>()
            while (true) {
                s.buildings.firstOrNull { it.damaged && affords(due(GameEngine.repairCost(s, it.id), GameEngine.repairMoba(s, it.id))) }?.let { b ->
                    s = GameEngine.repair(s, b.id, now, withMoba = true)
                    acts += "🔧${b.type.name.lowercase()}"
                    continue
                }
                val polishedNow = polish()
                val adv = GameEngine.advance(s, words, now, TARGET, rusty)
                if (adv.age != s.age) {
                    s = adv
                    acts += (if (polishedNow) "🔩" else "") + "→${adv.age}"
                    continue
                }
                val reserve = reserve()
                val w = wanted()
                val options = GameEngine.buildOptions(s).associateBy { it.type }
                val build = w.withIndex().firstOrNull { (i, t) ->
                    val o = options.getValue(t)
                    (o.available || o.mobaAvailable) && (i == 0 || keeps(due(o.cost, o.moba), reserve))
                }?.value
                if (build != null) {
                    val b = GameEngine.build(s, build, now, withMoba = true)
                    if (b !== s) {
                        acts += "+${build.name.lowercase()}" + if (b.help < s.help) "🤝" else ""
                        s = b
                        continue
                    }
                }
                val up = s.buildings.mapNotNull { b -> GameEngine.upgradeOption(s, b.id)?.takeIf { it.available || it.mobaAvailable }?.let { b to it } }
                    .filter { (_, o) -> keeps(due(o.cost, o.moba), reserve) }
                    .minByOrNull { (_, o) -> o.cost.values.sum() }
                if (up != null) {
                    val u = GameEngine.upgrade(s, up.first.id, now, withMoba = true)
                    acts += "⬆${up.first.type.name.lowercase()}${up.second.toLevel}" + if (u.help < s.help) "🤝" else ""
                    s = u
                    continue
                }
                val forge = s.chest.tools.keys.mapNotNull { Chest.forgeOption(s, it) }
                    .filter { it.available && keeps(it.cost, reserve) }
                    .minByOrNull { it.cost.values.sum() }
                if (forge != null) {
                    s = Chest.forge(s, forge.giver, now)
                    acts += "⚒${forge.giver}${forge.step}"
                    continue
                }
                val step = projectStep(reserve)
                if (step != null) {
                    acts += step
                    continue
                }
                val feast = Chest.feastOption(s, today)?.takeIf { it.available && keeps(it.cost, reserve) }
                if (feast != null) {
                    s = Chest.feast(s, today, now)
                    acts += "🎪"
                    continue
                }
                settle() // a building may have brought someone
                return acts
            }
        }

        /** The next project step the village can work towards (not one that waits for its leader). */
        private fun openProject(): ProjectOption? = Projects.options(s, today).firstOrNull { o ->
            !o.finished && o.waiting != ProjectOption.Why.LEADER && o.waiting != ProjectOption.Why.AGE
        }

        /**
         * Nothing is left to buy for this age: every plot built, every upgrade the age allows, every forging and every
         * project step (but those waiting for their leader) done.
         */
        private fun complete(): Boolean = openProject() == null &&
            s.plotsTaken >= PLOTS_PER_AGE[s.age.ordinal] &&
                s.buildings.all { b -> b.level >= minOf(Catalog.MAX_LEVEL, s.age.ordinal + 1) } &&
                s.chest.tools.keys.none { id -> Chest.forgeOption(s, id)?.reason?.let { it.startsWith("Manjka") } ?: false } &&
                s.chest.tools.keys.none { id -> Chest.forgeOption(s, id)?.available == true }

        private fun row(day: Int, active: Boolean, acts: List<String>): Row {
            val t = target()
            return Row(
                day, active, s.age, words, Res.entries.associateWith { s.res(it) }, GameEngine.attributes(s).caps,
                s.buildings, s.villagers, s.morale, s.fire, s.stats.eventsWon, s.stats.eventsLost, acts,
                t?.first, shortfall(goal()), goal().entries.sumOf { (r, n) -> minOf(n, s.res(r)) },
                Bonds.friends(s), s.help, s.chest.goods.values.sum(), s.chest.tools.size, complete(),
                news, s.projects.values.sum() + festivals + surprises + bought, rusty,
            )
        }

        // --- measures --------------------------------------------------------------------------

        val active get() = rows.filter { it.active }

        /** Day each age was reached, and the day its word milestone was reached. */
        fun reached(a: Age): Int? = rows.firstOrNull { it.age >= a }?.day

        fun wordsDay(a: Age): Int? = Catalog.wordsFor(a, TARGET).let { w -> rows.firstOrNull { it.words >= w }?.day }

        /** The day the village first had the friends [a] needs. */
        fun friendsDay(a: Age): Int? = Catalog.ageRules.getValue(a).friends.let { f -> rows.firstOrNull { it.active && it.friends >= f }?.day }

        /** Active days between the word milestone and the age (0 = the same day). */
        fun lag(a: Age): Int? {
            val r = reached(a) ?: return null
            val w = wordsDay(a) ?: return null
            return active.count { it.day in w until r }
        }

        private fun longest(p: (Row) -> Boolean) =
            active.fold(0 to 0) { (best, cur), r -> if (p(r)) maxOf(best, cur + 1) to cur + 1 else best to 0 }.first

        /** Longest run of active days with nothing built, upgraded, repaired or advanced ("nothing to buy"). */
        val idle get() = longest { it.acts.isEmpty() }

        /**
         * Longest run of active days with nothing visible while the age still has something to buy: no purchase, and
         * no clear progress towards what the player saves for (the stock that covers it grew by a tenth of what was
         * missing). Waiting on words alone is not progress; a complete age is measured by [waiting] instead.
         */
        val stuck: Int
            get() {
                val days = active
                return longest { r ->
                    val prev = days.getOrNull(days.indexOf(r) - 1)
                    val progress = prev != null && prev.shortfall > 0 && r.covered - prev.covered >= maxOf(1, prev.shortfall / 10)
                    !r.complete && r.acts.isEmpty() && !progress
                }
            }

        /** Longest run of active days with the age complete (nothing left to build, upgrade or forge) and nothing done. */
        val waiting get() = longest { it.complete && it.acts.isEmpty() }

        /** Longest run of active days ending with one resource (nearly) full while another is nearly empty. */
        val capped get() = longest { it.capped && it.starved }

        /** The first day the village had nothing left to buy in [a] (every plot, upgrade, forging and project step). */
        fun completeFrom(a: Age): Int? = rows.firstOrNull { it.complete && it.age == a }?.day

        /** Longest run of active days from Tabor on with nothing new: no surprise, no festival, no project step. */
        val nothingNew get() = longest { it.age >= Age.TABOR && !it.news }

        /** Longest run of active days with every store (nearly) full: what the village earns is thrown away. */
        val fullStores get() = longest { it.full }

        /** Active days after each gap until the village has no damage, food and wood for two days, a fire and its people. */
        fun recovery(): List<Int> = gapReturns.map { (day, people) ->
            val after = active.filter { it.day >= day }
            after.indexOfFirst { r ->
                val a = GameEngine.attributes(GameState(age = r.age, villagers = r.villagers))
                r.buildings.none { it.damaged } && r.res.getValue(Res.FOOD) >= a.foodUpkeep * 2 &&
                    r.res.getValue(Res.WOOD) >= a.woodUpkeep * 2 && r.fire >= 25 && r.villagers >= people
            }.let { if (it < 0) 99 else it + 1 }
        }

        fun print() {
            println("=== ${profile.name} ===")
            for (r in rows) {
                if (!r.active) {
                    println("day %2d  (away)".format(r.day))
                    continue
                }
                println(
                    "day %3d %-7s w%4d 🔩%-2d %s 👥%2d 😊%3d 🔥%3d ⚔️%d/%d 💞%d 🤝%3d 🧺%2d 🧰%d  🏠%2d %s  %s".format(
                        r.day, r.age, r.words, r.rusty,
                        Res.entries.joinToString(" ") { "${it.emoji}%4d/%-4d".format(r.res[it], r.caps[it]) },
                        r.villagers, r.morale, r.fire, r.won, r.lost, r.friends, r.help, r.goods, r.tools, r.buildings.size,
                        r.buildings.joinToString(",") { it.type.name.lowercase().take(4) + (if (it.level > 1) "${it.level}" else "") + if (it.damaged) "!" else "" },
                        r.acts.joinToString(" ").ifEmpty { (if (r.complete) "✔ " else "") + "… ${r.target} −${r.shortfall}" },
                    ),
                )
            }
            println(summary())
            println("income: " + income.entries.joinToString("; ") { (k, v) -> "$k " + Res.entries.filter { v.containsKey(it) }.joinToString(" ") { "${it.emoji}${v[it]}" } })
        }

        fun summary() = "${profile.name}: " + Age.entries.drop(1).joinToString(" ") { a ->
            "${a.sl} ${reached(a) ?: "-"}(w${wordsDay(a) ?: "-"} f${friendsDay(a) ?: "-"})"
        } + " | capped days ${active.count { it.capped }}, while starved ${capped}, full ${fullStores} | idle ${idle}, stuck ${stuck}, waiting ${waiting}, nothing new ${nothingNew} | recovery ${recovery()}" +
            " | projects ${Catalog.projects.count { Projects.finished(s, it) }} done (${s.projects.values.sum()} steps), complete " +
            Age.entries.mapNotNull { a -> rows.firstOrNull { it.complete && it.age == a }?.let { "${a.sl} ${it.day}" } }.joinToString(", ").ifEmpty { "-" } +
            " | surprises $surprises, festivals $festivals, bought $bought" +
            " | rusty ${rows.maxOfOrNull { it.rusty } ?: 0} at most, $polished polished in $polishes sessions" +
            " | events ${s.stats.eventsWon}/${s.stats.eventsLost} | ${s.buildings.size} buildings, levels ${s.buildings.sumOf { it.level }}" +
            " | 🤝 ${s.stats.helpEarned} earned, ${s.stats.mobas} mobas | tools ${s.chest.tools.size}, forged ${s.chest.tools.values.sumOf { it.forged }} | friends ${Bonds.friends(s)}"
    }

    private fun sim(p: Profile, days: Int = DAYS) = Sim(p, days).run().also { it.print() }

    // --- properties ---------------------------------------------------------------------------------

    private fun assertPacing(sim: Sim) {
        // The casual learner has one village run every other day, and an event may take it: an upgrade that waits on
        // 📜 (which only the runs bring them) can wait two sessions. Everyone else: at most one.
        val stuckMax = if (sim.profile === casual) 2 else 1
        val problems = buildList {
            for (a in Age.entries.drop(1)) {
                val lag = sim.lag(a) ?: continue
                if (lag > 3) add("reaches ${a.sl} $lag active days after its words")
            }
            if (sim.stuck > stuckMax) add("${sim.stuck} sessions in a row with nothing to buy and no progress")
            if (sim.idle > 10) add("${sim.idle} sessions in a row without buying anything")
            // a complete age waits on words; the weekly feast keeps a use for the stores
            if (sim.waiting > Catalog.FEAST_EVERY_DAYS) add("${sim.waiting} sessions in a row waiting with nothing to spend on")
            if (sim.capped > 5) add("${sim.capped} days with one resource full while another is nearly empty")
            // something new every day: the day's surprise at the road (from Tabor on), a festival, a project step
            if (sim.nothingNew > 0) add("${sim.nothingNew} sessions in a row with nothing new")
            // the long ages: projects, the feast and the stalls keep a use for the stores; never every store full a week long
            if (sim.fullStores > Catalog.FEAST_EVERY_DAYS) add("${sim.fullStores} sessions in a row with every store full")
        }
        assertTrue("${sim.summary()}: $problems", problems.isEmpty())
    }

    @Test fun `casual learner`() {
        val sim = sim(casual)
        assertPacing(sim)
        assertTrue("casual reaches Zaselek", sim.s.age >= Age.ZASELEK)
    }

    @Test fun `committed learner`() {
        val sim = sim(committed)
        assertPacing(sim)
        assertTrue("committed reaches Vas", sim.s.age >= Age.VAS)
        val wisdom = sim.income.mapValues { it.value[Res.WISDOM] ?: 0 }
        val others = wisdom.filterKeys { it != "talk" }.values.max()
        assertTrue("talk is clearly the best 📜 source: $wisdom", wisdom.getValue("talk") >= 2 * others)
    }

    @Test fun `binge and gap recovers`() {
        val sim = sim(binge)
        assertPacing(sim)
        assertEquals(3, sim.recovery().size)
        assertTrue("recovers within two active days: ${sim.recovery()}", sim.recovery().all { it <= 2 })
    }

    @Test fun `committed learner until the market town`() {
        val sim = sim(committed, 170)
        assertPacing(sim)
        assertTrue("committed reaches Trg", sim.s.age >= Age.TRG)
        // Vas is long (300 to 1000 words): its six projects keep something to build until late in it (before them the
        // village was complete by about day 106 and waited for Trg, day 163, with full stores)
        val vasDone = sim.completeFrom(Age.VAS)
        assertTrue("the Vas village has something to build until late: complete from day $vasDone", vasDone == null || vasDone >= 130)
        assertTrue("every project of Zaselek and Vas is done in Vas", Catalog.projects.filter { it.age <= Age.VAS }.all { Projects.finished(sim.s, it) })
        // eight friends (Mesto's) are within reach long before Mesto's 2000 words
        assertTrue("eight friends by day 170: ${sim.friendsDay(Age.MESTO)}", sim.friendsDay(Age.MESTO) != null)
    }

    @Test fun `pacing holds for other villages and other luck`() {
        for (seed in 1L..6L) for (p in listOf(casual, committed, binge, noTalk)) {
            val sim = Sim(p, seed = seed * 31, gameSeed = seed).run()
            println("seed $seed ${sim.summary()}")
            if (System.getenv("SIM_TRACE") == "${p.name}:$seed") sim.print()
            assertPacing(sim)
            if (p === binge) assertTrue("recovers within two active days: ${sim.recovery()}", sim.recovery().all { it <= 2 })
        }
    }

    @Test fun `wisdom without talk`() {
        val sim = sim(noTalk)
        assertPacing(sim)
        assertTrue("reaches Vas without talk", sim.s.age >= Age.VAS)
    }
}
