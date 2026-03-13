// MiddleMan.exe: attach agent to running RuneLite, or start RuneLite with agent, then open dashboard.
// Run after any changes; works if RuneLite is already running (started via launch-attachable.bat) or not.

using System;
using System.Diagnostics;
using System.IO;
using System.Management;

static class OpenDashboard
{
    static void Main(string[] args)
    {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(
            Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        string root = Path.GetDirectoryName(baseDir);
        string agentJar = Path.Combine(baseDir, "agent", "build", "MiddleManAgent.jar");
        string launchPs1 = Path.Combine(baseDir, "launcher", "launch.ps1");
        string dashboardHtml = Path.Combine(baseDir, "dashboard", "index.html");

        bool dashboardOnly = false;
        if (args != null && args.Length > 0)
        {
            string a = args[0].Trim().ToLowerInvariant();
            if (a == "--dashboard-only" || a == "-d" || a == "/d")
                dashboardOnly = true;
        }

        if (dashboardOnly)
        {
            if (File.Exists(dashboardHtml))
            {
                try { Process.Start(new ProcessStartInfo(dashboardHtml) { UseShellExecute = true }); } catch { }
            }
            return;
        }

        if (!File.Exists(launchPs1))
            return;

        int? runelitePid = null;
        if (!dashboardOnly)
            runelitePid = FindRuneLiteProcessId();

        bool attached = false;
        if (runelitePid.HasValue && File.Exists(agentJar))
        {
            string javaExe = FindJavaForAttach(root);
            if (!string.IsNullOrEmpty(javaExe))
            {
                int exit = RunInjector(javaExe, agentJar, runelitePid.Value);
                attached = (exit == 0);
            }
        }

        if (File.Exists(dashboardHtml))
        {
            try { Process.Start(new ProcessStartInfo(dashboardHtml) { UseShellExecute = true }); } catch { }
        }

        // Start RuneLite with agent only when no RuneLite is running (so we don't start a second one)
        if (!runelitePid.HasValue)
        {
            try
            {
                var psi = new ProcessStartInfo
                {
                    FileName = "powershell.exe",
                    Arguments = "-ExecutionPolicy Bypass -NoProfile -File \"" + launchPs1 + "\"",
                    WorkingDirectory = root,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };
                Process.Start(psi);
            }
            catch { }
        }
    }

    static int? FindRuneLiteProcessId()
    {
        try
        {
            using (var searcher = new ManagementObjectSearcher("SELECT ProcessId, CommandLine FROM Win32_Process WHERE Name = 'java.exe'"))
            using (var results = searcher.Get())
            {
                foreach (ManagementObject mo in results)
                {
                    object cmd = mo["CommandLine"];
                    if (cmd == null) continue;
                    string line = cmd.ToString();
                    if (string.IsNullOrEmpty(line)) continue;
                    string lower = line.ToLowerInvariant();
                    if (lower.Contains("runelite") || lower.Contains("runelite.jar") || lower.Contains("net.runelite"))
                    {
                        object pid = mo["ProcessId"];
                        if (pid != null)
                        {
                            try { return Convert.ToInt32(pid); } catch { }
                        }
                    }
                }
            }
        }
        catch { }
        return null;
    }

    static string FindJavaForAttach(string root)
    {
        string jre = Path.Combine(root, "jre", "bin", "java.exe");
        if (File.Exists(jre)) return jre;
        jre = Path.Combine(root, "jre", "bin", "java");
        if (File.Exists(jre)) return jre;
        return "java";
    }

    static int RunInjector(string javaExe, string agentJar, int pid)
    {
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = javaExe,
                Arguments = "--add-modules jdk.attach -cp \"" + agentJar + "\" middleman.agent.AttachMain " + pid + " \"" + agentJar + "\"",
                WorkingDirectory = Path.GetDirectoryName(agentJar),
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardError = true,
                RedirectStandardOutput = true
            };
            using (var p = Process.Start(psi))
            {
                p.WaitForExit(15000);
                return p.HasExited ? p.ExitCode : -1;
            }
        }
        catch
        {
            return -1;
        }
    }
}
