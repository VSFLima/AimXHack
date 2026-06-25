#include <jni.h>
#include <android/log.h>
#include "memory/MemoryManager.h"
#include "predictor/Prediction.h"

#define LOG_TAG "AimXHack"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static bool initialized = false;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_aimx_hack_NativeBridge_nativeInit(JNIEnv* env, jobject thiz) {
    if (initialized) return true;
    LOGD("Initializing memory manager...");
    initialized = MemoryManager::initialize();
    if (initialized) {
        LOGD("Memory manager initialized. Base: %p", (void*)MemoryManager::gameModuleBase);
    } else {
        LOGE("Failed to initialize memory manager");
    }
    return initialized;
}

JNIEXPORT jboolean JNICALL
Java_com_aimx_hack_NativeBridge_nativeIsInGame(JNIEnv* env, jobject thiz) {
    if (!initialized) return false;
    return MenuManager::isInGame() && GameManager::isPlayerTurn();
}

JNIEXPORT jfloatArray JNICALL
Java_com_aimx_hack_NativeBridge_nativeGetShotResult(JNIEnv* env, jobject thiz) {
    if (!initialized) return nullptr;
    if (!gPrediction->determineShotResult()) return nullptr;

    int size = gPrediction->getShotResultSize();
    float* result = gPrediction->getShotResult();

    jfloatArray jResult = env->NewFloatArray(size);
    if (jResult) {
        env->SetFloatArrayRegion(jResult, 0, size, result);
    }
    return jResult;
}

JNIEXPORT jdouble JNICALL
Java_com_aimx_hack_NativeBridge_nativeGetShotAngle(JNIEnv* env, jobject thiz) {
    if (!initialized) return 0;
    return VisualCue::getShotAngle();
}

JNIEXPORT jdouble JNICALL
Java_com_aimx_hack_NativeBridge_nativeGetShotPower(JNIEnv* env, jobject thiz) {
    if (!initialized) return 0;
    return VisualCue::getShotPower();
}

JNIEXPORT jint JNICALL
Java_com_aimx_hack_NativeBridge_nativeGetBallsCount(JNIEnv* env, jobject thiz) {
    if (!initialized) return 0;
    Balls::initializeBallsList();
    return Balls::getBallsCount();
}

JNIEXPORT jdoubleArray JNICALL
Java_com_aimx_hack_NativeBridge_nativeGetBallPositions(JNIEnv* env, jobject thiz) {
    if (!initialized) return nullptr;
    Balls::initializeBallsList();
    int count = Balls::getBallsCount();
    jdoubleArray result = env->NewDoubleArray(count * 2);
    if (!result) return nullptr;

    double* buf = new double[count * 2];
    for (int i = 0; i < count; i++) {
        buf[i * 2] = Balls::getBallPositionX(i);
        buf[i * 2 + 1] = Balls::getBallPositionY(i);
    }
    env->SetDoubleArrayRegion(result, 0, count * 2, buf);
    delete[] buf;
    return result;
}

JNIEXPORT jintArray JNICALL
Java_com_aimx_hack_NativeBridge_nativeGetBallClassifications(JNIEnv* env, jobject thiz) {
    if (!initialized) return nullptr;
    Balls::initializeBallsList();
    int count = Balls::getBallsCount();
    jintArray result = env->NewIntArray(count);
    if (!result) return nullptr;

    int* buf = new int[count];
    for (int i = 0; i < count; i++) {
        buf[i] = Balls::getBallClassification(i);
    }
    env->SetIntArrayRegion(result, 0, count, buf);
    delete[] buf;
    return result;
}

}
