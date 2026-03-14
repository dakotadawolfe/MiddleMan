// MiddleMan.exe: attach agent to running RuneLite, or start RuneLite with agent, then open dashboard.
// Run after any changes; works if RuneLite is already running (started via launch-attachable.bat) or not.

using System;
using System.Diagnostics;
using System.IO;
using System.Management;
using System.Text;

static class OpenDashboard
{
    const string SessionId = "01c49b";

    static string Esc(string s) { if (s == null) return ""; return s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\r", "").Replace("\n", " "); }

    static void DebugLog(string path, string location, string message, string dataJson, string hypothesisId)
    {
        try
        {
            var line = "{\"sessionId\":\"" + SessionId + "\",\"timestamp\":" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + ",\"location\":\"" + Esc(location) + "\",\"message\":\"" + Esc(message ?? "") + "\",\"data\":" + (dataJson ?? "{}") + ",\"hypothesisId\":\"" + Esc(hypothesisId ?? "") + "\"}\n";
            File.AppendAllText(path, line, Encoding.UTF8);
        }
        catch { }
    }

    static void Main(string[] args)
    {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(
            Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        string root = Path.GetDirectoryName(baseDir);
        string agentJar = Path.Combine(baseDir, "agent", "build", "MiddleManAgent.jar");
        string launchPs1 = Path.Combine(baseDir, "launcher", "launch.ps1");
        string dashboardHtml = Path.Combine(baseDir, "dashboard", "index.html");
        string logPath = Path.Combine(root, "debug-01c49b.log");

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

        string agentDir = Path.Combine(baseDir, "agent");
        string buildBat = Path.Combine(agentDir, "build.bat");
        if (File.Exists(buildBat))
        {
            try
            {
                string javacDir = FindJavacDir();
                var buildPsi = new ProcessStartInfo
                {
                    FileName = "cmd.exe",
                    Arguments = "/c \"" + buildBat + "\"",
                    WorkingDirectory = agentDir,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };
                if (!string.IsNullOrEmpty(javacDir))
                {
                    foreach (System.Collections.DictionaryEntry e in Environment.GetEnvironmentVariables())
                        buildPsi.EnvironmentVariables[e.Key.ToString()] = e.Value.ToString();
                    string path = buildPsi.EnvironmentVariables["PATH"] ?? "";
                    buildPsi.EnvironmentVariables["PATH"] = javacDir + ";" + path;
                    string jdkRoot = Path.GetDirectoryName(javacDir);
                    if (!string.IsNullOrEmpty(jdkRoot))
                        buildPsi.EnvironmentVariables["JAVA_HOME"] = jdkRoot;
                }
                int buildExit = -1;
                using (var p = Process.Start(buildPsi))
                {
                    if (p != null)
                    {
                        p.WaitForExit(60000);
                        buildExit = p.HasExited ? p.ExitCode : -1;
                    }
                }
                DebugLog(logPath, "OpenDashboard.Main", "Agent build", "{\"javacDir\":\"" + Esc(javacDir ?? "") + "\",\"buildExitCode\":" + buildExit + "}", "build");
            }
            catch { }
        }

        if (!File.Exists(launchPs1))
            return;

        int? runelitePid = null;
        if (!dashboardOnly)
            runelitePid = FindRuneLiteProcessId();

        DebugLog(logPath, "OpenDashboard.Main", "FindRuneLiteProcessId result", "{\"runelitePid\":" + (runelitePid.HasValue ? runelitePid.Value.ToString() : "null") + "}", "H1");

        bool attached = false;
        if (runelitePid.HasValue && File.Exists(agentJar))
        {
            string javaExe = FindJavaForAttach(root);
            DebugLog(logPath, "OpenDashboard.Main", "Paths and Java", "{\"agentJar\":\"" + Esc(agentJar) + "\",\"agentJarExists\":" + (File.Exists(agentJar) ? "true" : "false") + ",\"javaExe\":\"" + Esc(javaExe) + "\",\"root\":\"" + Esc(root) + "\"}", "H2,H4");
            if (!string.IsNullOrEmpty(javaExe))
            {
                int exit; string stderr;
                RunInjector(javaExe, agentJar, runelitePid.Value, logPath, root, out exit, out stderr);
                string errTrunc = (stderr ?? ""); if (errTrunc.Length > 500) errTrunc = errTrunc.Substring(0, 500);
                DebugLog(logPath, "OpenDashboard.Main", "RunInjector result", "{\"exit\":" + exit + ",\"stderr\":\"" + Esc(errTrunc) + "\"}", "H2,H3");
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

    static string FindJavacDir()
    {
        string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrEmpty(javaHome))
        {
            string jc = Path.Combine(javaHome.Trim(), "bin", "javac.exe");
            if (File.Exists(jc)) return Path.GetDirectoryName(jc);
        }
        try
        {
            using (var p = Process.Start(new ProcessStartInfo
            {
                FileName = "cmd.exe",
                Arguments = "/c where javac",
                UseShellExecute = false,
                RedirectStandardOutput = true,
                CreateNoWindow = true
            }))
            {
                if (p != null)
                {
                    string output = p.StandardOutput.ReadToEnd();
                    p.WaitForExit(2000);
                    if (p.ExitCode == 0 && !string.IsNullOrEmpty(output))
                    {
                        var lines = output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
                        if (lines.Length > 0)
                        {
                            string first = lines[0].Trim();
                            if (File.Exists(first)) return Path.GetDirectoryName(first);
                        }
                    }
                }
            }
        }
        catch { }
        string pf = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        string pf86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
        foreach (string basePath in new[] { pf, pf86 })
        {
            if (string.IsNullOrEmpty(basePath) || !Directory.Exists(basePath)) continue;
            try
            {
                foreach (string dir in Directory.GetDirectories(basePath, "jdk*"))
                {
                    string jc = Path.Combine(dir, "bin", "javac.exe");
                    if (File.Exists(jc)) return Path.GetDirectoryName(jc);
                }
                foreach (string dir in Directory.GetDirectories(basePath, "java*"))
                {
                    string jc = Path.Combine(dir, "bin", "javac.exe");
                    if (File.Exists(jc)) return Path.GetDirectoryName(jc);
                }
            }
            catch { }
        }
        return null;
    }

    static string FindJavaForAttach(string root)
    {
        // Prefer "java" from PATH so we use a JDK (with jdk.attach) for the injector when available.
        // The project jre is often a JRE and does not support --add-modules jdk.attach, causing exit 1.
        try
        {
            var p = Process.Start(new ProcessStartInfo
            {
                FileName = "java",
                Arguments = "-version",
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardError = true,
                RedirectStandardOutput = true
            });
            if (p != null) { p.WaitForExit(2000); p.Dispose(); return "java"; }
        }
        catch { }
        string jre = Path.Combine(root, "jre", "bin", "java.exe");
        if (File.Exists(jre)) return jre;
        jre = Path.Combine(root, "jre", "bin", "java");
        if (File.Exists(jre)) return jre;
        return "java";
    }

    static void RunInjector(string javaExe, string agentJar, int pid, string logPath, string root, out int exitCode, out string stderrOut)
    {
        exitCode = -1;
        stderrOut = "";
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = javaExe,
                Arguments = "--add-modules jdk.attach -cp \"" + agentJar + "\" middleman.agent.AttachMain " + pid + " \"" + agentJar + "\" \"" + logPath + "\"",
                WorkingDirectory = Path.GetDirectoryName(agentJar),
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardError = true,
                RedirectStandardOutput = true
            };
            using (var p = Process.Start(psi))
            {
                p.WaitForExit(15000);
                stderrOut = p.StandardError.ReadToEnd();
                if (stderrOut == null) stderrOut = "";
                string stdout = p.StandardOutput.ReadToEnd();
                if (!string.IsNullOrEmpty(stdout)) stderrOut = stderrOut + "[stdout] " + stdout;
                exitCode = p.HasExited ? p.ExitCode : -1;
            }
        }
        catch (Exception ex)
        {
            stderrOut = ex.Message;
        }
    }
}
