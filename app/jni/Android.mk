LOCAL_PATH := $(call my-dir)

bzip_cflags := \
    -O3 \
    -DUSE_MMAP \
    -DBZ_NO_STDIO \
    -Wno-unused-parameter \

bzlib_files := \
    blocksort.c \
    bzlib.c \
    compress.c \
    crctable.c \
    decompress.c \
    huffman.c \
    randtable.c \

include $(CLEAR_VARS)
# measurements show that the ARM version of ZLib is about x1.17 faster
# than the thumb one...
LOCAL_MODULE := bz
LOCAL_CFLAGS += $(bzip_cflags)
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)
LOCAL_SRC_FILES := $(bzlib_files)
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := bz2_jni
LOCAL_CFLAGS += $(bzip_cflags)
LOCAL_SRC_FILES := unbz_jni.c
LOCAL_STATIC_LIBRARIES := bz
LOCAL_LDLIBS    := -llog
include $(BUILD_SHARED_LIBRARY)


