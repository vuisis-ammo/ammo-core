/*
 * Copyright 2009 Cedric Priscal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <errno.h>
#include <termios.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <jni.h>
#include <linux/termios.h>
#include <sys/socket.h>
#include <netinet/in.h>

#include "android/log.h"
static const char *TAG="serial_port";
#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)


JNIEXPORT void JNICALL
Java_edu_vu_isis_ammo_util_TTLUtil_setNativeTTL ( JNIEnv *env,
						 jclass clazz,
						 jint sockd,
						 jint ttl)
{
  int rc = setsockopt(sockd, IPPROTO_IP, IP_MULTICAST_TTL, (char *)&ttl, sizeof(ttl));

  __android_log_print(
		      ANDROID_LOG_INFO, 
		      "ammo.net.mcast.TTL",  
		      "jni setttl  %d %d %d \n", ttl, rc, sockd);
    return;
}
