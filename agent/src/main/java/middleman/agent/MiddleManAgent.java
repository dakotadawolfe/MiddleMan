package middleman.agent;

import java.lang.instrument.Instrumentation;

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
        AgentLog.init();
        AgentLog.log("Agent thread started.");
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

    private static int parsePort(String args) {
        if (args != null && !args.isEmpty()) {
            try {
                return Integer.parseInt(args.trim());
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
}
