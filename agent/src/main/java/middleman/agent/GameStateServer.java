package middleman.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

// #region agent log
import java.nio.file.StandardOpenOption;
// #endregion

/**
 * HTTP server exposing game state as JSON. Uses JDK built-in HttpServer.
 */
final class GameStateServer {

    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    private final Object client;
    private final int port;
    private HttpServer server;
    private volatile StateSerializer serializer;

    GameStateServer(Object client, Object clientThread, Object itemManager, int port) {
        this.client = client;
        this.port = port;
        this.serializer = new StateSerializer(client, clientThread, itemManager);
    }

    void start() throws IOException {
        // #region agent log
        debugLog("start_enter", "agentVersion", StateSerializer.getAgentVersion());
        debugLog("class_source_middleman", "url", classSource(MiddleManAgent.class));
        debugLog("class_source_stateserializer", "url", classSource(StateSerializer.class));
        debugLog("class_loader_middleman", "id", String.valueOf(System.identityHashCode(MiddleManAgent.class.getClassLoader())));
        // #endregion
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 2);
                // #region agent log
                debugLog("bind_ok", "attempt", attempt);
                // #endregion
                break;
            } catch (BindException e) {
                // #region agent log
                debugLog("bind_failed", "attempt", attempt);
                // #endregion
                if (attempt == 0) {
                    AgentLog.log("Port " + port + " in use; asking previous agent to shut down so this instance can take over.");
                    boolean shutdownOk = requestShutdown(port);
                    // #region agent log
                    debugLog("requestShutdown_result", "ok", shutdownOk);
                    // #endregion
                    if (!shutdownOk) {
                        AgentLog.log("Could not reach previous agent. Dashboard will use existing server.");
                        // #region agent log
                        debugLog("start_aborted", "reason", "requestShutdown_failed");
                        // #endregion
                        return;
                    }
                    try { Thread.sleep(800); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                } else {
                    AgentLog.log("Port still in use after shutdown request. Dashboard will use existing server.");
                    // #region agent log
                    debugLog("start_aborted", "reason", "port_still_in_use");
                    // #endregion
                    return;
                }
            }
        }
        server.createContext("/dashboard", this::handleDashboard);
        server.createContext("/", this::handleRoot);
        server.createContext("/shutdown", this::handleShutdown);
        server.createContext("/game/state", this::handleFullState);
        server.createContext("/game/state/simple", this::handleGameStateOnly);
        server.createContext("/game/players", this::handlePlayers);
        server.createContext("/game/npcs", this::handleNpcs);
        server.setExecutor(null);
        server.start();
        AgentLog.log("Game state API listening on http://127.0.0.1:" + port);
    }

    private boolean requestShutdown(int port) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/shutdown").openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(2000);
            c.setReadTimeout(2000);
            int code = c.getResponseCode();
            c.disconnect();
            // #region agent log
            debugLog("requestShutdown_response", "code", code);
            // #endregion
            return code == 200;
        } catch (Exception e) {
            // #region agent log
            debugLog("requestShutdown_error", "message", e.getMessage());
            // #endregion
            return false;
        }
    }

    // #region agent log
    private static void debugLog(String message, String key, Object value) {
        try {
            Path logPath = Paths.get(System.getProperty("user.dir", "."), "MiddleMan", "debug-01c49b.log");
            String val = value instanceof Number || value instanceof Boolean ? String.valueOf(value) : "\"" + escapeJson(String.valueOf(value)) + "\"";
            String line = "{\"sessionId\":\"01c49b\",\"message\":\"" + escapeJson(message) + "\",\"data\":{\"" + escapeJson(key) + "\":" + val + "},\"timestamp\":" + System.currentTimeMillis() + "}\n";
            Files.write(logPath, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) { }
    }
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String classSource(Class<?> c) {
        try {
            if (c == null || c.getProtectionDomain() == null || c.getProtectionDomain().getCodeSource() == null
                || c.getProtectionDomain().getCodeSource().getLocation() == null) {
                return "unknown";
            }
            return String.valueOf(c.getProtectionDomain().getCodeSource().getLocation());
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
    // #endregion

    private void handleShutdown(HttpExchange exchange) throws IOException {
        send(exchange, 200, "{\"ok\":true}");
        final HttpServer s = server;
        if (s != null) {
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "MiddleMan-Shutdown");
                t.setDaemon(true);
                return t;
            }).execute(() -> {
                try { Thread.sleep(100); } catch (InterruptedException ignored) { }
                s.stop(0);
            });
        }
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        String userDir = System.getProperty("user.dir", ".");
        Path path = Paths.get(userDir, "MiddleMan", "dashboard", "index.html");
        if (!Files.isRegularFile(path)) {
            send(exchange, 404, "{\"error\":\"Dashboard not found\"}");
            return;
        }
        byte[] html = Files.readAllBytes(path);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, html.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(html);
        }
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        String body = "{\"service\":\"MiddleMan\",\"endpoints\":[" +
                "\"/game/state\",\"/game/state/simple\",\"/game/players\",\"/game/npcs\",\"/dashboard\"]}";
        send(exchange, 200, body);
    }

    private void handleFullState(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            StateSerializer s = serializer;
            if (s == null) {
                send(exchange, 503, "{\"error\":\"Client not ready\"}");
                return;
            }
            String json = s.serializeFullState();
            send(exchange, 200, json);
        } catch (Exception e) {
            send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleGameStateOnly(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            StateSerializer s = serializer;
            if (s == null) {
                send(exchange, 503, "{\"error\":\"Client not ready\"}");
                return;
            }
            String json = s.serializeGameState();
            send(exchange, 200, json);
        } catch (Exception e) {
            send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handlePlayers(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            StateSerializer s = serializer;
            if (s == null) {
                send(exchange, 503, "{\"error\":\"Client not ready\"}");
                return;
            }
            String json = "{\"players\":" + s.serializePlayers() + "}";
            send(exchange, 200, json);
        } catch (Exception e) {
            send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleNpcs(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            StateSerializer s = serializer;
            if (s == null) {
                send(exchange, 503, "{\"error\":\"Client not ready\"}");
                return;
            }
            String json = "{\"npcs\":" + s.serializeNpcs() + "}";
            send(exchange, 200, json);
        } catch (Exception e) {
            send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void send(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
