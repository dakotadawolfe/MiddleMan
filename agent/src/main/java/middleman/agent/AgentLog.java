package middleman.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Writes [MiddleMan] messages to both stderr and a log file so status is visible
 * even when the console is flooded with RuneLite DEBUG output.
 */
final class AgentLog {

    private static final String PREFIX = "[MiddleMan] ";
    private static PrintWriter fileWriter;
    private static final Object lock = new Object();

    static void log(String message) {
        String line = PREFIX + message;
        System.err.println(line);
        System.err.flush();
        synchronized (lock) {
            if (fileWriter != null) {
                try {
                    String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                    fileWriter.println(ts + " " + line);
                    fileWriter.flush();
                } catch (Exception ignored) {
                }
            }
        }
    }

    static void log(String message, Throwable t) {
        log(message);
        if (t != null) {
            t.printStackTrace(System.err);
            System.err.flush();
            synchronized (lock) {
                if (fileWriter != null) {
                    try {
                        t.printStackTrace(fileWriter);
                        fileWriter.flush();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /** Call once at agent startup to open the log file. */
    static void init() {
        synchronized (lock) {
            if (fileWriter != null) return;
            try {
                String dir = System.getProperty("user.dir", ".");
                File logFile = new File(new File(dir, "MiddleMan"), "middleman.log");
                fileWriter = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8),
                    true);
                log("Log file: " + logFile.getAbsolutePath());
            } catch (Throwable t) {
                System.err.println(PREFIX + "Could not open log file: " + t.getMessage());
                System.err.flush();
            }
        }
    }
}
