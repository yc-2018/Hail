package com.aistra.hail.extensions

import android.view.View
import android.view.animation.DecelerateInterpolator

val View.isRtl get() = layoutDirection == View.LAYOUT_DIRECTION_RTL

/** Spins the view a full turn, used as feedback on the freeze button. */
fun View.spin(duration: Long = 500L) {
    animate().cancel()
    rotation = 0f
    animate().rotationBy(360f).setDuration(duration).setInterpolator(DecelerateInterpolator()).start()
}
