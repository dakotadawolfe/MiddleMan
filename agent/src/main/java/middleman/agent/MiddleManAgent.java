package middleman.agent;

import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Java agent that loads into the RuneLite JVM and exposes game state via HTTP.
 * No compile-time dependency on RuneLite; uses reflection to access Client.
 */
public final class MiddleManAgent {

    public static void premain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> run(args), "MiddleMan-Agent");
        t.setDaemon(true);
        t.start();
    }

    public static void agentmain(String args, Instrumentation inst) {
        premain(args, inst);
    }

    private static void run(String args) {
        try {
            String agentPath = parseAgentPath(args);
            if (agentPath != null && !agentPath.isEmpty()) {
                runIsolated(agentPath, args);
            } else {
                ReloadRunner.run(args);
            }
        } catch (Throwable t) {
            AgentLog.init();
            AgentLog.log("Error: " + t.getMessage(), t);
        }
    }

    private static void runIsolated(String agentPath, String args) throws Exception {
        URL jarUrl = new java.io.File(agentPath).toURI().toURL();
        URLClassLoader cl = new URLClassLoader(new URL[]{jarUrl}, ClassLoader.getPlatformClassLoader());
        Class<?> runner = Class.forName("middleman.agent.ReloadRunner", true, cl);
        java.lang.reflect.Method m = runner.getMethod("run", String.class);
        m.invoke(null, args);
    }

    private static String parseAgentPath(String args) {
        if (args == null) return null;
        String[] parts = args.split("\\|", 3);
        if (parts.length < 3) return null;
        return parts[2].replace("%7C", "|").trim();
    }
}
