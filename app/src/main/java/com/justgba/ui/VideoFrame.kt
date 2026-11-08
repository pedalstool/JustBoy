package com.justgba.ui

import kotlin.math.min
import kotlin.math.roundToInt

object VideoFrame {
    fun size(viewW: Int, viewH: Int, srcW: Int, srcH: Int, screenSize: Int): Pair<Int, Int> {
        if (viewW <= 0 || viewH <= 0 || srcW <= 0 || srcH <= 0) return 1 to 1
        if (screenSize != 0) {
            val maxN = min(viewW / srcW, viewH / srcH)
            val requested = if (screenSize == 1) maxN else min(screenSize, maxN)
            val n = requested.coerceAtLeast(1)
            val w = srcW * n
            val h = srcH * n
            if (w <= viewW && h <= viewH) return w to h
        }
        val viewAspect = viewW.toFloat() / viewH
        val srcAspect = srcW.toFloat() / srcH
        return if (viewAspect > srcAspect) {
            val h = viewH
            val w = (h * srcAspect).roundToInt().coerceIn(1, viewW)
            w to h
        } else {
            val w = viewW
            val h = (w / srcAspect).roundToInt().coerceIn(1, viewH)
            w to h
        }
    }
}
