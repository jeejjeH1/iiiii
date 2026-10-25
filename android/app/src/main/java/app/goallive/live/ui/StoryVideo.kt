package app.goallive.live.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns a story's frames into an MP4 on the phone itself: each frame is drawn on a bitmap, put on the video encoder's surface
 * through OpenGL with its exact time, and a music track from res/raw is laid under it. No server, no internet.
 */
object StoryVideo {
    const val FPS = 30

    /**
     * Draws [seconds] of frames ([frame] gets the canvas, already scaled from 1080 wide, and the time in seconds) into [out].
     * [progress] gets 0..1; returning false from [keepGoing] stops early (the file is then not finished).
     */
    fun make(ctx: Context, out: File, w: Int, h: Int, seconds: Float, music: Int?, progress: (Float) -> Unit, keepGoing: () -> Boolean,
             bitrate: Int = 7_000_000, audio: StoryAudio.Encoded? = null, frame: (Canvas, Float) -> Unit): Boolean {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // the music, read from the bundled file and copied as is
        val ex = if (audio != null) null else music?.let { id ->
            runCatching {
                MediaExtractor().apply {
                    ctx.resources.openRawResourceFd(id).use { fd -> setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
                    selectTrack(0)
                }
            }.getOrNull()
        }
        var videoTrack = -1; var audioTrack = -1; var started = false
        val info = MediaCodec.BufferInfo()
        fun drain(end: Boolean) {
            var waits = 0
            while (true) {
                val i = enc.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end || ++waits > 500) return
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrack = mux.addTrack(enc.outputFormat)
                        ex?.let { audioTrack = mux.addTrack(it.getTrackFormat(0)) }
                        audio?.let { audioTrack = mux.addTrack(it.format) }
                        mux.start(); started = true
                    }
                    i >= 0 -> {
                        val buf = enc.getOutputBuffer(i)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            mux.writeSampleData(videoTrack, buf, info)
                        }
                        enc.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
        val gl = Gl(surface, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val frames = (seconds * FPS).toInt()
        var ok = true
        try {
            for (k in 0 until frames) {
                if (!keepGoing()) { ok = false; break }
                bmp.eraseColor(0xFF000000.toInt())
                c.save(); c.scale(w / 1080f, w / 1080f)
                frame(c, k.toFloat() / FPS)
                c.restore()
                gl.draw(bmp, k * 1_000_000_000L / FPS)
                drain(false)
                progress((k + 1f) / frames)
            }
            enc.signalEndOfInputStream()
            drain(true)
            if (ok && started && audio != null && audioTrack >= 0) {
                val ai = MediaCodec.BufferInfo()
                for (smp in audio.samples) {
                    ai.set(0, smp.data.size, smp.timeUs, smp.flags)
                    mux.writeSampleData(audioTrack, ByteBuffer.wrap(smp.data), ai)
                }
            }
            if (ok && started && ex != null && audioTrack >= 0) {
                val buf = ByteBuffer.allocate(256 * 1024)
                val ai = MediaCodec.BufferInfo()
                val endUs = frames * 1_000_000L / FPS
                while (true) {
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0 || ex.sampleTime > endUs) break
                    ai.set(0, n, ex.sampleTime, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    mux.writeSampleData(audioTrack, buf, ai)
                    ex.advance()
                }
            }
        } finally {
            gl.release()
            runCatching { enc.stop() }; enc.release(); surface.release()
            runCatching { if (started) mux.stop() }; runCatching { mux.release() }
            ex?.release()
            bmp.recycle()
        }
        return ok && started
    }

    /** A GL context on the encoder's surface that shows one bitmap full size per frame. */
    private class Gl(surface: android.view.Surface, val w: Int, val h: Int) {
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ctx: android.opengl.EGLContext
        val surf: android.opengl.EGLSurface
        var tex = 0; var prog = 0; var first = true
        val quad: java.nio.FloatBuffer

        init {
            val v = IntArray(2); EGL14.eglInitialize(dpy, v, 0, v, 1)
            val attrs = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, 0x3142 /* EGL_RECORDABLE_ANDROID */, 1, EGL14.EGL_NONE)
            val configs = arrayOfNulls<EGLConfig>(1); val num = IntArray(1)
            EGL14.eglChooseConfig(dpy, attrs, 0, configs, 0, 1, num, 0)
            check(num[0] > 0) { "no EGL config" }
            ctx = EGL14.eglCreateContext(dpy, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surf = EGL14.eglCreateWindowSurface(dpy, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            prog = program(
                "attribute vec4 p; attribute vec2 t; varying vec2 v; void main(){ gl_Position = p; v = t; }",
                "precision mediump float; varying vec2 v; uniform sampler2D s; void main(){ gl_FragColor = texture2D(s, v); }")
            // x, y, u, v: the bitmap's first row is the top of the picture
            quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                .put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)).apply { position(0) }
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        private fun program(vs: String, fs: String): Int {
            fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
            return GLES20.glCreateProgram().also { p ->
                GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
                GLES20.glLinkProgram(p)
            }
        }

        fun draw(b: Bitmap, nanos: Long) {
            GLES20.glViewport(0, 0, w, h)
            GLES20.glUseProgram(prog)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            if (first) { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0); first = false } else GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, b)
            val pa = GLES20.glGetAttribLocation(prog, "p"); val ta = GLES20.glGetAttribLocation(prog, "t")
            quad.position(0); GLES20.glVertexAttribPointer(pa, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(pa)
            quad.position(2); GLES20.glVertexAttribPointer(ta, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(ta)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "s"), 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            EGLExt.eglPresentationTimeANDROID(dpy, surf, nanos)
            EGL14.eglSwapBuffers(dpy, surf)
        }

        fun release() {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surf); EGL14.eglDestroyContext(dpy, ctx)
            EGL14.eglReleaseThread(); EGL14.eglTerminate(dpy)
        }
    }
}

/**
 * The sound of a longer video: a bundled music track (looped if short, faded out at the end) with sounds mixed in at given
 * moments — whistles, the crowd, whooshes — all made here, then encoded to AAC for the muxer.
 */
object StoryAudio {
    const val RATE = 44100

    class Sample(val data: ByteArray, val timeUs: Long, val flags: Int)
    class Encoded(val format: MediaFormat, val samples: List<Sample>)

    enum class Fx { WHISTLE, WHISTLE_HALF, WHISTLE_END, ROAR, WHOOSH, BOOM, CHEER }

    /** [seconds] of sound: [music] (a raw resource, or null for none) at [musicGain], [fx] at their times, a stadium murmur under it all when [crowd]. */
    fun make(ctx: Context, seconds: Float, music: Int?, musicGain: Float, crowd: Boolean, fx: List<Pair<Float, Fx>>): Encoded? = runCatching {
        val n = (seconds * RATE).toInt()
        val l = FloatArray(n); val r = FloatArray(n)
        music?.let { decode(ctx, it) }?.takeIf { it.isNotEmpty() }?.let { pcm ->
            val frames = pcm.size / 2
            for (i in 0 until n) { val j = i % frames; l[i] = pcm[2 * j] / 32768f * musicGain; r[i] = pcm[2 * j + 1] / 32768f * musicGain }
        }
        val rnd = java.util.Random(5)
        // under everything: a low murmur that breathes
        if (crowd) {
            var a = 0f; var b = 0f
            for (i in 0 until n) {
                val w = rnd.nextFloat() * 2 - 1
                a += 0.03f * (w - a); b += 0.004f * (a - b)
                val swell = 0.75f + 0.25f * kotlin.math.sin(i / RATE.toFloat() * 0.7f)
                val v = (a - b) * 0.55f * swell
                l[i] += v; r[i] += v * 0.92f
            }
        }
        // music ducks a little under each roar
        for ((at, k) in fx) if (k == Fx.ROAR || k == Fx.CHEER) duck(l, r, at, 3.2f)
        for ((at, k) in fx) {
            val sig = when (k) {
                Fx.WHISTLE -> whistle(0.55f, 1)
                Fx.WHISTLE_HALF -> whistle(0.4f, 2)
                Fx.WHISTLE_END -> whistle(0.45f, 3)
                Fx.ROAR -> roar(rnd, 4.2f, 1f)
                Fx.CHEER -> roar(rnd, 2.6f, 0.6f)
                Fx.WHOOSH -> whoosh(rnd, 0.45f)
                Fx.BOOM -> boom(1.4f)
            }
            val i0 = (at * RATE).toInt()
            for (j in sig.indices) { val i = i0 + j; if (i in 0 until n) { l[i] += sig[j]; r[i] += sig[j] } }
        }
        // the last two seconds fade out
        val fo = minOf(n, 2 * RATE)
        for (j in 0 until fo) { val g = j / fo.toFloat(); val i = n - 1 - j; l[i] *= g; r[i] *= g }
        val pcm = ShortArray(n * 2)
        for (i in 0 until n) {
            pcm[2 * i] = (kotlin.math.tanh(l[i] * 1.1f) * 32000).toInt().toShort()
            pcm[2 * i + 1] = (kotlin.math.tanh(r[i] * 1.1f) * 32000).toInt().toShort()
        }
        encode(pcm)
    }.getOrNull()

    private fun duck(l: FloatArray, r: FloatArray, at: Float, dur: Float) {
        val i0 = (at * RATE).toInt(); val m = (dur * RATE).toInt()
        for (j in 0 until m) {
            val i = i0 + j; if (i !in l.indices) continue
            val p = j / m.toFloat()
            val g = 1f - 0.45f * (if (p < 0.1f) p / 0.1f else 1f - (p - 0.1f) / 0.9f)
            l[i] *= g; r[i] *= g
        }
    }

    /** A referee's pea whistle: two close tones with a fast trill, [blows] short blasts. */
    private fun whistle(len: Float, blows: Int): FloatArray {
        val one = (len * RATE).toInt(); val gap = (0.12f * RATE).toInt()
        val out = FloatArray(blows * (one + gap))
        for (b in 0 until blows) {
            val l2 = if (b == blows - 1) one * 2 else one
            for (j in 0 until l2) {
                val i = b * (one + gap) + j; if (i >= out.size) break
                val t = j / RATE.toFloat()
                val trill = 0.6f + 0.4f * kotlin.math.sin(2 * PI_F * 34f * t)
                val e = minOf(1f, t / 0.02f) * minOf(1f, (l2 - j) / (0.05f * RATE))
                out[i] = (kotlin.math.sin(2 * PI_F * 2900f * t) + 0.6f * kotlin.math.sin(2 * PI_F * 3050f * t)) * trill * e * 0.13f
            }
        }
        return out
    }

    /** The crowd going up: noise in the voice band, rising fast and fading slowly. */
    private fun roar(rnd: java.util.Random, len: Float, gain: Float): FloatArray {
        val m = (len * RATE).toInt(); val out = FloatArray(m)
        var a = 0f; var b = 0f; var c = 0f
        for (j in 0 until m) {
            val t = j / RATE.toFloat()
            val w = rnd.nextFloat() * 2 - 1
            a += 0.18f * (w - a); b += 0.02f * (a - b); c += 0.5f * (a - b - c)
            val e = (if (t < 0.35f) t / 0.35f else kotlin.math.exp(-(t - 0.35f) / (len * 0.45f))) * (0.85f + 0.15f * kotlin.math.sin(2 * PI_F * 2.3f * t))
            out[j] = c * e * 1.6f * gain
        }
        return out
    }

    /** Air past the camera between parts. */
    private fun whoosh(rnd: java.util.Random, len: Float): FloatArray {
        val m = (len * RATE).toInt(); val out = FloatArray(m)
        var a = 0f
        for (j in 0 until m) {
            val p = j / m.toFloat()
            val k = 0.02f + 0.3f * p
            a += k * ((rnd.nextFloat() * 2 - 1) - a)
            out[j] = a * kotlin.math.sin(PI_F * p).let { it * it } * 0.35f
        }
        return out
    }

    /** A low hit for the final score. */
    private fun boom(len: Float): FloatArray {
        val m = (len * RATE).toInt(); val out = FloatArray(m)
        var ph = 0f
        for (j in 0 until m) {
            val t = j / RATE.toFloat()
            ph += 2 * PI_F * (36f + 70f * kotlin.math.exp(-t * 9f)) / RATE
            out[j] = kotlin.math.sin(ph) * kotlin.math.exp(-t * 3f) * 0.7f
        }
        return out
    }

    private const val PI_F = Math.PI.toFloat()

    /** The track as 16-bit stereo samples at 44.1 kHz. */
    private fun decode(ctx: Context, id: Int): ShortArray {
        val ex = MediaExtractor()
        ctx.resources.openRawResourceFd(id).use { fd -> ex.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        ex.selectTrack(0)
        val fmt = ex.getTrackFormat(0)
        val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(fmt, null, null, 0); dec.start()
        val out = java.io.ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inDone = false; var outDone = false; var idle = 0
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        while (!outDone && idle < 2000) {
            if (!inDone) {
                val ii = dec.dequeueInputBuffer(5_000)
                if (ii >= 0) {
                    val buf = dec.getInputBuffer(ii)!!
                    val sz = ex.readSampleData(buf, 0)
                    if (sz < 0) { dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                    else { dec.queueInputBuffer(ii, 0, sz, ex.sampleTime, 0); ex.advance() }
                }
            }
            val oi = dec.dequeueOutputBuffer(info, 5_000)
            if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) ch = runCatching { dec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(ch)
            else if (oi >= 0) {
                val buf = dec.getOutputBuffer(oi)!!
                val bytes = ByteArray(info.size); buf.position(info.offset); buf.get(bytes)
                out.write(bytes)
                dec.releaseOutputBuffer(oi, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                idle = 0
            } else idle++
        }
        dec.stop(); dec.release(); ex.release()
        val b = out.toByteArray()
        val sh = ShortArray(b.size / 2)
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(sh)
        // a mono track becomes stereo
        return if (ch == 1) ShortArray(sh.size * 2) { sh[it / 2] } else sh
    }

    /** 16-bit stereo PCM to AAC samples ready for the muxer. */
    private fun encode(pcm: ShortArray): Encoded {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); enc.start()
        val samples = ArrayList<Sample>()
        var outFmt: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var pos = 0; var inDone = false; var outDone = false; var idle = 0
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { asShortBuffer().put(pcm) }.array()
        while (!outDone && idle < 2000) {
            if (!inDone) {
                val ii = enc.dequeueInputBuffer(5_000)
                if (ii >= 0) {
                    val buf = enc.getInputBuffer(ii)!!
                    buf.clear()
                    val take = minOf(buf.remaining(), 4096 * 4, bytes.size - pos)
                    val timeUs = (pos / 4).toLong() * 1_000_000L / RATE
                    if (take <= 0) { enc.queueInputBuffer(ii, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                    else { buf.put(bytes, pos, take); enc.queueInputBuffer(ii, 0, take, timeUs, 0); pos += take }
                }
            }
            val oi = enc.dequeueOutputBuffer(info, 5_000)
            when {
                oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFmt = enc.outputFormat
                oi >= 0 -> {
                    val buf = enc.getOutputBuffer(oi)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        val d = ByteArray(info.size); buf.position(info.offset); buf.get(d)
                        samples += Sample(d, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    }
                    enc.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    idle = 0
                }
                else -> idle++
            }
        }
        enc.stop(); enc.release()
        return Encoded(outFmt ?: error("no audio format"), samples)
    }
}
