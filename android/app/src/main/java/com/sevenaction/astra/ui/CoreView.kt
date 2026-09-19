package com.sevenaction.astra.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.sevenaction.astra.R
import kotlin.math.min

class CoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING, RECORDING }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var phase = 0f
    private var state = State.IDLE

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
        start()
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
        val base = min(width, height) * 0.34f

        val accent = ContextCompat.getColor(context, R.color.astra_accent)
        val danger = ContextCompat.getColor(context, R.color.astra_red)
        val muted = ContextCompat.getColor(context, R.color.astra_muted)

        paint.color = if (state == State.RECORDING) danger else accent
        fill.color = paint.color

        val pulse = when (state) {
            State.IDLE -> 0.02f
            State.LISTENING -> 0.08f
            State.THINKING -> 0.05f
            State.SPEAKING -> 0.11f
            State.RECORDING -> 0.07f
        }
        val wobble = 1f + pulse * kotlin.math.sin(phase * Math.PI * 2).toFloat()

        canvas.drawCircle(cx, cy, base * 0.42f * wobble, fill)
        paint.strokeWidth = 4f
        canvas.drawCircle(cx, cy, base * 0.72f, paint)

        paint.strokeWidth = 3f
        canvas.save()
        canvas.rotate(phase * 360f, cx, cy)
        val sweep = when (state) {
            State.THINKING -> 220f
            State.LISTENING -> 140f
            State.SPEAKING -> 280f
            else -> 100f
        }
        canvas.drawArc(cx - base, cy - base, cx + base, cy + base, -80f, sweep, false, paint)
        canvas.restore()

        paint.color = muted
        paint.strokeWidth = 2f
        canvas.save()
        canvas.rotate(-phase * 220f, cx, cy)
        canvas.drawArc(cx - base * 1.22f, cy - base * 1.22f, cx + base * 1.22f, cy + base * 1.22f, 20f, 210f, false, paint)
        canvas.restore()
    }
}
