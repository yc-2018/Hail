package com.aistra.hail.views

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * Bottom bar background whose top edge swells upwards in the middle, cradling the freeze button.
 *
 * The edge is one smooth curve rather than an arc plus fillets: flat at both ends, rising through
 * a pair of mirrored bezier segments to a rounded crest behind the button. That matches the dock
 * look of launcher bars, where the bulge is wide and gentle instead of a tight circular notch.
 *
 * @param color bar fill color
 * @param crestHeight how far the crest rises above the flat edge, in pixels
 * @param crestHalfWidth half the width of the bulge, in pixels
 * @param topInset distance from the drawable's top down to the flat part of the edge
 */
class CradleDrawable(
    private val color: Int,
    private val crestHeight: Float,
    private val crestHalfWidth: Float,
    private val topInset: Float
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = this@CradleDrawable.color
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val left = b.left.toFloat()
        val right = b.right.toFloat()
        val bottom = b.bottom.toFloat()
        val flat = b.top + topInset
        val crest = flat - crestHeight
        val cx = b.exactCenterX()
        val startX = cx - crestHalfWidth
        val endX = cx + crestHalfWidth
        path.reset()
        path.moveTo(left, bottom)
        path.lineTo(left, flat)
        path.lineTo(startX, flat)
        // One smooth hump: the control points sit on the flat edge at the feet and level with the
        // crest in the middle, so the curve leaves and rejoins the edge tangentially.
        path.cubicTo(
            startX + crestHalfWidth * RAMP, flat,
            cx - crestHalfWidth * RAMP, crest,
            cx, crest
        )
        path.cubicTo(
            cx + crestHalfWidth * RAMP, crest,
            endX - crestHalfWidth * RAMP, flat,
            endX, flat
        )
        path.lineTo(right, flat)
        path.lineTo(right, bottom)
        path.close()
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

    private companion object {
        /** How much of the half width is spent on each ramp, as a fraction. */
        const val RAMP = 0.62f
    }
}
