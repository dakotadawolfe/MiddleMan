package middleman.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Writes one NDJSON line to debug-01c49b.log for this session. */
final class DebugLog {
    private static final String LOG_PATH = "debug-01c49b.log";
    private static final String SESSION = "01c49b";

    static void log(String location, String message, String dataJson, String hypothesisId) {
        try {
            String dir = System.getProperty("user.dir", ".");
            File f = new File(new File(dir, "MiddleMan"), LOG_PATH);
            long ts = System.currentTimeMillis();
            String data = (dataJson != null && !dataJson.isEmpty()) ? dataJson : "{}";
            String json = "{\"sessionId\":\"" + SESSION + "\",\"timestamp\":" + ts + ",\"location\":\"" + escape(location) + "\",\"message\":\"" + escape(message) + "\",\"data\":" + data + ",\"hypothesisId\":\"" + (hypothesisId != null ? hypothesisId : "") + "\"}\n";
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
