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
             frame: (Canvas, Float) -> Unit): Boolean {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 7_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // the music, read from the bundled file and copied as is
        val ex = music?.let { id ->
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
