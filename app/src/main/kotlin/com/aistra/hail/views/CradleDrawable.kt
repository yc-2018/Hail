package com.aistra.hail.views

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Bottom bar background with a concave notch (cradle) carved out around the freeze button.
 *
 * Drawn by hand rather than with Material's BottomAppBarTopEdgeTreatment so the geometry is fully
 * under our control: the notch is simply the bar rectangle minus a circle.
 *
 * @param color bar fill color
 * @param notchCenterY circle center measured from the drawable's top edge, may be negative
 * @param notchRadius circle radius, i.e. button radius plus the desired gap
 */
class CradleDrawable(
    private val color: Int,
    private val notchCenterY: Float,
    private val notchRadius: Float
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = this@CradleDrawable.color
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        path.reset()
        path.addRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), Path.Direction.CW)
        val cx = b.exactCenterX()
        val cy = b.top + notchCenterY
        val circle = Path().apply {
            addOval(
                RectF(cx - notchRadius, cy - notchRadius, cx + notchRadius, cy + notchRadius),
                Path.Direction.CCW
            )
        }
        path.op(circle, Path.Op.DIFFERENCE)
        canvas.drawPath(path, paint)
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
