package si.lanisce.lani.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the case lens remembers for the learner of this phone (docs/plans/05-built/case-lens.md): whether readings,
 * stories and dialogs underline each noun in its case's colour ([colours]: "🎨 Skloni · Cases"; off by default).
 */
class CasePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("case_lens", Context.MODE_PRIVATE)

    var colours by mutableStateOf(prefs.getBoolean(KEY_COLOURS, false))
        private set

    fun showColours(on: Boolean) {
        if (colours == on) return
        colours = on
        prefs.edit().putBoolean(KEY_COLOURS, on).apply()
    }

    private companion object {
        const val KEY_COLOURS = "colours"
    }
}
