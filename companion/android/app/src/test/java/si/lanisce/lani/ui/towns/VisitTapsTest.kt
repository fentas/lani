package si.lanisce.lani.ui.towns

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.game.sky.SkyTap
import si.lanisce.lani.ui.game.PlaceTap
import si.lanisce.lani.ui.towns.VisitTaps.Open

/**
 * A visited town's map answers the home village's taps that only look or teach (docs/plans/05-built/visit-voices.md, "Look
 * and learn on a visit"): the night sky, a wild animal's word, a place's card (a building, the fire, a landmark too, as a
 * guest's), someone of the town; never what would change the host.
 */
class VisitTapsTest {
    @Test fun `the night sky opens its card, the moon, a star, a planet, a shooting star`() {
        for (t in listOf(SkyTap.Moon, SkyTap.Star(424, "malivoz"), SkyTap.Figure("orion"), SkyTap.Planet("jupiter"), SkyTap.MilkyWay, SkyTap.Meteor(7, "perseids"))) {
            assertEquals(Open.Sky(t), VisitTaps.sky(t))
        }
    }

    @Test fun `TalkBack's tonight's sky opens its list, also when nothing is up (its sheet says so, as at home)`() {
        assertEquals(Open.SkyList(listOf(SkyTap.Moon)), VisitTaps.skyList(listOf(SkyTap.Moon)))
        assertEquals(Open.SkyList(emptyList()), VisitTaps.skyList(emptyList()))
    }

    @Test fun `a wild animal opens its word's card`() {
        assertEquals(Open.Word("scoiattolo"), VisitTaps.animal("scoiattolo"))
        assertEquals(Open.Nothing, VisitTaps.animal(" "))
    }

    @Test fun `a building, the fire and a landmark open their card, never the sheets that change the village`() {
        val hut = Building("b1", BuildingType.HUT, plot = 1)
        assertEquals(Open.Place(PlaceTap.Built(hut)), VisitTaps.place(PlaceTap.Built(hut), scenes = 0))
        assertEquals(Open.Place(PlaceTap.Fire), VisitTaps.place(PlaceTap.Fire, scenes = 0))
        assertEquals(Open.Place(PlaceTap.Landmark("mlin")), VisitTaps.place(PlaceTap.Landmark("mlin"), scenes = 0))
        assertEquals(Open.Place(PlaceTap.Forest), VisitTaps.place(PlaceTap.Forest, scenes = 0))
        assertEquals(Open.Place(PlaceTap.Spot("kopa")), VisitTaps.place(PlaceTap.Spot("kopa"), scenes = 0))
    }

    @Test fun `the far hills open only what opens there, a tap on nothing is nothing`() {
        assertEquals(Open.Nothing, VisitTaps.place(PlaceTap.Spot(TownSpots.HORIZON), scenes = 0))
        assertEquals(Open.Place(PlaceTap.Spot(TownSpots.HORIZON)), VisitTaps.place(PlaceTap.Spot(TownSpots.HORIZON), scenes = 1))
        assertEquals(Open.Nothing, VisitTaps.place(null, scenes = 0))
    }

    @Test fun `someone of the town opens their card, someone the town doesn't have nothing`() {
        assertEquals(Open.Person("rosa"), VisitTaps.person("rosa") { it == "rosa" })
        assertEquals(Open.Nothing, VisitTaps.person("pedlar") { it == "rosa" })
    }
}
