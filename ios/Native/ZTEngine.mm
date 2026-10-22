// SPDX-License-Identifier: GPL-3.0-or-later
#import "ZTEngine.h"
#include "ZeroTierOne.h"
#include "IPv4Bridge.hpp"
#include "MoonConnectivity.hpp"
#include <fcntl.h>
#include <unistd.h>
#include <memory>

static int64_t nowMS(){return (int64_t)(NSDate.date.timeIntervalSince1970*1000);}
static NSError *engineError(NSString *s){return [NSError errorWithDomain:@"NestLink.Engine" code:1 userInfo:@{NSLocalizedDescriptionKey:s}];}

@interface ZTEngine () {
@public
    dispatch_queue_t _queue;
    ZT_Node *_node;
    uint64_t _network;
    uint32_t _gateway;
    int _socket;
    dispatch_source_t _reader,_timer;
    NSURL *_store;
    std::unique_ptr<IPv4Bridge> _bridge;
    volatile int64_t _deadline;
    int64_t _lastExpire;
    uint64_t _tx,_rx,_sendErrors;
    NSString *_address,*_storageError;
    NSArray<NSDictionary<NSString *,NSString *> *> *_moons;
    int64_t _observedSince;
}
- (NSURL *)stateURL:(ZT_StateObjectType)type identifier:(const uint64_t *)identifier;
- (void)configure:(const ZT_VirtualNetworkConfig *)config operation:(ZT_VirtualNetworkConfigOperation)operation;
- (void)readWire;
- (void)closeEngine;
@end

static ZTEngine *engine(void *p){return (__bridge ZTEngine *)p;}
static void putState(ZT_Node *,void *u,void *,ZT_StateObjectType type,const uint64_t id[2],const void *data,int len){
    ZTEngine *e=engine(u);NSURL *url=[e stateURL:type identifier:id];if(!url||e->_storageError)return;
    if(len<0){[NSFileManager.defaultManager removeItemAtURL:url error:nil];return;}
    NSError *error=nil;
    [NSFileManager.defaultManager createDirectoryAtURL:url.URLByDeletingLastPathComponent withIntermediateDirectories:YES
        attributes:@{NSFilePosixPermissions:@0700,NSFileProtectionKey:NSFileProtectionCompleteUntilFirstUserAuthentication} error:&error];
    if(!error){
        NSData *bytes=[NSData dataWithBytes:data length:len];
        [bytes writeToURL:url options:NSDataWritingAtomic|NSDataWritingFileProtectionCompleteUntilFirstUserAuthentication error:&error];
        if(!error)[NSFileManager.defaultManager setAttributes:@{NSFilePosixPermissions:@0600} ofItemAtPath:url.path error:&error];
    }
    if(error){e->_storageError=@"无法安全保存 ZeroTier 身份或网络状态";if(e.failureHandler)e.failureHandler(e->_storageError);}
}
static int getState(ZT_Node *,void *u,void *,ZT_StateObjectType type,const uint64_t id[2],void *buffer,unsigned cap){
    ZTEngine *e=engine(u);NSURL *url=[e stateURL:type identifier:id];if(!url)return -1;
    NSError *error=nil;NSData *data=[NSData dataWithContentsOfURL:url options:0 error:&error];
    if(!data){
        if([NSFileManager.defaultManager fileExistsAtPath:url.path])e->_storageError=@"已保存的 ZeroTier 状态无法读取，请解锁设备后重试";
        return -1;
    }
    if(data.length>cap)return -1;memcpy(buffer,data.bytes,data.length);return (int)data.length;
}
static int sendWire(ZT_Node *,void *u,void *,int64_t,const sockaddr_storage *remote,const void *bytes,unsigned len,unsigned){
    ZTEngine *e=engine(u);if(e->_socket<0||remote->ss_family!=AF_INET)return -1;
    sockaddr_in address=*(const sockaddr_in *)remote;address.sin_len=sizeof(address);
    ssize_t n=sendto(e->_socket,bytes,len,0,(sockaddr *)&address,sizeof(address));
    if(n!=(ssize_t)len){++e->_sendErrors;return -1;}++e->_tx;return 0;
}
static void frame(ZT_Node *,void *u,void *,uint64_t nw,void **,uint64_t source,uint64_t,unsigned type,unsigned,const void *data,unsigned len){
    ZTEngine *e=engine(u);if(nw!=e->_network||!e->_bridge)return;
    if(type==0x0806)e->_bridge->inboundARP(source,(const uint8_t *)data,len);
    else if(type==0x0800&&IPv4Bridge::validIP((const uint8_t *)data,len)&&e.packetHandler){
        auto p=(const uint8_t *)data;unsigned size=(unsigned(p[2])<<8)|p[3];
        e.packetHandler([NSData dataWithBytes:data length:size]);
    }
}
static int config(ZT_Node *,void *u,void *,uint64_t,void **,ZT_VirtualNetworkConfigOperation op,const ZT_VirtualNetworkConfig *c){
    // The SDK forbids calling modifying APIs from this callback. Copy then enqueue.
    ZTEngine *e=engine(u);
    if(c){ZT_VirtualNetworkConfig copy=*c;dispatch_async(e->_queue,^{[e configure:&copy operation:op];});}
    else dispatch_async(e->_queue,^{[e configure:nullptr operation:op];});
    return 0;
}
static void event(ZT_Node *,void *u,void *,ZT_Event event,const void *){
    ZTEngine *e=engine(u);
    if(event==ZT_EVENT_FATAL_ERROR_IDENTITY_COLLISION&&e.failureHandler)e.failureHandler(@"ZeroTier 身份冲突，请断开后检查设备身份");
}

@implementation ZTEngine
- (instancetype)init {if((self=[super init])){_queue=dispatch_queue_create("app.nestlink.engine",DISPATCH_QUEUE_SERIAL);_socket=-1;_address=@"";}return self;}
- (NSURL *)stateURL:(ZT_StateObjectType)type identifier:(const uint64_t *)id {
    NSString *name=nil;
    switch(type){
        case ZT_STATE_OBJECT_IDENTITY_PUBLIC:name=@"identity.public";break;
        case ZT_STATE_OBJECT_IDENTITY_SECRET:name=@"identity.secret";break;
        case ZT_STATE_OBJECT_PLANET:name=@"planet";break;
        case ZT_STATE_OBJECT_MOON:name=[NSString stringWithFormat:@"moons.d/%016llx.moon",id[0]];break;
        case ZT_STATE_OBJECT_NETWORK_CONFIG:name=[NSString stringWithFormat:@"networks.d/%016llx.conf",id[0]];break;
        case ZT_STATE_OBJECT_PEER:name=[NSString stringWithFormat:@"peers.d/%010llx",id[0]];break;
        default:return nil;
    }
    return [_store URLByAppendingPathComponent:name];
}
- (BOOL)startWithNetwork:(NSString *)network gateway:(NSString *)gateway moons:(NSArray<NSDictionary<NSString *,NSString *> *> *)moons store:(NSURL *)store planet:(NSURL *)planet error:(NSError **)error {
    __block NSError *failure=nil;
    dispatch_sync(_queue,^{
        if(self->_node){failure=engineError(@"引擎已启动");return;}
        self->_store=store;self->_storageError=nil;self->_tx=0;self->_rx=0;self->_sendErrors=0;
        self->_moons=[moons copy];self->_observedSince=nowMS();
        self->_network=strtoull(network.UTF8String,nullptr,16);
        in_addr via={};if(inet_pton(AF_INET,gateway.UTF8String,&via)!=1||!self->_network){failure=engineError(@"网络 ID 或 NAS 地址无效");return;}
        self->_gateway=ntohl(via.s_addr);
        [NSFileManager.defaultManager createDirectoryAtURL:store withIntermediateDirectories:YES
            attributes:@{NSFilePosixPermissions:@0700,NSFileProtectionKey:NSFileProtectionCompleteUntilFirstUserAuthentication} error:&failure];
        if(failure)return;
        [store setResourceValue:@YES forKey:NSURLIsExcludedFromBackupKey error:&failure];if(failure)return;
        NSURL *seed=[store URLByAppendingPathComponent:@"planet"];
        if(![NSFileManager.defaultManager fileExistsAtPath:seed.path]){
            [NSFileManager.defaultManager copyItemAtURL:planet toURL:seed error:&failure];if(failure)return;
        }
        int fd=socket(AF_INET,SOCK_DGRAM,0);
        if(fd<0){failure=engineError(@"无法创建底层 UDP 套接字");return;}
        sockaddr_in local={};local.sin_len=sizeof(local);local.sin_family=AF_INET;
        if(bind(fd,(sockaddr *)&local,sizeof(local))||fcntl(fd,F_SETFL,O_NONBLOCK)<0){close(fd);failure=engineError(@"无法绑定底层 UDP 套接字");return;}
        self->_socket=fd;
        ZT_Node_Callbacks callbacks={};callbacks.statePutFunction=putState;callbacks.stateGetFunction=getState;
        callbacks.wirePacketSendFunction=sendWire;callbacks.virtualNetworkFrameFunction=frame;
        callbacks.virtualNetworkConfigFunction=config;callbacks.eventCallback=event;
        ZT_Node_Config options={};
        auto result=ZT_Node_new(&self->_node,&options,(__bridge void *)self,nullptr,&callbacks,nowMS());
        if(result!=ZT_RESULT_OK||self->_storageError){failure=engineError(self->_storageError?:[NSString stringWithFormat:@"ZeroTier 初始化失败：%d",result]);[self closeEngine];return;}
        for(NSDictionary *moon in moons){
            result=ZT_Node_orbit(self->_node,nullptr,strtoull([moon[@"world"] UTF8String],nullptr,16),strtoull([moon[@"seed"] UTF8String],nullptr,16));
            if(result!=ZT_RESULT_OK){failure=engineError(@"Moon 加载失败");[self closeEngine];return;}
        }
        result=ZT_Node_join(self->_node,self->_network,nullptr,nullptr);
        if(result!=ZT_RESULT_OK){failure=engineError(@"无法加入 ZeroTier 网络");[self closeEngine];return;}
        __weak ZTEngine *weakSelf=self;
        self->_reader=dispatch_source_create(DISPATCH_SOURCE_TYPE_READ,fd,0,self->_queue);
        dispatch_source_set_event_handler(self->_reader,^{[weakSelf readWire];});
        dispatch_source_set_cancel_handler(self->_reader,^{close(fd);});dispatch_resume(self->_reader);
        self->_lastExpire=nowMS();
        self->_timer=dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER,0,0,self->_queue);
        dispatch_source_set_timer(self->_timer,DISPATCH_TIME_NOW,100*NSEC_PER_MSEC,20*NSEC_PER_MSEC);
        dispatch_source_set_event_handler(self->_timer,^{
            ZTEngine *e=weakSelf;if(!e||!e->_node)return;
            auto r=ZT_Node_processBackgroundTasks(e->_node,nullptr,nowMS(),&e->_deadline);
            if(ZT_ResultCode_isFatal(r)&&e.failureHandler)e.failureHandler(@"ZeroTier 后台任务失败");
            if(nowMS()-e->_lastExpire>=60000){if(e->_bridge)e->_bridge->expire();e->_lastExpire=nowMS();}
        });dispatch_resume(self->_timer);
    });
    if(error)*error=failure;return failure==nil;
}
- (void)readWire {
    for(int i=0;i<128&&_node&&_socket>=0;++i){
        uint8_t bytes[65536];sockaddr_storage remote={};socklen_t size=sizeof(remote);
        ssize_t count=recvfrom(_socket,bytes,sizeof(bytes),0,(sockaddr *)&remote,&size);
        if(count<0)break;
        ++_rx;ZT_Node_processWirePacket(_node,nullptr,nowMS(),0,&remote,bytes,(unsigned)count,&_deadline);
    }
}
- (void)configure:(const ZT_VirtualNetworkConfig *)c operation:(ZT_VirtualNetworkConfigOperation)op {
    if(!_node)return;
    _bridge.reset();_address=@"";
    if(!c||op==ZT_VIRTUAL_NETWORK_CONFIG_OPERATION_DOWN||op==ZT_VIRTUAL_NETWORK_CONFIG_OPERATION_DESTROY){
        if(self.configurationHandler)self.configurationHandler(@"",0,0,-1);return;
    }
    if(c->status!=ZT_NETWORK_STATUS_OK){if(self.configurationHandler)self.configurationHandler(@"",0,0,c->status);return;}
    for(unsigned i=0;i<c->assignedAddressCount;++i){
        if(c->assignedAddresses[i].ss_family!=AF_INET)continue;
        auto a=(const sockaddr_in *)&c->assignedAddresses[i];unsigned prefix=ntohs(a->sin_port);uint32_t ip=ntohl(a->sin_addr.s_addr);
        if(prefix<1||prefix>30){if(self.failureHandler)self.failureHandler(@"仅支持 /1 至 /30 的 IPv4 子网");return;}
        uint32_t mask=0xffffffffU<<(32-prefix);
        if((ip&mask)!=(_gateway&mask)||ip==_gateway){if(self.failureHandler)self.failureHandler(@"NAS 网关必须和虚拟 IP 在同一子网，且不能等于本机地址");return;}
        char text[INET_ADDRSTRLEN];inet_ntop(AF_INET,&a->sin_addr,text,sizeof(text));_address=@(text);
        uint64_t mac=c->mac;__weak ZTEngine *weakSelf=self;
        _bridge=std::make_unique<IPv4Bridge>(mac,ip,prefix,_gateway,[weakSelf,mac](uint64_t dest,unsigned type,const IPv4Bridge::Bytes &data){
            ZTEngine *e=weakSelf;if(e&&e->_node)ZT_Node_processVirtualNetworkFrame(e->_node,nullptr,nowMS(),e->_network,mac,dest,type,0,data.data(),(unsigned)data.size(),&e->_deadline);
        });
        auto result=ZT_Node_multicastSubscribe(_node,nullptr,_network,0xffffffffffffULL,ip);
        if(result!=ZT_RESULT_OK){if(self.failureHandler)self.failureHandler(@"ARP 订阅失败");return;}
        if(self.configurationHandler)self.configurationHandler(_address,prefix,MIN(1400,c->mtu),c->status);return;
    }
    if(self.configurationHandler)self.configurationHandler(@"",0,0,c->status);
}
- (void)processPackets:(NSArray<NSData *> *)packets completion:(void (^)(void))completion {
    dispatch_async(_queue,^{if(self->_bridge)for(NSData *p in packets)self->_bridge->outbound((const uint8_t *)p.bytes,p.length,nowMS());completion();});
}
- (void)snapshot:(void (^)(NSDictionary<NSString *,id> *))completion {
    dispatch_async(_queue,^{
        ZT_NodeStatus status={};if(self->_node)ZT_Node_status(self->_node,&status);
        NSMutableDictionary *snapshot=[@{@"node":self->_node?[NSString stringWithFormat:@"%010llx",ZT_Node_address(self->_node)]:@"",
                     @"address":self->_address?:@"",@"engineOnline":@(status.online),@"tx":@(self->_tx),@"rx":@(self->_rx),@"sendErrors":@(self->_sendErrors)} mutableCopy];
        ZT_PeerList *list=self->_node?ZT_Node_peers(self->_node):nullptr;
        NSMutableDictionary *moons=[NSMutableDictionary dictionary];
        for(NSDictionary *moon in self->_moons){
            uint64_t seed=strtoull([moon[@"seed"] UTF8String],nullptr,16),last=0;const ZT_Peer *match=nullptr;
            if(list)for(unsigned long i=0;i<list->peerCount;++i)if(list->peers[i].address==seed){match=&list->peers[i];break;}
            if(match)for(unsigned i=0;i<match->pathCount;++i)last=MAX(last,match->paths[i].lastReceive);
            NSString *state=@(moonConnectivity(match!=nullptr,match&&match->role==ZT_PEER_ROLE_MOON,(int64_t)last,nowMS(),self->_observedSince));
            moons[moon[@"world"]]=@{@"state":state,@"latency":@(match?match->latency:-1)};
        }
        snapshot[@"moons"]=moons;
        if(self.diagnosticsEnabled){
            NSMutableArray *peers=[NSMutableArray array];
            if(list)for(unsigned long i=0;i<MIN(list->peerCount,64UL);++i){
                const ZT_Peer &p=list->peers[i];NSMutableArray *paths=[NSMutableArray array];
                for(unsigned j=0;j<p.pathCount;++j){
                    const auto &path=p.paths[j];char host[INET6_ADDRSTRLEN]={};unsigned port=0;
                    if(path.address.ss_family==AF_INET){auto a=(const sockaddr_in *)&path.address;inet_ntop(AF_INET,&a->sin_addr,host,sizeof(host));port=ntohs(a->sin_port);}
                    else if(path.address.ss_family==AF_INET6){auto a=(const sockaddr_in6 *)&path.address;inet_ntop(AF_INET6,&a->sin6_addr,host,sizeof(host));port=ntohs(a->sin6_port);}
                    [paths addObject:@{@"endpoint":[NSString stringWithFormat:@"%s:%u",host,port],@"rxAge":path.lastReceive?@(MAX(0LL,(nowMS()-(int64_t)path.lastReceive)/1000)):@(-1)}];
                }
                [peers addObject:@{@"id":[NSString stringWithFormat:@"%010llx",p.address],@"role":@(p.role),@"latency":@(p.latency),@"paths":paths}];
            }
            snapshot[@"peers"]=peers;
        }
        if(list)ZT_Node_freeQueryResult(self->_node,list);completion(snapshot);
    });
}
- (void)resetPath {dispatch_async(_queue,^{self->_observedSince=nowMS();if(self->_bridge)self->_bridge->expire();});}
- (void)closeEngine {
    if(_timer){dispatch_source_cancel(_timer);_timer=nil;}
    if(_reader){dispatch_source_cancel(_reader);_reader=nil;}else if(_socket>=0)close(_socket);
    _socket=-1;_bridge.reset();
    if(_node){ZT_Node_delete(_node);_node=nullptr;}_address=@"";
}
- (void)stopWithCompletion:(void (^)(void))completion {dispatch_async(_queue,^{[self closeEngine];completion();});}
@end
