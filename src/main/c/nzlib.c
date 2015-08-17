#include <stdlib.h>
#include <zlib.h>
#include <jni.h>
#include <stdarg.h>
#include <stdio.h>

#include "at_yawk_rjoin_zlib_NZlib.h"

void fail(JNIEnv *env, char *fmt, ...) {
    char buf[128];

    va_list vl;
    va_start(vl, fmt);

    vsnprintf(buf, sizeof(buf), fmt, vl);

    va_end(vl);

    (*env)->ThrowNew(env, (*env)->FindClass(env, "at/yawk/rjoin/zlib/ZlibException"), buf);
}

jlong Java_at_yawk_rjoin_zlib_NZlib_open(JNIEnv *env, jclass jc, jboolean inf) {
    int result;
    z_stream *stream = malloc(sizeof(z_stream));
    stream->zalloc = Z_NULL;
    stream->zfree = Z_NULL;
    stream->opaque = Z_NULL;
    stream->next_in = Z_NULL;
    stream->next_out = Z_NULL;
    if (inf) {
        result = inflateInit(stream);
    } else {
        result = deflateInit(stream, Z_DEFAULT_COMPRESSION);
    }
    // implicit throw: while ZlibException is not defined on the method, this is an extraordinary condition and we shouldn't catch it anyway
    if (result != Z_OK) { fail(env, "Failed to allocate stream", result); }
    return (jlong) stream;
}

jfieldID positionField = NULL;
jfieldID limitField = NULL;

jboolean Java_at_yawk_rjoin_zlib_NZlib_work(JNIEnv *env, jclass jc, jboolean inf, jlong stream, jobject in, jobject out, jboolean finish) {
    jint in_pos;
    jint out_pos;
    char *in_addr;
    char *out_addr;
    int result;

    if (!positionField) {
        positionField = (*env)->GetFieldID(env, (*env)->FindClass(env, "java/nio/Buffer"), "position", "I");
    }
    if (!limitField) {
        limitField = (*env)->GetFieldID(env, (*env)->FindClass(env, "java/nio/Buffer"), "limit", "I");
    }


    if (in) {
        in_pos = (*env)->GetIntField(env, in, positionField);
        in_addr = (char*) (*env)->GetDirectBufferAddress(env, in);
        ((z_stream*) stream)->next_in = in_addr + in_pos;
        ((z_stream*) stream)->avail_in = (*env)->GetIntField(env, in, limitField) - in_pos;
    }

    out_pos = (*env)->GetIntField(env, out, positionField);
    out_addr = (char*) (*env)->GetDirectBufferAddress(env, out);
    ((z_stream*) stream)->next_out = out_addr + out_pos;
    ((z_stream*) stream)->avail_out = (*env)->GetIntField(env, out, limitField) - out_pos;

    if (inf) {
        result = inflate((z_stream*) stream, Z_PARTIAL_FLUSH);
    } else {
        result = deflate((z_stream*) stream, finish ? Z_FINISH : Z_NO_FLUSH);
    }

    if (in) {
        (*env)->SetIntField(env, in, positionField, (size_t) ((z_stream*) stream)->next_in - (size_t) in_addr);
    }
    (*env)->SetIntField(env, out, positionField, (size_t) ((z_stream*) stream)->next_out - (size_t) out_addr);

    switch(result) {
    case Z_STREAM_END:
        return JNI_TRUE;
    case Z_OK:
    case Z_BUF_ERROR:
        return JNI_FALSE;
    default:
        fail(env, "Failed to process input: %d", result);
    }
}

void Java_at_yawk_rjoin_zlib_NZlib_reset(JNIEnv *env, jclass jc, jboolean inf, jlong stream) {
    int result;
    if (inf) {
        result = inflateReset((z_stream*) stream);
    } else {
        result = deflateReset((z_stream*) stream);
    }
}

void Java_at_yawk_rjoin_zlib_NZlib_close(JNIEnv *env, jclass jc, jboolean inf, jlong stream) {
    if (stream) {
        // we ignore result
        if (inf) {
            inflateEnd((z_stream*) stream);
        } else {
            deflateEnd((z_stream*) stream);
        }
        free((z_stream*) stream);
    }
}