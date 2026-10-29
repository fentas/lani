package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame

/**
 * Without a face of their own the people are drawn pixel for pixel as before the faces came (companion/SCENES.md, "Faces"):
 * every sprite in every pose, in a portrait at three sizes, by day and at night, and standing in a scene, still and talking,
 * at four moments (a blink, a breath, a talking mouth among them), against the digests taken from the painters as they
 * were on 2026-10-29, before the faces.
 */
class FacesGoldenTest {
    /** 64-bit FNV-1a. */
    private fun fnv(ints: IntArray): String {
        var h = -0x340d631b7bdddcdbL
        for (v in ints) { h = h xor (v.toLong() and 0xffffffffL); h *= 0x100000001b3L }
        return java.lang.Long.toHexString(h)
    }

    private class OnePerson(val p: PersonInScene) : ArtPainter() {
        override val art = "one"
        override fun backdrop(frame: SceneFrame): Int = 0xFF5F983D.toInt()
        override fun paint() { person(p, 24, 52, flip = false) }
    }

    /** [sprite] in [pose]: its portraits and its scene sprite at the four moments, one digest of them all. */
    private fun digest(sprite: String, pose: Pose): String {
        val painter = PortraitsPainter()
        val p = StringBuilder()
        for ((k, t) in listOf(0.4, 1.3, 2.75, 3.62).withIndex()) {
            for (size in listOf(29, 32, 64)) {
                val c = PixelCanvas(size, size)
                painter.render(c, sprite, pose, t, if (k % 2 == 0) 11f else 22f, k * 7919)
                p.append(fnv(c.pixels)).append(' ')
            }
            for (talking in listOf(false, true)) {
                val c = PixelCanvas(48, 56)
                OnePerson(PersonInScene("x$k", sprite, "a", pose = pose, talking = talking)).render(c, SceneFrame(time = t))
                p.append(fnv(c.pixels)).append(' ')
            }
        }
        return fnv(p.toString().map { it.code }.toIntArray())
    }

    @Test fun `without a face everyone is drawn as before the faces`() {
        val changed = ArrayList<String>()
        for (sprite in SceneArt.people + listOf("woman", "man")) for (pose in Pose.entries) {
            val want = golden[sprite]?.get(pose.name) ?: continue
            if (digest(sprite, pose) != want) changed += "$sprite/$pose"
        }
        assertEquals("drawn otherwise than before the faces", emptyList<String>(), changed)
        // every sprite and pose has its digest
        assertEquals((SceneArt.people + listOf("woman", "man")).toSet(), golden.keys)
    }

    /** Taken from the painters before the faces (2026-10-29). */
    private val golden: Map<String, Map<String, String>> = mapOf(
        "grandma" to mapOf("IDLE" to "796bcd3e241f0ee9", "TALK" to "8ba83b86d771f15a", "HAPPY" to "8f41ef577a292801", "CHEER" to "dc35b0dba34dec92", "SAD" to "e0b750dc282b5c55", "THINK" to "e3967f3af5400c8b", "LISTEN" to "bd2d69d830bcab1c", "WAVE" to "1da491e2591a329b", "SLEEP" to "4c52c2a299a2f0a1"),
        "grandpa" to mapOf("IDLE" to "77e580ea7ef658c7", "TALK" to "6737421608b5e0bc", "HAPPY" to "ddf3b493b9b71f47", "CHEER" to "4820573c70a31e0f", "SAD" to "ce15cb062a19aa1b", "THINK" to "e3ae579740d0938b", "LISTEN" to "1dce07df5b2e4113", "WAVE" to "2de83fd26f328663", "SLEEP" to "fab7af97f4a331a1"),
        "child1" to mapOf("IDLE" to "5324e48fdc1885d", "TALK" to "b653c1b20889bc7f", "HAPPY" to "e713449d9e2e997b", "CHEER" to "cb5c8f990a25d38e", "SAD" to "3a124f890c06f080", "THINK" to "7153eca987bcf632", "LISTEN" to "5707c1ca1d282405", "WAVE" to "ae6014aee1d15f89", "SLEEP" to "c526d9648dbe4b4b"),
        "child2" to mapOf("IDLE" to "93d097e7915bbcd6", "TALK" to "8802f149fbd0f2c9", "HAPPY" to "d39a2e6e3bf7749c", "CHEER" to "415322dbde5b1d01", "SAD" to "d3231f706566f1de", "THINK" to "e94626ec5d54853c", "LISTEN" to "3cad1d259655e563", "WAVE" to "ea383f22866d3574", "SLEEP" to "a121ea0ac731f2c5"),
        "child3" to mapOf("IDLE" to "2708e792197c94f1", "TALK" to "901145ce803a2edc", "HAPPY" to "ffd7b085dd9f9222", "CHEER" to "54c31f5f8abf7fc9", "SAD" to "eedec9f656aaec19", "THINK" to "a0f04ba7701f20d", "LISTEN" to "6da9fea10a8af9cc", "WAVE" to "9e27db4cad98cec5", "SLEEP" to "3899b1e48e90d04f"),
        "shepherd" to mapOf("IDLE" to "b3dd0a84d408007a", "TALK" to "cb9611038c99d8c5", "HAPPY" to "a4f46fa16f705079", "CHEER" to "302536c1b980ba74", "SAD" to "aeea2f95cc18be4b", "THINK" to "b0a5fb73de9e9293", "LISTEN" to "15ba88a1d4bd100a", "WAVE" to "ac85d02d24dac7e", "SLEEP" to "b62d6a41bb624f97"),
        "smith" to mapOf("IDLE" to "a371ef8757c555e3", "TALK" to "200f7896428c70a3", "HAPPY" to "c51e1da7d5f18b91", "CHEER" to "bc5078251f7ad937", "SAD" to "905ddf100228206", "THINK" to "c99f41263b215be", "LISTEN" to "f9cc5a7d1b30c8c0", "WAVE" to "c349009bc00ef3c9", "SLEEP" to "b17e366e51d4546f"),
        "teacher" to mapOf("IDLE" to "560e20af0f894658", "TALK" to "2cc95636a0daf4c3", "HAPPY" to "6d23096511b74cc5", "CHEER" to "8da897143112df82", "SAD" to "70217e74fd0011f1", "THINK" to "565a9a00282cb18c", "LISTEN" to "25c9c5a42092a99a", "WAVE" to "dc7af43b8501ede9", "SLEEP" to "a361a823c618f1e7"),
        "farmer" to mapOf("IDLE" to "d0a0614795a453b6", "TALK" to "9ae7591977a7180d", "HAPPY" to "1e1fa0ce4e2cec7c", "CHEER" to "cfef3bb761258a4a", "SAD" to "66cf05eb31ca5a4", "THINK" to "723a814e02bc3b90", "LISTEN" to "e7d2a52e5aee893b", "WAVE" to "3c58bf1dfc040ab2", "SLEEP" to "a15cbca26f2529a5"),
        "beekeeper" to mapOf("IDLE" to "93af88a437123d1", "TALK" to "1d69a68df31278f7", "HAPPY" to "c850d254b0c7726", "CHEER" to "6386048961fbf2f3", "SAD" to "efe575408b680b3a", "THINK" to "fa0403553da5a792", "LISTEN" to "9bfea0196e6b223b", "WAVE" to "e96ba583ecb271dd", "SLEEP" to "6c1a7bc0799c341f"),
        "innkeeper" to mapOf("IDLE" to "14792c9da10a4fc", "TALK" to "72c185871ccc1373", "HAPPY" to "ca2ddb93461c51bd", "CHEER" to "87e1153fbbce3b00", "SAD" to "3ddee708da8c3541", "THINK" to "c100307cecbe2afc", "LISTEN" to "b5d550c0873d3641", "WAVE" to "273c690dbb7dbded", "SLEEP" to "4db81959a7009beb"),
        "winemaker" to mapOf("IDLE" to "37faa68b6c72bbeb", "TALK" to "bde782c427f63ed5", "HAPPY" to "e0680c51c40ff308", "CHEER" to "91094fe0d3b2a36f", "SAD" to "8766d44437e4f3d6", "THINK" to "8191cef67d831982", "LISTEN" to "27f760f13537c870", "WAVE" to "c244a838fc1698a0", "SLEEP" to "be48071138952947"),
        "aunt" to mapOf("IDLE" to "f1943f16197a8c58", "TALK" to "90633ceecae569a6", "HAPPY" to "1ae58e4bcc764f71", "CHEER" to "97a3154c9434d20", "SAD" to "6c585f0224d3a5fb", "THINK" to "eceade0087716046", "LISTEN" to "fd69c54621232bc6", "WAVE" to "a4f003ede074f0", "SLEEP" to "d4950219480970eb"),
        "pedlar" to mapOf("IDLE" to "da16854adcee2480", "TALK" to "62eee1b5a9179e8a", "HAPPY" to "8cee917655b45865", "CHEER" to "689148b0ac24fb13", "SAD" to "7a958e23772e9850", "THINK" to "3c16fdeedbf4ff28", "LISTEN" to "cf6b01b5308c9a46", "WAVE" to "f9fc97fe448d860d", "SLEEP" to "2fdf66e8402a4163"),
        "pilgrim" to mapOf("IDLE" to "6ef790a239ccb1e6", "TALK" to "103a414b1525ad", "HAPPY" to "c7762534f8a1efbd", "CHEER" to "47ef604fd2ebdaa3", "SAD" to "180510899ef141f0", "THINK" to "95ff49897bbd0708", "LISTEN" to "6e36746956f76e46", "WAVE" to "35cad047532424b7", "SLEEP" to "4b7540cb4a2d090d"),
        "woman" to mapOf("IDLE" to "438731255bc4db7", "TALK" to "e161f8d8fbb1c15e", "HAPPY" to "5c8164b6ba2f2e2d", "CHEER" to "e3330e37558a6295", "SAD" to "11d124dd574ee392", "THINK" to "1f8da1cb4dae5f76", "LISTEN" to "31040454f89c5c34", "WAVE" to "47a27b9a0a666de3", "SLEEP" to "80b73a42eb41c7b9", "IDLE" to "438731255bc4db7", "TALK" to "e161f8d8fbb1c15e", "HAPPY" to "5c8164b6ba2f2e2d", "CHEER" to "e3330e37558a6295", "SAD" to "11d124dd574ee392", "THINK" to "1f8da1cb4dae5f76", "LISTEN" to "31040454f89c5c34", "WAVE" to "47a27b9a0a666de3", "SLEEP" to "80b73a42eb41c7b9"),
        "man" to mapOf("IDLE" to "43baf32be0fead8d", "TALK" to "15e2289c10283728", "HAPPY" to "259cdba4ee6eea58", "CHEER" to "ce61b05059d676b9", "SAD" to "47c2e4f018a18d65", "THINK" to "7185f4d8d4de3bb0", "LISTEN" to "73b363316db3b946", "WAVE" to "44a7e48176fd4bff", "SLEEP" to "3f51012afaa8e9ab", "IDLE" to "43baf32be0fead8d", "TALK" to "15e2289c10283728", "HAPPY" to "259cdba4ee6eea58", "CHEER" to "ce61b05059d676b9", "SAD" to "47c2e4f018a18d65", "THINK" to "7185f4d8d4de3bb0", "LISTEN" to "73b363316db3b946", "WAVE" to "44a7e48176fd4bff", "SLEEP" to "3f51012afaa8e9ab"),
        "baby" to mapOf("IDLE" to "d1906f9ed1ebdca6", "TALK" to "8b5374c1bc82166b", "HAPPY" to "3ac6be91b75d8239", "CHEER" to "9c804e3e22c3b3a4", "SAD" to "6c8354defa5d2d86", "THINK" to "8b8ca6432f8da493", "LISTEN" to "5a57bdf249a95cfc", "WAVE" to "78b7c28bc56d1241", "SLEEP" to "e00dadc18d8ff5c1"),
        "hunter" to mapOf("IDLE" to "405a5dc75185ff6f", "TALK" to "7ac3a704ee0181f3", "HAPPY" to "d5b62d8050002f52", "CHEER" to "7bd538022870b1b1", "SAD" to "73f396754cc5711a", "THINK" to "1b7499b07e818aa4", "LISTEN" to "373c658deb81be9f", "WAVE" to "4dd1753ba19b7068", "SLEEP" to "54cddf7ac35eb0f3"),
        "burner" to mapOf("IDLE" to "6d65cd2270f2ae8b", "TALK" to "3ecbdc50ac8aec9c", "HAPPY" to "1f8f7546559a95f3", "CHEER" to "826028c45b36a674", "SAD" to "856905bec6717ad", "THINK" to "72259a73cfe9e0f7", "LISTEN" to "1e29bae1fc33ed99", "WAVE" to "3b54951ee0809ef3", "SLEEP" to "ef51ab68260ece97"),
    )
}
