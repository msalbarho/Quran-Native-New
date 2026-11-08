#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define TAG "QuranWhisper"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_quransunah_app_recitation_engine_WhisperLib_initContext(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!ctx) __android_log_print(ANDROID_LOG_ERROR, TAG, "failed to load model");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_quransunah_app_recitation_engine_WhisperLib_freeContext(JNIEnv *, jobject, jlong ctx) {
    if (ctx) whisper_free(reinterpret_cast<whisper_context *>(ctx));
}

JNIEXPORT jbyteArray JNICALL
Java_com_quransunah_app_recitation_engine_WhisperLib_transcribeBytes(JNIEnv *env, jobject, jlong ctxPtr, jint threads,
                                                                jfloatArray samples, jstring prompt, jint audioCtx) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (!ctx) return env->NewByteArray(0);
    jsize n = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.print_realtime = false;
    p.print_progress = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.translate = false;
    p.language = "ar";
    p.n_threads = threads;
    p.no_context = true;
    p.single_segment = true;
    p.no_timestamps = true;
    p.suppress_blank = true;
    p.temperature_inc = 0.0f;
    // Encode only as many mel frames as the chunk needs (1500 = 30 s). For 1-8 s live chunks this
    // makes the encoder 4-20x faster, which is what keeps live latency under a second.
    if (audioCtx > 0 && audioCtx < 1500) p.audio_ctx = audioCtx;
    p.max_tokens = 64;
    std::string promptStr;
    if (prompt) {
        const char *c = env->GetStringUTFChars(prompt, nullptr);
        promptStr = c;
        env->ReleaseStringUTFChars(prompt, c);
        p.initial_prompt = promptStr.c_str();
    }
    std::string out;
    if (whisper_full(ctx, p, data, n) == 0) {
        int segs = whisper_full_n_segments(ctx);
        for (int i = 0; i < segs; ++i) {
            out += whisper_full_get_segment_text(ctx, i);
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_full failed");
    }
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    jbyteArray arr = env->NewByteArray((jsize) out.size());
    env->SetByteArrayRegion(arr, 0, (jsize) out.size(), reinterpret_cast<const jbyte *>(out.data()));
    return arr;
}

JNIEXPORT jstring JNICALL
Java_com_quransunah_app_recitation_engine_WhisperLib_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

}
