package middleman.agent;

/**
 * Runtime loaded from the current agent JAR via an isolated classloader.
 * This allows hot-reload attaches to use newly built classes without restarting RuneLite.
 */
public final class ReloadRunner {

    private ReloadRunner() {
    }

    public static void run(String args) {
        AgentLog.init();
        applyVersionFromArgs(args);
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

    private static void applyVersionFromArgs(String args) {
        String v = parseVersion(args);
        if (v != null && !v.isEmpty()) {
            System.setProperty("middleman.agent.version", v);
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

    private static String parseVersion(String args) {
        if (args == null) return null;
        String[] parts = args.split("\\|", 3);
        if (parts.length < 2) return null;
        String v = parts[1].trim();
        return v.isEmpty() ? null : v;
    }

    private static String readBuildTime() {
        try (java.io.InputStream in = ReloadRunner.class.getClassLoader().getResourceAsStream("buildtime.txt")) {
            if (in != null) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
