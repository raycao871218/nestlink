package com.naslink.android;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.os.*;
import com.zerotier.sdk.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.*;

/** One VPN, one embedded ZeroTier node. All native calls run on a single engine thread. */
public final class TunnelService extends VpnService {
    public static volatile boolean alive;
    public static final String LOCALIZE="com.naslink.LOCALIZE";
    public static final String RECHECK="com.naslink.RECHECK";
    public static final String STOP="com.naslink.STOP";
    private final ScheduledThreadPoolExecutor engine=new ScheduledThreadPoolExecutor(1);
    private final ScheduledExecutorService health=Executors.newSingleThreadScheduledExecutor();
    private final ConnectionHealth connectionHealth=new ConnectionHealth();
    private boolean checking;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean stopping;
    private volatile DatagramSocket udp;
    private ParcelFileDescriptor tun;
    private volatile FileInputStream tunInput;
    private volatile FileOutputStream tunOutput;
    private volatile BlockingQueue<byte[]> androidPackets;
    private Node node;
    private long networkId;
    private String gateway;
    private EthernetBridge bridge;
    private VirtualNetworkConfig current;
    private String currentAddress="";
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networks;
    private Network physical;
    private HomeProxyEngine homeProxy;
    private boolean homeMode;
    private long routeGeneration;
    private String activeRoute="";
    private final RouteStability routeStability=new RouteStability();
    private int routeSwitches, homeStarts, nodeStarts;
    private final long[] deadline=new long[1];
    private boolean starting;
    private File store;
    private long tx,rx,sendErrors,skippedIpv6;
    private long moonObservedSince;
    private String wireError="",lastEvent="",wireNetwork="未绑定";

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && STOP.equals(intent.getAction())) { shutdown("已断开"); return START_NOT_STICKY; }
        if(intent!=null && RECHECK.equals(intent.getAction())) { submit(this::checkHealth); return START_NOT_STICKY; }
        if(intent!=null && LOCALIZE.equals(intent.getAction())) {if(starting) getSystemService(NotificationManager.class).notify(7,notification(""));else stopSelf();return START_NOT_STICKY;}
        if(starting) return START_NOT_STICKY;
        starting=true;
        alive=true;
        resetMoonConnectivity();
        getSharedPreferences("status",0).edit().putString("phase","joining").remove("homeError").putBoolean("homeMode",false).remove("networkName").remove("networkNameId").putBoolean("lanReady",false).putBoolean("internetReady",false).apply();
        try {
            if(prepare(this)!=null) throw new IllegalStateException("尚未授予 VPN 权限");
            android.content.SharedPreferences p=getSharedPreferences("connection",0);
            String id=p.getString("network","");
            if(!id.matches("[0-9a-fA-F]{16}")) throw new IllegalArgumentException("网络 ID 必须为 16 位十六进制");
            networkId=Long.parseUnsignedLong(id,16);
            gateway=p.getString("gateway","");
            ConnectionSettings.ipv4(gateway);
            NotificationManager nm=getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("tunnel",AppLanguage.wrap(this).getString(R.string.notice_channel),NotificationManager.IMPORTANCE_LOW));
            if(Build.VERSION.SDK_INT>=34) startForeground(7,notification("正在启动 ZeroTier"),ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
            else startForeground(7,notification("正在启动 ZeroTier"));
            publish("正在启动 ZeroTier",true);
            connectivity=getSystemService(ConnectivityManager.class);
            networks=new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { submit(() -> selectNetwork(null)); }
                @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities c) { submit(() -> selectNetwork(null)); }
                @Override public void onLinkPropertiesChanged(Network n,LinkProperties l) { submit(() -> selectNetwork(null)); }
                @Override public void onLost(Network n) { submit(() -> selectNetwork(n)); }
            };
            connectivity.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),networks);
            engine.scheduleWithFixedDelay(() -> selectNetwork(null),0,3,TimeUnit.SECONDS);
            engine.scheduleWithFixedDelay(() -> {
                if(stopping || node==null) return;
                try { requireOk(node.processBackgroundTasks(System.currentTimeMillis(),deadline),"网络后台任务"); }
                catch(Exception e) { shutdown("引擎错误："+safe(e)); }
            },0,100,TimeUnit.MILLISECONDS);
            engine.scheduleWithFixedDelay(() -> { if(bridge!=null) bridge.expire(); },60,60,TimeUnit.SECONDS);
            engine.scheduleWithFixedDelay(this::updateMoonStatus,2,5,TimeUnit.SECONDS);
            engine.scheduleWithFixedDelay(this::updateDiagnostics,1,2,TimeUnit.SECONDS);
            health.scheduleWithFixedDelay(() -> submit(this::checkHealth),2,20,TimeUnit.SECONDS);
        } catch(Exception|LinkageError e) { shutdown("启动失败："+safe(e)); }
        return START_NOT_STICKY;
    }
    private void initialize() {
        if(stopping) return;
        try {
            store=new File(getNoBackupFilesDir(),"zerotier");
            if(!store.isDirectory() && !store.mkdirs()) throw new IOException("无法创建身份存储");
            installBootstrap();
            node=new Node(System.currentTimeMillis());
            nodeStarts++;recordRouteStats();
            final Node initializingNode=node;
            ResultCode result=node.init(this::readState,new DataStorePutListener() {
                @Override public int onDataStorePut(String name,byte[] data,boolean secure) {
                    try {
                        File f=stateFile(name); File parent=f.getParentFile();
                        if(!parent.isDirectory() && !parent.mkdirs()) return -1;
                        android.util.AtomicFile atom=new android.util.AtomicFile(f); FileOutputStream output=null;
                        try { output=atom.startWrite(); output.write(data); atom.finishWrite(output); }
                        catch(IOException e) { if(output!=null) atom.failWrite(output); throw e; }
                        f.setReadable(false,false); f.setWritable(false,false); f.setReadable(true,true); f.setWritable(true,true);
                        return 0;
                    } catch(Exception e) { return -1; }
                }
                @Override public int onDelete(String name) { try { File f=stateFile(name); return !f.exists()||f.delete()?0:-1; } catch(IOException e) { return -1; } }
            },this::sendWire,new com.zerotier.sdk.EventListener() {
                @Override public void onEvent(Event event) {
                    lastEvent=event.name();
                    if(event.name().contains("COLLISION") || event.name().contains("FATAL")) shutdown("ZeroTier 身份错误："+event.name());
                }
                @Override public void onTrace(String message) { /* Never persist native trace or identity material. */ }
            },this::receiveFrame,(id,operation,config) -> {
                submit(() -> { if(node==initializingNode) configure(config); }); return 0;
            },null);
            requireOk(result,"引擎初始化");
            loadMoons();
            getSharedPreferences("status",0).edit().putString("node",String.format(java.util.Locale.ROOT,"%010x",node.address())).apply();
            requireOk(node.join(networkId),"加入网络");
            publish("等待网络配置；首次连接请授权设备",true);
        } catch(Exception|LinkageError e) { shutdown("启动失败："+safe(e)); }
    }
    private void installBootstrap() throws IOException {
        File planet=new File(store,"planet");
        byte[] candidate;
        try(InputStream input=getAssets().open("bootstrap/planet"); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[1024]; int size;
            while((size=input.read(buffer))!=-1) { output.write(buffer,0,size); if(output.size()>4096) throw new IOException("内置根节点定义过大"); }
            candidate=output.toByteArray();
        }
        BootstrapPolicy.shouldInstall(null,candidate);
        String custom=getSharedPreferences("connection",0).getString("customPlanet","");
        byte[] selected=custom.isEmpty()?candidate:android.util.Base64.decode(custom,android.util.Base64.DEFAULT);
        PlanetSettings.validate(selected);
        String revision=getSharedPreferences("connection",0).getString("planetRevision","");
        String selection=BootstrapPolicy.hash(selected)+(revision.isEmpty()?"":":"+revision);
        File marker=new File(store,"planet.selection");
        String applied=marker.isFile()?new String(Files.readAllBytes(marker.toPath()),java.nio.charset.StandardCharsets.UTF_8):null;
        byte[] current=planet.isFile()?Files.readAllBytes(planet.toPath()):null;
        if(PlanetSettings.shouldReplace(applied,selection,!custom.isEmpty(),current,candidate)) {
            if(current!=null) {
                File backup=new File(store,"planet.before-bootstrap-migration");
                if(!backup.exists()) Files.write(backup.toPath(),current);
            }
            writeBootstrap(planet,selected);
        }
        writeBootstrap(marker,selection.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private void writeBootstrap(File file,byte[] bytes) throws IOException {
        android.util.AtomicFile atomic=new android.util.AtomicFile(file); FileOutputStream output=null;
        try { output=atomic.startWrite(); output.write(bytes); atomic.finishWrite(output); }
        catch(IOException e) { if(output!=null) atomic.failWrite(output); throw e; }
    }
    private void updateDiagnostics() {
        if(stopping || node==null || !getSharedPreferences("connection",0).getBoolean("developer",false)) return;
        try {
            StringBuilder text=new StringBuilder("引擎 online=").append(node.status().isOnline()).append(" · ").append(lastEvent)
                .append("\n底层网络：").append(wireNetwork).append(" · UDP 发/收：").append(tx).append('/').append(rx)
                .append(" · 发送错误：").append(sendErrors).append(" · 跳过 IPv6：").append(skippedIpv6);
            if(!wireError.isEmpty()) text.append("\n最近网络错误：").append(wireError);
            Peer[] peers=node.peers();
            if(peers!=null) for(Peer peer:peers) {
                text.append(String.format(java.util.Locale.ROOT,"\n%010x %s latency=%d",peer.getAddress(),peer.getRole(),peer.getLatency()));
                PeerPhysicalPath[] paths=peer.getPaths();
                if(paths!=null) for(PeerPhysicalPath path:paths) text.append(" [").append(path.getAddress()).append(" rxAge=")
                    .append(path.getLastReceive()==0?-1:(System.currentTimeMillis()-path.getLastReceive())/1000).append("s]");
                if(text.length()>5000) break;
            }
            getSharedPreferences("status",0).edit().putString("diagnostics",text.toString()).apply();
        } catch(Exception e) { getSharedPreferences("status",0).edit().putString("diagnostics","诊断错误："+safe(e)).apply(); }
    }
    private void loadMoons() throws IOException {
        android.content.SharedPreferences prefs=getSharedPreferences("connection",0);
        java.util.List<MoonSettings.Moon> configured=MoonSettings.parse(prefs.getString("moons",""));
        java.util.Set<Long> wanted=new java.util.HashSet<>();
        for(MoonSettings.Moon moon:configured) wanted.add(moon.world);
        for(MoonSettings.Moon old:MoonSettings.parse(prefs.getString("appliedMoons",""))) {
            if(!wanted.contains(old.world)) requireOk(node.deorbit(old.world),"移除 Moon");
        }
        for(MoonSettings.Moon moon:configured) requireOk(node.orbit(moon.world,moon.seed),"添加 Moon");
        prefs.edit().putString("appliedMoons",MoonSettings.format(configured)).apply();
        getSharedPreferences("status",0).edit().putString("moons",configured.isEmpty()?"未配置 Moon":"已提交 "+configured.size()+" 个 Moon，等待发现").apply();
    }
    private void resetMoonConnectivity() {
        moonObservedSince=System.currentTimeMillis();
        getSharedPreferences("status",0).edit().remove("moonConnectivity").putLong("moonCheckedAt",0).apply();
    }
    private void updateMoonStatus() {
        if(stopping || node==null) return;
        try {
            java.util.List<MoonSettings.Moon> configured=MoonSettings.parse(getSharedPreferences("connection",0).getString("moons",""));
            Peer[] peers=node.peers();org.json.JSONObject snapshot=new org.json.JSONObject();int online=0;
            long now=System.currentTimeMillis();
            for(MoonSettings.Moon moon:configured) {
                Peer match=null;
                if(peers!=null) for(Peer peer:peers) if(peer.getAddress()==moon.seed) {match=peer;break;}
                long lastReceive=0;
                if(match!=null && match.getPaths()!=null) for(PeerPhysicalPath path:match.getPaths()) lastReceive=Math.max(lastReceive,path.getLastReceive());
                String state=MoonConnectivity.state(match!=null,match!=null && match.getRole()==PeerRole.PEER_ROLE_MOON,lastReceive,now,moonObservedSince);
                if("online".equals(state)) online++;
                org.json.JSONObject entry=new org.json.JSONObject().put("state",state)
                    .put("seed",Long.toUnsignedString(moon.seed,16))
                    .put("latency",match==null?-1:match.getLatency());
                snapshot.put(Long.toUnsignedString(moon.world,16),entry);
            }
            getSharedPreferences("status",0).edit().putString("moonConnectivity",snapshot.toString())
                .putLong("moonCheckedAt",SystemClock.elapsedRealtime())
                .putString("moons","已配置 Moon："+configured.size()+" · 路径正常："+online).apply();
        } catch(Exception e) {
            getSharedPreferences("status",0).edit().remove("moonConnectivity").putLong("moonCheckedAt",0).putString("moons","Moon 状态暂不可用").apply();
        }
    }
    private File stateFile(String name) throws IOException {
        if(name.isEmpty() || name.contains("..") || name.contains("\\") || name.startsWith("/")) throw new IOException("无效存储路径");
        File f=new File(store,name);
        if(!f.getCanonicalPath().startsWith(store.getCanonicalPath()+File.separator)) throw new IOException("存储路径越界");
        return f;
    }
    private long readState(String name,byte[] target) {
        try {
            File f=stateFile(name); if(!f.isFile()) return -1;
            byte[] bytes=new android.util.AtomicFile(f).readFully();
            int count=Math.min(bytes.length,target.length); System.arraycopy(bytes,0,target,0,count); return count;
        } catch(Exception e) { return -2; }
    }
    private void selectNetwork(Network lost) {
        if(stopping) return;
        try {
            SharedPreferences prefs=getSharedPreferences("connection",0);
            Network selected=null; int best=-1;
            for(Network n:connectivity.getAllNetworks()) {
                if(n.equals(lost)) continue;
                NetworkCapabilities caps=connectivity.getNetworkCapabilities(n);
                if(caps==null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                boolean wifi=caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                boolean home=prefs.getBoolean("homeEnabled",false) && !prefs.getString("homeSsid","").isEmpty() && prefs.getString("homeSsid","").equals(CurrentNetworkReader.ssid(this,n));
                int score=(home?100:0)+(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?20:0)+(wifi?10:0)+(n.equals(physical)?1:0);
                if(score>best) { selected=n; best=score; }
            }
            long now=SystemClock.elapsedRealtime();
            if(selected==null) {
                if(physical!=null) {
                    if(homeMode) stopRoute(); else { invalidateHealth("reconnecting"); closeUdp(); }
                    physical=null;activeRoute="";setUnderlyingNetworks(null);
                    publish("等待可用网络",true);
                }
                return;
            }
            String observed=CurrentNetworkReader.ssid(this,selected);
            NetworkCapabilities selectedCaps=connectivity.getNetworkCapabilities(selected);
            boolean useHome=RouteStability.retainHome(prefs.getBoolean("homeEnabled",false),
                selectedCaps!=null && selectedCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                selected.equals(physical),homeMode,prefs.getString("homeSsid",""),observed);
            // DNS, validation, route ordering and other LinkProperties metadata do
            // not alter our TUN. Only local IPv4 changes matter to the home path.
            String local="";
            LinkProperties properties=connectivity.getLinkProperties(selected);
            if(useHome && properties!=null) {
                java.util.List<String> addresses=new java.util.ArrayList<>();
                for(LinkAddress ip:properties.getLinkAddresses()) if(ip.getAddress() instanceof Inet4Address) addresses.add(ip.toString());
                java.util.Collections.sort(addresses);local=addresses.toString();
            }
            String desired=selected.toString()+"/"+useHome+"/"+local;
            boolean usable=physical!=null && !physical.equals(lost) && (homeMode?homeProxy!=null:node!=null && udp!=null && !udp.isClosed());
            if(!routeStability.ready(activeRoute,desired,now,usable)) return;
            boolean preserveZeroTier=!useHome && !homeMode && node!=null;
            if(!preserveZeroTier) stopRoute();
            else closeUdp(); // Rebind the socket; keep the node and established TUN.
            physical=selected;homeMode=useHome;activeRoute=desired;
            routeSwitches++;recordRouteStats();
            setUnderlyingNetworks(new Network[]{selected});
            SharedPreferences.Editor status=getSharedPreferences("status",0).edit().putBoolean("homeMode",useHome).remove("homeError");
            if(!preserveZeroTier) status.remove("node").remove("networkName").remove("diagnostics");
            status.apply();
            if(useHome) startHome(prefs);
            else { attachNetwork(selected); if(!stopping && node==null) initialize(); }
        } catch(Exception|LinkageError e) { if(homeMode && getSharedPreferences("status",0).getString("homeError","").isEmpty()) getSharedPreferences("status",0).edit().putString("homeError","failed").apply(); shutdown("连接失败："+safe(e)); }
    }
    private void recordRouteStats() {
        getSharedPreferences("status",0).edit().putInt("routeSwitches",routeSwitches).putInt("homeStarts",homeStarts).putInt("nodeStarts",nodeStarts).apply();
    }
    private void stopRoute() throws Exception {
        routeGeneration++;
        if(homeProxy!=null) { HomeProxyEngine old=homeProxy; homeProxy=null; old.stop(); }
        closeTun(); closeUdp(); resetMoonConnectivity(); current=null;
        if(node!=null) { node.close(); node=null; }
    }
    private void startHome(SharedPreferences prefs) throws Exception {
        String ssid=prefs.getString("homeSsid","");
        String nas=prefs.getString("homeNas",""); int port=prefs.getInt("homePort",7890);
        HomeNetworkSettings.validate(true,ssid,nas,port);
        if(!CurrentNetworkReader.onSubnet(connectivity,physical,nas)) {
            getSharedPreferences("status",0).edit().putString("homeError","subnet").apply();
            throw new IOException(AppLanguage.wrap(this).getString(R.string.home_subnet_error));
        }
        Builder builder=new Builder().setSession(AppLanguage.wrap(this).getString(R.string.home_path)).setMtu(1500).setBlocking(false)
            .addAddress("198.18.0.1",32).addRoute("0.0.0.0",0).addDnsServer("1.1.1.1")
            .setConfigureIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE));
        if(Build.VERSION.SDK_INT>=29) builder.setMetered(false);
        tun=builder.establish(); if(tun==null) throw new IOException("VPN 授权失效");
        currentAddress="198.18.0.1";
        final long generation=routeGeneration;
        homeProxy=new HomeProxyEngine();
        homeStarts++;recordRouteStats();
        homeProxy.start(tun.getFd(),physical,this,nas,port,() -> submit(() -> {
            if(!stopping && homeProxy!=null && generation==routeGeneration) { getSharedPreferences("status",0).edit().putString("homeError","failed").apply(); shutdown("家庭代理转发失败"); }
        }));
        invalidateHealth("validating"); publish("家庭 NAS 直连 · 正在验证",true); checkHealth();
    }
    private void attachNetwork(Network network) {
        if(stopping) return;
        try {
            NetworkCapabilities caps=connectivity.getNetworkCapabilities(network);
            if(caps==null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return;
            if(network.equals(physical) && udp!=null && !udp.isClosed()) return;
            DatagramSocket replacement=new DatagramSocket(null);
            try {
                if(!protect(replacement)) throw new IOException("无法保护 ZeroTier 底层套接字");
                network.bindSocket(replacement);
                replacement.bind(new InetSocketAddress(0)); replacement.setSoTimeout(1000);
            } catch(Exception e) { replacement.close(); throw e; }
            invalidateHealth(tun==null?"joining":"validating"); resetMoonConnectivity(); closeUdp(); udp=replacement; physical=network; setUnderlyingNetworks(new Network[]{network});
            wireNetwork=network.toString()+" / "+(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":"其他网络")+" port="+replacement.getLocalPort();
            wireError="";
            if(bridge!=null) bridge.expire();
            Thread reader=new Thread(() -> readWire(replacement),"naslink-wire"); reader.start();
            publish(tun==null?"ZeroTier 正在连接网络":"隧道已建立 · 正在恢复路径",true);
        } catch(Exception e) { wireError=safe(e); publish("底层网络不可用："+safe(e),true); }
    }
    private int sendWire(long local,InetSocketAddress remote,byte[] bytes,int ttl) {
        DatagramSocket socket=udp;
        if(stopping || socket==null || remote==null) { sendErrors++; wireError="底层 UDP 未就绪"; return -1; }
        if(remote.getAddress() instanceof Inet6Address) { skippedIpv6++; return -1; }
        try { socket.send(new DatagramPacket(bytes,bytes.length,remote)); tx++; return 0; }
        catch(IOException e) { sendErrors++; wireError=safe(e); return -1; }
    }
    private void readWire(DatagramSocket socket) {
        byte[] buffer=new byte[65535];
        while(!stopping && !socket.isClosed()) {
            try {
                DatagramPacket packet=new DatagramPacket(buffer,buffer.length); socket.receive(packet);
                byte[] payload=Arrays.copyOf(packet.getData(),packet.getLength());
                InetSocketAddress remote=(InetSocketAddress)packet.getSocketAddress();
                submit(() -> { if(node!=null && socket==udp) { rx++; node.processWirePacket(System.currentTimeMillis(),-1,remote,payload,deadline); } });
            } catch(SocketTimeoutException ignored) {}
            catch(IOException e) { if(!socket.isClosed()) publish("底层网络暂不可用",true); break; }
        }
    }
    private void configure(VirtualNetworkConfig config) {
        if(stopping || homeMode || node==null) return;
        if(config!=null) getSharedPreferences("status",0).edit()
            .putString("networkName",config.getName()==null?"":config.getName())
            .putString("networkNameId",String.format(java.util.Locale.ROOT,"%016x",networkId)).apply();
        if(config!=null&&config.getName()!=null&&!config.getName().trim().isEmpty())
            getSharedPreferences("connection",0).edit().putString("network_name_"+String.format(java.util.Locale.ROOT,"%016x",networkId),config.getName().trim()).apply();
        if(config==null || config.getStatus()!=VirtualNetworkStatus.NETWORK_STATUS_OK) {
            String reason=config==null?"网络已关闭":config.getStatus().name();
            closeTun(); current=null;
            if(config!=null && config.getStatus().name().contains("ACCESS_DENIED")) getSharedPreferences("status",0).edit().putString("phase","authorization").apply();
            publish(config!=null && config.getStatus()==VirtualNetworkStatus.NETWORK_STATUS_REQUESTING_CONFIGURATION
                ?"正在联系 Controller，尚未收到网络配置":"Controller 网络状态："+reason,true); return;
        }
        InetSocketAddress address=null;
        for(InetSocketAddress candidate:config.getAssignedAddresses()) if(candidate.getAddress() instanceof Inet4Address) { address=candidate; break; }
        if(address==null) { closeTun(); publish("网络尚未分配 IPv4 地址",true); return; }
        if(current!=null && current.equals(config) && tun!=null) return;
        try {
            byte[] ip=address.getAddress().getAddress();
            int local=EthernetBridge.ip(ip,0), via=EthernetBridge.ip(ConnectionSettings.ipv4(gateway).getAddress(),0);
            int prefix=address.getPort(), mask=prefix==0?0:-1 << (32-prefix);
            if(prefix<1 || prefix>30 || (local&mask)!=(via&mask) || local==via) throw new IOException("NAS 网关必须在分配的 IPv4 子网内，且不能等于本机地址");
            closeTun();
            Builder builder=new Builder().setSession(AppLanguage.wrap(this).getString(R.string.vpn_session)).setMtu(Math.min(1400,config.getMtu())).setBlocking(true)
                .addAddress(address.getAddress(),prefix).addRoute("0.0.0.0",0).addDnsServer(gateway);
            // IPv6 is deliberately not enabled: Android blocks this family rather than leaking it outside the VPN.
            if(Build.VERSION.SDK_INT>=29) builder.setMetered(true);
            builder.setConfigureIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE));
            tun=builder.establish(); if(tun==null) throw new IOException("VPN 授权失效");
            tunInput=new FileInputStream(tun.getFileDescriptor()); tunOutput=new FileOutputStream(tun.getFileDescriptor());
            androidPackets=new ArrayBlockingQueue<>(128);
            final BlockingQueue<byte[]> packets=androidPackets;
            final FileOutputStream writer=tunOutput;
            new Thread(() -> {
                while(!stopping && writer==tunOutput) {
                    try { byte[] packet=packets.poll(1,TimeUnit.SECONDS); if(packet!=null && writer==tunOutput) writer.write(packet); }
                    catch(InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    catch(IOException e) { if(writer==tunOutput && !stopping) shutdown("隧道写入失败："+safe(e)); break; }
                }
            },"naslink-tun-writer").start();
            current=config; currentAddress=address.getAddress().getHostAddress();
            bridge=new EthernetBridge(config.getMac(),local,prefix,via,(destination,type,data) -> node.processVirtualNetworkFrame(System.currentTimeMillis(),networkId,config.getMac(),destination,type,0,data,deadline));
            requireOk(node.multicastSubscribe(networkId,0xffffffffffffL,Integer.toUnsignedLong(local)),"ARP 订阅");
            FileInputStream reader=tunInput;
            new Thread(() -> readTun(reader),"naslink-tun").start();
            publish("隧道已建立 · 正在自动验证",true);
            getSharedPreferences("status",0).edit().putString("phase","validating").apply();
            checkHealth();
        } catch(Exception e) { closeTun(); publish("隧道配置失败："+safe(e),true); }
    }
    private void readTun(FileInputStream input) {
        byte[] buffer=new byte[32768];
        while(!stopping && input==tunInput) {
            try {
                int length=input.read(buffer); if(length<0) break;
                if(length==0) continue;
                byte[] packet=Arrays.copyOf(buffer,length);
                submit(() -> { if(input==tunInput && bridge!=null) bridge.outbound(packet,System.currentTimeMillis()); });
            } catch(IOException e) { if(input==tunInput && !stopping) shutdown("隧道读取失败："+safe(e)); break; }
        }
    }
    private void receiveFrame(long id,long source,long destination,long type,long vlan,byte[] data) {
        if(stopping || id!=networkId || bridge==null) return;
        if(type==0x0806) { bridge.inboundArp(source,data); return; }
        BlockingQueue<byte[]> packets=androidPackets;
        if(type==0x0800 && data.length>=20 && packets!=null) packets.offer(data);
    }
    private void submit(Runnable job) {
        if(stopping || engine.isShutdown() || engine.getQueue().size()>256) return;
        try { engine.execute(job); } catch(RejectedExecutionException ignored) {}
    }
    private void requireOk(ResultCode result,String action) throws IOException {
        if(result!=ResultCode.RESULT_OK && result!=ResultCode.RESULT_OK_IGNORED) throw new IOException(action+": "+result);
    }
    private Notification notification(String message) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,TunnelService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"tunnel").setContentTitle(AppLanguage.wrap(this).getString(R.string.app_name)).setContentText(AppLanguage.notification(this,getSharedPreferences("status",0).getString("phase","joining")))
            .setSmallIcon(android.R.drawable.stat_sys_upload_done).setOngoing(true).setContentIntent(open)
            .addAction(new Notification.Action.Builder(null,AppLanguage.wrap(this).getString(R.string.notice_stop),stop).build()).build();
    }
    private void publish(String message,boolean active) {
        getSharedPreferences("status",0).edit().putString("message",message).putBoolean("active",active).putString("address",currentAddress).apply();
        if(active && !stopping) getSystemService(NotificationManager.class).notify(7,notification(message));
    }
    private void invalidateHealth(String phase) {
        connectionHealth.invalidate();
        getSharedPreferences("status",0).edit().putString("phase",phase).putBoolean("lanReady",false).putBoolean("internetReady",false).putLong("checkedAt",0).apply();
    }
    private void checkHealth() {
        if(stopping || tun==null || physical==null || currentAddress.isEmpty() || checking) return;
        Network selected=null;
        for(Network network:connectivity.getAllNetworks()) {
            NetworkCapabilities caps=connectivity.getNetworkCapabilities(network);
            LinkProperties links=connectivity.getLinkProperties(network);
            if(caps==null || links==null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
            for(LinkAddress ip:links.getLinkAddresses()) if(currentAddress.equals(ip.getAddress().getHostAddress())) selected=network;
        }
        if(selected==null) return;
        final Network vpn=selected;
        final long epoch=connectionHealth.epoch();
        final String nas=homeMode?getSharedPreferences("connection",0).getString("homeNas",""):gateway;
        final int proxyPort=getSharedPreferences("connection",0).getInt(homeMode?"homePort":"port",7890);
        checking=true;
        try { health.execute(() -> {
            boolean lan=false, internet=false;
            String failure="";
            javax.net.ssl.HttpsURLConnection request=null;
            try {
                try(java.net.Socket socket=vpn.getSocketFactory().createSocket()) {
                    socket.connect(new InetSocketAddress(nas,proxyPort),5000); lan=true;
                }
                request=(javax.net.ssl.HttpsURLConnection)vpn.openConnection(new java.net.URL("https://www.google.com/generate_204"),java.net.Proxy.NO_PROXY);
                request.setConnectTimeout(8000); request.setReadTimeout(8000); request.setInstanceFollowRedirects(false);
                int code=request.getResponseCode();
                if(code!=204) throw new IOException("HTTPS status "+code);
                internet=true;
            } catch(Exception e) { failure=e.getClass().getSimpleName()+": "+safe(e); }
            finally { if(request!=null) request.disconnect(); }
            final boolean localOk=lan, internetOk=internet;
            final String log=failure;
            submit(() -> {
                checking=false;
                if(stopping || !connectionHealth.accept(epoch,localOk,internetOk)) {
                    if(!stopping) checkHealth();
                    return;
                }
                String phase=connectionHealth.connected()?"connected":"degraded";
                getSharedPreferences("status",0).edit().putString("phase",phase)
                    .putBoolean("lanReady",localOk).putBoolean("internetReady",internetOk)
                    .putLong("checkedAt",System.currentTimeMillis())
                    .putString("healthLog",log.isEmpty()?"NAS TCP reachable; VPN HTTPS verified; HTTP 204":log).apply();
                publish(connectionHealth.connected()?"已连接 · 内网与互联网可用":"连接待恢复 · 将自动重试",true);
            });
        }); } catch(RejectedExecutionException e) { checking=false; }
    }
    private void closeUdp() { DatagramSocket old=udp; udp=null; if(old!=null) old.close(); }
    private void closeTun() {
        invalidateHealth(stopping?"disconnected":"joining");
        FileInputStream input=tunInput; tunInput=null;
        FileOutputStream output=tunOutput; tunOutput=null;
        ParcelFileDescriptor fd=tun; tun=null; bridge=null; androidPackets=null; currentAddress="";
        try { if(input!=null) input.close(); } catch(IOException ignored) {}
        try { if(output!=null) output.close(); } catch(IOException ignored) {}
        try { if(fd!=null) fd.close(); } catch(IOException ignored) {}
    }
    private static String safe(Throwable e) { return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
    private synchronized void shutdown(String reason) {
        if(stopping) return;
        stopping=true; alive=false;
        // Stop the native reader before releasing its borrowed TUN descriptor.
        try { engine.execute(() -> { try { stopRoute(); } catch(Exception ignored) {} }); } catch(RejectedExecutionException ignored) {}
        resetMoonConnectivity();
        if(connectivity!=null && networks!=null) { try { connectivity.unregisterNetworkCallback(networks); } catch(Exception ignored) {} }
        publish(reason,false);
        try { engine.execute(() -> { if(node!=null) { try { node.close(); } catch(Exception ignored) {} node=null; } }); }
        catch(RejectedExecutionException ignored) {}
        engine.shutdown(); health.shutdownNow();
        main.post(() -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); });
    }
    @Override public void onRevoke() { shutdown("VPN 权限已撤销"); super.onRevoke(); }
    @Override public void onDestroy() { shutdown("已断开"); super.onDestroy(); }
}
