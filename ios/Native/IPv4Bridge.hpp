// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <cstdint>
#include <deque>
#include <functional>
#include <map>
#include <vector>

// All addresses are host-order integers. Called only on the engine queue.
class IPv4Bridge {
public:
    using Bytes = std::vector<uint8_t>;
    using Sender = std::function<void(uint64_t, unsigned, const Bytes &)>;
    static uint32_t ip(const uint8_t *p) {
        return uint32_t(p[0])<<24 | uint32_t(p[1])<<16 | uint32_t(p[2])<<8 | p[3];
    }
    static uint64_t mac(const uint8_t *p) {
        uint64_t m=0; for(int i=0;i<6;++i) m=(m<<8)|p[i]; return m;
    }
    static bool validIP(const uint8_t *p, size_t n) {
        if(n<20 || p[0]>>4!=4) return false;
        size_t h=(p[0]&15)*4, length=(unsigned(p[2])<<8)|p[3];
        return h>=20 && length>=h && length<=n;
    }
    static Bytes arp(unsigned op,uint64_t fm,uint32_t fi,uint64_t tm,uint32_t ti) {
        Bytes b={0,1,8,0,6,4,0,uint8_t(op)};
        auto putMac=[&](uint64_t m){for(int i=5;i>=0;--i)b.push_back(m>>(8*i));};
        auto putIP=[&](uint32_t ip){for(int i=3;i>=0;--i)b.push_back(ip>>(8*i));};
        putMac(fm);putIP(fi);putMac(tm);putIP(ti);return b;
    }
    IPv4Bridge(uint64_t m,uint32_t l,unsigned p,uint32_t g,Sender s)
        : localMac(m),local(l),mask(p ? 0xffffffffU<<(32-p):0),gateway(g),send(s) {}
    void outbound(const uint8_t *p,size_t n,int64_t now) {
        if(!validIP(p,n))return;
        auto dest=ip(p+16),next=(dest&mask)==(local&mask)?dest:gateway;
        Bytes data(p,p+((unsigned(p[2])<<8)|p[3]));
        auto found=neighbors.find(next);
        if(found!=neighbors.end()){send(found->second,0x0800,data);return;}
        if(queued<64){pending[next].push_back(std::move(data));++queued;}
        if(!requested.count(next)||now-requested[next]>=1000){
            requested[next]=now;send(0xffffffffffffULL,0x0806,arp(1,localMac,local,0,next));
        }
    }
    void inboundARP(uint64_t source,const uint8_t *p,size_t n) {
        if(n<28||p[0]!=0||p[1]!=1||p[2]!=8||p[3]!=0||p[4]!=6||p[5]!=4)return;
        unsigned op=(unsigned(p[6])<<8)|p[7];auto from=ip(p+14),to=ip(p+24);
        if((op!=1&&op!=2)||mac(p+8)!=source||!source||(source&0x010000000000ULL))return;
        if((from&mask)!=(local&mask)||from==local||to!=local)return;
        neighbors[from]=source;
        auto found=pending.find(from);
        if(found!=pending.end()){
            auto packets=std::move(found->second);pending.erase(found);
            for(const auto &data:packets){--queued;send(source,0x0800,data);}
        }
        requested.erase(from);
        if(op==1)send(source,0x0806,arp(2,localMac,local,source,from));
    }
    void expire(){neighbors.clear();pending.clear();requested.clear();queued=0;}
private:
    uint64_t localMac;uint32_t local,mask,gateway;Sender send;
    std::map<uint32_t,uint64_t> neighbors;
    std::map<uint32_t,int64_t> requested;
    std::map<uint32_t,std::deque<Bytes>> pending;
    size_t queued=0;
};
