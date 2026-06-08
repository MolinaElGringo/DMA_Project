package ch.heigvd.iict.dma.labo5.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Petite vue qui dessine une forme d'onde audio en barres.
 *  - Mode "live" (enregistrement) : on pousse les amplitudes une à une via [pushAmplitude],
 *    les barres défilent de droite à gauche au fil du temps.
 *  - Mode "lecture" : on fixe toute la liste via [setAmplitudes] puis on anime [setProgress]
 *    (0f..1f) pour colorer la partie déjà jouée.
 */
class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val d = resources.displayMetrics.density
    private val barWidth = 3f * d
    private val barGap = 2f * d
    private val minBarHeight = 3f * d
    private val radius = 2f * d

    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6750A4.toInt() }
    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9BEDD.toInt() }

    private val amplitudes = ArrayList<Float>() // normalisées 0f..1f
    private var live = false
    private var progress = 0f                    // 0f..1f (lecture)

    fun setColors(played: Int, unplayed: Int) {
        playedPaint.color = played
        unplayedPaint.color = unplayed
        invalidate()
    }

    /** Enregistrement : amplitude brute de MediaRecorder.getMaxAmplitude() (0..32767). */
    fun pushAmplitude(raw: Int) {
        live = true
        amplitudes.add(min(1f, raw / 16000f))
        val maxBars = maxLiveBars()
        while (amplitudes.size > maxBars) amplitudes.removeAt(0)
        invalidate()
    }

    /** Lecture : amplitudes pré-calculées (chacune 0..100). */
    fun setAmplitudes(values: List<Int>) {
        live = false
        amplitudes.clear()
        values.forEach { amplitudes.add(min(1f, max(0f, it / 100f))) }
        progress = 0f
        invalidate()
    }

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }

    fun clear() {
        amplitudes.clear()
        live = false
        progress = 0f
        invalidate()
    }

    private fun maxLiveBars(): Int {
        val w = if (width > 0) width else 600
        return max(1, (w / (barWidth + barGap)).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (amplitudes.isEmpty() || width == 0) return
        val h = height.toFloat()
        val cy = h / 2f

        if (live) {
            // barres de largeur fixe, alignées à droite (les plus récentes à droite)
            val step = barWidth + barGap
            val total = amplitudes.size * step
            val startX = max(0f, width - total)
            for (i in amplitudes.indices) {
                val barH = max(minBarHeight, amplitudes[i] * h)
                val left = startX + i * step
                canvas.drawRoundRect(
                    RectF(left, cy - barH / 2f, left + barWidth, cy + barH / 2f),
                    radius, radius, playedPaint
                )
            }
        } else {
            // lecture : on étale les barres sur toute la largeur
            val count = amplitudes.size
            val step = width.toFloat() / count
            val bw = max(2f * d, step * 0.6f)
            val playedBars = (count * progress).toInt()
            for (i in 0 until count) {
                val barH = max(minBarHeight, amplitudes[i] * h)
                val cx = i * step + step / 2f
                val left = cx - bw / 2f
                val paint = if (i <= playedBars) playedPaint else unplayedPaint
                canvas.drawRoundRect(
                    RectF(left, cy - barH / 2f, left + bw, cy + barH / 2f),
                    radius, radius, paint
                )
            }
        }
    }
}