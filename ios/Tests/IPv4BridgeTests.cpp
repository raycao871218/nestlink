#include "../Native/IPv4Bridge.hpp"
#include "../Native/MoonConnectivity.hpp"
#include <cassert>
#include <iostream>
#include <string>

struct Frame {uint64_t mac;unsigned type;IPv4Bridge::Bytes bytes;};
static IPv4Bridge::Bytes packet(uint32_t destination){
    IPv4Bridge::Bytes p(20);p[0]=0x45;p[3]=20;p[8]=64;p[9]=6;
    p[12]=10;p[13]=1;p[14]=2;p[15]=3;
    for(int i=0;i<4;++i)p[16+i]=(destination>>(24-8*i))&255;return p;
}
int main(){
    auto state=[](bool found,bool moon,int64_t last,int64_t now,int64_t epoch){return std::string(moonConnectivity(found,moon,last,now,epoch));};
    assert(state(false,false,100,200,100)=="undiscovered");
    assert(state(true,false,100,200,100)=="notMoon");
    assert(state(true,true,0,200,100)=="unavailable");
    assert(state(true,true,99,200,100)=="waiting");
    assert(state(true,true,100,120100,100)=="online");
    assert(state(true,true,100,120101,100)=="unavailable");
    assert(state(true,true,201,200,100)=="unavailable");
    assert(state(true,true,100,200,101)=="waiting");
    constexpr uint32_t local=0x0a010203,gateway=0x0a010201,peer=0x0a010209;
    constexpr uint64_t localMac=0x020000000003,peerMac=0x020000000009;
    std::vector<Frame> sent;
    IPv4Bridge bridge(localMac,local,24,gateway,[&](uint64_t m,unsigned t,const auto &b){sent.push_back({m,t,b});});
    auto p=packet(0x08080808);bridge.outbound(p.data(),p.size(),1000);
    assert(sent.size()==1&&sent[0].type==0x0806&&IPv4Bridge::ip(sent[0].bytes.data()+24)==gateway);
    auto reply=IPv4Bridge::arp(2,peerMac,gateway,localMac,local);
    bridge.inboundARP(peerMac+2,reply.data(),reply.size());assert(sent.size()==1); // forged source
    auto wrong=IPv4Bridge::arp(2,peerMac,gateway,localMac,peer);
    bridge.inboundARP(peerMac,wrong.data(),wrong.size());assert(sent.size()==1); // not addressed to us
    bridge.inboundARP(peerMac,reply.data(),reply.size());
    assert(sent.size()==2&&sent[1].mac==peerMac&&sent[1].bytes==p); // flush gateway queue
    bridge.outbound(p.data(),p.size(),1100);assert(sent.size()==3&&sent.back().type==0x0800);
    bridge.expire();sent.clear();p=packet(peer);
    for(int i=0;i<100;++i)bridge.outbound(p.data(),p.size(),2000);
    assert(sent.size()==1&&IPv4Bridge::ip(sent[0].bytes.data()+24)==peer); // LAN ARP, throttled
    reply=IPv4Bridge::arp(2,peerMac,peer,localMac,local);
    bridge.inboundARP(peerMac,reply.data(),reply.size());assert(sent.size()==65); // bounded pending queue
    bridge.expire();sent.clear();
    p[0]=0x65;bridge.outbound(p.data(),p.size(),3000);assert(sent.empty()); // IPv6
    p[0]=0x44;bridge.outbound(p.data(),p.size(),3000);assert(sent.empty()); // invalid IHL
    p[0]=0x45;p[3]=21;bridge.outbound(p.data(),p.size(),3000);assert(sent.empty()); // truncated payload
    p[3]=19;bridge.outbound(p.data(),p.size(),3000);assert(sent.empty()); // total < header
    p=packet(peer);p.push_back(0xff);bridge.outbound(p.data(),p.size(),4000);
    bridge.inboundARP(peerMac,reply.data(),reply.size());assert(sent.back().bytes.size()==20); // trim padding
    bridge.expire();sent.clear();
    auto request=IPv4Bridge::arp(1,peerMac,peer,0,local);
    bridge.inboundARP(peerMac,request.data(),request.size());assert(sent.size()==1&&sent[0].bytes[7]==2);
    // Arbitrary malformed input must never read outside the packet.
    for(size_t n=0;n<64;++n){std::vector<uint8_t> junk(n,0xff);bridge.outbound(junk.data(),n,5000);bridge.inboundARP(peerMac,junk.data(),n);}
    std::cout<<"IPv4/ARP routing, spoof rejection, bounds, expiry and malformed packets: PASS\n";
    std::cout<<"Moon roles, actual receive freshness, network changes and future timestamps: PASS\n";
}
