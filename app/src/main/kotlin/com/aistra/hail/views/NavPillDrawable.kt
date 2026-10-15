package com.aistra.hail.views

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Highlight for a selected navigation item: a stadium-shaped fill of a fixed size, centered in the
 * drawable's bounds.
 *
 * The size is fixed rather than filling the bounds so the highlight keeps its proportions no matter
 * how wide the item ends up on a given screen. Drawing nothing else keeps it usable in a state list,
 * where the unselected items simply have no highlight.
 */
class NavPillDrawable(
    private val color: Int,
    private val width: Float,
    private val height: Float
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = this@NavPillDrawable.color
    }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = min(width, b.width().toFloat())
        val h = min(height, b.height().toFloat())
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = if (Color.alpha(color) == 255) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
}
