from pathlib import Path

root = Path(__file__).resolve().parents[1] / "third_party" / "llama.cpp"
logging_h = root / "examples/llama.android/lib/src/main/cpp/logging.h"
ai_cpp = root / "examples/llama.android/lib/src/main/cpp/ai_chat.cpp"
iface = root / "examples/llama.android/lib/src/main/java/com/arm/aichat/InferenceEngine.kt"
impl = root / "examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt"

s = logging_h.read_text()
s = s.replace("#include <android/log.h>\n", "#include <android/log.h>\n#include <mutex>\n#include <string>\n")
needle = "static inline int android_log_prio_from_ggml(enum ggml_log_level level) {"
helpers = """static std::mutex aichat_log_mutex;
static std::string aichat_native_log;

static inline void aichat_log_clear() {
    std::lock_guard<std::mutex> guard(aichat_log_mutex);
    aichat_native_log.clear();
}

static inline void aichat_log_append(const char * text) {
    if (!text) return;
    std::lock_guard<std::mutex> guard(aichat_log_mutex);
    aichat_native_log.append(text);
    if (aichat_native_log.size() > 32768) {
        aichat_native_log.erase(0, aichat_native_log.size() - 32768);
    }
}

static inline std::string aichat_log_snapshot() {
    std::lock_guard<std::mutex> guard(aichat_log_mutex);
    return aichat_native_log;
}

"""
if helpers not in s:
    s = s.replace(needle, helpers + needle)
old_cb = """    const int prio = android_log_prio_from_ggml(level);
    if (!ai_should_log(prio)) return;
    __android_log_write(prio, LOG_TAG, text);
"""
new_cb = """    aichat_log_append(text);
    const int prio = android_log_prio_from_ggml(level);
    if (!ai_should_log(prio)) return;
    __android_log_write(prio, LOG_TAG, text);
"""
s = s.replace(old_cb, new_cb)
logging_h.write_text(s)

s = ai_cpp.read_text()

# Android can package native libraries directly inside the APK. In that case
# nativeLibraryDir may not expose backend plugins as normal files, so the
# generic directory scan can find zero backends. Keep the normal scan, then
# explicitly load the most portable ARM64 CPU backend as a fallback.
old_init = """    const auto *path_to_backend = env->GetStringUTFChars(nativeLibDir, 0);
    LOGi("Loading backends from %s", path_to_backend);
    ggml_backend_load_all_from_path(path_to_backend);
    env->ReleaseStringUTFChars(nativeLibDir, path_to_backend);

    // Initialize backends
    llama_backend_init();
"""
new_init = """    const auto *path_to_backend = env->GetStringUTFChars(nativeLibDir, 0);
    LOGi("Loading backends from %s", path_to_backend);
    ggml_backend_load_all_from_path(path_to_backend);

    if (ggml_backend_reg_count() == 0) {
        LOGw("No backend found by directory scan; trying explicit ARM64 CPU backend");
        const std::string cpu_backend_path =
                std::string(path_to_backend) + "/libggml-cpu-android_armv8.0_1.so";
        auto * reg = ggml_backend_load(cpu_backend_path.c_str());
        if (!reg) {
            reg = ggml_backend_load("libggml-cpu-android_armv8.0_1.so");
        }
        if (!reg) {
            LOGe("Explicit CPU backend load failed");
        }
    }

    LOGi("Registered backend count: %zu", ggml_backend_reg_count());
    env->ReleaseStringUTFChars(nativeLibDir, path_to_backend);

    // Initialize backends
    llama_backend_init();
"""
if old_init in s:
    s = s.replace(old_init, new_init)
s = s.replace(
    "    llama_model_params model_params = llama_model_default_params();\n\n    const auto *model_path",
    "    aichat_log_clear();\n    llama_model_params model_params = llama_model_default_params();\n\n    const auto *model_path"
)
s = s.replace(
    "    if (!model) {\n        return 1;\n    }",
    "    if (!model) {\n        LOGe(\"%s: llama_model_load_from_file returned null\", __func__);\n        return 1;\n    }"
)
marker = """extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_systemInfo"""
getter = """extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_getLastNativeLog(JNIEnv *env, jobject /*unused*/) {
    const auto log = aichat_log_snapshot();
    return env->NewStringUTF(log.c_str());
}

"""
if getter not in s:
    s = s.replace(marker, getter + marker)
ai_cpp.write_text(s)

s = iface.read_text()
needle = """    fun sendUserPrompt(message: String, predictLength: Int = DEFAULT_PREDICT_LENGTH): Flow<String>
"""
if "fun lastNativeLog()" not in s:
    s = s.replace(needle, needle + "\n    /** Returns recent native llama.cpp diagnostics. */\n    fun lastNativeLog(): String\n")
iface.write_text(s)

s = impl.read_text()
needle = """    @FastNative
    private external fun systemInfo(): String
"""
insert = """    @FastNative
    private external fun getLastNativeLog(): String

    override fun lastNativeLog(): String = getLastNativeLog()

"""
if "getLastNativeLog" not in s:
    s = s.replace(needle, needle + "\n" + insert)
impl.write_text(s)
