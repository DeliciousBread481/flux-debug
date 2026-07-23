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

    private long tick = 0;

    private DebugLogger() {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
    }

    public void onServerTick() {
        tick++;
    }

    public synchronized void logWorldTime(int dim, long worldTime) {
        String key = "worldTime/dim" + dim;
        String prevStr = last.get(key);
        last.put(key, String.valueOf(worldTime));
        if (prevStr != null) {
            long prev = Long.parseLong(prevStr);
            long delta = worldTime - prev;
            if (delta != 1) {
                line("WORLDTIME", key, prev + " -> " + worldTime + " (delta=" + delta + ")",
                        delta == 0 ? "该维度世界时间停滞（可能未 tick）" : "世界时间跳变，非正常每 tick +1");
            }
        }
    }

    public long tick() {
        return tick;
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
