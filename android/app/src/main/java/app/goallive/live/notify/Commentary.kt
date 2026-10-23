package app.goallive.live.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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

    /** "امتحان صدا" in settings: one line with the chosen voice, outside the service. */
    fun test(ctx: Context) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sp = Speaker(ctx, scope)
        sp.say(L("گل! چه گلی! این صدای گزارشگر گل‌لایو است."))
        scope.launch { sp.awaitIdle(); delay(500); sp.close(); scope.cancel() }
    }

    fun stopAll(ctx: Context) { matches = emptySet(); ctx.stopService(Intent(ctx, CommentaryService::class.java)) }
}

class CommentaryService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var speaker: Speaker
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
            .createNotificationChannel(NotificationChannel(CH, L("گزارش صوتی"), NotificationManager.IMPORTANCE_LOW))
        val n = notification(L("در حال آماده‌سازی گزارشگر…"))
        ServiceCompat.startForeground(this, 77, n, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        speaker = Speaker(this, scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { Commentary.stopAll(this); return START_NOT_STICKY }
        if (loop == null) loop = scope.launch { run() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        speaker.close()
        super.onDestroy()
    }

    private fun say(text: String) = speaker.say(text)

    private suspend fun run() {
        while (scope.isActive) {
            val ids = Commentary.matches
            if (ids.isEmpty()) { stopSelf(); return }
            var line = ""
            for (id in ids) {
                val m = runCatching { MatchData(Api.fm("matchDetails", mapOf("matchId" to id), 5_000)) }.getOrNull() ?: continue
                val prefix = if (ids.size > 1) L("از بازی {0} و {1}: ", team(m.homeId, m.homeName), team(m.awayId, m.awayName)) else ""
                lines(m).forEach { say(prefix + it) }
                line = "${m.homeName} ${m.home.i("score") ?: 0} - ${m.away.i("score") ?: 0} ${m.awayName}" + if (m.live) " · ${m.minute}" else ""
                if (m.finished && ids.size == 1) { delay(15_000); Commentary.stopAll(this); return }
            }
            if (line.isNotEmpty()) getSystemService(NotificationManager::class.java).notify(77, notification(line))
            delay(15_000)
        }
    }

    private fun team(id: Int, name: String) = if (app.goallive.live.Lang.en) name else faTeamName(id, name) ?: app.goallive.live.data.FaNames.of(name)
    private fun name(e: JSONObject) = (e.o("player").s("lastName") ?: e.s("nameStr")?.substringAfterLast(' ') ?: e.o("player").s("name") ?: "")
        .let { if (app.goallive.live.Lang.en) it else app.goallive.live.data.FaNames.of(it) }
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
                m.finished -> L("بازی {0} و {1} تمام شده. نتیجه نهایی: {2} {3}، {4} {5}.", hn, an, hn, h, an, a)
                !m.started -> L("گزارشگر گل‌لایو آماده است. بازی {0} و {1} هنوز شروع نشده؛ با سوت شروع، گزارش آغاز می‌شود.", hn, an)
                else -> L("سلام! با گزارش زنده بازی {0} و {1} در خدمتتان هستیم. دقیقه {2}، نتیجه: {3} {4}، {5} {6}.", hn, an, m.minute.replace("'", ""), hn, h, an, a)
            }
            state[m.matchId] = Snap(evs.map(evKey).toSet(), m.shots.mapNotNull { it.s("id") }.toSet(), m.started, m.finished, ht, h, a, now, now)
            return out
        }
        if (m.started && !prev.started) out += pick(L("سوت آغاز بازی زده شد!"), L("بازی شروع شد! {0} و {1} به میدان آمده‌اند.", hn, an))
        // new events in order
        for (e in evs.filter { evKey(it) !in prev.events }) {
            val tm = if (e.b("isHome")) hn else an
            val min = (e.s("timeStr") ?: e.s("time") ?: "").replace("'", "")
            when (e.s("type")) {
                "Goal" -> {
                    val sc = e.a("newScore")
                    val score = if (sc != null && sc.length() == 2) L("حالا نتیجه {0} {1}، {2} {3}", hn, sc.optInt(0), an, sc.optInt(1)) else ""
                    out += when {
                        e.b("ownGoal") -> L("گل به خودی! {0} توپ را به دروازه خودشان زد. دقیقه {1}. {2}", name(e), min, score)
                        e.s("goalDescriptionKey") == "penalty" || (e.s("suffix") ?: "").contains("pen", true) -> L("پنالتی… و گل! {0} برای {1} گل زد. دقیقه {2}. {3}", name(e), tm, min, score)
                        else -> pick(L("گللللل! {0} برای {1}! دقیقه {2}.", name(e), tm, min), L("گل! چه گلی! {0} دروازه را باز کرد، دقیقه {1}.", name(e), min), L("توپ در دروازه است! {0} برای {1} گل زد!", name(e), tm)) +
                            (e.s("assistInput")?.let { L(" پاس گل از {0}.", it.substringAfterLast(' ')) } ?: "") + " $score"
                    }
                }
                "Card" -> when (e.s("card")) {
                    "Red" -> out += L("کارت قرمز! {0} از {1} اخراج شد. {2} ده نفره شد!", name(e), tm, tm)
                    "YellowRed" -> out += L("کارت زرد دوم و اخراج! {0} از زمین بیرون می‌رود.", name(e))
                    else -> if (!Commentary.importantOnly) out += L("کارت زرد برای {0} از {1}.", name(e), tm)
                }
                "Substitution" -> if (!Commentary.importantOnly) {
                    val sw = e.a("swap").objs()
                    out += L("تعویض در {0}: {1} به جای {2}.", tm, sw.getOrNull(0).s("name")?.substringAfterLast(' ') ?: "", sw.getOrNull(1).s("name")?.substringAfterLast(' ') ?: "")
                }
            }
        }
        // a goal taken back
        if (h + a < prev.h + prev.a && m.live) out += L("گل مردود شد! داور بعد از بررسی ویدیویی گل را قبول نکرد. نتیجه {0} {1}، {2} {3}.", hn, h, an, a)
        // big chances and the woodwork
        if (!Commentary.importantOnly) for (s in m.shots.filter { (it.s("id") ?: "") !in prev.shots && it.s("eventType") != "Goal" }.takeLast(2)) {
            val who = s.s("lastName") ?: s.s("playerName")?.substringAfterLast(' ') ?: ""
            val xg = s.d("expectedGoals") ?: 0.0
            out += when {
                s.s("eventType") == "Post" -> L("وای! شوت {0} به تیرک خورد!", who)
                s.b("isOnTarget") && !s.b("isBlocked") -> if (xg > 0.3) L("موقعیت عالی برای {0}… دروازه‌بان مهار کرد!", who) else L("شوت {0} در چارچوب، اما دروازه‌بان آماده بود.", who)
                xg > 0.3 -> L("موقعیت خیلی خوبی بود! {0} از دست داد.", who)
                else -> continue
            }
        }
        if (ht && !prev.ht) out += L("پایان نیمه اول. نتیجه: {0} {1}، {2} {3}.", hn, h, an, a) + stat(m, hn, an)
        if (!ht && prev.ht && m.live) out += L("نیمه دوم آغاز شد.")
        if (m.finished && !prev.finished) out += L("سوت پایان! نتیجه نهایی: {0} {1}، {2} {3}. ", hn, h, an, a) + when {
            h > a -> L("{0} برنده این بازی شد.", hn)
            a > h -> L("{0} برنده این بازی شد.", an)
            else -> L("بازی مساوی تمام شد.")
        }
        // pressure, every few minutes when it is clear who is on top
        if (m.live && !ht && !Commentary.importantOnly && now - prev.lastPressure > 4 * 60_000) {
            val mom = m.content.path("momentum.main").a("data").objs().takeLast(4)
            val p = if (mom.isEmpty()) 0.0 else mom.sumOf { it.d("value") ?: 0.0 } / mom.size
            if (abs(p) > 45) { out += if (p > 0) L("{0} فشار آورده و پشت سر هم حمله می‌کند.", hn) else L("{0} این دقایق فشار سنگینی آورده.", an); prev.lastPressure = now }
        }
        // fill long silences with a number
        if (m.live && !ht && out.isEmpty() && !Commentary.importantOnly && now - prev.lastTalk > 5 * 60_000) out += L("دقیقه {0}، نتیجه {1} {2}، {3} {4}.", m.minute.replace("'", ""), hn, h, an, a) + stat(m, hn, an)
        if (out.isNotEmpty()) prev.lastTalk = now
        state[m.matchId] = Snap(evs.map(evKey).toSet(), m.shots.mapNotNull { it.s("id") }.toSet(), m.started, m.finished, ht, h, a, prev.lastTalk, prev.lastPressure)
        return out
    }

    private fun stat(m: MatchData, hn: String, an: String): String {
        val top = m.content.path("stats.Periods.All").a("stats").objs().flatMap { it.a("stats").objs() }
        val poss = top.firstOrNull { it.s("key") == "BallPossesion" }?.a("stats")
        val shots = top.firstOrNull { it.s("key") == "total_shots" }?.a("stats")
        return when {
            poss != null && rnd.nextBoolean() -> L(" مالکیت توپ: {0} {1} درصد، {2} {3} درصد.", hn, poss.optString(0), an, poss.optString(1))
            shots != null -> L(" تعداد شوت‌ها: {0} {1}، {2} {3}.", hn, shots.optString(0), an, shots.optString(1))
            else -> ""
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 78, Intent(this, MainActivity::class.java).putExtra("match", Commentary.matches.firstOrNull() ?: 0)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 79, Intent(this, CommentaryService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_ball).setColor(0xFF2BD576.toInt())
            .setContentTitle(L("🎙️ گزارش زنده گل‌لایو")).setContentText(text)
            .setOngoing(true).setSilent(true).setContentIntent(open)
            .addAction(0, L("توقف گزارش"), stop)
            .build()
    }

    companion object {
        const val CH = "commentary"
        const val STOP = "app.goallive.live.COMMENTARY_STOP"
    }
}
