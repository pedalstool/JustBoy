package com.justgba.emulator

object NativeBridge {
    init {
        System.loadLibrary("justgba")
    }

    external fun nativeCopyFrame(buffer: java.nio.ByteBuffer)
    @Synchronized external fun nativeClearCheats()
    @Synchronized external fun nativeSetCheat(index: Int, code: String): Int
    @Synchronized external fun nativeInit(sysDir: String, saveDir: String): Boolean
    @Synchronized external fun nativeLoadGame(romPath: String): Boolean
    external fun nativeRunFrame()
    @Synchronized external fun nativeReset()
    external fun nativeFrameReady(): Boolean
    external fun nativeGetVideoWidth(): Int
    external fun nativeGetVideoHeight(): Int
    external fun nativeReadAudio(buffer: ShortArray, capacity: Int): Int
    external fun nativeAudioReadable(): Int
    external fun nativeSetInput(mask: Int)
    external fun nativeSetSkipRender(skip: Boolean)
    @Synchronized external fun nativeBatterySave(savePath: String): Boolean
    @Synchronized external fun nativeBatteryLoad(savePath: String): Boolean
    @Synchronized external fun nativeStateSave(savePath: String): Boolean
    @Synchronized external fun nativeStateLoad(savePath: String): Boolean
    @Synchronized external fun nativeDeinit()
}
