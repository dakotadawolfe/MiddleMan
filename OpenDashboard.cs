// MiddleMan launcher exe: runs launch.ps1 so RuneLite starts with the agent and the dashboard opens.
// Use MiddleMan.exe -d (or --dashboard-only) to only open the dashboard when RuneLite is already running with the agent.
// Place MiddleMan.exe in the MiddleMan folder (same folder as launcher\, agent\, dashboard\).

using System;
using System.Diagnostics;
using System.IO;

static class OpenDashboard
{
    static void Main(string[] args)
    {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(
            Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        bool dashboardOnly = false;
        if (args != null && args.Length > 0)
        {
            string a = args[0].Trim().ToLowerInvariant();
            if (a == "--dashboard-only" || a == "-d" || a == "/d")
                dashboardOnly = true;
        }

        if (dashboardOnly)
        {
            string html = Path.Combine(baseDir, "dashboard", "index.html");
            if (File.Exists(html))
            {
                try
                {
                    Process.Start(new ProcessStartInfo(html) { UseShellExecute = true });
                }
                catch { }
            }
            return;
        }

        string root = Path.GetDirectoryName(baseDir);
        string configPath = Path.Combine(root, "config.json");
        string launchPs1 = Path.Combine(baseDir, "launcher", "launch.ps1");

        if (!File.Exists(configPath) || !File.Exists(launchPs1))
            return;

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
