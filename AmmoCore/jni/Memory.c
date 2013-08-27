#include <jni.h>
#include <unistd.h>


typedef struct {
	jclass byteArrayClass;
	jclass nullPointerExceptionClass;
} JniCache;
static JniCache jniCache;

JNIEXPORT void JNICALL
Java_edu_vu_isis_ammo_util_Memory_nativeInit(
		JNIEnv* env)
{
	jniCache.byteArrayClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "[B"));
	jniCache.nullPointerExceptionClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/NullPointerException"));
}


JNIEXPORT void JNICALL
Java_edu_vu_isis_ammo_util_Memory_memmove(
		JNIEnv* env,
		jclass c,
		jobject dstObject,
		jint dstOffset,
		jobject srcObject,
		jint srcOffset,
		jlong length)
{
	jbyteArray dstByteArray = NULL;
	jbyteArray srcByteArray = NULL;
	jbyte* dst = NULL;
	jbyte* src = NULL;

	// make sure we are valid
	if( dstObject == NULL )
		(*env)->ThrowNew( env, jniCache.nullPointerExceptionClass, "Destination" );
	if( srcObject == NULL )
		(*env)->ThrowNew( env, jniCache.nullPointerExceptionClass, "Source" );


	// detect the types and grab the pointers
	if( (*env)->IsInstanceOf(env, srcObject, jniCache.byteArrayClass) )
	{
		srcByteArray = (jbyteArray) srcObject;
		src = (*env)->GetByteArrayElements(env, srcByteArray, NULL);
	}
	else
		src = (jbyte*) (*env)->GetDirectBufferAddress(env, srcObject);

	if( (*env)->IsInstanceOf(env, dstObject, jniCache.byteArrayClass) )
	{
		dstByteArray = (jbyteArray) dstObject;
		dst = (*env)->GetByteArrayElements(env, dstByteArray, NULL);
	}
	else
		dst = (jbyte*) (*env)->GetDirectBufferAddress(env, dstObject);
		

	// do the cpy (faster than memmove)
	memcpy(dst + dstOffset, src + srcOffset, length);

	// now make sure to release if these are java byte[]
	if( dstByteArray != NULL )
		(*env)->ReleaseByteArrayElements(env, dstByteArray, dst, 0); // 0 to copy bytes back
	if( srcByteArray != NULL )
		(*env)->ReleaseByteArrayElements(env, srcByteArray, src, JNI_ABORT); // JNI_ABORT to skip back copy
}


