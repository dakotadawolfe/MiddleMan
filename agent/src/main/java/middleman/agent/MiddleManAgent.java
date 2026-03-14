package middleman.agent;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;

/**
 * Java agent that loads into the RuneLite JVM and exposes game state via HTTP.
 * No compile-time dependency on RuneLite; uses reflection to access Client.
 */
public final class MiddleManAgent {

    /** Version passed at load time via agent args (e.g. "8765|4.0-hotreload-test") to avoid classloader reusing an old class. */
    private static volatile String reportedVersion;

    public static void premain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> run(args), "MiddleMan-Agent");
        t.setDaemon(true);
        t.start();
    }

    public static void agentmain(String args, Instrumentation inst) {
        premain(args, inst);
    }

    /** Used by StateSerializer/API; prefer version from agent args so hot-reload shows the correct build. */
    static String getReportedVersion() {
        String v = reportedVersion;
        return (v != null && !v.isEmpty()) ? v : AgentVersion.V;
    }

    private static void run(String args) {
        AgentLog.init();
        parseAgentArgs(args);
        String buildTime = readBuildTime();
        AgentLog.log("Agent thread started. JAR build time: " + (buildTime != null ? buildTime : "?"));
        try {
            int port = parsePort(args);
            AgentLog.log("Waiting for RuneLite Client (port " + port + ")...");
            ClientDiscovery discovery = new ClientDiscovery();
            discovery.waitForClient();
            AgentLog.log("Client found, starting HTTP server.");
            GameStateServer server = new GameStateServer(discovery.getClient(), discovery.getClientThread(), discovery.getItemManager(), port);
            server.start();
        } catch (Throwable t) {
            AgentLog.log("Error: " + t.getMessage(), t);
        }
    }

    private static void parseAgentArgs(String args) {
        if (args != null && args.contains("|")) {
            int i = args.indexOf('|');
            String ver = args.substring(i + 1).trim();
            if (!ver.isEmpty()) reportedVersion = ver;
        }
    }

    private static int parsePort(String args) {
        if (args != null && !args.isEmpty()) {
            String portPart = args.contains("|") ? args.substring(0, args.indexOf('|')).trim() : args.trim();
            try {
                return Integer.parseInt(portPart);
            } catch (NumberFormatException ignored) {
            }
        }
        String env = System.getenv("MIDDLEMAN_PORT");
        if (env != null && !env.isEmpty()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 8765;
    }

    private static String readBuildTime() {
        try (InputStream in = MiddleManAgent.class.getClassLoader().getResourceAsStream("buildtime.txt")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
