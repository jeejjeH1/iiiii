package app.goallive.live.notify

import app.goallive.live.L

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.goallive.live.MainActivity
import app.goallive.live.R
import app.goallive.live.data.Api
import app.goallive.live.data.MatchItem
import app.goallive.live.data.Store
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.parseMatchList
import app.goallive.live.data.path
import app.goallive.live.data.s
import app.goallive.live.ui.dayKey
import app.goallive.live.ui.fa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Goal / line-up / kick-off / full-time alerts for followed teams and matches, without a push server:
 * the phone checks the scores itself on an alarm chain (every minute while a followed match is live,
 * rarely otherwise), and a 15-minute WorkManager job restarts the chain if the system dropped it.
 */
object LiveAlerts {
    // "_v2": channels are fixed once created, so the ones with our own sounds get new ids
    const val CH_GOALS = "goals_v2"
    const val CH_VAR = "var_v2"
    const val CH_LINEUPS = "lineups_v2"
    const val CH_KICKOFF = "kickoff_v2"
    const val CH_FULLTIME = "fulltime_v2"

    /** Each kind of alert has its own sound (res/raw, made by android/tools/gen-sounds.py). */
    private fun sound(channel: String) = when (channel) {
        CH_GOALS -> R.raw.notif_goal
        CH_VAR -> R.raw.notif_var_cancel
        CH_LINEUPS -> R.raw.notif_lineup
        CH_KICKOFF -> R.raw.notif_kickoff
        else -> R.raw.notif_fulltime
    }

    private fun soundUri(ctx: Context, channel: String) = android.net.Uri.parse("android.resource://${ctx.packageName}/${sound(channel)}")
    private const val ACTION = "app.goallive.live.ALERT_TICK"
    private val lock = Mutex()

    class Receiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try { run(ctx.applicationContext) } finally { pending.finish() }
            }
        }
    }

    class Watchdog(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val last = Store.prefs().getLong("alertsLast", 0)
            if (System.currentTimeMillis() - last > 4 * 60_000) run(applicationContext)
            return Result.success()
        }
    }

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        listOf("goals", "lineups", "match").forEach { runCatching { nm.deleteNotificationChannel(it) } }
        val attrs = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        for ((id, name, info, importance) in listOf(
            listOf(CH_GOALS, L("گل‌ها"), L("گل تیم‌ها و بازی‌های دنبال‌شده؛ با صدای غرش ورزشگاه"), NotificationManager.IMPORTANCE_HIGH),
            listOf(CH_VAR, L("گل مردود"), L("وقتی داور گلی را پس می‌گیرد"), NotificationManager.IMPORTANCE_HIGH),
            listOf(CH_LINEUPS, L("ترکیب تیم‌ها"), L("اعلام ترکیب حدود یک ساعت قبل از بازی"), NotificationManager.IMPORTANCE_DEFAULT),
            listOf(CH_KICKOFF, L("شروع بازی"), L("سوت شروع"), NotificationManager.IMPORTANCE_DEFAULT),
            listOf(CH_FULLTIME, L("پایان بازی"), L("سوت پایان و نتیجه نهایی"), NotificationManager.IMPORTANCE_DEFAULT),
        )) {
            nm.createNotificationChannel(NotificationChannel(id as String, name as String, importance as Int).apply {
                description = info as String
                setSound(soundUri(ctx, id as String), attrs)
            })
        }
    }

    /** Start (or restart) checking right away; called on app start, after boot and when follows change. */
    fun start(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "alerts-watchdog", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Watchdog>(15, TimeUnit.MINUTES).build(),
        )
        schedule(ctx, 5_000)
    }

    private fun intent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, Receiver::class.java).setAction(ACTION), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun schedule(ctx: Context, delay: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = SystemClock.elapsedRealtime() + delay
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent(ctx))
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent(ctx))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent(ctx))
        }
    }

    suspend fun run(ctx: Context) {
        val next = lock.withLock {
            Store.prefs().edit().putLong("alertsLast", System.currentTimeMillis()).apply()
            runCatching { tick(ctx) }.getOrElse { 3 * 60_000L }
        }
        if (next != null) schedule(ctx, next)
    }

    /** One check. Returns the delay until the next one, or null when nothing is followed. */
    private suspend fun tick(ctx: Context): Long? {
        if (Store.teams.isEmpty() && Store.matches.isEmpty() && Store.myTeam == null) return null
        val now = System.currentTimeMillis()
        // yesterday + today covers late-night matches that run past midnight
        val lists = listOf(now - 86_400_000L, now).map { t ->
            runCatching { parseMatchList(Api.fm("matches", Api.matchesParams(dayKey(t)), 10_000)) }.getOrDefault(emptyList())
        }
        val followed = lists.flatten().flatMap { it.matches }.filter { Store.follows(it) }.distinctBy { it.id }
            .filter { it.live || it.finished || (it.time - now in -3_600_000L..36 * 3_600_000L) }

        val state = runCatching { JSONObject(Store.prefs().getString("alertsState", "{}")!!) }.getOrDefault(JSONObject())
        for (m in followed) {
            val key = m.id.toString()
            val prev = state.optJSONObject(key)
            val total = (m.home.score ?: 0) + (m.away.score ?: 0)
            val cur = JSONObject().put("st", m.started).put("fi", m.finished).put("g", total).put("lu", prev?.optBoolean("lu") ?: false).put("t", m.time)
            if (prev != null) {
                if (Store.notifyStartEnd && m.started && !prev.optBoolean("st") && !m.finished) {
                    notify(ctx, m.id * 10 + 1, CH_KICKOFF, L("سوت شروع بازی"), "${app.goallive.live.ui.teamName(m.home.id, m.home.name)} - ${app.goallive.live.ui.teamName(m.away.id, m.away.name)}", m.id)
                }
                val before = prev.optInt("g")
                if (Store.notifyGoals && total > before) goal(ctx, m)
                if (Store.notifyGoals && total < before && m.live) {
                    notify(ctx, m.id * 10 + 2, CH_VAR, L("گل مردود شد"), "${app.goallive.live.ui.teamName(m.home.id, m.home.name)} ${fa(m.home.score)} - ${fa(m.away.score)} ${app.goallive.live.ui.teamName(m.away.id, m.away.name)}", m.id)
                }
                if (Store.notifyStartEnd && m.finished && !prev.optBoolean("fi")) {
                    notify(ctx, m.id * 10 + 3, CH_FULLTIME, L("پایان بازی"), "${app.goallive.live.ui.teamName(m.home.id, m.home.name)} ${fa(m.home.score)} - ${fa(m.away.score)} ${app.goallive.live.ui.teamName(m.away.id, m.away.name)}", m.id)
                }
            }
            // line-ups come out about an hour before kick-off
            if (Store.notifyLineups && !m.started && !cur.optBoolean("lu") && m.time - now in 0..80 * 60_000L) {
                val d = runCatching { Api.fm("matchDetails", mapOf("matchId" to m.id), 30_000) }.getOrNull()
                val lu = d?.path("content.lineup")
                val starters = lu?.o("homeTeam")?.a("starters")?.length() ?: 0
                if (starters > 0 && (lu.s("lineupType") ?: "standard") == "standard") {
                    cur.put("lu", true)
                    val hf = lu.o("homeTeam").s("formation") ?: ""
                    val af = lu.o("awayTeam").s("formation") ?: ""
                    notify(ctx, m.id * 10 + 4, CH_LINEUPS, L("ترکیب‌ها اعلام شد"), "${app.goallive.live.ui.teamName(m.home.id, m.home.name)} ($hf) - ${app.goallive.live.ui.teamName(m.away.id, m.away.name)} ($af)", m.id)
                }
            }
            state.put(key, cur)
        }
        // forget matches older than two days
        state.keys().asSequence().toList().forEach { k -> if (now - (state.optJSONObject(k)?.optLong("t") ?: 0) > 2 * 86_400_000L) state.remove(k) }
        Store.prefs().edit().putString("alertsState", state.toString()).apply()

        return when {
            followed.any { it.live } -> 60_000L
            followed.any { !it.started && it.time - now in 0..90 * 60_000L } -> 3 * 60_000L
            else -> followed.filter { !it.started && it.time > now }.minOfOrNull { it.time - now - 85 * 60_000L }
                ?.coerceIn(3 * 60_000L, 30 * 60_000L) ?: 60 * 60_000L
        }
    }

    private suspend fun goal(ctx: Context, m: MatchItem) {
        var body = ""
        runCatching {
            val d = Api.fm("matchDetails", mapOf("matchId" to m.id), 0)
            val g = d.path("content.matchFacts.events")?.a("events").objs().lastOrNull { it.s("type") == "Goal" }
            if (g != null) {
                val who = g.s("nameStr") ?: g.o("player").s("name") ?: ""
                val min = g.s("timeStr") ?: g.s("time") ?: ""
                body = "$who ${fa(min)}'" + when {
                    g.b("ownGoal") -> L(" (گل به خودی)")
                    g.s("goalDescriptionKey") == "penalty" -> L(" (پنالتی)")
                    else -> ""
                }
            }
        }
        val title = L("⚽ گل! {0} {1} - {2} {3}", app.goallive.live.ui.teamName(m.home.id, m.home.name), fa(m.home.score), fa(m.away.score), app.goallive.live.ui.teamName(m.away.id, m.away.name))
        notify(ctx, m.id * 10 + 5, CH_GOALS, title, body.ifEmpty { m.leagueName }, m.id)
    }

    fun notify(ctx: Context, id: Int, channel: String, title: String, text: String, matchId: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java).putExtra("match", matchId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setColor(0xFF2BD576.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(if (channel == CH_GOALS || channel == CH_VAR) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setSound(soundUri(ctx, channel)) // Android 7 and older (newer ones take the channel's sound)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }
}
