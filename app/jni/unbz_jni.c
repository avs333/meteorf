#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/types.h>
#include <unistd.h>
#include <jni.h>
#include <errno.h>
#include <syscall.h>
#include <android/log.h>
#include "bzlib.h"

#define OUTBUFSZ (16*1024)
#define log_err(fmt, args...)   __android_log_print(ANDROID_LOG_ERROR, "meteoinfo:libbz2_jni", fmt, ##args)
#define log_msg(fmt, args...)   __android_log_print(ANDROID_LOG_INFO, "meteoinfo:libbz2_jni", fmt, ##args)

void bz_internal_error ( int errcode ) { /* java callback required */ }

JNIEXPORT jint Java_ru_meteoinfo_Util_unBzip2(JNIEnv *env, jobject obj, jbyteArray jb, jstring jfile)
{
    const char *file = 0;
    int  count, fd, i, k, to_write, ret = 0;
    bz_stream *strm = 0;
    jbyte *in = 0, *out = 0;

	log_msg("unBzip2 starting");

	file = (*env)->GetStringUTFChars(env,jfile,NULL);
        if(!file) {
	    log_err("null filename");
	    return -1;
	}

	fd = open(file, O_RDWR|O_CREAT|O_TRUNC, 0666);
        (*env)->ReleaseStringUTFChars(env,jfile,file);

	if(fd < 0) {
	    log_err("error creating output file");	
	    return -2;
	}

	count = (*env)->GetArrayLength(env, jb);
	if(count <= 0 || count > 1024*1024) {
	    log_err("invalid byte array supplied");
	    return -3;
	}
	
	in = (jbyte *) malloc(count);
	out = (jbyte *) malloc(OUTBUFSZ);
	strm = (bz_stream *) calloc(1, sizeof(bz_stream));
	
	if(!in || !out || !strm) {
	    log_err("no memory");	
	    ret = -4;
	    goto exit;
	}

	(*env)->GetByteArrayRegion(env, jb, 0, count, in);
	
	k = BZ2_bzDecompressInit(strm, 3, 0);
	if(k != BZ_OK) {
	    log_err("error %d in BZ2_bzDecompressInit()", k);	
	    ret = -5;
	    goto exit; 	
	}

	/* to be hopefully updated by the library itself */
	strm->avail_in = count;
	strm->next_in = (char *) in; 

#if 0	
	if(strncmp(strm->next_in, "Rez \n", 5) == 0) {
	    strm->avail_in -= 5;
	    strm->next_in += 5; 	
	}
#endif

	while(1) {
	    strm->next_out = (char *) out;
	    strm->avail_out = OUTBUFSZ;
	    k = BZ2_bzDecompress(strm); 	
	    if(k != BZ_STREAM_END && k != BZ_OK) {
		log_err("BZ2_bzDecompress returned error %d", k);
		ret = -6;
		goto exit;
	    }
	    to_write = OUTBUFSZ - strm->avail_out;	
	    if(to_write != 0) {
		errno = 0;
		i = write(fd, out, to_write);
		if(i != to_write) {
		    if(i < 0) log_err("write error: %s", strerror(errno));
		    else log_err("write error: %d bytes requested, %d bytes written", to_write, i);
		    ret = -7;
		    goto exit;
		}
	    }	
	    if(k == BZ_STREAM_END) break;
	}

	close(fd);
	ret = strm->total_out_lo32;

	log_msg("done, decompressed size=%d", ret);	

	k = BZ2_bzDecompressEnd(strm);
	strm = 0;
    exit:
	if(in) free(in);
	if(out) free(out);
	if(strm) free(strm);

	return ret;

}

JNIEXPORT jint Java_ru_meteoinfo_Util_gettid(JNIEnv *env, jobject obj) {
    return syscall(__NR_gettid);
}



