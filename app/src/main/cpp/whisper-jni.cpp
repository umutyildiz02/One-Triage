// Eren_Project's PCM16 -> whisper_full JNI flow adapted for VitalTriage.
// whisper.cpp itself is pinned upstream MIT source; see third_party/whisper.cpp/LICENSE.
// No audio, text, model path or credentials are logged.
#include <jni.h>
#include <whisper.h>
#include <ggml-backend.h>
#include <mutex>
#include <atomic>
#include <chrono>
#include <cmath>
#include <string>
#include <vector>
#include <algorithm>

struct Engine {
    whisper_context *ctx;
    std::atomic<bool> cancelled{false};
    std::chrono::steady_clock::time_point deadline;
};
static void silent_log(enum ggml_log_level, const char *, void *) {}
static bool abort_work(void *data) {
    auto *e = static_cast<Engine *>(data);
    return e->cancelled.load() || std::chrono::steady_clock::now() > e->deadline;
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_vitaltriage_app_WhisperNative_load(JNIEnv *env, jobject, jstring path, jstring libraryDirectory) {
    whisper_log_set(silent_log, nullptr);
    ggml_log_set(silent_log, nullptr);
#ifdef VT_DYNAMIC_CPU
    const char *directory = env->GetStringUTFChars(libraryDirectory, nullptr);
    static std::once_flag initialized;
    std::call_once(initialized, [directory] { ggml_backend_load_all_from_path(directory); });
    env->ReleaseStringUTFChars(libraryDirectory, directory);
    if (ggml_backend_dev_count() == 0) return 0;
#endif
    const char *p = env->GetStringUTFChars(path, nullptr);
    auto params = whisper_context_default_params();
    params.use_gpu = false;
    auto *ctx = whisper_init_from_file_with_params(p, params);
    env->ReleaseStringUTFChars(path, p);
    if (!ctx) return 0;
    if (!whisper_is_multilingual(ctx)) { whisper_free(ctx); return 0; }
    return reinterpret_cast<jlong>(new Engine{ctx});
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_vitaltriage_app_WhisperNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}
extern "C" JNIEXPORT void JNICALL
Java_com_vitaltriage_app_WhisperNative_prepare(JNIEnv *, jobject, jlong handle) {
    auto *e = reinterpret_cast<Engine *>(handle);
    e->cancelled.store(false);
    e->deadline = std::chrono::steady_clock::now() + std::chrono::seconds(120);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_vitaltriage_app_WhisperNative_timedOut(JNIEnv *, jobject, jlong handle) {
    auto *e = reinterpret_cast<Engine *>(handle);
    return !e->cancelled.load() && std::chrono::steady_clock::now() > e->deadline;
}
extern "C" JNIEXPORT void JNICALL
Java_com_vitaltriage_app_WhisperNative_cancel(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<Engine *>(handle)->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_com_vitaltriage_app_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    auto *e = reinterpret_cast<Engine *>(handle);
    whisper_free(e->ctx); delete e;
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_vitaltriage_app_WhisperNative_run(JNIEnv *env, jobject, jlong handle,
                                        jshortArray samples, jstring language, jboolean translate, jboolean beamSearch, jint threads) {
    auto *e = reinterpret_cast<Engine *>(handle);
    const int n = env->GetArrayLength(samples);
    if (n < 1600 || n > 29 * 16000 || abort_work(e)) return nullptr;
    std::vector<jshort> pcm(n);
    env->GetShortArrayRegion(samples, 0, n, pcm.data());
    std::vector<float> audio(n);
    double energy = 0; int active = 0;
    for (int i = 0; i < n; ++i) {
        audio[i] = pcm[i] / 32768.0f;
        energy += audio[i] * audio[i];
        if (std::abs(static_cast<int>(pcm[i])) > 400) ++active;
    }
    std::fill(pcm.begin(), pcm.end(), 0);
    // Do not feed silence/near-zero capture to a generative recognizer.
    if (std::sqrt(energy / n) < 0.003 || active < 160) return nullptr;
    const char *lang = env->GetStringUTFChars(language, nullptr);
    const std::string selected(lang);
    env->ReleaseStringUTFChars(language, lang);
    auto params = whisper_full_default_params(beamSearch ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    if (beamSearch) params.beam_search.beam_size = 5;
    else params.greedy.best_of = 1; // temperature=0; avoid unused decoder allocations on mobile.
    params.n_threads = std::clamp(static_cast<int>(threads), 1, 4);
    params.language = selected == "auto" ? nullptr : selected.c_str();
    // Auto language + transcription: detect_language=true would return before decoding.
    params.detect_language = false;
    params.translate = translate;
    params.no_context = true;
    params.no_timestamps = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_nst = true;
    params.temperature_inc = 0.0f;
    params.abort_callback = abort_work;
    params.abort_callback_user_data = e;
    const int result = whisper_full(e->ctx, params, audio.data(), n);
    std::fill(audio.begin(), audio.end(), 0.0f);
    if (result != 0 || abort_work(e)) return nullptr;
    std::string text;
    for (int i = 0; i < whisper_full_n_segments(e->ctx); ++i) {
        const char *segment = whisper_full_get_segment_text(e->ctx, i);
        if (segment) text += segment;
        if (text.size() > 16000) return nullptr;
    }
    if (text.empty()) return nullptr;
    const char *detected = whisper_lang_str(whisper_full_lang_id(e->ctx));
    std::string output = std::string(detected ? detected : "und") + "\n" + text;
    auto bytes = env->NewByteArray(static_cast<jsize>(output.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(output.size()),
                           reinterpret_cast<const jbyte *>(output.data()));
    return bytes;
}
