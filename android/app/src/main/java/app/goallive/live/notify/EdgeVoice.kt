package app.goallive.live.notify

import app.goallive.live.data.Api
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Persian speech straight from Microsoft Edge's "read aloud" voices (the same service /api/tts uses),
 * so the online commentator works on the phone even when our server is not reachable.
 */
object EdgeVoice {
    private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val WSS = "wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1"
    private const val GEC_VERSION = "1-143.0.3650.96"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"

    /** Difference between the server's clock and the phone's; a wrong phone clock makes the token invalid. */
    @Volatile private var clockOffset = 0L

    private fun gec(): String {
        val ticks = (System.currentTimeMillis() + clockOffset) / 1000 + 11_644_473_600L
        val rounded = ticks - ticks % 300
        val digest = MessageDigest.getInstance("SHA-256").digest("${rounded * 10_000_000L}$TOKEN".toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun stamp() = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    /** MP3 bytes for [text] in [voice] ("fa-IR-FaridNeural" / "fa-IR-DilaraNeural"), or null. */
    suspend fun speak(text: String, voice: String): ByteArray? {
        val before = clockOffset
        once(text, voice)?.let { return it }
        // a refusal told us the phone's clock is off: the token is now right, try once more
        return if (clockOffset != before) once(text, voice) else null
    }

    private suspend fun once(text: String, voice: String): ByteArray? = withTimeoutOrNull(20_000) {
        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString().replace("-", "")
            val url = "$WSS?Ocp-Apim-Subscription-Key=$TOKEN&Sec-MS-GEC=${gec()}&Sec-MS-GEC-Version=$GEC_VERSION&ConnectionId=$id"
            val req = Request.Builder().url(url).header("User-Agent", UA).header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold").build()
            val audio = ByteArrayOutputStream()
            val delim = "Path:audio\r\n".toByteArray()
            fun done(v: ByteArray?) { if (cont.isActive) cont.resume(v) }
            val ws = Api.client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("X-Timestamp:${stamp()}\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                        """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}""")
                    val safe = text.replace("&", " ").replace("<", " ").replace(">", " ")
                    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='fa-IR'><voice name='$voice'>" +
                        "<prosody pitch='+2Hz' rate='+8%' volume='+0%'>$safe</prosody></voice></speak>"
                    val reqId = UUID.randomUUID().toString().replace("-", "")
                    webSocket.send("X-RequestId:$reqId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${stamp()}Z\r\nPath:ssml\r\n\r\n$ssml")
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) { webSocket.close(1000, null); done(audio.toByteArray().takeIf { it.size > 500 }) }
                }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val b = bytes.toByteArray()
                    val at = indexOf(b, delim)
                    if (at >= 0) audio.write(b, at + delim.size, b.size - at - delim.size)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // 403 with the server's date: our clock is off; remember the difference for the next try
                    response?.header("Date")?.let { d ->
                        runCatching { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(d)!!.time }.getOrNull()
                            ?.let { clockOffset = it - System.currentTimeMillis() }
                    }
                    done(null)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { done(audio.toByteArray().takeIf { it.size > 500 }) }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }
    }

    private fun indexOf(a: ByteArray, b: ByteArray): Int {
        outer@ for (i in 0..a.size - b.size) {
            for (j in b.indices) if (a[i + j] != b[j]) continue@outer
            return i
        }
        return -1
    }
}
