// MiddleMan launcher exe: runs launch.ps1 so RuneLite starts with the agent and the dashboard opens.
// Place MiddleMan.exe in the MiddleMan folder (same folder as launcher\, agent\, dashboard\).
// Build: MiddleMan\build-exe.bat
// On another PC: copy the whole MiddleMan folder (with exe, launcher, agent, dashboard) next to RuneLite files (config.json, jre, etc.) and run the exe.

using System;
using System.Diagnostics;
using System.IO;

static class OpenDashboard
{
    static void Main()
    {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(
            Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        string root = Path.GetDirectoryName(baseDir);
        string configPath = Path.Combine(root, "config.json");
        string launchPs1 = Path.Combine(baseDir, "launcher", "launch.ps1");

        if (!File.Exists(configPath))
        {
            return;
        }
        if (!File.Exists(launchPs1))
        {
            return;
        }

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
