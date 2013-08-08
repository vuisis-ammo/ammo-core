
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include "zlib.h"
#include <jni.h>
#include <unistd.h>

JNIEXPORT jlong JNICALL
Java_edu_vu_isis_ammo_util_CRC32_updateBuffer( 
	JNIEnv *env,
	jlong crc,
	jint off,
	jint len,
	jobject byteBuffer)
{
	char* buf = (*env)->GetDirectBufferAddress(env,byteBuffer);
	if( buf == NULL )
		return 0;
	jlong result = crc32(crc, (const Bytef*)(buf + off), len);
	
	return result;
}

JNIEXPORT jlong JNICALL
Java_edu_vu_isis_ammo_util_CRC32_updateBytes( 
	JNIEnv *env,
	jlong crc,
	jint off,
	jint len,
	jbyteArray byteArray)
{
	char* bytes = (*env)->GetByteArrayElements(env, byteArray, NULL);
	if( bytes == NULL )
		return 0;
	jlong result = crc32(crc, (const Bytef*)(bytes + off), len);
	(*env)->ReleaseByteArrayElements(env, byteArray, bytes, 0);
	return result;
}
