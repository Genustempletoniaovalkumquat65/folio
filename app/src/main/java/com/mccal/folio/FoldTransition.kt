package com.mccal.folio

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.exp

/**
 * iPhone Duo–style fold effect, as dynamic as a Galaxy Z Fold allows.
 *
 * Apps only get coarse hinge steps (0°, 90°, 180°), and One UI decides when displays switch. So the
 * effect is *step-anchored and speed-adaptive*: each real step is a checkpoint, the motion between
 * checkpoints is predicted from how fast this person folds (learned over time), and a late or early
 * step bends the animation instead of snapping it.
 *
 * Visual model (from the MIT Three.js recreation): the half left of the hinge is blurred with
 * radius ∝ m·e^1.35 and darkened toward its outer edge; m is 1 half-folded and 0 flat.
 */
@Composable
fun FoldTransitionHost(enabled: Boolean = true, intensity: Float = 1f, stayAwake: Boolean = true, content: @Composable () -> Unit) {
    val expanded = LocalConfiguration.current.screenWidthDp >= EXPANDED_WIDTH_DP
    val view = LocalView.current
    val context = LocalContext.current
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) DuoShader() else null }
    val fold = remember { FoldTimeline(context) }
    fold.stayAwake = stayAwake
    // m: 0 = clean, 1 = fully half-folded look. cover = whole-screen mode on the cover display.
    var m by remember { mutableFloatStateOf(0f) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> fold.start()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> fold.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); fold.stop() }
    }

    // Decide during composition so the very first frame on the new display is already covered.
    if (expanded != fold.expanded) {
        fold.expanded = expanded
        fold.onDisplaySwitched(SystemClock.uptimeMillis())
        m = if (expanded) START_M_ON_UNFOLD else START_M_ON_COVER
    }

    LaunchedEffect(Unit) {
        var litFrames = 0
        var lastFrame = 0L
        while (true) {
            if (!fold.busy && m == 0f) { lastFrame = 0L; kotlinx.coroutines.delay(IDLE_POLL_MS); continue }
            withFrameNanos { frame ->
                val now = SystemClock.uptimeMillis()
                val dt = if (lastFrame == 0L) 16f else ((frame - lastFrame) / 1_000_000f).coerceIn(1f, 64f)
                lastFrame = frame
                if (fold.waitingForPanel) {
                    val lit = view.display?.state == Display.STATE_ON
                    litFrames = if (lit) litFrames + 1 else 0
                    if (litFrames >= 2 || now - fold.switchedAt > LIT_TIMEOUT_MS) { fold.onPanelLit(now); litFrames = 0 }
                }
                val target = fold.targetM(now)
                // Follow the target closely but never jump: small time constant, frame-rate independent.
                val next = m + (target - m) * (1f - exp(-dt / FOLLOW_MS))
                m = if (target == 0f && next < .003f) 0f else next
            }
        }
    }

    Box(Modifier.fillMaxSize().then(
        if (shader != null) Modifier.graphicsLayer {
            renderEffect = if (enabled && m > 0f) shader.effect(size.width, size.height, (m * intensity).coerceIn(0f, 1.5f), cover = !fold.expanded) else null
        } else Modifier.drawWithContent {
            drawContent()
            if (enabled && m > 0f) {
                if (fold.expanded) drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = m),
                    .5f to Color.Transparent, startX = 0f, endX = size.width))
                else drawRect(Color.Black.copy(alpha = .5f * m))
            }
        })) { content() }
}

/** Hinge steps + learned timing → target effect strength over time. */
private class FoldTimeline(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val hinge: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    private val prefs = context.getSharedPreferences("folio", 0)

    var expanded = false
    var stayAwake = true
    var switchedAt = -1L; private set
    var waitingForPanel = false; private set

    private var angle: Float? = null
    private var angleAt = 0L

    // Unfold (inner display): lit → flat.
    private var litAt = -1L
    private var flatAt = -1L
    private var predictedOpenMs = prefs.getFloat("fold_open_ms", 520f)

    // Fold (inner display): 180→90 step → 0 step.
    private var closeStartAt = -1L
    private var closedAt = -1L
    private var reopenedAt = -1L
    private var predictedCloseMs = prefs.getFloat("fold_close_ms", 650f)

    // Cover display after folding, and while starting to open from the cover.
    private var coverLitAt = -1L
    private var coverOpeningAt = -1L
    private val appContext = context.applicationContext

    val busy get() = waitingForPanel || litAt >= 0 || closeStartAt >= 0 || reopenedAt >= 0 || coverLitAt >= 0 || coverOpeningAt >= 0

    fun start() { hinge?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() { sensors?.unregisterListener(this) }

    fun onDisplaySwitched(now: Long) {
        switchedAt = now; waitingForPanel = true
        litAt = -1L; flatAt = -1L; closeStartAt = -1L; closedAt = -1L; reopenedAt = -1L; coverLitAt = -1L; coverOpeningAt = -1L
    }

    fun onPanelLit(now: Long) {
        waitingForPanel = false
        if (expanded) { litAt = now; if ((angle ?: 0f) >= FLAT_DEG) flatAt = now } else coverLitAt = now
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull() ?: return
        val now = SystemClock.uptimeMillis()
        val previous = angle
        angle = value; angleAt = now
        if (previous == null || previous == value) return
        if (expanded) {
            when {
                // Reached flat while revealing: learn how long lit → flat takes for this person.
                value >= FLAT_DEG && litAt >= 0 && flatAt < 0 -> {
                    flatAt = now
                    learnOpen((now - litAt).toFloat())
                }
                // Started folding from flat: keep One UI from sleeping, and start the fold-away.
                previous >= FLAT_DEG && value < FLAT_DEG -> {
                    closeStartAt = now; closedAt = -1L; reopenedAt = -1L
                    if (stayAwake) FoldBridgeActivity.start(appContext)
                }
                // Nearly closed: learn how long the fold takes, and make sure the bridge is up.
                value <= CLOSED_DEG && closeStartAt >= 0 && closedAt < 0 -> {
                    closedAt = now
                    learnClose((now - closeStartAt).toFloat())
                    if (stayAwake) FoldBridgeActivity.start(appContext)
                }
                // Folding that didn't start from flat (e.g. from half-open).
                value < previous -> {
                    if (closeStartAt < 0) { closeStartAt = now; closedAt = if (value <= CLOSED_DEG) now else -1L; reopenedAt = -1L }
                    if (stayAwake) FoldBridgeActivity.start(appContext)
                }
                // Opened back up before closing.
                value >= FLAT_DEG && closeStartAt >= 0 -> {
                    closeStartAt = -1L; closedAt = -1L; reopenedAt = now
                    FoldBridgeActivity.cancel()
                }
            }
        } else {
            when {
                // Starting to open on the cover: blur the whole cover screen.
                previous <= CLOSED_DEG && value > CLOSED_DEG -> coverOpeningAt = now
                value <= CLOSED_DEG -> coverOpeningAt = -1L
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun targetM(now: Long): Float = when {
        waitingForPanel -> if (expanded) START_M_ON_UNFOLD else START_M_ON_COVER

        // Unfold: predicted path from the lit moment to flat, bent by the real flat step.
        expanded && litAt >= 0 -> {
            val sinceLit = (now - litAt).toFloat()
            val predicted = 1f - easeInOutSine((sinceLit / predictedOpenMs).coerceIn(0f, 1f))
            val path = START_M_ON_UNFOLD * predicted
            val m = if (flatAt >= 0) {
                // Flat is real: finish from wherever we are within FINISH_MS.
                val finish = easeOutCubic(((now - flatAt) / FINISH_MS).coerceIn(0f, 1f))
                minOf(path, START_M_ON_UNFOLD * (1f - finish))
            } else maxOf(path, HOLD_M_BEFORE_FLAT) // not flat yet: don't clear fully early
            if (m <= 0.001f && (flatAt >= 0 || sinceLit > predictedOpenMs + STALL_MS)) { litAt = -1L; flatAt = -1L; 0f } else m
        }

        // Fold: blur builds at the learned speed and completes at the closed step.
        expanded && closeStartAt >= 0 -> {
            val since = (now - closeStartAt).toFloat()
            val stalled = closedAt < 0 && now - angleAt > predictedCloseMs + STALL_MS
            when {
                stalled -> { closeStartAt = -1L; 0f } // deliberately half-open (flex mode): clear
                closedAt >= 0 -> 1f
                else -> easeInOutSine((since / predictedCloseMs).coerceIn(0f, 1f)) * HOLD_M_BEFORE_CLOSED
            }
        }

        // Reopened before closing: settle back.
        expanded && reopenedAt >= 0 -> { if (now - reopenedAt > FINISH_MS * 2) reopenedAt = -1L; 0f }

        // Opening from the cover: quick whole-screen blur until the inner display takes over.
        !expanded && coverOpeningAt >= 0 -> {
            if (now - coverOpeningAt > COVER_OPEN_STALL_MS) { coverOpeningAt = -1L; 0f }
            else easeOutCubic(((now - coverOpeningAt) / COVER_OPEN_MS).coerceIn(0f, 1f))
        }

        // Cover after folding: short focus-in.
        !expanded && coverLitAt >= 0 -> {
            val t = ((now - coverLitAt) / COVER_MS).coerceIn(0f, 1f)
            if (t >= 1f) { coverLitAt = -1L; 0f } else START_M_ON_COVER * (1f - easeOutCubic(t))
        }
        else -> 0f
    }

    private fun learnOpen(ms: Float) {
        predictedOpenMs = (predictedOpenMs * .7f + ms.coerceIn(200f, 1400f) * .3f)
        prefs.edit().putFloat("fold_open_ms", predictedOpenMs).apply()
    }

    private fun learnClose(ms: Float) {
        predictedCloseMs = (predictedCloseMs * .7f + ms.coerceIn(250f, 1800f) * .3f)
        prefs.edit().putFloat("fold_close_ms", predictedCloseMs).apply()
    }
}

private fun easeOutCubic(t: Float): Float { val u = 1f - t; return 1f - u * u * u }
private fun easeInOutSine(t: Float): Float = (-(kotlin.math.cos(Math.PI * t) - 1) / 2).toFloat()

@RequiresApi(33)
private class DuoShader {
    private val shader = RuntimeShader(SOURCE)

    fun effect(width: Float, height: Float, m: Float, cover: Boolean): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("m", m)
        // 72px on a 1600px-wide canvas in the recreation ≈ 4.5% of width; the cover uses a light version.
        shader.setFloatUniform("maxRadius", width * .045f)
        shader.setFloatUniform("cover", if (cover) 1f else 0f)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }

    companion object {
        private const val SOURCE = """
            uniform shader content;
            uniform float2 size;
            uniform float m;
            uniform float maxRadius;
            uniform float cover;

            // 24-tap disk (3 rings) keeps large radii smooth.
            half4 blur(float2 p, float r) {
                if (r < 0.75) return content.eval(p);
                float a = r * 0.33; float b = r * 0.66; float c = r;
                float a7 = a * 0.7071; float b7 = b * 0.7071; float c7 = c * 0.7071;
                half4 sum = content.eval(p) * 0.08;
                sum += (content.eval(p + float2(a, 0.0)) + content.eval(p + float2(-a, 0.0)) + content.eval(p + float2(0.0, a)) + content.eval(p + float2(0.0, -a))
                      + content.eval(p + float2(a7, a7)) + content.eval(p + float2(-a7, a7)) + content.eval(p + float2(a7, -a7)) + content.eval(p + float2(-a7, -a7))) * 0.05;
                sum += (content.eval(p + float2(b, 0.0)) + content.eval(p + float2(-b, 0.0)) + content.eval(p + float2(0.0, b)) + content.eval(p + float2(0.0, -b))
                      + content.eval(p + float2(b7, b7)) + content.eval(p + float2(-b7, b7)) + content.eval(p + float2(b7, -b7)) + content.eval(p + float2(-b7, -b7))) * 0.04;
                sum += (content.eval(p + float2(c, 0.0)) + content.eval(p + float2(-c, 0.0)) + content.eval(p + float2(0.0, c)) + content.eval(p + float2(0.0, -c))
                      + content.eval(p + float2(c7, c7)) + content.eval(p + float2(-c7, c7)) + content.eval(p + float2(c7, -c7)) + content.eval(p + float2(-c7, -c7))) * 0.025;
                return sum;
            }

            half4 main(float2 p) {
                float mc = clamp(m, 0.0, 1.0);
                float mm = mc * mc * (3.0 - 2.0 * mc) * max(1.0, m); // smoothstep (as in the recreation), scaled by intensity
                if (cover > 0.5) {
                    // Outer screen, as on iPhone Duo: the hinge is the cover's left edge, so blur and
                    // darkness grow toward the free (right) edge. Same curves as the inner half.
                    float eo = clamp(p.x / size.x, 0.0, 1.0);
                    half4 co = blur(p, maxRadius * mm * pow(eo, 1.35));
                    float dO = clamp((eo - 0.2) / 0.8, 0.0, 1.0);
                    float ko = 1.0 - min(1.0, 2.0 * mm * pow(dO, 1.35));
                    return half4(co.rgb * ko, co.a);
                }
                float hinge = size.x * 0.5;
                if (p.x >= hinge) return content.eval(p);
                float e = clamp((hinge - p.x) / hinge, 0.0, 1.0); // 0 at hinge, 1 at outer edge
                half4 c = blur(p, maxRadius * mm * pow(e, 1.35));
                float d = clamp((e - 0.2) / 0.8, 0.0, 1.0);
                float k = 1.0 - min(1.0, 2.0 * mm * pow(d, 1.35));
                return half4(c.rgb * k, c.a);
            }
        """
    }
}

private const val EXPANDED_WIDTH_DP = 600
/** Inner panel lights around 120–135° on Z Fold: the cover half is still ~50° from flat. */
private const val START_M_ON_UNFOLD = .78f
private const val HOLD_M_BEFORE_FLAT = .08f
private const val HOLD_M_BEFORE_CLOSED = .9f
private const val FINISH_MS = 160f
private const val STALL_MS = 900f
private const val COVER_MS = 450f
/** The cover lights right at closed, where the Duo outer screen is nearly clean: a light settle. */
private const val START_M_ON_COVER = .45f
private const val COVER_OPEN_MS = 220f
private const val COVER_OPEN_STALL_MS = 2_000L
private const val FOLLOW_MS = 28f
private const val FLAT_DEG = 170f
private const val CLOSED_DEG = 10f
private const val LIT_TIMEOUT_MS = 1_200L
private const val IDLE_POLL_MS = 50L
