package com.sevenaction.astra.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.sevenaction.astra.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING, RECORDING }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var phase = 0f
    private var state = State.IDLE

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2100L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
        start()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setState(newState: State) {
        state = newState
        contentDescription = when (newState) {
            State.IDLE -> "Assistant prêt"
            State.LISTENING -> "Assistant en écoute"
            State.THINKING -> "Assistant en réflexion"
            State.SPEAKING -> "Assistant parle"
            State.RECORDING -> "Réunion en cours d'enregistrement"
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height).toFloat()
        val accent = ContextCompat.getColor(context, R.color.astra_accent)
        val accentSoft = ContextCompat.getColor(context, R.color.astra_accent_soft)
        val muted = ContextCompat.getColor(context, R.color.astra_muted)

        val pulseAmount = when (state) {
            State.IDLE -> 0.018f
            State.LISTENING -> 0.055f
            State.THINKING -> 0.035f
            State.SPEAKING -> 0.075f
            State.RECORDING -> 0.05f
        }
        val pulse = 1f + pulseAmount * sin((phase * PI * 2).toFloat())
        val crystal = size * 0.29f * pulse

        drawHud(canvas, cx, cy, size, accent, muted)
        drawCrystal(canvas, cx, cy, crystal, accent, accentSoft)
        drawCoreLight(canvas, cx, cy, crystal, accent, accentSoft)
    }

    private fun drawHud(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        accent: Int,
        muted: Int
    ) {
        val r1 = size * 0.36f
        val r2 = size * 0.43f

        stroke.shader = null
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = size * 0.008f
        stroke.color = Color.argb(180, Color.red(accent), Color.green(accent), Color.blue(accent))
        stroke.setShadowLayer(size * 0.035f, 0f, 0f, accent)

        canvas.save()
        canvas.rotate(phase * 80f, cx, cy)
        canvas.drawArc(cx - r1, cy - r1, cx + r1, cy + r1, -55f, 76f, false, stroke)
        canvas.drawArc(cx - r1, cy - r1, cx + r1, cy + r1, 125f, 76f, false, stroke)
        canvas.restore()

        stroke.clearShadowLayer()
        stroke.strokeWidth = size * 0.004f
        stroke.color = Color.argb(95, Color.red(muted), Color.green(muted), Color.blue(muted))
        canvas.save()
        canvas.rotate(-phase * 42f, cx, cy)
        canvas.drawArc(cx - r2, cy - r2, cx + r2, cy + r2, 8f, 118f, false, stroke)
        canvas.drawArc(cx - r2, cy - r2, cx + r2, cy + r2, 188f, 118f, false, stroke)
        canvas.restore()

        val tickR1 = size * 0.405f
        val tickR2 = size * 0.445f
        stroke.strokeWidth = size * 0.006f
        stroke.color = Color.argb(155, Color.red(accent), Color.green(accent), Color.blue(accent))
        for (i in 0 until 12) {
            val a = ((i * 30f - 90f) * PI / 180f).toFloat()
            val inner = if (i % 3 == 0) tickR1 * 0.96f else tickR1
            canvas.drawLine(
                cx + cos(a) * inner,
                cy + sin(a) * inner,
                cx + cos(a) * tickR2,
                cy + sin(a) * tickR2,
                stroke
            )
        }
    }

    private fun drawCrystal(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        accent: Int,
        accentSoft: Int
    ) {
        val topY = cy - r * 1.15f
        val bottomY = cy + r * 1.18f
        val leftX = cx - r * 0.82f
        val rightX = cx + r * 0.82f
        val shoulderY = cy + r * 0.08f

        val outer = Path().apply {
            moveTo(cx, topY)
            lineTo(rightX, shoulderY)
            lineTo(cx, bottomY)
            lineTo(leftX, shoulderY)
            close()
        }

        fill.shader = LinearGradient(
            cx, topY, cx, bottomY,
            intArrayOf(Color.rgb(35, 35, 33), Color.rgb(12, 12, 11), Color.rgb(30, 21, 9)),
            null,
            Shader.TileMode.CLAMP
        )
        fill.setShadowLayer(r * 0.28f, 0f, 0f, Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent)))
        canvas.drawPath(outer, fill)
        fill.clearShadowLayer()

        val leftFacet = Path().apply {
            moveTo(cx, topY)
            lineTo(cx, cy)
            lineTo(leftX, shoulderY)
            close()
        }
        fill.shader = LinearGradient(leftX, shoulderY, cx, topY, Color.rgb(15,15,14), Color.rgb(64,52,34), Shader.TileMode.CLAMP)
        canvas.drawPath(leftFacet, fill)

        val rightFacet = Path().apply {
            moveTo(cx, topY)
            lineTo(rightX, shoulderY)
            lineTo(cx, cy)
            close()
        }
        fill.shader = LinearGradient(cx, topY, rightX, shoulderY, Color.rgb(78,70,57), Color.rgb(18,18,17), Shader.TileMode.CLAMP)
        canvas.drawPath(rightFacet, fill)

        val bottomFacet = Path().apply {
            moveTo(leftX, shoulderY)
            lineTo(cx, cy)
            lineTo(rightX, shoulderY)
            lineTo(cx, bottomY)
            close()
        }
        fill.shader = LinearGradient(cx, cy, cx, bottomY, Color.rgb(66,38,8), Color.rgb(14,12,9), Shader.TileMode.CLAMP)
        canvas.drawPath(bottomFacet, fill)

        stroke.shader = null
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = r * 0.035f
        stroke.color = Color.argb(210, Color.red(accentSoft), Color.green(accentSoft), Color.blue(accentSoft))
        canvas.drawPath(outer, stroke)

        stroke.strokeWidth = r * 0.018f
        stroke.color = Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawLine(cx, topY, cx, bottomY, stroke)
        canvas.drawLine(leftX, shoulderY, rightX, shoulderY, stroke)
        canvas.drawLine(cx, topY, leftX, shoulderY, stroke)
        canvas.drawLine(cx, topY, rightX, shoulderY, stroke)
        canvas.drawLine(leftX, shoulderY, cx, bottomY, stroke)
        canvas.drawLine(rightX, shoulderY, cx, bottomY, stroke)
    }

    private fun drawCoreLight(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        accent: Int,
        accentSoft: Int
    ) {
        val inner = r * 0.43f
        fill.shader = RadialGradient(
            cx, cy,
            inner * 1.8f,
            intArrayOf(
                Color.argb(255, Color.red(accentSoft), Color.green(accentSoft), Color.blue(accentSoft)),
                Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.34f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, inner * 1.8f, fill)

        val tri = Path().apply {
            moveTo(cx, cy - inner)
            lineTo(cx + inner * 0.92f, cy + inner * 0.56f)
            lineTo(cx - inner * 0.92f, cy + inner * 0.56f)
            close()
        }
        fill.shader = LinearGradient(
            cx, cy - inner, cx, cy + inner,
            accentSoft, accent, Shader.TileMode.CLAMP
        )
        fill.setShadowLayer(inner * 0.45f, 0f, 0f, accent)
        canvas.drawPath(tri, fill)
        fill.clearShadowLayer()

        stroke.shader = null
        stroke.strokeWidth = inner * 0.11f
        stroke.color = Color.WHITE
        stroke.alpha = 170
        canvas.drawLine(cx, cy - inner * 0.72f, cx, cy + inner * 0.36f, stroke)
        stroke.alpha = 255
    }
}
