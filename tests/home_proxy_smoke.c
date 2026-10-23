/* Android native smoke test: no VPN consent, network changes, or NAS required.
 * Load the APK's actual forwarding library and exercise repeated/early stop.
 * This checks native initialization and cleanup, not end-to-end forwarding. */
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <sys/socket.h>
#include <unistd.h>

static int (*run_core)(const unsigned char *,unsigned int,int);
static void (*quit_core)(void);
static int tunnel_fd, result;
static const unsigned char config[]=
    "tunnel:\n  mtu: 1500\n  ipv4: 198.18.0.1\n"
    "socks5:\n  address: '192.168.1.66'\n  port: 7890\n  udp: udp\n"
    "misc:\n  task-stack-size: 24576\n  log-level: error\n";
static void *worker(void *unused) {
    result=run_core(config,sizeof(config)-1,tunnel_fd);
    return NULL;
}
int main(int argc,char **argv) {
    if(argc!=2) return 2;
    void *library=dlopen(argv[1],RTLD_NOW);
    if(!library) { fprintf(stderr,"%s\n",dlerror()); return 3; }
    run_core=dlsym(library,"hev_socks5_tunnel_main_from_str");
    quit_core=dlsym(library,"hev_socks5_tunnel_quit");
    if(!run_core || !quit_core) return 4;
    for(int iteration=0;iteration<20;iteration++) {
        int pair[2]; pthread_t thread;
        if(socketpair(AF_UNIX,SOCK_DGRAM,0,pair)) return 5;
        tunnel_fd=pair[0];result=-99;
        // Exercise the early stop handshake as well as a running engine.
        if(iteration%2==0) quit_core();
        if(pthread_create(&thread,NULL,worker,NULL)) return 6;
        if(iteration%2!=0) { usleep(100000); quit_core(); }
        pthread_join(thread,NULL);
        close(pair[0]);close(pair[1]);
        if(result!=0) { fprintf(stderr,"iteration %d returned %d\n",iteration,result); return 7; }
    }
    dlclose(library);
    puts("PASS 20 native core start/stop cycles, including early cancellation");
    return 0;
}
