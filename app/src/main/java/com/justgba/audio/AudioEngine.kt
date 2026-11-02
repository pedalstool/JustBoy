package com.justgba.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.justgba.emulator.NativeBridge
import kotlin.math.min

class AudioEngine {
    companion object {
        private const val TAG = "AudioEngine"
        private const val SAMPLE_RATE = 48000
        private const val CHANNELS = AudioFormat.CHANNEL_OUT_STEREO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_MS = 80
        private const val READ_BUF_SIZE = 4096
        private const val LEFTOVER_BUF_SIZE = 8192
    }

    private var audioTrack: AudioTrack? = null
    private val readBuf = ShortArray(READ_BUF_SIZE)
    private var fastForward = false
    private var ffAudioMode = 1
    private var appliedPlaybackRate = SAMPLE_RATE
    private val leftoverBuf = ShortArray(LEFTOVER_BUF_SIZE)
    private var leftoverFrames = 0

    fun init(): Boolean {
        val bufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING)
        val desiredSize = (SAMPLE_RATE * BUFFER_MS / 1000 * 2 * 2).coerceAtLeast(bufSize)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(ENCODING)
            .setChannelMask(CHANNELS)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(desiredSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val track = audioTrack
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack initialization failed")
            audioTrack?.release()
            audioTrack = null
            return false
        }

        track.play()
        appliedPlaybackRate = SAMPLE_RATE
        return true
    }

    fun setFastForward(enabled: Boolean, speedMultiplier: Float) {
        fastForward = enabled
        val track = audioTrack ?: return
        val rate = if (enabled && speedMultiplier > 1f && ffAudioMode == 1) {
            (SAMPLE_RATE * minOf(speedMultiplier, 4f)).toInt()
        } else {
            SAMPLE_RATE
        }
        if (rate == appliedPlaybackRate) return
        try {
            track.playbackRate = rate
            appliedPlaybackRate = rate
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set playback rate", e)
        }
    }

    fun setFfAudioMode(mode: Int) {
        ffAudioMode = mode
    }

    fun pause() {
        try {
            audioTrack?.pause()
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            audioTrack?.play()
        } catch (_: Exception) {}
    }

    fun tick() {
        val track = audioTrack ?: return

        val readable = NativeBridge.nativeAudioReadable()
        var newFrames = 0

        if (readable > 0) {
            val samples = NativeBridge.nativeReadAudio(readBuf, min(readable, readBuf.size))
            if (fastForward && ffAudioMode == 0) {
                leftoverFrames = 0
                return
            }
            if (samples > 0) newFrames = samples / 2
        }

        if (leftoverFrames > 0) {
            val written = track.write(leftoverBuf, 0, leftoverFrames * 2, AudioTrack.WRITE_NON_BLOCKING)
            if (written > 0) {
                leftoverFrames -= written / 2
                if (leftoverFrames > 0) {
                    System.arraycopy(leftoverBuf, written, leftoverBuf, 0, leftoverFrames * 2)
                }
            }
        }

        if (newFrames > 0) {
            if (leftoverFrames > 0) return
            val written = track.write(readBuf, 0, newFrames * 2, AudioTrack.WRITE_NON_BLOCKING)
            if (written < newFrames * 2) {
                val framesToSave = min(newFrames - (written / 2), leftoverBuf.size / 2)
                System.arraycopy(readBuf, written, leftoverBuf, 0, framesToSave * 2)
                leftoverFrames = framesToSave
            }
        }
    }

    fun release() {
        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        audioTrack?.release()
        audioTrack = null
    }
}
