LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := xtbridge
LOCAL_SRC_FILES := xtbridge.c
LOCAL_LDLIBS := -ldl
include $(BUILD_SHARED_LIBRARY)
