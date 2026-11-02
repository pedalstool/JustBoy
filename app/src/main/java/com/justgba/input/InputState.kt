package com.justgba.input

object InputState {
    @Volatile private var physicalMask = 0
    fun getPlayer1Mask(): Int = physicalMask
    fun setPhysicalMask(mask: Int) { physicalMask = mask }
    fun clear() { physicalMask = 0 }
}
