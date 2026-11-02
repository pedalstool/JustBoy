package com.justgba.emulator

import com.justgba.audio.AudioEngine
import com.justgba.input.InputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.LockSupport

class EmulatorThread(
    private val audioEngine: AudioEngine,
) {
    companion object {
        private const val TARGET_FPS = 60.0
        private const val FRAME_TIME_NS = (1_000_000_000.0 / TARGET_FPS).toLong()
    }

    private val pauseLock = Object()
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var fastForward = false
    @Volatile var paused = false
    @Volatile var ffSpeedMultiplier = 0f
    @Volatile var ffAudioMode = 1

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "emulator").apply { start() }
    }

    fun stop() {
        running = false
        resume()
        thread?.join()
        thread = null
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
        synchronized(pauseLock) {
            pauseLock.notifyAll()
        }
    }

    private fun runLoop() {
        var lastFrameTime = System.nanoTime()
        var lastRenderTime = System.nanoTime()
        var renderAccumulator = 0.0
        var framesEmulated = 0
        var lastFpsTime = System.currentTimeMillis()
        var lastMask = Int.MIN_VALUE
        var lastSkipRender = false
        var lastFf = false
        var lastFfSpeed = Float.NaN
        var lastFfAudio = Int.MIN_VALUE

        while (running) {
            synchronized(pauseLock) {
                while (paused && running) {
                    pauseLock.wait()
                    lastFrameTime = System.nanoTime()
                    lastRenderTime = System.nanoTime()
                }
            }

            if (!running) break

            val ff = fastForward
            val ffSpeed = ffSpeedMultiplier
            val ffAudio = ffAudioMode
            if (ff != lastFf || ffSpeed != lastFfSpeed || ffAudio != lastFfAudio) {
                audioEngine.setFfAudioMode(ffAudio)
                audioEngine.setFastForward(ff, ffSpeed)
                lastFf = ff
                lastFfSpeed = ffSpeed
                lastFfAudio = ffAudio
            }

            val mask = InputState.getPlayer1Mask()
            if (mask != lastMask) {
                NativeBridge.nativeSetInput(mask)
                lastMask = mask
            }
            audioEngine.tick()

            if (!ff) {
                lastSkipRender = setSkipRender(false, lastSkipRender)
                NativeBridge.nativeRunFrame()
                framesEmulated++
                pace(FRAME_TIME_NS, lastFrameTime)
                lastFrameTime += FRAME_TIME_NS
                if (System.nanoTime() - lastFrameTime > FRAME_TIME_NS * 5) {
                    lastFrameTime = System.nanoTime()
                }
            } else if (ffSpeed > 0) {
                renderAccumulator += (1.0 / ffSpeed)
                val shouldRender = if (renderAccumulator >= 1.0) {
                    renderAccumulator -= 1.0
                    true
                } else {
                    false
                }
                lastSkipRender = setSkipRender(!shouldRender, lastSkipRender)
                NativeBridge.nativeRunFrame()
                framesEmulated++
                val targetFrameTimeNs = (FRAME_TIME_NS / ffSpeed).toLong()
                pace(targetFrameTimeNs, lastFrameTime)
                lastFrameTime += targetFrameTimeNs
                if (System.nanoTime() - lastFrameTime > targetFrameTimeNs * 5) {
                    lastFrameTime = System.nanoTime()
                }
            } else {
                val now = System.nanoTime()
                val shouldRender = (now - lastRenderTime) >= FRAME_TIME_NS
                lastSkipRender = setSkipRender(!shouldRender, lastSkipRender)
                NativeBridge.nativeRunFrame()
                framesEmulated++
                if (shouldRender) lastRenderTime = System.nanoTime()
                lastFrameTime = System.nanoTime()
            }

            val currentMillis = System.currentTimeMillis()
            if (currentMillis - lastFpsTime >= 1000) {
                _fps.value = framesEmulated
                framesEmulated = 0
                lastFpsTime = currentMillis
            }
        }
    }

    private fun setSkipRender(skip: Boolean, lastSkip: Boolean): Boolean {
        if (skip != lastSkip) NativeBridge.nativeSetSkipRender(skip)
        return skip
    }

    private fun pace(frameTimeNs: Long, lastFrameTime: Long) {
        val sleepTimeNs = frameTimeNs - (System.nanoTime() - lastFrameTime)
        if (sleepTimeNs > 0) LockSupport.parkNanos(sleepTimeNs)
    }
}
