package si.lanisce.lani.spike.validators

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox

/** Jetpack JavaScriptEngine: the WebView's V8 in a sandboxed process (its futures awaited on this thread). */
object Engine {
    suspend fun evaluate(context: Context, script: String): String = runCatching {
        if (!JavaScriptSandbox.isSupported()) return "JavaScriptSandbox not supported"
        JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext).get().use { sandbox ->
            sandbox.createIsolate().use { it.evaluateJavaScriptAsync(script).get() }
        }
    }.getOrElse { "error: ${it.message}" }
}
