package si.lanisce.lani.spike.validators

import android.content.Context

/** No engine: the baseline APK. */
object Engine {
    suspend fun evaluate(context: Context, script: String): String = "no engine (${script.length} characters)"
}
