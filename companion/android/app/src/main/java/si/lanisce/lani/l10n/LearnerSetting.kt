package si.lanisce.lani.l10n

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Who the content speaks to on this phone ([Learner.current]): the learner profile's name, gender and name forms
 * (learner-profile.json `learner.name`, `learner.gender`, `learner.name_forms`, served by the bridge's /state), as last
 * read, so the village speaks to them offline and before the first connection too.
 *
 * SharedPreferences, like the language pair (LangSetting): read before the first frame, and by the widget, the background
 * check and the car, none of which should wait for a coroutine.
 */
class LearnerSetting(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("learner", Context.MODE_PRIVATE)

    /** The profile as last read: its name, its gender ("male", "female"; null: not said) and its name forms. */
    data class Profile(val name: String?, val gender: String?, val forms: Map<String, String> = emptyMap())

    val profile: Profile
        get() = Profile(
            store.getString(NAME, null),
            store.getString(GENDER, null),
            store.getString(FORMS, null)?.let { raw ->
                runCatching { (Json.parseToJsonElement(raw) as JsonObject).mapValues { (_, v) -> (v as JsonPrimitive).content } }.getOrNull()
            }.orEmpty(),
        )

    /** The learner profile says [p]; true when that changed it. */
    fun profileSays(p: Profile): Boolean {
        if (p == profile) return false
        store.edit()
            .putString(NAME, p.name)
            .putString(GENDER, p.gender)
            .putString(FORMS, buildJsonObject { p.forms.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }.toString())
            .apply()
        return true
    }

    /** The content speaks to the profile's learner from now on (the village's [language] names one without a name); returns them. */
    fun apply(language: String = L10n.ownPair.target.code): Learner {
        val p = profile
        return Learner.of(p.name, p.gender, p.forms, language).also { Learner.current = it }
    }

    private companion object {
        const val NAME = "name"
        const val GENDER = "gender"
        const val FORMS = "name_forms"
    }
}
