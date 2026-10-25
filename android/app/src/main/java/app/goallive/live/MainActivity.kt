package app.goallive.live

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import app.goallive.live.ui.Root

class MainActivity : ComponentActivity() {
    /** Match to open, set when the app is launched from a notification. */
    val openMatch = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        val light = app.goallive.live.ui.Look.light
        enableEdgeToEdge(
            statusBarStyle = if (light) SystemBarStyle.light(0, 0) else SystemBarStyle.dark(0),
            navigationBarStyle = if (light) SystemBarStyle.light(0xFFE9EEE9.toInt(), 0) else SystemBarStyle.dark(0xFF0B0F14.toInt()),
        )
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { Root(openMatch) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val id = i?.getIntExtra("match", 0) ?: 0
        if (id > 0) openMatch.intValue = id
    }

    override fun onStart() {
        super.onStart()
        app.goallive.live.data.Telemetry.sessionStart()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) app.goallive.live.data.Telemetry.sessionEnd()
    }

    override fun onPause() {
        super.onPause()
        // the user may have just picked "my team": let the home-screen widget catch up
        ScoreWidget.requestUpdate(this)
        TodayWidget.requestUpdate(this)
    }
}
