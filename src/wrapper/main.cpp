
#include <valhalla/worker.h>
#include <functional>
#include "main.h"
#include "valhalla_actor.h"

namespace {
// Runs one Valhalla actor operation and converts any C++ exception into a JSON
// error object matching the route() contract: {"code":<int>,"message":"<str>"}.
// Every value that crosses the JNI/Obj-C boundary must go through this helper —
// C++ exceptions may never propagate into Kotlin or Swift.
std::string run_actor_op(const char* op_name, const std::function<std::string()>& op) {
    try {
        return op();
    } catch (const valhalla::valhalla_exception_t &err) {
        printf("[ValhallaActor] %s valhalla_exception: %s\n", op_name, err.what());
        std::string code = std::to_string(err.code);
        std::string message = err.message.c_str();
        return "{\"code\":" + code + ",\"message\":\"" + message + "\"}";
    } catch (const std::exception &err) {
        printf("[ValhallaActor] %s std::exception: %s\n", op_name, err.what());
        return "{\"code\":-1,\"message\":\"" + std::string(err.what()) + "\"}";
    } catch (...) {
        printf("[ValhallaActor] %s unknown exception\n", op_name);
        return "{\"code\":-1,\"message\":\"unknown exception\"}";
    }
}
} // namespace

#ifdef __ANDROID__
// The Android JNI interface uses a different function signature.
#include <jni.h>

extern "C"
JNIEXPORT jstring

JNICALL
Java_com_valhalla_valhalla_ValhallaKotlin_route(JNIEnv *env,
                                                jobject thiz,
                                                jstring jRequest,
                                                jstring jConfigPath) {

    const char *request = env->GetStringUTFChars(jRequest, 0);
    const char *config_path = env->GetStringUTFChars(jConfigPath, 0);

    // TODO: Android currently creates a new actor every time. Optimize to be like iOS later.
    // The actor is constructed inside run_actor_op so a bad config path surfaces as a
    // JSON error instead of a C++ exception crossing the JNI boundary.
    std::string result = run_actor_op("route", [&] {
        ValhallaActor valhallaActor(config_path);
        return valhallaActor.route(request);
    });

    env->ReleaseStringUTFChars(jRequest, request);
    env->ReleaseStringUTFChars(jConfigPath, config_path);

    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_valhalla_valhalla_ValhallaKotlin_traceAttributes(JNIEnv *env,
                                                jobject thiz,
                                                jstring jRequest,
                                                jstring jConfigPath) {

    const char *request = env->GetStringUTFChars(jRequest, 0);
    const char *config_path = env->GetStringUTFChars(jConfigPath, 0);

    // TODO: Android currently creates a new actor every time. Optimize to be like iOS later.
    std::string result = run_actor_op("trace_attributes", [&] {
        ValhallaActor valhallaActor(config_path);
        return valhallaActor.trace_attributes(request);
    });

    env->ReleaseStringUTFChars(jRequest, request);
    env->ReleaseStringUTFChars(jConfigPath, config_path);

    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_valhalla_valhalla_ValhallaKotlin_matrix(JNIEnv *env,
                                                jobject thiz,
                                                jstring jRequest,
                                                jstring jConfigPath) {

    const char *request = env->GetStringUTFChars(jRequest, 0);
    const char *config_path = env->GetStringUTFChars(jConfigPath, 0);

    // TODO: Android currently creates a new actor every time. Optimize to be like iOS later.
    std::string result = run_actor_op("matrix", [&] {
        ValhallaActor valhallaActor(config_path);
        return valhallaActor.matrix(request);
    });

    env->ReleaseStringUTFChars(jRequest, request);
    env->ReleaseStringUTFChars(jConfigPath, config_path);

    return env->NewStringUTF(result.c_str());
}

#elif __APPLE__
void* create_valhalla_actor(const char *config_path, ValhallaMobileHttpClient* http_client) {
    return new ValhallaActor(config_path, http_client);
}

void delete_valhalla_actor(void* actor) {
    delete ((ValhallaActor*) actor);
}

std::string route(const char *request, void* actor) {
    return run_actor_op("route", [&] {
        return ((ValhallaActor*) actor)->route(request);
    });
}

std::string trace_attributes(const char *request, void* actor) {
    return run_actor_op("trace_attributes", [&] {
        return ((ValhallaActor*) actor)->trace_attributes(request);
    });
}

std::string matrix(const char *request, void* actor) {
    return run_actor_op("matrix", [&] {
        return ((ValhallaActor*) actor)->matrix(request);
    });
}
#endif
