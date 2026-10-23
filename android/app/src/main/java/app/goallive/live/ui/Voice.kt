package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goallive.live.data.Store
import app.goallive.live.notify.Commentary

/** "🎙️ گزارش صوتی" on the match hero: start/stop the spoken commentary for this match. */
@Composable
fun VoiceButton(m: MatchData) {
    if (m.finished || m.cancelled) return
    val ctx = LocalContext.current
    val on = m.matchId in Commentary.matches
    var guide by remember { mutableStateOf(false) }
    if (guide) HowToSheet("🎙️", L("گزارش صوتی زنده"), VOICE_STEPS, L("شروع گزارش"),
        onClose = { Store.markTip("voice"); guide = false }, onGo = { Store.markTip("voice"); guide = false; Commentary.toggle(ctx, m.matchId) })
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
        Row(
            Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(if (on) Color.White else Color.Black.copy(alpha = 0.35f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)).clickable { if (!on && !Store.tipSeen("voice")) guide = true else Commentary.toggle(ctx, m.matchId) }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (on) LiveDot(C.live)
            Text("🎙️", fontSize = 16.sp)
            T(if (on) L("در حال گزارش · توقف") else L("گزارش صوتی زنده"), 13, FontWeight.Black, if (on) Color(0xFF111111) else Color.White)
        }
        if (on) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((imp, label) in listOf(false to L("همه اتفاق‌ها"), true to L("فقط لحظه‌های مهم"))) {
                    val sel = Commentary.importantOnly == imp
                    T(label, 11, FontWeight.ExtraBold, if (sel) Color(0xFF111111) else Color.White,
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.3f))
                            .clickable { Commentary.importantOnly = imp }.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            if (Commentary.matches.size > 1) T(L("{0} بازی هم‌زمان گزارش می‌شود", Commentary.matches.size), 11, color = Color.White.copy(alpha = 0.85f))
            Commentary.problem?.let { p ->
                T(p, 12, FontWeight.Bold, C.gold)
                // no Persian voice on the phone: switch to the online voice with one tap
                if (Store.voice == "phone") T(L("استفاده از صدای آنلاین"), 12, FontWeight.Black, Color(0xFF111111),
                    Modifier.clip(RoundedCornerShape(50)).background(C.gold).clickable { Store.setVoice(mode = "online"); Commentary.problem = null }
                        .padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
    }
}
