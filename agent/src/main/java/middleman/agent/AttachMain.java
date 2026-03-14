package middleman.agent;

import com.sun.tools.attach.VirtualMachine;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Standalone entry point to load MiddleManAgent into an already-running JVM.
 * Usage: java --add-modules jdk.attach -cp MiddleManAgent.jar middleman.agent.AttachMain <pid> <path-to-MiddleManAgent.jar> [logPath] [version]
 * Pass version so the agent uses it (avoids classloader reusing an old AgentVersion class).
 */
public final class AttachMain {
    public static void main(String[] args) {
        if (args == null || args.length < 2) {
            System.err.println("Usage: AttachMain <pid> <agent-jar-path> [logPath] [version]");
            System.exit(1);
        }
        String pid = args[0].trim();
        String agentPath = args[1].trim();
        String logPath = args.length >= 3 ? args[2].trim() : null;
        String version = args.length >= 4 && args[3] != null ? args[3].trim() : "";
        String agentOptions = version.isEmpty() ? "8765" : ("8765|" + version);
        // #region agent log
        if (logPath != null && !logPath.isEmpty()) {
            try {
                String escapedPath = (agentPath != null ? agentPath : "").replace("\\", "\\\\").replace("\"", "\\\"");
                String line = "{\"sessionId\":\"01c49b\",\"timestamp\":" + System.currentTimeMillis() + ",\"message\":\"attach_start\",\"data\":{\"pid\":\"" + pid + "\",\"agentPath\":\"" + escapedPath + "\"}}\n";
                Files.write(new File(logPath).toPath(), line.getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Throwable t) { /* ignore */ }
        }
        // #endregion
        try {
            VirtualMachine vm = VirtualMachine.attach(pid);
            try {
                vm.loadAgent(agentPath, agentOptions);
            } finally {
                vm.detach();
            }
            // #region agent log
            if (logPath != null && !logPath.isEmpty()) {
                try {
                    String line = "{\"sessionId\":\"01c49b\",\"timestamp\":" + System.currentTimeMillis() + ",\"location\":\"AttachMain.main\",\"message\":\"attach_done\",\"data\":{},\"hypothesisId\":\"H3\"}\n";
                    Files.write(new File(logPath).toPath(), line.getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                } catch (Throwable t) { /* ignore */ }
            }
            // #endregion
        } catch (Exception e) {
            // #region agent log
            if (logPath != null && !logPath.isEmpty()) {
                try {
                    String msg = (e.getMessage() != null ? e.getMessage() : e.getClass().getName()).replace("\\", "\\\\").replace("\"", "\\\"");
                    String line = "{\"sessionId\":\"01c49b\",\"timestamp\":" + System.currentTimeMillis() + ",\"location\":\"AttachMain.main\",\"message\":\"attach_fail\",\"data\":{\"error\":\"" + msg + "\"},\"hypothesisId\":\"H2\"}\n";
                    Files.write(new File(logPath).toPath(), line.getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                } catch (Throwable t) { /* ignore */ }
            }
            // #endregion
            System.err.println("Attach failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
