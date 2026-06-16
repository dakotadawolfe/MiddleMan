package middleman.agent;

import com.sun.tools.attach.VirtualMachine;

/**
 * Standalone entry point to load MiddleManAgent into an already-running JVM.
 * Usage: java --add-modules jdk.attach -cp MiddleManAgent.jar middleman.agent.AttachMain <pid> <path-to-MiddleManAgent.jar> [version]
 * Pass version so the agent uses it (avoids classloader reusing an old AgentVersion class).
 */
public final class AttachMain {
    public static void main(String[] args) {
        if (args == null || args.length < 2) {
            System.err.println("Usage: AttachMain <pid> <agent-jar-path> [version]");
            System.exit(1);
        }
        String pid = args[0].trim();
        String agentPath = args[1].trim();
        String version = args.length >= 3 && args[2] != null ? args[2].trim() : "";
        String escapedAgentPathForOptions = agentPath.replace("|", "%7C");
        String agentOptions = version.isEmpty() ? ("8765||" + escapedAgentPathForOptions) : ("8765|" + version + "|" + escapedAgentPathForOptions);
        try {
            VirtualMachine vm = VirtualMachine.attach(pid);
            try {
                vm.loadAgent(agentPath, agentOptions);
            } finally {
                vm.detach();
            }
        } catch (Exception e) {
            System.err.println("Attach failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
