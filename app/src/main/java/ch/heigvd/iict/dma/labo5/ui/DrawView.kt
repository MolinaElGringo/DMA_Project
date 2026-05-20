package ch.heigvd.iict.dma.labo5.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paths = mutableListOf<Pair<Path, Paint>>()
    private var currentPath = Path()
    private var currentPaint = createPaint()

    private fun createPaint(
        color: Int = Color.BLACK,
        strokeWidth: Float = 8f
    ) = Paint().apply {
        this.color = color
        this.strokeWidth = strokeWidth
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Fond blanc
        canvas.drawColor(Color.WHITE)
        // Dessiner tous les paths précédents
        for ((path, paint) in paths) {
            canvas.drawPath(path, paint)
        }
        // Dessiner le path en cours
        canvas.drawPath(currentPath, currentPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path()
                currentPaint = createPaint()
                currentPath.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                paths.add(Pair(currentPath, currentPaint))
                invalidate()
            }
        }
        return true
    }

    fun getBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    fun clear() {
        paths.clear()
        currentPath = Path()
        invalidate()
    }
}