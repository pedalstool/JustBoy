package com.justgba.settings

import kotlin.math.roundToInt

data class Picture(
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val sharpness: Float = 0f,
    val pixelGrid: Float = 0f,
) {
    fun sanitized(): Picture = Picture(
        brightness = brightness.coerceIn(BRIGHTNESS_RANGE),
        contrast = contrast.coerceIn(CONTRAST_RANGE),
        saturation = saturation.coerceIn(SATURATION_RANGE),
        temperature = temperature.coerceIn(TEMPERATURE_RANGE),
        sharpness = sharpness.coerceIn(SHARPNESS_RANGE),
        pixelGrid = pixelGrid.coerceIn(GRID_RANGE),
    )

    companion object {
        val BRIGHTNESS_RANGE = 0.5f..1.5f
        val CONTRAST_RANGE = 0.5f..1.5f
        val SATURATION_RANGE = 0f..2f
        val TEMPERATURE_RANGE = -1f..1f
        val SHARPNESS_RANGE = 0f..1f
        val GRID_RANGE = 0f..1f
        const val STEP = 0.05f

        fun step(value: Float, direction: Int, range: ClosedFloatingPointRange<Float>): Float {
            val stepped = ((value / STEP).roundToInt() + direction) * STEP
            return stepped.coerceIn(range)
        }
    }
}
