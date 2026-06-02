#include <dlfcn.h>
#include <jni.h>

typedef void (*xt_set_string_fn)(char*);
typedef char* (*xt_start_fn)(char*, char*);
typedef char* (*xt_string_fn)(void);
typedef int (*xt_int_fn)(void);
typedef void (*xt_free_fn)(char*);

static void* xt_handle;

static void* xt_symbol(const char* name) {
    if (!xt_handle) {
        xt_handle = dlopen("libgo.so", RTLD_NOW | RTLD_GLOBAL);
    }
    void* symbol = xt_handle ? dlsym(xt_handle, name) : NULL;
    return symbol ? symbol : dlsym(RTLD_DEFAULT, name);
}

static jstring xt_string_result(JNIEnv* env, char* value) {
    xt_free_fn free_fn = (xt_free_fn)xt_symbol("XTunnelFree");
    jstring result = (*env)->NewStringUTF(env, value ? value : "");
    if (value && free_fn) {
        free_fn(value);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_magnetcatcher_xtunnel_XTunnelNative_nativeSetEmbedPath(JNIEnv* env, jclass clazz, jstring path) {
    (void)clazz;
    xt_set_string_fn fn = (xt_set_string_fn)xt_symbol("XTunnelSetEmbedPath");
    if (!fn || !path) {
        return;
    }
    const char* raw = (*env)->GetStringUTFChars(env, path, NULL);
    if (raw) {
        fn((char*)raw);
        (*env)->ReleaseStringUTFChars(env, path, raw);
    }
}

JNIEXPORT void JNICALL
Java_com_example_magnetcatcher_xtunnel_XTunnelNative_nativeSetMachineCode(JNIEnv* env, jclass clazz, jstring code) {
    (void)clazz;
    xt_set_string_fn fn = (xt_set_string_fn)xt_symbol("XTunnelSetMachineCode");
    if (!fn || !code) {
        return;
    }
    const char* raw = (*env)->GetStringUTFChars(env, code, NULL);
    if (raw) {
        fn((char*)raw);
        (*env)->ReleaseStringUTFChars(env, code, raw);
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_magnetcatcher_xtunnel_XTunnelNative_nativeStartXTunnel(JNIEnv* env, jclass clazz, jstring addr, jstring data_dir) {
    (void)clazz;
    xt_start_fn fn = (xt_start_fn)xt_symbol("XTunnelStart");
    if (!fn || !addr || !data_dir) {
        return (*env)->NewStringUTF(env, "");
    }
    const char* raw_addr = (*env)->GetStringUTFChars(env, addr, NULL);
    const char* raw_dir = (*env)->GetStringUTFChars(env, data_dir, NULL);
    char* result = NULL;
    if (raw_addr && raw_dir) {
        result = fn((char*)raw_addr, (char*)raw_dir);
    }
    if (raw_addr) {
        (*env)->ReleaseStringUTFChars(env, addr, raw_addr);
    }
    if (raw_dir) {
        (*env)->ReleaseStringUTFChars(env, data_dir, raw_dir);
    }
    return xt_string_result(env, result);
}

JNIEXPORT jstring JNICALL
Java_com_example_magnetcatcher_xtunnel_XTunnelNative_nativeGetAddr(JNIEnv* env, jclass clazz) {
    (void)clazz;
    xt_string_fn fn = (xt_string_fn)xt_symbol("XTunnelGetAddr");
    return xt_string_result(env, fn ? fn() : NULL);
}

JNIEXPORT jboolean JNICALL
Java_com_example_magnetcatcher_xtunnel_XTunnelNative_nativeIsRunning(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    xt_int_fn fn = (xt_int_fn)xt_symbol("XTunnelIsRunning");
    return fn && fn() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_example_magnetcatcher_xtunnel_XTunnelNative_nativeGetStatusJson(JNIEnv* env, jclass clazz) {
    (void)clazz;
    xt_string_fn fn = (xt_string_fn)xt_symbol("XTunnelGetStatusJSON");
    return xt_string_result(env, fn ? fn() : NULL);
}

JNIEXPORT jstring JNICALL
Java_com_example_magnetcatcher_xtunnel_XTunnelNative_nativeStopXTunnel(JNIEnv* env, jclass clazz) {
    (void)clazz;
    xt_string_fn fn = (xt_string_fn)xt_symbol("XTunnelStop");
    return xt_string_result(env, fn ? fn() : NULL);
}
