package si.lanisce.lani.spike.validators

import android.content.Context
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers

/** QuickJS (quickjs-kt): a runtime in this process. */
object Engine {
    suspend fun evaluate(context: Context, script: String): String = runCatching {
        val js = QuickJs.create(Dispatchers.Default)
        try { js.evaluate<Any?>(script, "main.js", false).toString() } finally { js.close() }
    }.getOrElse { "error: ${it.message}" }
}
