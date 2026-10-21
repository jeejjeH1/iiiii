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
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0), navigationBarStyle = SystemBarStyle.dark(0xFF0B0F14.toInt()))
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

    override fun onPause() {
        super.onPause()
        // the user may have just picked "my team": let the home-screen widget catch up
        ScoreWidget.requestUpdate(this)
    }
}
