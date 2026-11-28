package io.github.galis228.materialereader.ui

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Looks at a comic page: where the drawing is (to trim margins) and where its panels are,
 * by cutting along empty gutters (XY-cut). All rects are 0..1 of the full image.
 */
object ComicImage {
    class Layout(val content: RectF, val panels: List<RectF>)

    fun analyze(src: Bitmap, rtl: Boolean): Layout {
        val w = 240
        val h = (w.toFloat() * src.height / src.width).toInt().coerceIn(40, 1200)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val lum = IntArray(w * h)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small != src) small.recycle()
        for (i in px.indices) { val c = px[i]; lum[i] = ((c shr 16 and 255) * 3 + (c shr 8 and 255) * 6 + (c and 255)) / 10 }
        // background = the most common brightness along the border (white page or black page)
        val hist = IntArray(256)
        for (x in 0 until w) { hist[lum[x]]++; hist[lum[(h - 1) * w + x]]++ }
        for (y in 0 until h) { hist[lum[y * w]]++; hist[lum[y * w + w - 1]]++ }
        val bg = hist.indices.maxBy { hist[it] }
        fun isBg(x: Int, y: Int) = kotlin.math.abs(lum[y * w + x] - bg) < 30

        fun rowFull(y: Int, x0: Int, x1: Int): Boolean { var n = 0; for (x in x0 until x1) if (!isBg(x, y)) n++; return n <= (x1 - x0) / 80 }
        fun colFull(x: Int, y0: Int, y1: Int): Boolean { var n = 0; for (y in y0 until y1) if (!isBg(x, y)) n++; return n <= (y1 - y0) / 80 }

        data class R(val x0: Int, val y0: Int, val x1: Int, val y1: Int) { val area get() = (x1 - x0) * (y1 - y0) }
        fun trim(r: R): R? {
            var (x0, y0, x1, y1) = r
            while (y0 < y1 && rowFull(y0, x0, x1)) y0++
            while (y1 > y0 && rowFull(y1 - 1, x0, x1)) y1--
            while (x0 < x1 && colFull(x0, y0, y1)) x0++
            while (x1 > x0 && colFull(x1 - 1, y0, y1)) x1--
            return if (x1 - x0 < 4 || y1 - y0 < 4) null else R(x0, y0, x1, y1)
        }
        val content = trim(R(0, 0, w, h)) ?: R(0, 0, w, h)

        /** Runs of non-gutter lines along one axis. */
        fun segments(n0: Int, n1: Int, gutter: (Int) -> Boolean, minLen: Int): List<Pair<Int, Int>> {
            val out = ArrayList<Pair<Int, Int>>()
            var start = -1
            for (i in n0..n1) {
                val g = i == n1 || gutter(i)
                if (!g && start < 0) start = i
                if (g && start >= 0) { if (i - start >= minLen) out += start to i; start = -1 }
            }
            return out
        }
        val panels = ArrayList<R>()
        fun cut(r: R, depth: Int) {
            val rows = segments(r.y0, r.y1, { rowFull(it, r.x0, r.x1) }, (h * 0.04f).toInt().coerceAtLeast(3))
            if (rows.size > 1 && depth < 5) { rows.forEach { (a, b) -> trim(R(r.x0, a, r.x1, b))?.let { cut(it, depth + 1) } }; return }
            val cols = segments(r.x0, r.x1, { colFull(it, r.y0, r.y1) }, (w * 0.06f).toInt().coerceAtLeast(3))
            if (cols.size > 1 && depth < 5) {
                (if (rtl) cols.reversed() else cols).forEach { (a, b) -> trim(R(a, r.y0, b, r.y1))?.let { cut(it, depth + 1) } }
                return
            }
            panels += r
        }
        cut(content, 0)
        val big = panels.filter { it.area >= content.area * 0.02f }
        fun f(r: R) = RectF(r.x0.toFloat() / w, r.y0.toFloat() / h, r.x1.toFloat() / w, r.y1.toFloat() / h)
        return Layout(f(content), if (big.size <= 1) emptyList() else big.map(::f))
    }
}
