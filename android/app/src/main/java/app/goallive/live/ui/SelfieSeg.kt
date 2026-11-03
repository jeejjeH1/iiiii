package app.goallive.live.ui

import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/*
 * Finding the person in a photo, on the phone, with no library: MediaPipe's selfie segmenter (Apache 2.0, 256×256, about a
 * hundred small layers) converted to a plain file (assets/seg.bin: its weights and its layers in order) and run here with
 * plain loops. A few hundred milliseconds for one photo, and a quarter of a megabyte in the app instead of tens.
 *
 * Tensors are height × width × channels, row by row. The layers: convolutions (any size, stride, SAME or VALID, an optional
 * ReLU), depthwise convolutions, add, multiply (by a per-channel vector or elementwise), hard-swish, ReLU, sigmoid, global
 * average pooling, 2× bilinear upsampling (half-pixel centres), and the last 2×2 stride-2 transposed convolution.
 */
internal object SelfieSeg {
    private class Net(val input: Int, val output: Int, val consts: Map<Int, Pair<IntArray, FloatArray>>, val ops: List<Op>)
    private class Op(val kind: Int, val ins: IntArray, val out: Int, val stride: Int = 1, val same: Boolean = true, val relu: Boolean = false)
    private class T(val h: Int, val w: Int, val c: Int, val d: FloatArray)

    @Volatile private var net: Net? = null

    private fun load(ctx: Context): Net {
        net?.let { return it }
        val bytes = ctx.assets.open("seg.bin").use { it.readBytes() }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { b.get(it) }
        require(String(magic) == "GLSG") { "not a segmenter" }
        val input = b.int; val output = b.int; val nc = b.int
        val consts = HashMap<Int, Pair<IntArray, FloatArray>>()
        repeat(nc) {
            val id = b.int; val nd = b.int
            val shape = IntArray(nd) { b.int }
            val n = shape.fold(1) { a, x -> a * x }
            val d = FloatArray(n); b.asFloatBuffer().get(d); b.position(b.position() + n * 4)
            consts[id] = shape to d
        }
        val no = b.int
        val ops = ArrayList<Op>(no)
        repeat(no) {
            val kind = b.get().toInt(); val ni = b.get().toInt()
            val ins = IntArray(ni) { b.int }; val out = b.int
            ops += if (kind == 1 || kind == 2) { val s = b.get().toInt(); val pad = b.get().toInt(); val act = b.get().toInt(); Op(kind, ins, out, s, pad == 0, act == 1) }
                else Op(kind, ins, out)
        }
        return Net(input, output, consts, ops).also { net = it }
    }

    /** How sure the model is, for each of the 256×256 points of [img] (scaled to that size), that it is the person: 0 … 1. */
    fun mask(ctx: Context, img: Bitmap): FloatArray {
        val n = load(ctx)
        val small = Bitmap.createScaledBitmap(img, 256, 256, true)
        val px = IntArray(256 * 256); small.getPixels(px, 0, 256, 0, 0, 256, 256)
        val x = FloatArray(256 * 256 * 3)
        for (i in px.indices) { val p = px[i]; x[i * 3] = ((p shr 16) and 255) / 255f; x[i * 3 + 1] = ((p shr 8) and 255) / 255f; x[i * 3 + 2] = (p and 255) / 255f }
        // each tensor is dropped once its last reader has run
        val last = HashMap<Int, Int>()
        n.ops.forEachIndexed { k, op -> op.ins.forEach { last[it] = k } }
        val live = HashMap<Int, T>()
        live[n.input] = T(256, 256, 3, x)
        n.ops.forEachIndexed { k, op ->
            val a = live[op.ins[0]]!!
            live[op.out] = when (op.kind) {
                1 -> conv(a, n.consts[op.ins[1]]!!, n.consts[op.ins[2]]!!.second, op.stride, op.same, op.relu)
                2 -> depthwise(a, n.consts[op.ins[1]]!!, n.consts[op.ins[2]]!!.second, op.stride, op.same, op.relu)
                3 -> { val b = live[op.ins[1]]!!; T(a.h, a.w, a.c, FloatArray(a.d.size) { a.d[it] + b.d[it] }) }
                4 -> { val b = live[op.ins[1]]!!
                    if (b.d.size == a.c && a.d.size != a.c) T(a.h, a.w, a.c, FloatArray(a.d.size) { a.d[it] * b.d[it % a.c] })
                    else if (a.d.size == b.c && b.d.size != b.c) T(b.h, b.w, b.c, FloatArray(b.d.size) { b.d[it] * a.d[it % b.c] })
                    else T(a.h, a.w, a.c, FloatArray(a.d.size) { a.d[it] * b.d[it] }) }
                5 -> T(a.h, a.w, a.c, FloatArray(a.d.size) { val v = a.d[it]; v * min(6f, max(0f, v + 3f)) / 6f })
                6 -> T(a.h, a.w, a.c, FloatArray(a.d.size) { max(0f, a.d[it]) })
                8 -> T(a.h, a.w, a.c, FloatArray(a.d.size) { 1f / (1f + exp(-a.d[it])) })
                7 -> { val s = FloatArray(a.c); for (i in a.d.indices) s[i % a.c] += a.d[i]; val k2 = 1f / (a.h * a.w); for (c in 0 until a.c) s[c] *= k2; T(1, 1, a.c, s) }
                9 -> up2(a)
                10 -> tconv2(a, n.consts[op.ins[1]]!!, n.consts[op.ins[2]]!!.second)
                else -> error("layer ${op.kind}")
            }
            op.ins.forEach { if (last[it] == k && it != n.output) live.remove(it) }
        }
        return live[n.output]!!.d
    }

    private fun conv(a: T, w: Pair<IntArray, FloatArray>, bias: FloatArray, s: Int, same: Boolean, relu: Boolean): T {
        val (shape, wd) = w; val oc = shape[0]; val kh = shape[1]; val kw = shape[2]; val ic = shape[3]
        val oh: Int; val ow: Int; val pt: Int; val pl: Int
        if (same) { oh = (a.h + s - 1) / s; ow = (a.w + s - 1) / s; pt = max((oh - 1) * s + kh - a.h, 0) / 2; pl = max((ow - 1) * s + kw - a.w, 0) / 2 }
        else { oh = (a.h - kh) / s + 1; ow = (a.w - kw) / s + 1; pt = 0; pl = 0 }
        val o = FloatArray(oh * ow * oc)
        if (kh == 1 && kw == 1 && s == 1) {
            // pointwise: most of the network
            for (p in 0 until oh * ow) {
                val ib = p * ic; val ob = p * oc
                for (k in 0 until oc) {
                    var acc = bias[k]; val wb = k * ic
                    for (i in 0 until ic) acc += a.d[ib + i] * wd[wb + i]
                    o[ob + k] = if (relu && acc < 0f) 0f else acc
                }
            }
        } else for (y in 0 until oh) for (xx in 0 until ow) {
            val ob = (y * ow + xx) * oc
            for (k in 0 until oc) o[ob + k] = bias[k]
            for (dy in 0 until kh) { val iy = y * s + dy - pt; if (iy < 0 || iy >= a.h) continue
                for (dx in 0 until kw) { val ix = xx * s + dx - pl; if (ix < 0 || ix >= a.w) continue
                    val ib = (iy * a.w + ix) * ic
                    for (k in 0 until oc) {
                        val wb = ((k * kh + dy) * kw + dx) * ic
                        var acc = 0f
                        for (i in 0 until ic) acc += a.d[ib + i] * wd[wb + i]
                        o[ob + k] += acc
                    }
                }
            }
            if (relu) for (k in 0 until oc) if (o[ob + k] < 0f) o[ob + k] = 0f
        }
        return T(oh, ow, oc, o)
    }

    private fun depthwise(a: T, w: Pair<IntArray, FloatArray>, bias: FloatArray, s: Int, same: Boolean, relu: Boolean): T {
        val (shape, wd) = w; val kh = shape[1]; val kw = shape[2]; val c = a.c
        val oh: Int; val ow: Int; val pt: Int; val pl: Int
        if (same) { oh = (a.h + s - 1) / s; ow = (a.w + s - 1) / s; pt = max((oh - 1) * s + kh - a.h, 0) / 2; pl = max((ow - 1) * s + kw - a.w, 0) / 2 }
        else { oh = (a.h - kh) / s + 1; ow = (a.w - kw) / s + 1; pt = 0; pl = 0 }
        val o = FloatArray(oh * ow * c)
        for (y in 0 until oh) for (xx in 0 until ow) {
            val ob = (y * ow + xx) * c
            for (k in 0 until c) o[ob + k] = bias[k]
            for (dy in 0 until kh) { val iy = y * s + dy - pt; if (iy < 0 || iy >= a.h) continue
                for (dx in 0 until kw) { val ix = xx * s + dx - pl; if (ix < 0 || ix >= a.w) continue
                    val ib = (iy * a.w + ix) * c; val wb = (dy * kw + dx) * c
                    for (k in 0 until c) o[ob + k] += a.d[ib + k] * wd[wb + k]
                }
            }
            if (relu) for (k in 0 until c) if (o[ob + k] < 0f) o[ob + k] = 0f
        }
        return T(oh, ow, c, o)
    }

    /** Twice the size, bilinear with half-pixel centres. */
    private fun up2(a: T): T {
        val h = a.h * 2; val w = a.w * 2; val c = a.c
        val o = FloatArray(h * w * c)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / 2f - 0.5f).coerceIn(0f, (a.h - 1).toFloat()); val y0 = fy.toInt(); val y1 = min(y0 + 1, a.h - 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / 2f - 0.5f).coerceIn(0f, (a.w - 1).toFloat()); val x0 = fx.toInt(); val x1 = min(x0 + 1, a.w - 1); val tx = fx - x0
                val ob = (y * w + x) * c
                val i00 = (y0 * a.w + x0) * c; val i01 = (y0 * a.w + x1) * c; val i10 = (y1 * a.w + x0) * c; val i11 = (y1 * a.w + x1) * c
                for (k in 0 until c) {
                    val top = a.d[i00 + k] * (1 - tx) + a.d[i01 + k] * tx
                    val bot = a.d[i10 + k] * (1 - tx) + a.d[i11 + k] * tx
                    o[ob + k] = top * (1 - ty) + bot * ty
                }
            }
        }
        return T(h, w, c, o)
    }

    /** The last layer: a 2×2 transposed convolution with stride 2 (each point spread over the four it becomes). */
    private fun tconv2(a: T, w: Pair<IntArray, FloatArray>, bias: FloatArray): T {
        val (shape, wd) = w; val oc = shape[0]; val ic = shape[3]
        val h = a.h * 2; val ww = a.w * 2
        val o = FloatArray(h * ww * oc)
        for (y in 0 until a.h) for (x in 0 until a.w) {
            val ib = (y * a.w + x) * ic
            for (dy in 0..1) for (dx in 0..1) {
                val ob = ((y * 2 + dy) * ww + x * 2 + dx) * oc
                for (k in 0 until oc) {
                    val wb = ((k * 2 + dy) * 2 + dx) * ic
                    var acc = bias[k]
                    for (i in 0 until ic) acc += a.d[ib + i] * wd[wb + i]
                    o[ob + k] = acc
                }
            }
        }
        return T(h, ww, oc, o)
    }
}
