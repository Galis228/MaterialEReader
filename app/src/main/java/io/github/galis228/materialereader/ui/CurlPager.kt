package io.github.galis228.materialereader.ui

import androidx.compose.animation.core.Animatable
import android.os.Build
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.min
import androidx.compose.animation.core.VectorConverter
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
private class Turn(val dir: Int, val cornerMode: Int, val point: Animatable<Offset, AnimationVector2D>)

private const val CORNER_TOP = 0
private const val CORNER_MIDDLE = 1
private const val CORNER_BOTTOM = 2

/** How the sheet leaves the page. All three bend toward the reader, never into the book. */
enum class CurlStyle { ROLL, BEND, FLIP }

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Page turning that bends the sheet outward, toward the reader.
 *
 * The finger position is kept as a point that runs from x = w (page flat) to x = -w (page gone), so all
 * styles share the same gestures and timing:
 *  - ROLL: the right edge rolls up into a tube that travels left;
 *  - BEND: the corner the finger holds lifts in a soft curve, with no crease;
 *  - FLIP: the whole sheet swings away from the left spine in perspective.
 * ROLL and BEND are drawn by an AGSL shader (Android 13+); older systems fall back to a flat fold.
 */
@Composable
fun CurlPager(
    style: CurlStyle,
    dark: Boolean,
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
    val curlStyle by rememberUpdatedState(style)
    val curl = remember { CurlEffect.createOrNull() }

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

        fun finish(t: Turn, commit: Boolean, velocity: Offset = Offset.Zero) = scope.launch {
            val cy = cornerY(t, t.point.value)
            val flat = Offset(w, cy); val turned = Offset(-w * 1.15f, cy)
            val target = if (t.dir > 0) (if (commit) turned else flat) else (if (commit) flat else turned)
            // a critically damped spring picks up the finger's speed, so the release has no visible seam
            t.point.animateTo(target, spring(dampingRatio = 1f, stiffness = 260f, visibilityThreshold = Offset(0.5f, 0.5f)), velocity)
            if (commit) onCurrentChange((cur + t.dir).coerceIn(0, cnt - 1))
            turn = null
        }

        SideEffect {
            registerTurn { dir ->
                if (turn != null) return@registerTurn
                if (dir > 0 && cur >= cnt - 1) return@registerTurn
                if (dir < 0 && cur <= 0) return@registerTurn
                val start = if (dir > 0) Offset(w, h) else Offset(-w * 1.15f, h)
                val t = Turn(dir, CORNER_BOTTOM, Animatable(start, Offset.VectorConverter))
                turn = t
                scope.launch {
                    val end = if (dir > 0) Offset(-w * 1.15f, h) else Offset(w, h)
                    if (curlStyle == CurlStyle.BEND) {
                        // the corner rises a little on its way, like a sheet lifted by the hand
                        val mid = Offset((start.x + end.x) / 2, h * 0.88f)
                        t.point.animateTo(mid, tween(300, easing = CubicBezierEasing(0.3f, 0f, 0.6f, 1f)))
                        t.point.animateTo(end, tween(300, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
                    } else {
                        t.point.animateTo(end, tween(560, easing = Emphasized))
                    }
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
                                curlStyle != CurlStyle.BEND -> CORNER_MIDDLE
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
                            val v = tracker.calculateVelocity()
                            val vx = v.x
                            val f = (w - t.point.value.x) / (2 * w) // 0 = flat, 1 = turned over
                            val commit = if (t.dir > 0) (vx < -700f || (f > 0.28f && vx < 600f)) else (vx > 700f || (f < 0.72f && vx > -600f))
                            finish(t, commit, Offset(v.x * 2f, v.y))
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
                if (style == CurlStyle.FLIP) {
                    // shadow the turning sheet casts on the next page
                    Canvas(Modifier.fillMaxSize()) {
                        val e = flipEdge(t.point.value.x, size.width)
                        val k = 1f - e / size.width
                        if (k > 0.001f) drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.45f * (1f - k * 0.6f)), 1f to Color.Transparent,
                                startX = e, endX = e + size.width * (0.12f + 0.25f * k),
                            ),
                        )
                    }
                    key(top) {
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer {
                                    val e = flipEdge(t.point.value.x, size.width)
                                    val angle = Math.toDegrees(acos((e / size.width).coerceIn(0f, 1f).toDouble())).toFloat()
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    rotationY = angle
                                    cameraDistance = 12f * density
                                    alpha = if (angle >= 89.5f) 0f else 1f
                                }
                                .drawWithContent {
                                    drawContent()
                                    val k = 1f - flipEdge(t.point.value.x, size.width) / size.width
                                    // the sheet turns away from the light as it lifts
                                    drawRect(Brush.horizontalGradient(
                                        0f to Color.Black.copy(alpha = 0.05f + 0.30f * k),
                                        1f to Color.Black.copy(alpha = 0.12f + 0.45f * k),
                                    ))
                                },
                        ) { page(top) }
                    }
                } else {
                    val layer = rememberGraphicsLayer()
                    key(top) {
                        Box(
                            Modifier.fillMaxSize().drawWithContent {
                                layer.record { this@drawWithContent.drawContent() }
                                val p = t.point.value
                                val corner = Offset(size.width, cornerY(t, p))
                                val shader = curl
                                if (shader == null) {
                                    layer.renderEffect = null
                                    drawCurl(layer, p, corner, paper)
                                } else if (style == CurlStyle.ROLL) {
                                    val e = flipEdge(p.x, size.width)
                                    val r = min(size.width * 0.085f, (size.width - e) * 0.5f)
                                    val axis = e - r * (1f - e / size.width) // the tube ends exactly off-screen
                                    layer.renderEffect = shader.effect(size, Offset(axis, 0f), Offset(1f, 0f), r, roll = true, paper, dark)
                                    drawLayer(layer)
                                } else {
                                    val d = corner - p
                                    val len = d.getDistance()
                                    if (len < 1f) { layer.renderEffect = null; drawLayer(layer) } else {
                                        val n = Offset(d.x / len, d.y / len)
                                        val r = min(size.width * 0.17f, len * 0.4f)
                                        val e = (PI.toFloat() * r + len) / 2f // the corner lands under the finger
                                        layer.renderEffect = shader.effect(size, corner - n * e, n, r, roll = false, paper, dark)
                                        drawLayer(layer)
                                    }
                                }
                            },
                        ) { page(top) }
                    }
                }
            }
        }
    }
}

/** Where the free edge of the sheet is for a turn point running from w (flat) to -w (gone). */
private fun flipEdge(px: Float, w: Float) = ((px + w) / 2f).coerceIn(0f, w)

/**
 * Cylinder page curl. For every screen pixel it finds which point of the sheet lands there: the flat
 * part, the lower (front) half of the cylinder, or the upper half that shows the back of the sheet.
 */
private class CurlEffect private constructor(private val shader: android.graphics.RuntimeShader) {
    fun effect(size: Size, axis: Offset, dir: Offset, radius: Float, roll: Boolean, paper: Color, dark: Boolean): RenderEffect {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("axis", axis.x, axis.y)
        shader.setFloatUniform("dir", dir.x, dir.y)
        shader.setFloatUniform("radius", radius.coerceAtLeast(0.5f))
        shader.setFloatUniform("roll", if (roll) 1f else 0f)
        shader.setFloatUniform("lift", if (dark) 0.16f else 0.04f)
        shader.setColorUniform("paper", paper.toArgb())
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "page").asComposeRenderEffect()
    }

    companion object {
        fun createOrNull(): CurlEffect? =
            if (Build.VERSION.SDK_INT >= 33) runCatching { CurlEffect(android.graphics.RuntimeShader(SRC)) }.getOrNull() else null

        private const val SRC = """
uniform shader page;
uniform float2 size;
uniform float2 axis;
uniform float2 dir;
uniform float radius;
uniform float roll;
uniform float lift;
layout(color) uniform half4 paper;

const float PI = 3.14159265;

bool inPage(float2 q) { return q.x >= 0.0 && q.y >= 0.0 && q.x <= size.x && q.y <= size.y; }

// is this screen point covered by the part of the sheet lying flat over the page, back side up?
bool onBack(float2 q) {
    float d = dot(q - axis, dir);
    return d < 0.0 && inPage(q + dir * (PI * radius - 2.0 * d));
}
float onBackF(float2 q) { return onBack(q) ? 1.0 : 0.0; }

// back of the sheet: paper with the print showing through faintly, lit like a curved surface
half4 backSide(float2 src, float light) {
    half4 c = page.eval(src);
    half3 b = mix(paper.rgb, c.rgb, 0.10);
    b = mix(b, half3(1.0), lift * light);
    return half4(b * (0.80 + 0.20 * light), 1.0);
}

half4 main(float2 p) {
    float R = radius;
    float d = dot(p - axis, dir);
    if (d > R) {
        // the sheet has left this spot: soft shadow of the curl on the next page
        float s = clamp((d - R) / (R * 1.6 + 8.0), 0.0, 1.0);
        float a = 0.38 * (1.0 - s) * (1.0 - s);
        return half4(0.0, 0.0, 0.0, a);
    }
    if (d >= 0.0) {
        float th = asin(clamp(d / R, 0.0, 1.0));
        // upper half of the cylinder, nearest to the reader: back of the sheet
        float s2 = R * (PI - th);
        float2 src2 = p + dir * (s2 - d);
        if (inPage(src2)) {
            float light = sin(th * 1.6 + 0.3);
            return backSide(src2, clamp(light, 0.0, 1.0));
        }
        // lower half: the printed side bending up and away from the light
        float s1 = R * th;
        float2 src1 = p + dir * (s1 - d);
        if (inPage(src1)) {
            half4 c = page.eval(src1);
            float k = d / R;
            float shade = 1.0 - 0.42 * k * k * k;
            return half4(c.rgb * shade, c.a);
        }
        float s = clamp(d / R, 0.0, 1.0);
        return half4(0.0, 0.0, 0.0, 0.30 * (1.0 - s));
    }
    float shadow = 0.0;
    if (roll < 0.5) {
        // past the cylinder the sheet lies over the page, back side up
        if (onBack(p)) return backSide(p + dir * (PI * R - 2.0 * d), 0.55);
        // soft shadow of the lifted sheet: sample its outline a few times, shifted away from the light
        float k = R * 0.35 + 3.0;
        shadow = 0.10 * (onBackF(p - float2(k, k) * 0.5) + onBackF(p - float2(k, k)) + onBackF(p - float2(k, k) * 1.6));
    }
    half4 c = page.eval(p);
    // ambient shadow under the rising part of the sheet
    float ao = 0.22 * exp(d / (R * 0.7 + 1.0));
    return half4(c.rgb * (1.0 - max(ao, shadow)), c.a);
}
"""
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

