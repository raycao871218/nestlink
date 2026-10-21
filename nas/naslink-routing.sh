#!/bin/sh
# Scoped routing for NAS Link. Does not replace the NAS default gateway.
set -eu
# Set these in the environment or a root-owned /volume1/docker/mihomo/naslink.env.
if [ -f /volume1/docker/mihomo/naslink.env ]; then
    set -a
    . /volume1/docker/mihomo/naslink.env
    set +a
fi
: "${ZT_IF:?Set ZeroTier interface}"
: "${ZT_NET:?Set ZeroTier IPv4 subnet}"
: "${ZT_IP:?Set NAS ZeroTier IPv4}"
: "${LAN_IF:?Set LAN interface}"
: "${LAN_NET:?Set LAN IPv4 subnet}"
TUN_IF=naslink0
TABLE=2022
PRIORITY=12500

remove_rules() {
    ip rule del priority "$PRIORITY" iif "$ZT_IF" lookup "$TABLE" 2>/dev/null || true
    ip route flush table "$TABLE" 2>/dev/null || true
    for proto in udp tcp; do
        iptables -t nat -D PREROUTING -i "$ZT_IF" -s "$ZT_NET" -d "$ZT_IP" -p "$proto" --dport 53 -j REDIRECT --to-ports 1053 2>/dev/null || true
    done
    iptables -t nat -D POSTROUTING -s "$ZT_NET" -d "$LAN_NET" -o "$LAN_IF" -j MASQUERADE 2>/dev/null || true
    iptables -D FORWARD -i "$ZT_IF" -s "$ZT_NET" -j ACCEPT 2>/dev/null || true
    iptables -D FORWARD -o "$ZT_IF" -d "$ZT_NET" -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT 2>/dev/null || true
    iptables -D INPUT -i "$ZT_IF" -s "$ZT_NET" -d "$ZT_IP" -p udp --dport 1053 -j ACCEPT 2>/dev/null || true
    iptables -D INPUT -i "$ZT_IF" -s "$ZT_NET" -d "$ZT_IP" -p tcp --dport 1053 -j ACCEPT 2>/dev/null || true
}

case "${1:-start}" in
stop) remove_rules; exit 0 ;;
start)
    count=0
    until ip link show "$TUN_IF" >/dev/null 2>&1 && ip link show "$ZT_IF" >/dev/null 2>&1; do
        count=$((count+1)); [ "$count" -lt 90 ] || { echo 'NAS Link interfaces not ready'; exit 1; }
        sleep 1
    done
    remove_rules
    sysctl -w net.ipv4.ip_forward=1 >/dev/null
    ip route replace "$LAN_NET" dev "$LAN_IF" table "$TABLE"
    ip route replace "$ZT_NET" dev "$ZT_IF" table "$TABLE"
    ip route replace blackhole default metric 32760 table "$TABLE"
    ip route replace default dev "$TUN_IF" metric 10 table "$TABLE"
    for proto in udp tcp; do
        iptables -t nat -I PREROUTING 1 -i "$ZT_IF" -s "$ZT_NET" -d "$ZT_IP" -p "$proto" --dport 53 -j REDIRECT --to-ports 1053
    done
    iptables -t nat -I POSTROUTING 1 -s "$ZT_NET" -d "$LAN_NET" -o "$LAN_IF" -j MASQUERADE
    iptables -I FORWARD 1 -i "$ZT_IF" -s "$ZT_NET" -j ACCEPT
    iptables -I FORWARD 1 -o "$ZT_IF" -d "$ZT_NET" -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
    iptables -I INPUT 1 -i "$ZT_IF" -s "$ZT_NET" -d "$ZT_IP" -p udp --dport 1053 -j ACCEPT
    iptables -I INPUT 1 -i "$ZT_IF" -s "$ZT_NET" -d "$ZT_IP" -p tcp --dport 1053 -j ACCEPT
    ip rule add priority "$PRIORITY" iif "$ZT_IF" lookup "$TABLE"
    echo 'NAS Link scoped routing enabled'
    ;;
*) echo 'Usage: naslink-routing.sh start|stop'; exit 2 ;;
esac
