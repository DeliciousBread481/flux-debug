package deliciousbread481.fluxdebug;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugLogger {

    public static final DebugLogger INSTANCE = new DebugLogger();

    private static final long MAX_BYTES = 4L * 1024 * 1024;
    private static final int SNAPSHOT_INTERVAL = 600;

    private final File file = new File("logs", "flux-debug.log");
    private final Map<String, String> last = new ConcurrentHashMap<>();
    private final Map<String, Field> fieldCache = new HashMap<>();
    private final Map<String, Method> methodCache = new HashMap<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS");

    private volatile long tick = 0;

    private DebugLogger() {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
    }

    public synchronized long tick() {
        return ++tick;
    }

    public synchronized void logWorldTime(int dim, long totalWorldTime, long worldTime) {
        if (dim != 0) {
            return;
        }
        String prevTotal = last.get("wt/total");
        last.put("wt/total", String.valueOf(totalWorldTime));
        last.put("wt/day", String.valueOf(worldTime));

        boolean stalled = prevTotal != null && Long.parseLong(prevTotal) == totalWorldTime;
        String stallNow = String.valueOf(stalled);
        String stallPrev = last.get("wt/stalled");
        if (!stallNow.equals(stallPrev)) {
            last.put("wt/stalled", stallNow);
            line("WORLDTIME", "dim0",
                    "total=" + totalWorldTime + " day=" + worldTime + " tick=" + tick,
                    stalled ? "主世界 totalWorldTime 停止推进（dim0 未 tick）"
                            : "主世界 totalWorldTime 恢复推进");
        }
    }

    public boolean isSnapshotTick() {
        return tick % SNAPSHOT_INTERVAL == 0;
    }

    public synchronized void logIfChanged(String key, String value, String reason) {
        String prev = last.get(key);
        if (prev == null || !prev.equals(value)) {
            last.put(key, value);
            line("CHANGE", key, (prev == null ? "<none>" : prev) + " -> " + value, reason);
        }
    }

    public synchronized void line(String kind, String key, String value, String reason) {
        rollIfNeeded();
        String row = String.format("[%s] tick=%d %-9s %-28s %-40s %s%n",
                fmt.format(new Date()), tick, kind, key, value, reason == null ? "" : reason);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file, true))) {
            w.write(row);
        } catch (Exception ignored) {
        }
    }

    private void rollIfNeeded() {
        try {
            if (file.exists() && file.length() > MAX_BYTES) {
                File bak = new File(file.getParentFile(), file.getName() + ".1");
                if (bak.exists()) {
                    bak.delete();
                }
                file.renameTo(bak);
            }
        } catch (Exception ignored) {
        }
    }

    public Object getField(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            String cacheKey = target.getClass().getName() + "#" + name;
            Field f = fieldCache.get(cacheKey);
            if (f == null) {
                f = findField(target.getClass(), name);
                if (f == null) {
                    return null;
                }
                f.setAccessible(true);
                fieldCache.put(cacheKey, f);
            }
            return f.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    public Object invoke(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            String cacheKey = target.getClass().getName() + "@" + name;
            Method m = methodCache.get(cacheKey);
            if (m == null) {
                m = findMethod(target.getClass(), name);
                if (m == null) {
                    return null;
                }
                m.setAccessible(true);
                methodCache.put(cacheKey, m);
            }
            return m.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private Method findMethod(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public long asLong(Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        return 0L;
    }

    public int sizeOf(Object o) {
        if (o instanceof Collection) {
            return ((Collection<?>) o).size();
        }
        if (o instanceof Map) {
            return ((Map<?, ?>) o).size();
        }
        return 0;
    }

    public synchronized void onPointCycleStartHead(Object pointHandler) {
        String loc = locate(getField(pointHandler, "device"));
        String thread = Thread.currentThread().getName();
        long demand = asLong(getField(pointHandler, "demand"));
        last.put("cs-head/" + loc, "1");
        boolean worker = !thread.startsWith("Server thread");
        logIfChanged("point@" + loc + "/thread", thread,
                worker ? "onCycleStart 运行在工作线程(并行路径已启用) → 存在线程安全风险"
                       : "onCycleStart 运行在主线程(安全路径)");
        logIfChanged("point@" + loc + "/demandIn", String.valueOf(demand), "进入 onCycleStart 时 demand");
    }

    public synchronized void onPointCycleStartTail(Object pointHandler) {
        String loc = locate(getField(pointHandler, "device"));
        long demand = asLong(getField(pointHandler, "demand"));
        last.remove("cs-head/" + loc);
        logIfChanged("point@" + loc + "/demandOut", String.valueOf(demand),
                demand <= 0 ? "onCycleStart 后 demand<=0 → getRequest 将为 0 → 拖低 bufferLimiter" : "demand 正常");
    }

    public synchronized void onNetworkTick(Object network, String thread) {
        int plugs = sizeOf(getField(network, "sortedPlugs"));
        int points = sizeOf(getField(network, "sortedPoints"));
        long limiter = asLong(getField(network, "bufferLimiter"));
        logIfChanged("net/thread", thread, "onEndServerTick 执行线程");
        logIfChanged("net/sortedPlugs", String.valueOf(plugs), plugs == 0 ? "无活跃塞子，CYCLE 跳过" : "塞子在线");
        logIfChanged("net/sortedPoints", String.valueOf(points), "活跃 Point 数");
        logIfChanged("net/bufferLimiter", String.valueOf(limiter),
                limiter == 0 ? "bufferLimiter=0 → 下一 tick 塞子将拒收发电机电力(疑似并行 demand 丢失)" : "限流值正常");
        for (Map.Entry<String, String> e : last.entrySet()) {
            if (e.getKey().startsWith("cs-head/")) {
                line("EXCEPTION", e.getKey().substring(8), "onCycleStart 未到达 TAIL",
                        "该 Point 的 onCycleStart 抛异常被 StellarCore 吞掉 → demand 未更新，根因命中");
            }
        }
    }

    private String locate(Object device) {
        if (device instanceof net.minecraft.tileentity.TileEntity) {
            net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) device;
            int dim = te.getWorld() != null ? te.getWorld().provider.getDimension() : -999;
            return "dim" + dim + "/" + te.getPos().getX() + "," + te.getPos().getY() + "," + te.getPos().getZ();
        }
        return "?";
    }

    public synchronized void pollFluxNetworks() {
        try {
            Class<?> cacheClz = Class.forName("sonar.fluxnetworks.common.connection.FluxNetworkCache");
            Object cache = cacheClz.getField("instance").get(null);
            Collection<?> networks = (Collection<?>) cacheClz.getMethod("getAllNetworks").invoke(cache);

            Class<?> logicClz = Class.forName("sonar.fluxnetworks.api.network.FluxLogicType");
            Object PLUG = Enum.valueOf(logicClz.asSubclass(Enum.class), "PLUG");
            Object POINT = Enum.valueOf(logicClz.asSubclass(Enum.class), "POINT");
            Method getConnections = networks.iterator().hasNext()
                    ? findMethod(networks.iterator().next().getClass(), "getConnections") : null;

            for (Object network : networks) {
                if ((Boolean) invoke(network, "isInvalid")) {
                    continue;
                }
                int id = asInt(invoke(network, "getNetworkID"));
                long limiter = asLong(getField(network, "bufferLimiter"));

                List<?> plugs = (List<?>) network.getClass()
                        .getMethod("getConnections", logicClz).invoke(network, PLUG);
                List<?> points = (List<?>) network.getClass()
                        .getMethod("getConnections", logicClz).invoke(network, POINT);

                int owPlugs = pollConnectors(id, "PLUG", plugs, limiter);
                int owPoints = pollConnectors(id, "POINT", points, limiter);

                if (owPlugs > 0 || owPoints > 0) {
                    String key = "net" + id;
                    logIfChanged(key + "/bufferLimiter", String.valueOf(limiter),
                            limiter == 0 ? "网络限流为 0：下一 tick plug 将拒收发电机电力" : "");
                    logIfChanged(key + "/size", owPlugs + "P/" + owPoints + "p", "");
                }
            }
        } catch (Throwable e) {
            line("POLL_ERROR", "pollFluxNetworks", e.toString(), "轮询 Flux 网络异常");
        }
    }

    private int pollConnectors(int netId, String kind, List<?> connectors, long limiter) {
        int count = 0;
        for (Object c : connectors) {
            try {
                Object coords = invoke(c, "getCoords");
                if (asInt(invoke(coords, "getDimension")) != 0) {
                    continue;
                }
                count++;
                String pos = String.valueOf(invoke(coords, "getStringInfo"));
                String base = "net" + netId + "/" + kind + "/" + pos;
                long buffer = asLong(invoke(c, "getTransferBuffer"));
                long change = asLong(invoke(c, "getTransferChange"));
                boolean active = Boolean.TRUE.equals(invoke(c, "isActive"));
                boolean loaded = Boolean.TRUE.equals(invoke(c, "isChunkLoaded"));

                String reason = "";
                if ("PLUG".equals(kind)) {
                    reason = change > 0 ? "在收电" : (limiter == 0 ? "限流0，无法收电" : "未收电");
                } else {
                    reason = change < 0 ? "在向机械送电" : "未送电";
                }
                logIfChanged(base, "buf=" + buffer + " chg=" + change
                        + " active=" + active + " loaded=" + loaded, reason);
            } catch (Throwable ignored) {
            }
        }
        return count;
    }

    private int asInt(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    public synchronized void pollFlux() {
        try {
            Class<?> cacheCls = Class.forName("sonar.fluxnetworks.common.connection.FluxNetworkCache");
            Object cache = cacheCls.getField("instance").get(null);
            Collection<?> networks = (Collection<?>) cacheCls.getMethod("getAllNetworks").invoke(cache);

            Class<?> nsCls = Class.forName("sonar.fluxnetworks.api.network.NetworkSettings");
            Object allConnectors = nsCls.getField("ALL_CONNECTORS").get(null);
            Method getSetting = Class.forName("sonar.fluxnetworks.api.network.IFluxNetwork")
                    .getMethod("getSetting", nsCls);

            int nets = networks.size();
            int dim0 = 0;

            for (Object network : networks) {
                Long limiter = null;
                Object lim = getField(network, "bufferLimiter");
                if (lim != null) {
                    limiter = asLong(lim);
                }

                List<?> connectors = (List<?>) getSetting.invoke(network, allConnectors);
                if (connectors == null) {
                    continue;
                }
                for (Object c : connectors) {
                    Object coords = invoke(c, "getCoords");
                    if (coords == null) {
                        continue;
                    }
                    int dim = (int) invoke(coords, "getDimension");
                    if (dim != 0) {
                        continue;
                    }
                    dim0++;

                    String pos = String.valueOf(invoke(c, "getCoords"));
                    long buf = asLong(invoke(c, "getTransferBuffer"));
                    long chg = asLong(invoke(c, "getTransferChange"));
                    Object active = invoke(c, "isActive");
                    Object loaded = invoke(c, "isChunkLoaded");

                    logIfChanged("flux/" + pos,
                            "buf=" + buf + " chg=" + chg + " active=" + active
                                    + " loaded=" + loaded + " limiter=" + limiter,
                            (limiter != null && limiter == 0) ? "bufferLimiter=0：下一 tick 将拒收发电机电力" : null);
                }
            }

            logIfChanged("flux/summary", "networks=" + nets + " dim0Connectors=" + dim0,
                    dim0 == 0 ? "dim0 无任何已注册 Flux 连接（大概率因 dim0 未 tick，tile 未 addConnection）" : null);
        } catch (Throwable e) {
            line("ERROR", "pollFlux", e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
    }
}