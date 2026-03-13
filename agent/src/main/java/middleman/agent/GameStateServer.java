package middleman.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 2);
        server.createContext("/", this::handleRoot);
        server.createContext("/game/state", this::handleFullState);
        server.createContext("/game/state/simple", this::handleGameStateOnly);
        server.createContext("/game/players", this::handlePlayers);
        server.createContext("/game/npcs", this::handleNpcs);
        server.setExecutor(null);
        server.start();
        AgentLog.log("Game state API listening on http://127.0.0.1:" + port);
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        String body = "{\"service\":\"MiddleMan\",\"endpoints\":[" +
                "\"/game/state\",\"/game/state/simple\",\"/game/players\",\"/game/npcs\"]}";
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
