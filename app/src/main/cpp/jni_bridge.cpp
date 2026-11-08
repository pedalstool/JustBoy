#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>

#include <unistd.h>

#include <fcntl.h>

#include <mgba/core/blip_buf.h>
#include <mgba/core/cheats.h>
#include <mgba/core/config.h>
#include <mgba/core/core.h>
#include <mgba/core/serialize.h>
#include <mgba/internal/gb/gb.h>
#include <mgba/internal/gba/gba.h>
#include <mgba-util/vfs.h>

#define LOG_TAG "JustBoy-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr int AUDIO_RING_CAPACITY = 48000 * 2;
static constexpr int AUDIO_SAMPLE_RATE = 48000;
static constexpr int SAVESTATE_FLAGS = SAVESTATE_SAVEDATA | SAVESTATE_RTC | SAVESTATE_METADATA | SAVESTATE_CHEATS;

static std::mutex video_mutex;
static struct mCore* core = nullptr;
static color_t* video_buffer = nullptr;
static uint16_t* display_pixels = nullptr;
static size_t display_bytes = 0;
static unsigned video_width = 240;
static unsigned video_height = 160;
static size_t video_stride = 240;

static int16_t audio_ring[AUDIO_RING_CAPACITY];
static std::atomic<int> audio_wpos{0};
static std::atomic<int> audio_rpos{0};

static std::atomic<uint32_t> joypad_bits{0};
static std::atomic<bool> skip_render{false};
static std::atomic<bool> frame_ready{false};

static char system_dir[512] = {};

static void ring_write(int start, const int16_t* src, int count) {
    int first = std::min(count, AUDIO_RING_CAPACITY - start);
    memcpy(audio_ring + start, src, static_cast<size_t>(first) * sizeof(int16_t));
    if (first < count) memcpy(audio_ring, src + first, static_cast<size_t>(count - first) * sizeof(int16_t));
}

static void ring_read(int16_t* dst, int start, int count) {
    int first = std::min(count, AUDIO_RING_CAPACITY - start);
    memcpy(dst, audio_ring + start, static_cast<size_t>(first) * sizeof(int16_t));
    if (first < count) memcpy(dst + first, audio_ring, static_cast<size_t>(count - first) * sizeof(int16_t));
}

static uint32_t convert_keys(uint32_t bits) {
    uint32_t keys = 0;
    if (bits & (1u << 8)) keys |= 1u << 0;  // A
    if (bits & (1u << 0)) keys |= 1u << 1;  // B
    if (bits & (1u << 2)) keys |= 1u << 2;  // Select
    if (bits & (1u << 3)) keys |= 1u << 3;  // Start
    if (bits & (1u << 7)) keys |= 1u << 4;  // Right
    if (bits & (1u << 6)) keys |= 1u << 5;  // Left
    if (bits & (1u << 4)) keys |= 1u << 6;  // Up
    if (bits & (1u << 5)) keys |= 1u << 7;  // Down
    if (bits & (1u << 11)) keys |= 1u << 8; // R
    if (bits & (1u << 10)) keys |= 1u << 9; // L
    return keys;
}

static void push_audio(const int16_t* data, int frames) {
    int samples = frames * 2;
    int wp = audio_wpos.load(std::memory_order_relaxed);
    int rp = audio_rpos.load(std::memory_order_acquire);
    int filled = (wp >= rp) ? (wp - rp) : (AUDIO_RING_CAPACITY - rp + wp);
    int free_space = AUDIO_RING_CAPACITY - filled - 1;
    if (free_space < samples) return;
    ring_write(wp, data, samples);
    audio_wpos.store((wp + samples) % AUDIO_RING_CAPACITY, std::memory_order_release);
}

static void pull_audio() {
    if (!core) return;
    blip_t* left = core->getAudioChannel(core, 0);
    blip_t* right = core->getAudioChannel(core, 1);
    if (!left || !right) return;
    int avail = blip_samples_avail(left);
    if (avail <= 0) return;
    if (avail > 2048) avail = 2048;
    int16_t tmp[2048 * 2];
    blip_read_samples(left, tmp, avail, 1);
    blip_read_samples(right, tmp + 1, avail, 1);
    push_audio(tmp, avail);
}

static void setup_audio() {
    if (!core) return;
    core->setAudioBufferSize(core, 2048);
    int32_t freq = core->frequency(core);
    blip_t* left = core->getAudioChannel(core, 0);
    blip_t* right = core->getAudioChannel(core, 1);
    if (left) blip_set_rates(left, freq, AUDIO_SAMPLE_RATE);
    if (right) blip_set_rates(right, freq, AUDIO_SAMPLE_RATE);
}

static void setup_video() {
    unsigned w = 240, h = 160;
    core->desiredVideoDimensions(core, &w, &h);
    if (w == 0 || h == 0) {
        w = 240;
        h = 160;
    }
    free(video_buffer);
    video_buffer = static_cast<color_t*>(calloc(static_cast<size_t>(w) * h, sizeof(color_t)));
    {
        std::lock_guard<std::mutex> lock(video_mutex);
        video_width = w;
        video_height = h;
        video_stride = w;
        display_bytes = static_cast<size_t>(w) * h * 2;
        display_pixels = static_cast<uint16_t*>(realloc(display_pixels, display_bytes));
        if (display_pixels) memset(display_pixels, 0, display_bytes);
    }
    core->setVideoBuffer(core, video_buffer, w);
}

static void snapshot_video() {
    if (!video_buffer || !display_pixels) return;
    std::lock_guard<std::mutex> lock(video_mutex);
    if (video_stride == video_width) {
        memcpy(display_pixels, video_buffer, display_bytes);
    } else {
        for (unsigned y = 0; y < video_height; ++y) {
            memcpy(display_pixels + y * video_width, video_buffer + y * video_stride,
                   static_cast<size_t>(video_width) * 2);
        }
    }
    frame_ready.store(true, std::memory_order_release);
}

static void destroy_core() {
    if (core) {
        core->deinit(core);
        core = nullptr;
    }
    free(video_buffer);
    video_buffer = nullptr;
    std::lock_guard<std::mutex> lock(video_mutex);
    free(display_pixels);
    display_pixels = nullptr;
    display_bytes = 0;
}

static bool load_optional_bios() {
    if (!core || !system_dir[0]) return false;
    if (core->platform(core) != mPLATFORM_GBA) return false;
    char path[768];
    snprintf(path, sizeof(path), "%s/gba_bios.bin", system_dir);
    struct VFile* vf = VFileOpen(path, O_RDONLY);
    if (!vf) return false;
    bool ok = core->loadBIOS(core, vf, 0);
    if (!ok) vf->close(vf);
    return ok;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeInit(JNIEnv* env, jclass, jstring sysDir, jstring) {
    const char* sys = env->GetStringUTFChars(sysDir, nullptr);
    if (sys) {
        strncpy(system_dir, sys, sizeof(system_dir) - 1);
        system_dir[sizeof(system_dir) - 1] = 0;
        env->ReleaseStringUTFChars(sysDir, sys);
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeLoadGame(JNIEnv* env, jclass, jstring romPath) {
    const char* path = env->GetStringUTFChars(romPath, nullptr);
    if (!path) return JNI_FALSE;

    destroy_core();
    core = mCoreFind(path);
    if (!core) {
        LOGE("mCoreFind failed for %s", path);
        env->ReleaseStringUTFChars(romPath, path);
        return JNI_FALSE;
    }
    if (!core->init(core)) {
        LOGE("mCore init failed");
        destroy_core();
        env->ReleaseStringUTFChars(romPath, path);
        return JNI_FALSE;
    }
    mCoreInitConfig(core, "justgba");
    mCoreConfigSetDefaultIntValue(&core->config, "sgb.borders", 0);
    if (!mCoreLoadFile(core, path)) {
        LOGE("mCoreLoadFile failed");
        destroy_core();
        env->ReleaseStringUTFChars(romPath, path);
        return JNI_FALSE;
    }
    load_optional_bios();
    core->reloadConfigOption(core, "sgb.borders", &core->config);
    setup_video();
    setup_audio();
    core->reset(core);
    setup_audio();
    LOGI("Loaded %s as %s %ux%u", path, core->platform(core) == mPLATFORM_GB ? "GB/GBC" : "GBA", video_width, video_height);
    env->ReleaseStringUTFChars(romPath, path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeRunFrame(JNIEnv*, jclass) {
    if (!core) return;
    core->setKeys(core, convert_keys(joypad_bits.load(std::memory_order_acquire)));
    core->runFrame(core);
    pull_audio();
    if (!skip_render.load(std::memory_order_relaxed)) snapshot_video();
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeReset(JNIEnv*, jclass) {
    if (core) core->reset(core);
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeFrameReady(JNIEnv*, jclass) {
    return frame_ready.exchange(false, std::memory_order_acq_rel) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_justgba_emulator_NativeBridge_nativeGetVideoWidth(JNIEnv*, jclass) {
    return static_cast<jint>(video_width);
}

JNIEXPORT jint JNICALL
Java_com_justgba_emulator_NativeBridge_nativeGetVideoHeight(JNIEnv*, jclass) {
    return static_cast<jint>(video_height);
}

JNIEXPORT jint JNICALL
Java_com_justgba_emulator_NativeBridge_nativeReadAudio(JNIEnv* env, jclass, jshortArray jbuffer, jint capacity) {
    jshort* dst = env->GetShortArrayElements(jbuffer, nullptr);
    if (!dst) return 0;
    int rp = audio_rpos.load(std::memory_order_acquire);
    int wp = audio_wpos.load(std::memory_order_acquire);
    int avail = (wp >= rp) ? (wp - rp) : (AUDIO_RING_CAPACITY - rp + wp);
    int to_read = avail < capacity ? avail : capacity;
    ring_read(reinterpret_cast<int16_t*>(dst), rp, to_read);
    audio_rpos.store((rp + to_read) % AUDIO_RING_CAPACITY, std::memory_order_release);
    env->ReleaseShortArrayElements(jbuffer, dst, 0);
    return to_read;
}

JNIEXPORT jint JNICALL
Java_com_justgba_emulator_NativeBridge_nativeAudioReadable(JNIEnv*, jclass) {
    int rp = audio_rpos.load(std::memory_order_acquire);
    int wp = audio_wpos.load(std::memory_order_acquire);
    return (wp >= rp) ? (wp - rp) : (AUDIO_RING_CAPACITY - rp + wp);
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeSetInput(JNIEnv*, jclass, jint mask) {
    joypad_bits.store(static_cast<uint32_t>(mask), std::memory_order_release);
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeSetSkipRender(JNIEnv*, jclass, jboolean skip) {
    skip_render.store(skip != JNI_FALSE, std::memory_order_relaxed);
}

static bool cartridge_save_dirty() {
    if (!core || !core->board) return false;
    switch (core->platform(core)) {
    case mPLATFORM_GBA:
        return static_cast<struct GBA*>(core->board)->memory.savedata.dirty != 0;
    case mPLATFORM_GB:
        return static_cast<struct GB*>(core->board)->sramDirty != 0;
    default:
        return false;
    }
}

static bool save_image_blank(const uint8_t* data, size_t size) {
    for (size_t i = 0; i < size; ++i) {
        if (data[i] != 0xFF) return false;
    }
    return true;
}

static bool real_save_exists(const char* tmpPath) {
    std::string path(tmpPath);
    constexpr char suffix[] = ".tmp";
    constexpr size_t suffixLen = sizeof(suffix) - 1;
    if (path.size() >= suffixLen && path.compare(path.size() - suffixLen, suffixLen, suffix) == 0) {
        path.resize(path.size() - suffixLen);
    }
    return access(path.c_str(), F_OK) == 0;
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeBatterySave(JNIEnv* env, jclass, jstring savePath) {
    if (!core) return JNI_FALSE;
    // Opening a game allocates blank SRAM/flash. Only write a file after the cart changes it.
    if (!cartridge_save_dirty()) return JNI_TRUE;
    const char* path = env->GetStringUTFChars(savePath, nullptr);
    if (!path) return JNI_FALSE;
    void* sram = nullptr;
    size_t size = core->savedataClone(core, &sram);
    if (!size || !sram || (save_image_blank(static_cast<uint8_t*>(sram), size) && !real_save_exists(path))) {
        free(sram);
        env->ReleaseStringUTFChars(savePath, path);
        return JNI_TRUE;
    }
    FILE* f = fopen(path, "wb");
    bool ok = false;
    if (f) {
        ok = fwrite(sram, 1, size, f) == size;
        fclose(f);
    }
    free(sram);
    env->ReleaseStringUTFChars(savePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeBatteryLoad(JNIEnv* env, jclass, jstring savePath) {
    if (!core) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(savePath, nullptr);
    if (!path) return JNI_FALSE;
    // savedataRestore is a no-op while the cart type is still AUTODETECT.
    // Golden Sun stays that way until the game first touches flash, so the
    // file has to be the core's save backing store instead of a one-shot copy.
    bool ok = mCoreLoadSaveFile(core, path, false);
    env->ReleaseStringUTFChars(savePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeStateSave(JNIEnv* env, jclass, jstring savePath) {
    if (!core) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(savePath, nullptr);
    if (!path) return JNI_FALSE;
    struct VFile* vf = VFileOpen(path, O_CREAT | O_TRUNC | O_RDWR);
    bool ok = false;
    if (vf) {
        ok = mCoreSaveStateNamed(core, vf, SAVESTATE_FLAGS);
        vf->close(vf);
    }
    env->ReleaseStringUTFChars(savePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_justgba_emulator_NativeBridge_nativeStateLoad(JNIEnv* env, jclass, jstring savePath) {
    if (!core) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(savePath, nullptr);
    if (!path) return JNI_FALSE;
    struct VFile* vf = VFileOpen(path, O_RDONLY);
    bool ok = false;
    if (vf) {
        ok = mCoreLoadStateNamed(core, vf, SAVESTATE_FLAGS);
        vf->close(vf);
    }
    env->ReleaseStringUTFChars(savePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeDeinit(JNIEnv*, jclass) {
    destroy_core();
    frame_ready.store(false, std::memory_order_relaxed);
    audio_wpos.store(0, std::memory_order_relaxed);
    audio_rpos.store(0, std::memory_order_relaxed);
    joypad_bits.store(0, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeCopyFrame(JNIEnv* env, jclass, jobject buffer) {
    void* dst = env->GetDirectBufferAddress(buffer);
    jlong cap = env->GetDirectBufferCapacity(buffer);
    if (!dst || cap <= 0) return;
    std::lock_guard<std::mutex> lock(video_mutex);
    size_t n = display_bytes < static_cast<size_t>(cap) ? display_bytes : static_cast<size_t>(cap);
    if (display_pixels && n) memcpy(dst, display_pixels, n);
}

JNIEXPORT void JNICALL
Java_com_justgba_emulator_NativeBridge_nativeClearCheats(JNIEnv*, jclass) {
    if (!core) return;
    struct mCheatDevice* device = core->cheatDevice(core);
    if (device) mCheatDeviceClear(device);
}

JNIEXPORT jint JNICALL
Java_com_justgba_emulator_NativeBridge_nativeSetCheat(JNIEnv* env, jclass, jint, jstring code) {
    if (!core) return 4;
    const char* text = env->GetStringUTFChars(code, nullptr);
    if (!text) return 4;
    struct mCheatDevice* device = core->cheatDevice(core);
    if (!device) {
        env->ReleaseStringUTFChars(code, text);
        return 4;
    }
    struct mCheatSet* set = device->createSet(device, "JustBoy");
    if (!set) {
        env->ReleaseStringUTFChars(code, text);
        return 4;
    }
    set->enabled = true;
    std::string blob(text);
    env->ReleaseStringUTFChars(code, text);
    bool any = false;
    size_t start = 0;
    while (start < blob.size()) {
        size_t end = blob.find('\n', start);
        if (end == std::string::npos) end = blob.size();
        std::string line = blob.substr(start, end - start);
        while (!line.empty() && (line.back() == '\r' || line.back() == ' ')) line.pop_back();
        if (!line.empty()) {
            if (!mCheatAddLine(set, line.c_str(), 0)) {
                if (set->deinit) set->deinit(set);
                free(set);
                return 4;
            }
            any = true;
        }
        start = end + 1;
    }
    if (!any) {
        if (set->deinit) set->deinit(set);
        free(set);
        return 4;
    }
    mCheatAddSet(device, set);
    mCheatRefresh(device, set);
    return 0;
}

}  // extern "C"
