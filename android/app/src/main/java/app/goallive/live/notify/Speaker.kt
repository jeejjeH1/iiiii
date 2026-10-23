package app.goallive.live.notify

import app.goallive.live.L

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Speaks Persian lines one after another, either with the phone's own voice or, when the phone has no Persian voice
 * (or the user picked it), with the online voice from our server (/api/tts, a natural Persian newsreader voice).
 */
class Speaker(ctx: Context, private val scope: CoroutineScope) {
    private val app = ctx.applicationContext
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private var tts: TextToSpeech? = null
    private val phoneReady = CompletableDeferred<Boolean>()
    private var player: MediaPlayer? = null
    private val done = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    init {
        if (Store.voice != "online") {
            tts = TextToSpeech(app) { status ->
                val t = tts
                val r = if (status == TextToSpeech.SUCCESS && t != null) t.setLanguage(if (app.goallive.live.Lang.en) Locale.US else Locale("fa", "IR")) else TextToSpeech.LANG_NOT_SUPPORTED
                val ok = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (ok) {
                    t!!.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    t.setSpeechRate(1.08f)
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { done.remove(id)?.complete(Unit) }
                        @Deprecated("old API") override fun onError(id: String?) { done.remove(id)?.complete(Unit) }
                    })
                }
                phoneReady.complete(ok)
            }
        } else phoneReady.complete(false)
        scope.launch { for (line in queue) { runCatching { speak(line) }; pending.decrementAndGet() } }
    }

    fun say(text: String) {
        pending.incrementAndGet()
        queue.trySend(text.replace("'", "").trim())
    }

    /** Waits until everything queued has been spoken. */
    suspend fun awaitIdle() { while (pending.get() > 0) delay(200) }

    fun close() {
        queue.close()
        runCatching { player?.release() }
        tts?.stop(); tts?.shutdown()
    }

    private suspend fun speak(text: String) {
        if (text.isEmpty()) return
        val phone = withTimeoutOrNull(6000) { phoneReady.await() } ?: false
        val online = Store.voice == "online" || (Store.voice == "auto" && !phone)
        if (online) {
            val f = download(text)
            if (f != null) { Commentary.problem = null; play(f); return }
            if (!phone) { Commentary.problem = L("صدای آنلاین الان جواب نداد؛ چند لحظه بعد دوباره امتحان کنید (اگر نشد، فیلترشکن را روشن کنید)"); return }
        } else if (!phone) {
            Commentary.problem = L("صدای فارسی روی گوشی نصب نیست")
            return
        }
        val id = "c${System.nanoTime()}"
        val d = CompletableDeferred<Unit>().also { done[id] = it }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        withTimeoutOrNull(30_000) { d.await() }
    }

    /** The server's mp3 for a line; identical lines ("نیمه دوم آغاز شد.") are kept on disk and reused. */
    private suspend fun download(text: String): File? = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "tts").apply { mkdirs() }
        val f = File(dir, "${Store.voiceName}-${if (app.goallive.live.Lang.en) "en" else "fa"}-${Integer.toHexString(text.hashCode())}.mp3")
        if (f.length() > 0) return@withContext f
        // keep the cache small
        dir.listFiles()?.takeIf { it.size > 200 }?.sortedBy { it.lastModified() }?.take(100)?.forEach { it.delete() }
        // first straight from the voice service, then through our server
        // the male / female commentator in the app's language
        val (male, female) = if (app.goallive.live.Lang.en) "en-GB-RyanNeural" to "en-GB-SoniaNeural" else "fa-IR-FaridNeural" to "fa-IR-DilaraNeural"
        val voice = if (Store.voiceName == "dilara") female else male
        EdgeVoice.speak(text.take(400), voice)?.let { f.writeBytes(it); return@withContext f }
        // the other commentator rather than silence
        EdgeVoice.speak(text.take(400), if (voice == male) female else male)?.let { f.writeBytes(it); return@withContext f }
        val url = "${Api.SITE}/api/tts?v=${Store.voiceName}&t=${URLEncoder.encode(text.take(400), "UTF-8")}"
        repeat(2) {
            runCatching {
                Api.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (r.isSuccessful && r.header("Content-Type")?.startsWith("audio") == true) {
                        val bytes = r.body!!.bytes()
                        if (bytes.size > 500) { f.writeBytes(bytes); return@withContext f }
                    }
                }
            }
        }
        null
    }

    private suspend fun play(f: File) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            player = mp
            val finish = { if (cont.isActive) cont.resume(Unit); runCatching { mp.release() }; Unit }
            runCatching {
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                mp.setDataSource(f.absolutePath)
                mp.setOnCompletionListener { finish() }
                mp.setOnErrorListener { _, _, _ -> finish(); true }
                mp.setOnPreparedListener { it.start() }
                mp.prepareAsync()
            }.onFailure { finish() }
            cont.invokeOnCancellation { runCatching { mp.release() } }
        }
    }
}
