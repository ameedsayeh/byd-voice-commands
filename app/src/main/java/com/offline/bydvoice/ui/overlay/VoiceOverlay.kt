package com.offline.bydvoice.ui.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Visual state of the floating voice pill. */
enum class OverlayState { LISTENING, THINKING, SUCCESS, ERROR }

/**
 * Floating "voice pill" drawn over any app (needs Display-over-other-apps):
 *   [ glowing orb ]  STATUS                                   ▂▄▇▅▃▆█▅▂
 *                    "live transcript of what you're saying"
 * The orb's halo and ripples and the waveform follow the live mic level. Tapping it cancels.
 * Must be driven from the main thread.
 */
class VoiceOverlay(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private lateinit var orb: OrbView
    private lateinit var wave: WaveView
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var reply: TextView
    private var onTap: (() -> Unit)? = null
    private val hideRunnable = Runnable { hideNow() }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (root == null) return
            orb.tick(); wave.tick()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    val isShowing: Boolean get() = root != null

    fun setOnTap(block: () -> Unit) { onTap = block }

    fun show() {
        main.removeCallbacks(hideRunnable)
        if (root == null) attach()
        setState(OverlayState.LISTENING, "LISTENING", "Say a command…", null)
    }

    fun level(value: Float) { if (root != null) { orb.target = value; wave.push(value) } }

    fun transcript(text: String) {
        if (root == null || text.isBlank()) return
        transcript.text = "“$text”"
        transcript.alpha = 1f
    }

    fun thinking(heard: String) {
        setState(OverlayState.THINKING, "WORKING ON IT", if (heard.isBlank()) "…" else "“$heard”", null)
    }

    fun result(ok: Boolean, heard: String, message: String) {
        setState(if (ok) OverlayState.SUCCESS else OverlayState.ERROR, if (ok) "DONE" else "SORRY",
            if (heard.isBlank()) message else "“$heard”", if (heard.isBlank()) null else message)
    }

    fun warn(message: String) { if (root != null) { reply.text = message; reply.visibility = View.VISIBLE } }

    fun hide(afterMs: Long = 0) {
        main.removeCallbacks(hideRunnable)
        if (afterMs <= 0) hideNow() else main.postDelayed(hideRunnable, afterMs)
    }

    // ---------------------------------------------------------------------------------------------

    private fun setState(state: OverlayState, label: String, main: String, sub: String?) {
        if (root == null) return
        orb.state = state; wave.state = state
        status.text = label
        status.setTextColor(color(state))
        transcript.text = main
        reply.text = sub ?: ""
        reply.visibility = if (sub.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach() {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(Color.parseColor("#FF101318"))
                setStroke(dp(1), Color.parseColor("#22FFFFFF"))
            }
            elevation = dp(14).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, dp(30).toFloat())
            }
            setOnClickListener { onTap?.invoke() }
        }
        orb = OrbView(context)
        pill.addView(orb, LinearLayout.LayoutParams(dp(50), dp(50)))

        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        status = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f); letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        transcript = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f); setTextColor(Color.parseColor("#F3F5F7"))
            maxLines = 2; ellipsize = TextUtils.TruncateAt.START
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        reply = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); setTextColor(Color.parseColor("#AEB6C2"))
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(status); texts.addView(transcript); texts.addView(reply)
        pill.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        wave = WaveView(context)
        pill.addView(wave, LinearLayout.LayoutParams(dp(108), dp(40)))

        val frameRoot = FrameLayout(context).apply { setPadding(dp(16), dp(8), dp(16), dp(20)) }
        val width = min(context.resources.displayMetrics.widthPixels - dp(48), dp(520))
        frameRoot.addView(pill, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))

        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Sit just below the head unit's top status bar instead of under it.
            y = statusBarHeight() + dp(6)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = dp(24)
            }
        }
        if (runCatching { wm.addView(frameRoot, lp) }.isFailure) return
        root = frameRoot
        pill.alpha = 0f; pill.translationY = -dp(28).toFloat(); pill.scaleX = 0.96f; pill.scaleY = 0.96f
        pill.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun hideNow() {
        val r = root ?: return
        root = null
        val pill = r.getChildAt(0)
        pill.animate().alpha(0f).translationY(-dp(20).toFloat()).setDuration(200).withEndAction {
            runCatching { wm.removeView(r) }
        }.start()
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(28)
    }

    companion object {
        val ACCENT = Color.parseColor("#E5484D")
        val ACCENT_DEEP = Color.parseColor("#B4122B")
        val SUCCESS = Color.parseColor("#30A46C")
        val WARN = Color.parseColor("#F5A524")
        val THINK = Color.parseColor("#7C8CFF")
        fun color(s: OverlayState) = when (s) {
            OverlayState.LISTENING -> ACCENT; OverlayState.THINKING -> THINK
            OverlayState.SUCCESS -> SUCCESS; OverlayState.ERROR -> WARN
        }
    }
}

/** Glowing orb: halo grows with your voice, ripples spawn on loud peaks, icon reflects the state. */
private class OrbView(context: Context) : View(context) {
    var target = 0f
    var state = OverlayState.LISTENING
        set(v) { field = v; stateChangedAt = SystemClock.uptimeMillis() }
    private var level = 0f
    private var stateChangedAt = 0L
    private var lastRipple = 0L
    private val ripples = ArrayList<Pair<Long, Float>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val rect = RectF()

    fun tick() {
        level += (target - level) * 0.35f
        val now = SystemClock.uptimeMillis()
        if (state == OverlayState.LISTENING && level > 0.42f && now - lastRipple > 220) {
            ripples += now to level; lastRipple = now
        }
        ripples.removeAll { now - it.first > 900 }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r0 = min(width, height) * 0.30f
        val col = VoiceOverlay.color(state)
        val now = SystemClock.uptimeMillis()

        // ripples
        paint.style = Paint.Style.STROKE
        for ((born, strength) in ripples) {
            val t = (now - born) / 900f
            paint.strokeWidth = r0 * 0.08f * (1 - t)
            paint.color = col; paint.alpha = (150 * (1 - t) * strength).toInt().coerceIn(0, 255)
            c.drawCircle(cx, cy, r0 * (1.05f + 0.75f * t), paint)
        }
        // halo (reset alpha: the ripple loop above leaves the paint's alpha near zero)
        paint.alpha = 255
        val breathe = if (state == OverlayState.THINKING) 0.15f * (0.5f + 0.5f * sin(now / 220.0).toFloat()) else 0f
        val haloR = r0 * (1.25f + 0.55f * level + breathe)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, haloR, intArrayOf(withAlpha(col, 120), withAlpha(col, 0)), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, haloR, paint)
        // core
        val coreR = r0 * (1f + 0.08f * level)
        paint.shader = RadialGradient(cx - r0 * 0.3f, cy - r0 * 0.4f, coreR * 1.6f,
            intArrayOf(lighten(col), col, darken(col)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, coreR, paint)
        paint.shader = null

        // thinking: orbiting arc
        if (state == OverlayState.THINKING) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = r0 * 0.1f; paint.color = Color.WHITE; paint.alpha = 220
            paint.strokeCap = Paint.Cap.ROUND
            val a = (now % 1000) / 1000f * 360f
            rect.set(cx - coreR * 1.25f, cy - coreR * 1.25f, cx + coreR * 1.25f, cy + coreR * 1.25f)
            c.drawArc(rect, a, 80f, false, paint)
        }

        // icon
        icon.strokeWidth = r0 * 0.13f
        when (state) {
            OverlayState.LISTENING, OverlayState.THINKING -> drawMic(c, cx, cy, r0 * 0.55f)
            OverlayState.SUCCESS -> {
                val p = ((now - stateChangedAt) / 260f).coerceIn(0f, 1f)
                path.reset()
                path.moveTo(cx - r0 * 0.42f, cy + r0 * 0.02f)
                path.lineTo(cx - r0 * 0.12f, cy + r0 * 0.32f)
                path.lineTo(cx + r0 * 0.45f, cy - r0 * 0.3f)
                val m = android.graphics.PathMeasure(path, false)
                val seg = Path(); m.getSegment(0f, m.length * p, seg, true)
                c.drawPath(seg, icon)
            }
            OverlayState.ERROR -> {
                c.drawLine(cx, cy - r0 * 0.4f, cx, cy + r0 * 0.1f, icon)
                c.drawPoint(cx, cy + r0 * 0.38f, icon)
            }
        }
    }

    private fun drawMic(c: Canvas, cx: Float, cy: Float, s: Float) {
        rect.set(cx - s * 0.32f, cy - s * 0.85f, cx + s * 0.32f, cy + s * 0.25f)
        icon.style = Paint.Style.FILL
        c.drawRoundRect(rect, s * 0.32f, s * 0.32f, icon)
        icon.style = Paint.Style.STROKE
        rect.set(cx - s * 0.62f, cy - s * 0.45f, cx + s * 0.62f, cy + s * 0.55f)
        c.drawArc(rect, 0f, 180f, false, icon)
        c.drawLine(cx, cy + s * 0.55f, cx, cy + s * 0.85f, icon)
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
    private fun lighten(c: Int) = Color.rgb(min(255, Color.red(c) + 60), min(255, Color.green(c) + 60), min(255, Color.blue(c) + 60))
    private fun darken(c: Int) = Color.rgb((Color.red(c) * 0.62).toInt(), (Color.green(c) * 0.62).toInt(), (Color.blue(c) * 0.62).toInt())
}

/** Scrolling waveform of recent mic levels; becomes a travelling wave while thinking. */
private class WaveView(context: Context) : View(context) {
    var state = OverlayState.LISTENING
    private val bars = 26
    private val history = FloatArray(bars)
    private val shown = FloatArray(bars)
    private var pending = 0f
    private var lastShift = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    fun push(level: Float) { pending = max(pending, level) }

    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastShift >= 55) {
            System.arraycopy(history, 1, history, 0, bars - 1)
            history[bars - 1] = if (state == OverlayState.LISTENING) pending else 0f
            pending *= 0.4f
            lastShift = now
        }
        for (i in 0 until bars) {
            val target = when (state) {
                OverlayState.LISTENING -> history[i].toDouble().pow(0.75).toFloat()
                OverlayState.THINKING -> 0.18f + 0.16f * sin(now / 140.0 - i * 0.55).toFloat()
                else -> 0.05f
            }
            shown[i] += (target - shown[i]) * 0.28f
        }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val gap = width / bars.toFloat()
        val w = gap * 0.52f
        paint.strokeWidth = w
        val col = VoiceOverlay.color(state)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.argb(90, Color.red(col), Color.green(col), Color.blue(col)), col, Shader.TileMode.CLAMP)
        val mid = height / 2f
        for (i in 0 until bars) {
            // Gentle edge taper so the shape reads as a voice print, not a bar chart.
            val taper = (sin(PI * (i + 0.5) / bars)).toFloat().let { 0.35f + 0.65f * it }
            val h = max(w, shown[i] * taper * height * 0.92f)
            val x = gap * i + gap / 2
            c.drawLine(x, mid - h / 2 + w / 2, x, mid + h / 2 - w / 2, paint)
        }
    }
}
