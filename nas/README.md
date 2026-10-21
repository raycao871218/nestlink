# NAS gateway reference

Reuse your existing mihomo instance. This reference assumes Linux, Docker host networking, `ip`, `iptables`, and a working ZeroTier interface; it is not a universal automatic installer.

1. Back up the current mihomo configuration and Docker container definition. Confirm your existing explicit proxy works.
2. Give the existing mihomo container `NET_ADMIN` and `/dev/net/tun:/dev/net/tun`, preserving its existing volumes, environment, image, command and restart policy. This usually requires recreating that container.
3. Merge `mihomo-extra.yaml` into your configuration. If `tun` or `dns` already exists, merge deliberately; do not append duplicate YAML keys. Validate with `/mihomo -t -f <config-path>` before restarting.
4. Copy `naslink.env.example` to `/volume1/docker/mihomo/naslink.env`; replace every example with your actual NAS/ZeroTier/LAN values. Keep it root-owned and non-writable by others. The NAS ZeroTier address must share the phone's assigned IPv4 subnet.
5. Inspect policy table 2022 and priority 12500 first (`ip rule`; `ip route show table 2022`). They must be unused. The supplied script owns these identifiers.
6. Copy `naslink-routing.sh` to `/volume1/docker/mihomo/`, then run as root: `sh /volume1/docker/mihomo/naslink-routing.sh start`.
7. Connect HarborLink and wait for both health checks. Test a separate LAN device too; that validates forwarding/NAT rather than just NAS reachability.

The script scopes policy routing to traffic entering the ZeroTier interface, routes the LAN subnet with source NAT, redirects clients' NAS DNS requests to port 1053, and routes other destinations into `naslink0`. A lower-priority blackhole prevents that policy table from falling through to the normal default when TUN disappears. It leaves the NAS default gateway intact. Every member able to route through this ZeroTier interface may use the gateway; use Controller authorization and network rules to restrict membership.

To stop its rules: `sh /volume1/docker/mihomo/naslink-routing.sh stop`. Restore your backed-up mihomo/container configuration if reverting the TUN changes. Rules must be reapplied after NAS reboot or firewall reload using your NAS's normal task scheduler. This public reference does not install a platform-specific startup hook.

If another tool regenerates your mihomo configuration, preserve the merged TUN/DNS sections. Keep DNS access limited by your firewall to intended clients. No subscriptions, credentials or personal network IDs are included.
