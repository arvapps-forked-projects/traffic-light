LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := iperf3
LOCAL_MODULE_FILENAME := libiperf3.so

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/.. \
    $(LOCAL_PATH)/../iperf/src

LOCAL_SRC_FILES := \
    ../iperf/src/main.c \
    ../iperf/src/cjson.c \
    ../iperf/src/dscp.c \
    ../iperf/src/iperf_api.c \
    ../iperf/src/iperf_auth.c \
    ../iperf/src/iperf_client_api.c \
    ../iperf/src/iperf_error.c \
    ../iperf/src/iperf_locale.c \
    ../iperf/src/iperf_sctp.c \
    ../iperf/src/iperf_server_api.c \
    ../iperf/src/iperf_tcp.c \
    ../iperf/src/iperf_time.c \
    ../iperf/src/iperf_udp.c \
    ../iperf/src/iperf_util.c \
    ../iperf/src/iperf_pthread.c \
    ../iperf/src/net.c \
    ../iperf/src/tcp_info.c \
    ../iperf/src/timer.c \
    ../iperf/src/units.c

LOCAL_CFLAGS := -DHAVE_CONFIG_H -DHAVE_PTHREAD

include $(BUILD_EXECUTABLE)