package middleman.agent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Discovers the RuneLite Client instance via reflection on net.runelite.client.RuneLite's Guice injector.
 * In REFLECT mode the launcher (runelite/launcher) creates a URLClassLoader for the client and runs
 * it in a thread named "RuneLite" without setting context classloader; we find the loader from that thread's runnable.
 */
final class ClientDiscovery {

    private static final String RUNELITE_CLASS = "net.runelite.client.RuneLite";
    private static final String CLIENT_CLASS = "net.runelite.api.Client";
    private static final String INJECTOR_FIELD = "injector";

    private static final String CLIENT_THREAD_CLASS = "net.runelite.client.callback.ClientThread";
    private static final String ITEM_MANAGER_CLASS = "net.runelite.client.game.ItemManager";

    private Object client;
    private Object clientThread;
    private Object itemManager;
    private volatile boolean ready;

    /** Thread runnable field name (varies by JRE: "target" in OpenJDK, sometimes "runnable"). */
    private static final String[] THREAD_RUNNABLE_FIELD_NAMES = {"target", "runnable"};

    /**
     * Find the client ClassLoader. In REFLECT mode the launcher creates a URLClassLoader and runs
     * the client in a thread; the runnable (lambda) captures that loader. We scan all threads.
     */
    private static ClassLoader findClientClassLoader() {
        java.util.Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread thread : threads) {
            ClassLoader loader = thread.getContextClassLoader();
            if (loader != null && tryLoadRuneLite(loader) != null)
                return loader;
        }
        for (Thread thread : threads) {
            Object runnable = getThreadRunnable(thread);
            if (runnable == null) continue;
            ClassLoader found = findLoaderInObject(runnable);
            if (found != null) return found;
        }
        return null;
    }

    /** Look for a ClassLoader field in obj (and superclasses). If obj is a wrapper, unwrap one Runnable level. */
    private static ClassLoader findLoaderInObject(Object obj) {
        ClassLoader from = findLoaderInClass(obj, obj.getClass());
        if (from != null) return from;
        // One level of unwrap: runnable might be a wrapper (e.g. RunnableAdapter) holding the lambda
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Runnable.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object inner = f.get(obj);
                    if (inner != null && inner != obj) {
                        ClassLoader loader = findLoaderInClass(inner, inner.getClass());
                        if (loader != null) return loader;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static ClassLoader findLoaderInClass(Object obj, Class<?> start) {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!ClassLoader.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    ClassLoader loader = (ClassLoader) f.get(obj);
                    if (loader != null && tryLoadRuneLite(loader) != null)
                        return loader;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static ClassLoader tryLoadRuneLite(ClassLoader loader) {
        try {
            Class.forName(RUNELITE_CLASS, false, loader);
            return loader;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Object getThreadRunnable(Thread thread) {
        for (String name : THREAD_RUNNABLE_FIELD_NAMES) {
            try {
                Field f = Thread.class.getDeclaredField(name);
                if (Runnable.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f.get(thread);
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
        }
        return null;
    }

    void waitForClient() throws InterruptedException {
        boolean loggedWaiting = false;
        Exception lastException = null;
        int attempts = 0;
        while (!ready) {
            try {
                ClassLoader clientLoader = findClientClassLoader();
                Class<?> runeliteClass = clientLoader != null
                    ? Class.forName(RUNELITE_CLASS, false, clientLoader)
                    : Class.forName(RUNELITE_CLASS);
                Field injectorField = runeliteClass.getDeclaredField(INJECTOR_FIELD);
                injectorField.setAccessible(true);
                Object injector = injectorField.get(null);
                if (injector != null) {
                    Class<?> clientClass = clientLoader != null
                        ? Class.forName(CLIENT_CLASS, false, clientLoader)
                        : Class.forName(CLIENT_CLASS);
                    Method getInstance = injector.getClass().getMethod("getInstance", Class.class);
                    getInstance.setAccessible(true); // cross-classloader: agent cannot access InjectorImpl otherwise
                    Object c = getInstance.invoke(injector, clientClass);
                    if (c != null) {
                        this.client = c;
                        try {
                            Class<?> ctClass = Class.forName(CLIENT_THREAD_CLASS, false, clientLoader);
                            this.clientThread = getInstance.invoke(injector, ctClass);
                        } catch (Exception ignored) {
                            this.clientThread = null;
                        }
                        try {
                            Class<?> imClass = Class.forName(ITEM_MANAGER_CLASS, false, clientLoader);
                            this.itemManager = getInstance.invoke(injector, imClass);
                        } catch (Exception ignored) {
                            this.itemManager = null;
                        }
                        this.ready = true;
                        return;
                    }
                }
                lastException = null;
            } catch (Exception e) {
                lastException = e;
                if (!loggedWaiting) {
                    loggedWaiting = true;
                    AgentLog.log("Discovery waiting (will retry): " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
                // Log every 20 attempts (~30s) so we don't spam
                if (++attempts % 20 == 1 && attempts > 1) {
                    AgentLog.log("Still waiting for Client... (" + e.getMessage() + ")");
                }
            }
            Thread.sleep(1500);
        }
    }

    Object getClient() {
        return client;
    }

    Object getClientThread() {
        return clientThread;
    }

    Object getItemManager() {
        return itemManager;
    }
}
