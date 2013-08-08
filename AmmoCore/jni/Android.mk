LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := ammocore
LOCAL_SRC_FILES := SerialPort.c
LOCAL_SRC_FILES += android_net_ethernet_svc.c
LOCAL_SRC_FILES += CRC32.c
LOCAL_SRC_FILES += MulticastTTL.c
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog -lz
include $(BUILD_SHARED_LIBRARY)

