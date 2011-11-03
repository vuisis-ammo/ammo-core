LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)


LOCAL_MODULE    := serialchan
LOCAL_SRC_FILES := SerialPort.c
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog 

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := ethrmon
LOCAL_SRC_FILES := android_net_ethernet_svc.c
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog 

include $(BUILD_SHARED_LIBRARY)
