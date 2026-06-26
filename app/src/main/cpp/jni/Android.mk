LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := aimxhack
LOCAL_SRC_FILES := ../aimxhack.cpp
LOCAL_LDLIBS := -llog -landroid
LOCAL_CFLAGS := -std=c++17 -O2

include $(BUILD_SHARED_LIBRARY)
