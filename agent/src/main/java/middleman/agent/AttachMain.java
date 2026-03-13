package middleman.agent;

import com.sun.tools.attach.VirtualMachine;

/**
 * Standalone entry point to load MiddleManAgent into an already-running JVM.
 * Usage: java --add-modules jdk.attach -cp MiddleManAgent.jar middleman.agent.AttachMain <pid> <path-to-MiddleManAgent.jar>
 * The target JVM must not have been started with -XX:+DisableAttachMechanism.
 */
public final class AttachMain {
    public static void main(String[] args) {
        if (args == null || args.length < 2) {
            System.err.println("Usage: AttachMain <pid> <agent-jar-path>");
            System.exit(1);
        }
        String pid = args[0].trim();
        String agentPath = args[1].trim();
        try {
            VirtualMachine vm = VirtualMachine.attach(pid);
            try {
                vm.loadAgent(agentPath, "8765");
            } finally {
                vm.detach();
            }
        } catch (Exception e) {
            System.err.println("Attach failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
