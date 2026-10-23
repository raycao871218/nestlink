#include <jni.h>
#include <android/multinetwork.h>
#include <sys/socket.h>
#include <unistd.h>
#include "hev-socks5-tunnel.h"

// All core sockets are created on run's native worker. Protect before any I/O,
// then pin to the selected Wi-Fi so the proxy can never recurse into the VPN.
static _Thread_local JNIEnv *worker_env;
static _Thread_local jobject vpn_service;
static _Thread_local jmethodID protect_method;
static _Thread_local net_handle_t physical_network;
int __real_socket(int,int,int);
int __wrap_socket(int family,int type,int protocol) {
    int fd=__real_socket(family,type,protocol);
    if(fd<0 || (family!=AF_INET && family!=AF_INET6)) return fd;
    if(!worker_env || !(*worker_env)->CallBooleanMethod(worker_env,vpn_service,protect_method,fd)
       || android_setsocknetwork(physical_network,fd)!=0) {
        close(fd); return -1;
    }
    return fd;
}
JNIEXPORT jint JNICALL Java_com_naslink_android_HomeProxyEngine_run(JNIEnv *env,jclass cls,jstring config,jint fd,jlong network,jobject service) {
    worker_env=env; vpn_service=service; physical_network=(net_handle_t)network;
    jclass type=(*env)->GetObjectClass(env,service);
    protect_method=(*env)->GetMethodID(env,type,"protect","(I)Z");
    if(!protect_method) return -1;
    const char *text=(*env)->GetStringUTFChars(env,config,0);
    if(!text) return -1;
    int result=hev_socks5_tunnel_main_from_str((const unsigned char *)text,(*env)->GetStringUTFLength(env,config),fd);
    (*env)->ReleaseStringUTFChars(env,config,text);
    worker_env=0; vpn_service=0;
    return result;
}
JNIEXPORT void JNICALL Java_com_naslink_android_HomeProxyEngine_quit(JNIEnv *env,jclass cls) { hev_socks5_tunnel_quit(); }
