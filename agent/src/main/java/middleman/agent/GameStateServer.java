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
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 2);
                break;
            } catch (BindException e) {
                if (attempt == 0) {
                    AgentLog.log("Port " + port + " in use; asking previous agent to shut down so this instance can take over.");
                    boolean shutdownOk = requestShutdown(port);
                    if (!shutdownOk) {
                        AgentLog.log("Could not reach previous agent. Dashboard will use existing server.");
                        return;
                    }
                    try { Thread.sleep(800); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                } else {
                    AgentLog.log("Port still in use after shutdown request. Dashboard will use existing server.");
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
        server.createContext("/game/worldobjects", this::handleWorldObjects);
        server.createContext("/game/worldobject/action", this::handleWorldObjectAction);
        server.createContext("/game/npc/action", this::handleNpcAction);
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
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

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
                "\"/game/state\",\"/game/state/simple\",\"/game/players\",\"/game/npcs\",\"/game/worldobjects\",\"/game/worldobject/action\",\"/game/npc/action\",\"/dashboard\"]}";
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

    private void handleWorldObjects(HttpExchange exchange) throws IOException {
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
            String json = "{\"worldObjects\":" + s.serializeWorldObjects() + "}";
            send(exchange, 200, json);
        } catch (Exception e) {
            send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleWorldObjectAction(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            StateSerializer s = serializer;
            if (s == null) {
                send(exchange, 503, "{\"error\":\"Client not ready\"}");
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int id = -1, worldX = 0, worldY = 0, plane = 0, actionIndex = 0;
            String type = "gameObject";
            for (String part : body.split("&")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                String key = part.substring(0, eq).trim();
                String val = part.substring(eq + 1).trim();
                switch (key) {
                    case "id": id = Integer.parseInt(val); break;
                    case "worldX": worldX = Integer.parseInt(val); break;
                    case "worldY": worldY = Integer.parseInt(val); break;
                    case "plane": plane = Integer.parseInt(val); break;
                    case "actionIndex": actionIndex = Integer.parseInt(val); break;
                    case "type": type = val; break;
                    default: break;
                }
            }
            String err = s.invokeWorldObjectAction(id, worldX, worldY, plane, type, actionIndex);
            if (err != null) {
                send(exchange, 400, "{\"ok\":false,\"error\":\"" + escape(err) + "\"}");
                return;
            }
            send(exchange, 200, "{\"ok\":true}");
        } catch (NumberFormatException e) {
            send(exchange, 400, "{\"ok\":false,\"error\":\"Invalid number\"}");
        } catch (Exception e) {
            send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleNpcAction(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            StateSerializer s = serializer;
            if (s == null) {
                send(exchange, 503, "{\"error\":\"Client not ready\"}");
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int id = -1, worldX = 0, worldY = 0, plane = 0, actionIndex = 0;
            for (String part : body.split("&")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                String key = part.substring(0, eq).trim();
                String val = part.substring(eq + 1).trim();
                switch (key) {
                    case "id": id = Integer.parseInt(val); break;
                    case "worldX": worldX = Integer.parseInt(val); break;
                    case "worldY": worldY = Integer.parseInt(val); break;
                    case "plane": plane = Integer.parseInt(val); break;
                    case "actionIndex": actionIndex = Integer.parseInt(val); break;
                    default: break;
                }
            }
            String err = s.invokeNpcAction(id, worldX, worldY, plane, actionIndex);
            if (err != null) {
                send(exchange, 400, "{\"ok\":false,\"error\":\"" + escape(err) + "\"}");
                return;
            }
            send(exchange, 200, "{\"ok\":true}");
        } catch (NumberFormatException e) {
            send(exchange, 400, "{\"ok\":false,\"error\":\"Invalid number\"}");
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
