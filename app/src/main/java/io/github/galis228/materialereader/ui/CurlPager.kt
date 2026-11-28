package io.github.galis228.materialereader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch

/** One page turn in progress. dir = +1 forward (top = current), -1 backward (top = previous). */
private class Turn(val dir: Int, val cornerMode: Int, val point: Animatable<Offset, *>)

private const val CORNER_TOP = 0
private const val CORNER_MIDDLE = 1
private const val CORNER_BOTTOM = 2

/**
 * Realistic page curl in the spirit of Google Play Books: the page folds along a line that follows
 * the finger, the back of the sheet shows a faint mirror of the text, and soft shadows sit along the fold.
 */
@Composable
fun CurlPager(
    count: Int,
    current: Int,
    onCurrentChange: (Int) -> Unit,
    paper: Color,
    registerTurn: ((Int) -> Unit) -> Unit,
    onTap: (Float) -> Unit,
    onLongPress: (Offset) -> Unit = {},
    page: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tapAt = LocalTapAt.current
    var turn by remember { mutableStateOf<Turn?>(null) }
    val cur by rememberUpdatedState(current)
    val cnt by rememberUpdatedState(count)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        fun cornerY(t: Turn, p: Offset) = when (t.cornerMode) { CORNER_TOP -> 0f; CORNER_BOTTOM -> h; else -> p.y }

        /** Keeps the sheet attached to the spine: the curl point can't go farther than one page width from it. */
        fun constrain(t: Turn, p: Offset): Offset {
            val sy = cornerY(t, p)
            val v = Offset(p.x, p.y - sy)
            val d = v.getDistance()
            val q = if (d > w) Offset(v.x * w / d, sy + v.y * w / d) else p
            return Offset(q.x.coerceIn(-w, w), q.y.coerceIn(-h * 0.25f, h * 1.25f))
        }

        fun finish(t: Turn, commit: Boolean) = scope.launch {
            val cy = when (t.cornerMode) { CORNER_TOP -> 0f; CORNER_BOTTOM -> h; else -> t.point.value.y }
            val flat = Offset(w, cy); val turned = Offset(-w, cy)
            val target = if (t.dir > 0) (if (commit) turned else flat) else (if (commit) flat else turned)
            t.point.animateTo(target, tween(320, easing = FastOutSlowInEasing))
            if (commit) onCurrentChange((cur + t.dir).coerceIn(0, cnt - 1))
            turn = null
        }

        SideEffect {
            registerTurn { dir ->
                if (turn != null) return@registerTurn
                if (dir > 0 && cur >= cnt - 1) return@registerTurn
                if (dir < 0 && cur <= 0) return@registerTurn
                val start = if (dir > 0) Offset(w, h) else Offset(-w, h)
                val t = Turn(dir, CORNER_BOTTOM, Animatable(start, Offset.VectorConverter))
                turn = t
                scope.launch {
                    val end = if (dir > 0) Offset(-w, h) else Offset(w, h)
                    t.point.animateTo(end, keyframes {
                        durationMillis = 420
                        Offset(if (dir > 0) w * 0.35f else -w * 0.35f, h * 0.86f) at 160 using FastOutSlowInEasing
                        Offset(if (dir > 0) -w * 0.4f else w * 0.4f, h * 0.9f) at 320
                    })
                    onCurrentChange((cur + dir).coerceIn(0, cnt - 1))
                    turn = null
                }
            }
        }

        val tracker = remember { VelocityTracker() }
        var downX by remember { mutableStateOf(0f) }
        var downY by remember { mutableStateOf(0f) }

        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress(it) }) { if (!tapAt(it)) onTap(it.x / w) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o ->
                            if (turn != null) return@detectDragGestures
                            tracker.resetTracking()
                            downX = o.x; downY = o.y
                            val forward = o.x > w * 0.4f
                            if (forward && cur >= cnt - 1) return@detectDragGestures
                            if (!forward && cur <= 0) return@detectDragGestures
                            val mode = when {
                                o.y < h * 0.3f -> CORNER_TOP
                                o.y > h * 0.62f -> CORNER_BOTTOM
                                else -> CORNER_MIDDLE
                            }
                            val cy = when (mode) { CORNER_TOP -> 0f; CORNER_BOTTOM -> h; else -> o.y }
                            turn = Turn(if (forward) 1 else -1, mode, Animatable(Offset(if (forward) w else -w, cy), Offset.VectorConverter))
                        },
                        onDrag = { change, _ ->
                            val t = turn ?: return@detectDragGestures
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dx = change.position.x - downX
                            val dy = change.position.y - downY
                            val base = if (t.dir > 0) w else -w
                            val cy = when (t.cornerMode) { CORNER_TOP -> 0f; CORNER_BOTTOM -> h; else -> downY }
                            val p = constrain(t, Offset(base + dx * 2f, cy + dy))
                            scope.launch { t.point.snapTo(p) }
                        },
                        onDragEnd = {
                            val t = turn ?: return@detectDragGestures
                            val vx = tracker.calculateVelocity().x
                            val f = (w - t.point.value.x) / (2 * w) // 0 = flat, 1 = turned over
                            val commit = if (t.dir > 0) (vx < -800f || (f > 0.28f && vx < 600f)) else (vx > 800f || (f < 0.72f && vx > -600f))
                            finish(t, commit)
                        },
                        onDragCancel = { turn?.let { finish(it, false) } },
                    )
                },
        ) {
            val t = turn
            if (t == null) {
                key(current) { page(current) }
            } else {
                val top = if (t.dir > 0) current else current - 1
                val under = if (t.dir > 0) current + 1 else current
                key(under) { Box(Modifier.fillMaxSize()) { page(under) } }
                val layer = rememberGraphicsLayer()
                key(top) {
                    Box(
                        Modifier.fillMaxSize().drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            val p = t.point.value
                            drawCurl(layer, p, Offset(size.width, cornerY(t, p)), paper)
                        },
                    ) { page(top) }
                }
            }
        }
    }
}

private fun DrawScope.drawCurl(layer: androidx.compose.ui.graphics.layer.GraphicsLayer, p: Offset, c: Offset, paper: Color) {
    val w = size.width; val h = size.height
    val d = c - p
    val len = d.getDistance()
    if (len < 1f) { drawLayer(layer); return }
    val n = Offset(d.x / len, d.y / len)          // fold normal, pointing to the corner
    val m = Offset((c.x + p.x) / 2, (c.y + p.y) / 2) // a point on the fold line
    val rect = listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w, h), Offset(0f, h))
    fun side(x: Offset) = (x.x - m.x) * n.x + (x.y - m.y) * n.y
    val kept = clip(rect) { side(it) <= 0f }
    val removed = clip(rect) { side(it) > 0f }
    if (kept.size < 3) return
    val nm = n.x * m.x + n.y * m.y
    fun reflect(x: Offset): Offset { val k = 2 * ((x.x * n.x + x.y * n.y) - nm); return Offset(x.x - k * n.x, x.y - k * n.y) }
    val flap = removed.map(::reflect)
    val depth = removed.maxOfOrNull { side(it) } ?: 0f // how far the flap reaches from the fold

    // 1. shadow cast on the next page, right behind the fold
    if (removed.size >= 3) clipPath(path(removed)) {
        val reach = (depth * 0.5f).coerceIn(16f, w * 0.25f)
        drawRect(Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent), m, m + n * reach))
    }
    // 2. the part of the sheet still lying flat
    clipPath(path(kept)) { drawLayer(layer) }
    // 3. the lifted flap: back of the sheet with the text showing through, shaded like a curved surface
    if (flap.size >= 3) {
        val fp = path(flap)
        // soft shadow of the flap onto the page below it
        clipPath(path(kept)) {
            val reach = (depth * 0.35f).coerceIn(10f, w * 0.12f)
            drawRect(Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), m - n * depth, m - n * (depth + reach)))
        }
        clipPath(fp) {
            drawRect(paper)
            val mx = Matrix().apply {
                val a = 1 - 2 * n.x * n.x; val b = -2 * n.x * n.y; val dd = 1 - 2 * n.y * n.y
                values[Matrix.ScaleX] = a; values[Matrix.SkewX] = b; values[Matrix.SkewY] = b; values[Matrix.ScaleY] = dd
                values[Matrix.TranslateX] = 2 * n.x * nm; values[Matrix.TranslateY] = 2 * n.y * nm
            }
            withTransform({ transform(mx) }) { drawLayer(layer) }
            drawRect(paper.copy(alpha = 0.86f)) // the mirrored print only shows through faintly
            val far = m - n * depth
            drawRect(Brush.linearGradient(
                0f to Color.Black.copy(alpha = 0.16f), 0.18f to Color.White.copy(alpha = 0.10f),
                0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.10f),
                start = m, end = far,
            ))
        }
    }
}

/** Sutherland–Hodgman clip of a convex polygon by a half-plane given as a predicate (straight edge). */
private fun clip(poly: List<Offset>, inside: (Offset) -> Boolean): List<Offset> {
    val out = ArrayList<Offset>()
    for (i in poly.indices) {
        val a = poly[i]; val b = poly[(i + 1) % poly.size]
        val ia = inside(a); val ib = inside(b)
        if (ia) out += a
        if (ia != ib) out += intersect(a, b, inside)
    }
    return out
}

/** Binary search for the crossing point on segment a-b (the boundary is a straight line, so this converges fast). */
private fun intersect(a: Offset, b: Offset, inside: (Offset) -> Boolean): Offset {
    var lo = a; var hi = b
    val ia = inside(a)
    repeat(24) {
        val mid = Offset((lo.x + hi.x) / 2, (lo.y + hi.y) / 2)
        if (inside(mid) == ia) lo = mid else hi = mid
    }
    return Offset((lo.x + hi.x) / 2, (lo.y + hi.y) / 2)
}

private fun path(pts: List<Offset>) = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    close()
}

