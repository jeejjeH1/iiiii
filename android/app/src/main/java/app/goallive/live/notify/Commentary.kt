package app.goallive.live.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.goallive.live.MainActivity
import app.goallive.live.R
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.d
import app.goallive.live.data.faTeamName
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.path
import app.goallive.live.data.s
import app.goallive.live.ui.MatchData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * "گزارشگر زنده": turns the live match data into spoken Persian commentary.
 * A foreground service keeps it talking with the screen off; it follows one or several matches at once.
 */
object Commentary {
    /** Matches being commentated. */
    var matches by mutableStateOf<Set<Int>>(emptySet()); internal set
    /** Only goals, penalties, red cards and the final whistle. */
    var importantOnly by mutableStateOf(false)
    /** null = fine, otherwise a problem to show (e.g. no Persian voice on the phone). */
    var problem by mutableStateOf<String?>(null); internal set

    fun toggle(ctx: Context, id: Int) {
        val now = if (id in matches) matches - id else matches + id
        matches = now
        val i = Intent(ctx, CommentaryService::class.java)
        if (now.isEmpty()) ctx.stopService(i) else ContextCompat.startForegroundService(ctx, i)
    }

    fun stopAll(ctx: Context) { matches = emptySet(); ctx.stopService(Intent(ctx, CommentaryService::class.java)) }
}

class CommentaryService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tts: TextToSpeech? = null
    private var ready = false
    private var loop: Job? = null
    private val state = HashMap<Int, Snap>()
    private val rnd = Random(System.currentTimeMillis())

    private class Snap(
        val events: Set<String>, val shots: Set<String>, val started: Boolean, val finished: Boolean, val ht: Boolean,
        val h: Int, val a: Int, var lastTalk: Long, var lastPressure: Long,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "گزارش صوتی", NotificationManager.IMPORTANCE_LOW))
        val n = notification("در حال آماده‌سازی گزارشگر…")
        ServiceCompat.startForeground(this, 77, n, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        tts = TextToSpeech(this) { status ->
            val t = tts ?: return@TextToSpeech
            val r = if (status == TextToSpeech.SUCCESS) t.setLanguage(Locale("fa", "IR")) else TextToSpeech.LANG_NOT_SUPPORTED
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                Commentary.problem = "صدای فارسی روی گوشی نصب نیست"
            } else {
                Commentary.problem = null
                t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                t.setSpeechRate(1.08f)
                ready = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { Commentary.stopAll(this); return START_NOT_STICKY }
        if (loop == null) loop = scope.launch { run() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }

    private fun say(text: String) {
        if (!ready) return
        tts?.speak(text.replace("'", ""), TextToSpeech.QUEUE_ADD, null, "c${System.nanoTime()}")
    }

    private suspend fun run() {
        // wait a little for the voice engine
        repeat(20) { if (!ready && Commentary.problem == null) delay(250) }
        while (scope.isActive) {
            val ids = Commentary.matches
            if (ids.isEmpty()) { stopSelf(); return }
            var line = ""
            for (id in ids) {
                val m = runCatching { MatchData(Api.fm("matchDetails", mapOf("matchId" to id), 5_000)) }.getOrNull() ?: continue
                val prefix = if (ids.size > 1) "از بازی ${team(m.homeId, m.homeName)} و ${team(m.awayId, m.awayName)}: " else ""
                lines(m).forEach { say(prefix + it) }
                line = "${m.homeName} ${m.home.i("score") ?: 0} - ${m.away.i("score") ?: 0} ${m.awayName}" + if (m.live) " · ${m.minute}" else ""
                if (m.finished && ids.size == 1) { delay(15_000); Commentary.stopAll(this); return }
            }
            if (line.isNotEmpty()) getSystemService(NotificationManager::class.java).notify(77, notification(line))
            delay(15_000)
        }
    }

    private fun team(id: Int, name: String) = faTeamName(id, name) ?: name
    private fun name(e: JSONObject) = e.o("player").s("lastName") ?: e.s("nameStr")?.substringAfterLast(' ') ?: e.o("player").s("name") ?: ""
    private fun pick(vararg s: String) = s[rnd.nextInt(s.size)]

    /** What to say about one match since the last check. */
    private fun lines(m: MatchData): List<String> {
        val out = ArrayList<String>()
        val hn = team(m.homeId, m.homeName); val an = team(m.awayId, m.awayName)
        val h = m.home.i("score") ?: 0; val a = m.away.i("score") ?: 0
        val evKey = { e: JSONObject -> "${e.s("type")}-${e.s("time")}-${e.s("overloadTime")}-${e.o("player").i("id") ?: e.s("nameStr")}" }
        val evs = m.events.filter { it.s("type") in setOf("Goal", "Card", "Substitution") }
        val now = System.currentTimeMillis()
        val ht = m.minute == "HT"
        val prev = state[m.matchId]
        if (prev == null) {
            out += when {
                m.finished -> "بازی $hn و $an تمام شده. نتیجه نهایی: $hn $h، $an $a."
                !m.started -> "گزارشگر گل‌لایو آماده است. بازی $hn و $an هنوز شروع نشده؛ با سوت شروع، گزارش آغاز می‌شود."
                else -> "سلام! با گزارش زنده بازی $hn و $an در خدمتتان هستیم. دقیقه ${m.minute.replace("'", "")}، نتیجه: $hn $h، $an $a."
            }
            state[m.matchId] = Snap(evs.map(evKey).toSet(), m.shots.mapNotNull { it.s("id") }.toSet(), m.started, m.finished, ht, h, a, now, now)
            return out
        }
        if (m.started && !prev.started) out += pick("سوت آغاز بازی زده شد!", "بازی شروع شد! $hn و $an به میدان آمده‌اند.")
        // new events in order
        for (e in evs.filter { evKey(it) !in prev.events }) {
            val tm = if (e.b("isHome")) hn else an
            val min = (e.s("timeStr") ?: e.s("time") ?: "").replace("'", "")
            when (e.s("type")) {
                "Goal" -> {
                    val sc = e.a("newScore")
                    val score = if (sc != null && sc.length() == 2) "حالا نتیجه $hn ${sc.optInt(0)}، $an ${sc.optInt(1)}" else ""
                    out += when {
                        e.b("ownGoal") -> "گل به خودی! ${name(e)} توپ را به دروازه خودشان زد. دقیقه $min. $score"
                        e.s("goalDescriptionKey") == "penalty" || (e.s("suffix") ?: "").contains("pen", true) -> "پنالتی… و گل! ${name(e)} برای $tm گل زد. دقیقه $min. $score"
                        else -> pick("گللللل! ${name(e)} برای $tm! دقیقه $min.", "گل! چه گلی! ${name(e)} دروازه را باز کرد، دقیقه $min.", "توپ در دروازه است! ${name(e)} برای $tm گل زد!") +
                            (e.s("assistInput")?.let { " پاس گل از ${it.substringAfterLast(' ')}." } ?: "") + " $score"
                    }
                }
                "Card" -> when (e.s("card")) {
                    "Red" -> out += "کارت قرمز! ${name(e)} از $tm اخراج شد. $tm ده نفره شد!"
                    "YellowRed" -> out += "کارت زرد دوم و اخراج! ${name(e)} از زمین بیرون می‌رود."
                    else -> if (!Commentary.importantOnly) out += "کارت زرد برای ${name(e)} از $tm."
                }
                "Substitution" -> if (!Commentary.importantOnly) {
                    val sw = e.a("swap").objs()
                    out += "تعویض در $tm: ${sw.getOrNull(0).s("name")?.substringAfterLast(' ') ?: ""} به جای ${sw.getOrNull(1).s("name")?.substringAfterLast(' ') ?: ""}."
                }
            }
        }
        // a goal taken back
        if (h + a < prev.h + prev.a && m.live) out += "گل مردود شد! داور بعد از بررسی ویدیویی گل را قبول نکرد. نتیجه $hn $h، $an $a."
        // big chances and the woodwork
        if (!Commentary.importantOnly) for (s in m.shots.filter { (it.s("id") ?: "") !in prev.shots && it.s("eventType") != "Goal" }.takeLast(2)) {
            val who = s.s("lastName") ?: s.s("playerName")?.substringAfterLast(' ') ?: ""
            val xg = s.d("expectedGoals") ?: 0.0
            out += when {
                s.s("eventType") == "Post" -> "وای! شوت $who به تیرک خورد!"
                s.b("isOnTarget") && !s.b("isBlocked") -> if (xg > 0.3) "موقعیت عالی برای $who… دروازه‌بان مهار کرد!" else "شوت $who در چارچوب، اما دروازه‌بان آماده بود."
                xg > 0.3 -> "موقعیت خیلی خوبی بود! $who از دست داد."
                else -> continue
            }
        }
        if (ht && !prev.ht) out += "پایان نیمه اول. نتیجه: $hn $h، $an $a." + stat(m, hn, an)
        if (!ht && prev.ht && m.live) out += "نیمه دوم آغاز شد."
        if (m.finished && !prev.finished) out += "سوت پایان! نتیجه نهایی: $hn $h، $an $a. " + when {
            h > a -> "$hn برنده این بازی شد."
            a > h -> "$an برنده این بازی شد."
            else -> "بازی مساوی تمام شد."
        }
        // pressure, every few minutes when it is clear who is on top
        if (m.live && !ht && !Commentary.importantOnly && now - prev.lastPressure > 4 * 60_000) {
            val mom = m.content.path("momentum.main").a("data").objs().takeLast(4)
            val p = if (mom.isEmpty()) 0.0 else mom.sumOf { it.d("value") ?: 0.0 } / mom.size
            if (abs(p) > 45) { out += if (p > 0) "$hn فشار آورده و پشت سر هم حمله می‌کند." else "$an این دقایق فشار سنگینی آورده."; prev.lastPressure = now }
        }
        // fill long silences with a number
        if (m.live && !ht && out.isEmpty() && !Commentary.importantOnly && now - prev.lastTalk > 5 * 60_000) out += "دقیقه ${m.minute.replace("'", "")}، نتیجه $hn $h، $an $a." + stat(m, hn, an)
        if (out.isNotEmpty()) prev.lastTalk = now
        state[m.matchId] = Snap(evs.map(evKey).toSet(), m.shots.mapNotNull { it.s("id") }.toSet(), m.started, m.finished, ht, h, a, prev.lastTalk, prev.lastPressure)
        return out
    }

    private fun stat(m: MatchData, hn: String, an: String): String {
        val top = m.content.path("stats.Periods.All").a("stats").objs().flatMap { it.a("stats").objs() }
        val poss = top.firstOrNull { it.s("key") == "BallPossesion" }?.a("stats")
        val shots = top.firstOrNull { it.s("key") == "total_shots" }?.a("stats")
        return when {
            poss != null && rnd.nextBoolean() -> " مالکیت توپ: $hn ${poss.optString(0)} درصد، $an ${poss.optString(1)} درصد."
            shots != null -> " تعداد شوت‌ها: $hn ${shots.optString(0)}، $an ${shots.optString(1)}."
            else -> ""
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 78, Intent(this, MainActivity::class.java).putExtra("match", Commentary.matches.firstOrNull() ?: 0)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 79, Intent(this, CommentaryService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_ball).setColor(0xFF2BD576.toInt())
            .setContentTitle("🎙️ گزارش زنده گل‌لایو").setContentText(text)
            .setOngoing(true).setSilent(true).setContentIntent(open)
            .addAction(0, "توقف گزارش", stop)
            .build()
    }

    companion object {
        const val CH = "commentary"
        const val STOP = "app.goallive.live.COMMENTARY_STOP"
    }
}
