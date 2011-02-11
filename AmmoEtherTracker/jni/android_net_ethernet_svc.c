/*
 * Copyright 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Yi Sun(beyounn@gmail.com)
 */

#define LOG_TAG "EthernetSVC"

#include <jni.h>
#include <inttypes.h>
//#include <utils/misc.h>
//#include <android_runtime/AndroidRuntime.h>
//#include <utils/Log.h>
#include <android/log.h>
#include <asm/types.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <poll.h>
#include <net/if_arp.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <dirent.h>
#include <stdio.h>
#include <pthread.h>

#define PROPERY_VALUE_MAX  256

static jmethodID method_onWaitForEvent;
static JNIEnv* jenv;
static JavaVM *jvm;
static jobject java_class;
static pthread_t thread;
static char iface[256];
static char valid_iface[10][256];
static int valid_ifaces = 0;

#define ETH_PKG_NAME "edu/isis/ammmo/ethertracker"
    
static struct fieldIds {
	jclass dhcpInfoClass;
	jmethodID constructorId;
	jfieldID ipaddress;
	jfieldID gateway;
	jfieldID netmask;
	jfieldID dns1;
	jfieldID dns2;
	jfieldID serverAddress;
	jfieldID leaseDuration;
} dhcpInfoFieldIds;

typedef struct _interface_info_t {
	unsigned int i;                            /* interface index        */
	char *name;                       /* name (eth0, eth1, ...) */
	struct _interface_info_t *next;
} interface_info_t;

#define NL_SOCK_INV      -1
#define RET_STR_SZ       4096
#define NL_POLL_MSG_SZ   8*1024
#define SYSFS_PATH_MAX   256
static const char SYSFS_CLASS_NET[]     = "/sys/class/net";
static int nl_socket_msg = NL_SOCK_INV;
static struct sockaddr_nl addr_msg;
static int nl_socket_poll = NL_SOCK_INV;
static struct sockaddr_nl addr_poll;
static int getinterfacename(int index, char *name, size_t len);

/*

	Function : parse_msg_detail

	This function prints the details of a 
	RTM* message.

	@input mlmsghdr : The RTM message
*/
void parse_msg_detail (struct nlmsghdr* nl_msg)
{
	int rtl;
	struct ifinfomsg *linfo;
	struct rtattr *rtap;

	linfo = (struct ifinfomsg *)NLMSG_DATA(nl_msg);

	__android_log_print(
			ANDROID_LOG_INFO, 
			LOG_TAG,  
			"got a new link creation message: family (%d), type (%d), flags(%x) index(%d)\n", 
			linfo->ifi_family, 
			linfo->ifi_type, 
			linfo->ifi_flags, 
			linfo->ifi_index);

	/* determine the type and name of the link */
	rtap = IFLA_RTA(linfo);

	/* get length of attributes */
	rtl = IFLA_PAYLOAD(nl_msg);

	/* process attributes of the link info message */
	for(;RTA_OK(rtap, rtl); rtap=RTA_NEXT(rtap, rtl)) {

		if (rtap->rta_type == IFLA_IFNAME) { /* get interface name */
			strncpy(iface, RTA_DATA(rtap), RTA_PAYLOAD(rtap));

			__android_log_print(
					ANDROID_LOG_INFO, 
					LOG_TAG,  
					"interface %s \n", iface);

		}

		else if (rtap->rta_type == IFLA_LINK) { /* link type */

			int x = *(int *)RTA_DATA(rtap);

			__android_log_print(
					ANDROID_LOG_INFO,
					LOG_TAG,  
					"interface linktype %x \n", x);

		}
		else if (rtap->rta_type == IFLA_LINKMODE) { /* link mode */

			int x = *(int *)RTA_DATA(rtap);

			__android_log_print(
					ANDROID_LOG_INFO, 
					LOG_TAG,  
					"interface linkmode %x \n", x);

		}
		else if (rtap->rta_type == IFLA_OPERSTATE) { /* link type */
			int x = *(int *)RTA_DATA(rtap);
			switch (x)
			{
				case (IF_OPER_DOWN):
					__android_log_print(
							ANDROID_LOG_INFO, 
							LOG_TAG,  
							"IF_OPER_DOWN");
					break;
				case (IF_OPER_UP):
					__android_log_print(
							ANDROID_LOG_INFO, 
							LOG_TAG,  
							"IF_OPER_UP");
					break;
				case (IF_OPER_LOWERLAYERDOWN):
					__android_log_print(
							ANDROID_LOG_INFO, 
							LOG_TAG,  
							"IF_OPER_LOWERLAYERDOWN");
					break;
				case (IF_OPER_TESTING):
					__android_log_print(
							ANDROID_LOG_INFO, 
							LOG_TAG,  
							"IF_OPER_TESTING");
					break;
				case (IF_OPER_DORMANT):
					__android_log_print(
							ANDROID_LOG_INFO, 
							LOG_TAG,  
							"IF_OPER_DORMANT");
					break;
				case (IF_OPER_UNKNOWN):
					__android_log_print(
							ANDROID_LOG_INFO, 
							LOG_TAG,  
							"IF_OPER_UNKNOWN");
					break;
				case (IF_OPER_NOTPRESENT):
					__android_log_print(
							ANDROID_LOG_INFO, 
							LOG_TAG,  
							"IF_OPER_NOTPRESENT");
					break;
			}
			__android_log_print(
					ANDROID_LOG_INFO, 
					LOG_TAG,  
					"interface new operstate %x \n", x);
		}
	}

}


/*
	Function : process_msg
	It parses the header received from the RTM* message
	It send a interface Up msg if it receives a RTM_NEWADDR
	and a interface Down msg if it receives the RTM_DELLINK

	Notes: Right now I can't detect when the interface comes
	up but the link is not up. There must be a way to detect that 
	from the RTM_NEWLINK messages by parsing the flags and 
	operstates.

	input :
		nlmsghdr : The header received from the RTM* messages

	output : char* : buffer containing the msg 
			"Interface <interface name> Up"
			"Interface <interface name> Down"
*/
static char* process_msg (struct nlmsghdr* nl_msg)
{ 
	char* buffer = 0;

	if (nl_msg->nlmsg_type == RTM_NEWLINK) {

		parse_msg_detail (nl_msg);

		buffer = (char*)malloc (100);
		memset (buffer, 0, 100);
		sprintf (buffer, "New Link %s Message", iface);
		__android_log_print(
				ANDROID_LOG_INFO,
				LOG_TAG,  
				"BUFFER %s", buffer);

	} else if (nl_msg->nlmsg_type == RTM_DELLINK) {

		parse_msg_detail (nl_msg);

		buffer = (char*)malloc (100);
		memset (buffer, 0, 100);
		sprintf (buffer, "Interface %s Down", iface);
		__android_log_print(
				ANDROID_LOG_INFO,
				LOG_TAG,  
				"BUFFER %s", buffer);

	} else if (nl_msg->nlmsg_type == RTM_NEWADDR) {

		parse_msg_detail (nl_msg);

		buffer = (char*)malloc (100);
		memset (buffer, 0, 100);
		sprintf (buffer, "Interface %s Up", iface);
	}

	return buffer;

}

/*
	Function waitForEvent

	This function makes a call on the socket.recvmsg 
	and waits for RTM msgs to come in. It then makes a call on 
	to process_msg for actual parsing.

	@return jstring : Returns the status of the link
*/

	jstring 
Java_edu_vu_isis_ammmo_ethertracker_EthTrackSvc_waitForEvent(JNIEnv *env,
		jobject clazz)
{
	char *buff;
	struct nlmsghdr *nh;
	struct ifinfomsg *einfo;
	struct iovec iov;
	struct msghdr msg;
	char *result = NULL;
	char rbuf[4096];
	unsigned int left;
	interface_info_t *info;
	int len;

	__android_log_print(
			ANDROID_LOG_INFO, 
			LOG_TAG,
			"Poll events from ethernet devices");

	/*
	 *wait on uevent netlink socket for the ethernet device
	 */

	buff = (char *)malloc(NL_POLL_MSG_SZ);
	memset (buff, 0, NL_POLL_MSG_SZ);

	if (!buff) {
		__android_log_print(
				ANDROID_LOG_INFO, 
				LOG_TAG,
				"Allocate poll buffer failed");
		goto error;
	}

	iov.iov_base = buff;
	iov.iov_len = NL_POLL_MSG_SZ;
	memset(&msg,   0, sizeof(msg));
	msg.msg_name = (void *)&addr_msg;
	msg.msg_namelen =  sizeof(addr_msg);
	msg.msg_iov =  &iov;
	msg.msg_iovlen =  1;
	msg.msg_control =  NULL;
	msg.msg_controllen =  0;
	msg.msg_flags =  0;
	char * ethstatmsg; 

	/*
	   __android_log_print(
	   ANDROID_LOG_INFO, 
	   LOG_TAG,
	   "Making a call on recvmsg");
	 */
	if((len = recvmsg(nl_socket_poll, &msg, 0))>= 0) {
		__android_log_print(ANDROID_LOG_INFO, LOG_TAG,
				"recvmsg get data");
		result = rbuf;
		left = 4096;
		rbuf[0] = '\0';
		for (nh = (struct nlmsghdr *) buff; NLMSG_OK (nh, len);
				nh = NLMSG_NEXT (nh, len))
		{

			if (nh->nlmsg_type == NLMSG_DONE){
				//LOGE("Did not find useful eth interface information");
				goto error;
			}

			if (nh->nlmsg_type == NLMSG_ERROR){

				/* Do some error handling. */
				//LOGE("Read device name failed");
				goto error;
			}

			if (nh->nlmsg_type == RTM_DELLINK ||
					nh->nlmsg_type == RTM_NEWADDR)
				ethstatmsg = process_msg (nh);
			else
				ethstatmsg = result;

		}
		//LOGI("Done parsing");
		rbuf[4096 - left] = '\0';
		//LOGI("poll state :%s, left:%d",rbuf, left);
	}
	if (ethstatmsg != 0)
		return (*env)->NewStringUTF(env, ethstatmsg);
	else
		return (*env)->NewStringUTF(env, rbuf);

error:
	if(buff)
		free(buff);
	return (*env)->NewStringUTF(env, rbuf);
}


/*

	This function is not used now
*/
static int netlink_send_dump_request(int sock, int type, int family) {
	int ret;
	char buf[4096];
	struct sockaddr_nl snl;
	struct nlmsghdr *nlh;
	struct rtgenmsg *g;

	memset(&snl, 0, sizeof(snl));
	snl.nl_family = AF_NETLINK;

	memset(buf, 0, sizeof(buf));
	nlh = (struct nlmsghdr *)buf;
	g = (struct rtgenmsg *)(buf + sizeof(struct nlmsghdr));

	nlh->nlmsg_len = NLMSG_LENGTH(sizeof(struct rtgenmsg));
	nlh->nlmsg_flags = NLM_F_REQUEST|NLM_F_DUMP;
	nlh->nlmsg_type = type;
	g->rtgen_family = family;

	ret = sendto(sock, buf, nlh->nlmsg_len, 0, (struct sockaddr *)&snl,
			sizeof(snl));
	if (ret < 0) {
		perror("netlink_send_dump_request sendto");
		return -1;
	}

	return ret;
}


/*

	Function initEthernetNative

	This function is called from the Java side. It 
	initiates the netlink socket. Creates them and then binds them.
 
	This function needs to be called first from the java side
	followed by the waitForEvent function.
 */

jint Java_edu_vu_isis_ammmo_ethertracker_EthTrackSvc_initEthernetNative(JNIEnv *env,
		jobject clazz)
{
	int ret = -1;

	//LOGI("==>%s",__FUNCTION__);
	//	__android_log_print(ANDROID_LOG_INFO, LOG_TAG,
	//            "==>%s",__FUNCTION__);
	memset(&addr_msg, 0, sizeof(struct sockaddr_nl));
	addr_msg.nl_family = AF_NETLINK;
	memset(&addr_poll, 0, sizeof(struct sockaddr_nl));
	addr_poll.nl_family = AF_NETLINK;
	addr_poll.nl_pid = 0;//getpid();
	addr_poll.nl_groups = RTMGRP_LINK | RTMGRP_IPV4_IFADDR;

	/*
	 *Create connection to netlink socket
	 */
	nl_socket_msg = socket(AF_NETLINK,SOCK_RAW,NETLINK_ROUTE);
	if (nl_socket_msg <= 0) {
		//LOGE("Can not create netlink msg socket");
		goto error;
	}

	//	__android_log_print(ANDROID_LOG_INFO, LOG_TAG,
	//            "Got socket for msg"); 

	if (bind(nl_socket_msg, (struct sockaddr *)(&addr_msg),
				sizeof(struct sockaddr_nl))) {
		//LOGE("Can not bind to netlink msg socket");
		goto error;
	}
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG,
			"Bound msg socket .... Getting poll socket"); 

	nl_socket_poll = socket(AF_NETLINK,SOCK_RAW,NETLINK_ROUTE);
	if (nl_socket_poll <= 0) {
		//LOGE("Can not create netlink poll socket");
		__android_log_print(ANDROID_LOG_INFO, LOG_TAG,
				"Can not create netlink poll socket"); 
		goto error;
	}

	//	__android_log_print(ANDROID_LOG_INFO, LOG_TAG,
	//            "Got Poll socket ... Trying to bind poll socket"); 

	errno = 0;
	if(bind(nl_socket_poll, (struct sockaddr *)(&addr_poll),
				sizeof(struct sockaddr_nl))) {
		//LOGE("Can not bind to netlink poll socket,%s",strerror(errno));

		goto error;
	}

	//LOGE("%s exited with success",__FUNCTION__);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG,
			"%s exited with success",__FUNCTION__);



	return ret;
error:
	//LOGE("%s exited with error",__FUNCTION__);
	__android_log_print(ANDROID_LOG_INFO, LOG_TAG,
			"%s exited with error",__FUNCTION__);
	if (nl_socket_msg >0)
		close(nl_socket_msg);
	if (nl_socket_poll >0)
		close(nl_socket_poll);
	return ret;

}

/*

	This function is not used now
*/
jstring 
Java_edu_vu_isis_ammmo_ethertracker_EthTrackSvc_getInterfaceName (
		JNIEnv *env,
		jobject clazz,
		jint index
		)
{
	return (*env)->NewStringUTF(env, NULL);
}


/*

	This function is not used now
*/
jint 
Java_edu_vu_isis_ammmo_ethertracker_EthTrackSvc_getInterfaceCnt(
		JNIEnv *env,
		jobject clazz
		)
{
	return 0;
}

/*

	This is not used now
*/
static JNINativeMethod gEthernetMethods[] = {
	{"waitForEvent", "()Ljava/lang/String;",
		//(void *)android_net_ethernet_waitForEvent},
	(void *)Java_edu_vu_isis_ammmo_ethertracker_EthTrackSvc_waitForEvent},
{"getInterfaceName", "(I)Ljava/lang/String;",
	(void *)Java_edu_vu_isis_ammmo_ethertracker_EthTrackSvc_getInterfaceName},
{"initEthernetNative", "()I",
	(void *)Java_edu_vu_isis_ammmo_ethertracker_EthTrackSvc_initEthernetNative},
{"getInterfaceCnt","()I",
	(void *)Java_edu_vu_isis_ammmo_ethertracker_EthTrackSvc_getInterfaceCnt}
	};

/*

	This function is not used now
*/
int register_android_net_ethernet_EthernetManagerSvc (JNIEnv* env)
{
	jclass eth = (*env)->FindClass(env, ETH_PKG_NAME);
	//LOGI("Loading ethernet jni class");
	//LOG_FATAL_IF( eth== NULL, "Unable to find class " ETH_PKG_NAME);

	dhcpInfoFieldIds.dhcpInfoClass =
		(*env)->FindClass(env, "android/net/DhcpInfo");
	if (dhcpInfoFieldIds.dhcpInfoClass != NULL) {
		dhcpInfoFieldIds.constructorId =
			(*env)->GetMethodID(env, dhcpInfoFieldIds.dhcpInfoClass,
					"<init>", "()V");
		dhcpInfoFieldIds.ipaddress =
			(*env)->GetFieldID(env, dhcpInfoFieldIds.dhcpInfoClass,
					"ipAddress", "I");
		dhcpInfoFieldIds.gateway =
			(*env)->GetFieldID(env, dhcpInfoFieldIds.dhcpInfoClass,
					"gateway", "I");
		dhcpInfoFieldIds.netmask =
			(*env)->GetFieldID(env, dhcpInfoFieldIds.dhcpInfoClass,
					"netmask", "I");
		dhcpInfoFieldIds.dns1 =
			(*env)->GetFieldID(env, dhcpInfoFieldIds.dhcpInfoClass, "dns1", "I");
		dhcpInfoFieldIds.dns2 =
			(*env)->GetFieldID(env, dhcpInfoFieldIds.dhcpInfoClass, "dns2", "I");
		dhcpInfoFieldIds.serverAddress =
			(*env)->GetFieldID(env, dhcpInfoFieldIds.dhcpInfoClass,
					"serverAddress", "I");
		dhcpInfoFieldIds.leaseDuration =
			(*env)->GetFieldID(env, dhcpInfoFieldIds.dhcpInfoClass,
					"leaseDuration", "I");
	}

	//        return AndroidRuntime::registerNativeMethods(env,
	//ETH_PKG_NAME,
	//                                                     gEthernetMethods,
	//                 NELEM(gEthernetMethods));

	return 1; // just to stop the error
}

//};
