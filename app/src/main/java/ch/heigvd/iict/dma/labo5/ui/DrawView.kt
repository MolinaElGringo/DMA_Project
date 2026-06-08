package ch.heigvd.iict.dma.labo5.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import ch.heigvd.iict.dma.labo5.model.DrawAction
import ch.heigvd.iict.dma.labo5.model.DrawEvent

/**
 * Vue de dessin collaborative.
 *
 *  - Les traits dessinés localement sont rendus immédiatement ET signalés via [onLocalDraw]
 *    (le Fragment les diffuse aux pairs).
 *  - Les traits distants arrivent via [applyRemoteEvent]. Chaque pair a son propre trait
 *    "en cours" (clé = sender) pour gérer plusieurs personnes qui dessinent en même temps.
 *
 * Toutes les méthodes sont appelées sur le thread principal (touch + collecte de flow),
 * donc pas de synchronisation nécessaire.
 */
class DrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Traits terminés (locaux + distants)
    private val committed = mutableListOf<Pair<Path, Paint>>()

    // Trait local en cours
    private var localPath: Path? = null
    private var localPaint = createPaint(0xFF000000.toInt())

    // Traits distants en cours, un par auteur
    private val remoteInProgress = HashMap<String, Pair<Path, Paint>>()

    /** Couleur courante du pinceau local. Modifiable depuis l'UI. */
    var currentColor: Int = 0xFF000000.toInt()
    private val strokeWidthPx = 8f

    /** Callback : (action, xNormalisé, yNormalisé, couleur, épaisseur). */
    var onLocalDraw: ((DrawAction, Float, Float, Int, Float) -> Unit)? = null

    private fun createPaint(color: Int, widthPx: Float = strokeWidthPx) = Paint().apply {
        this.color = color
        this.strokeWidth = widthPx
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        for ((path, paint) in committed) canvas.drawPath(path, paint)
        for ((path, paint) in remoteInProgress.values) canvas.drawPath(path, paint)
        localPath?.let { canvas.drawPath(it, localPaint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                localPaint = createPaint(currentColor)
                localPath = Path().apply { moveTo(x, y) }
                onLocalDraw?.invoke(DrawAction.BEGIN, x / w, y / h, currentColor, strokeWidthPx)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                localPath?.lineTo(x, y)
                onLocalDraw?.invoke(DrawAction.POINT, x / w, y / h, currentColor, strokeWidthPx)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                localPath?.let { committed.add(it to localPaint) }
                onLocalDraw?.invoke(DrawAction.END, x / w, y / h, currentColor, strokeWidthPx)
                localPath = null
                invalidate()
            }
        }
        return true
    }

    /** Applique un évènement reçu d'un pair. */
    fun applyRemoteEvent(e: DrawEvent) {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val px = e.x * w
        val py = e.y * h

        when (e.action) {
            DrawAction.BEGIN -> {
                remoteInProgress[e.sender] = Path().apply { moveTo(px, py) } to createPaint(e.color, e.width)
            }
            DrawAction.POINT -> {
                remoteInProgress[e.sender]?.first?.lineTo(px, py)
            }
            DrawAction.END -> {
                remoteInProgress.remove(e.sender)?.let { committed.add(it) }
            }
            DrawAction.CLEAR -> {
                committed.clear()
                remoteInProgress.clear()
                localPath = null
            }
        }
        invalidate()
    }

    fun getBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    /** Efface le tableau localement (le Fragment se charge de diffuser le CLEAR aux pairs). */
    fun clear() {
        committed.clear()
        remoteInProgress.clear()
        localPath = null
        invalidate()
    }
}
