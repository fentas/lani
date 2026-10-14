package si.lanisce.lani.spike.validators

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs one script in the flavor's engine (Engine.kt) and shows its answer: what keeps the engine in a release build. */
class MainActivity : Activity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply { text = "…" }
        setContentView(view)
        scope.launch {
            view.text = withContext(Dispatchers.Default) { Engine.evaluate(this@MainActivity, "JSON.stringify({ engine: typeof globalThis, sum: 1 + 2 })") }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
