#!/usr/bin/env python3
"""
MiddleMan launcher: starts RuneLite with the MiddleMan agent loaded via -javaagent,
and without -XX:+DisableAttachMechanism so the agent can run.

Run from the RuneLite project root, or from any directory (script locates root).
Usage: python MiddleMan/launcher/launch.py
   or: python launch.py   (when run from MiddleMan/launcher with cwd = project root)
"""

import json
import os
import subprocess
import sys

def find_project_root():
    # When run as "python MiddleMan/launcher/launch.py", __file__ is in MiddleMan/launcher
    script_dir = os.path.dirname(os.path.abspath(__file__))
    # MiddleMan/launcher -> go up to MiddleMan, then to project root
    middleman_dir = os.path.dirname(script_dir)
    root = os.path.dirname(middleman_dir)
    return root

def main():
    root = find_project_root()
    config_path = os.path.join(root, "config.json")
    if not os.path.isfile(config_path):
        print("Error: config.json not found at", config_path, file=sys.stderr)
        sys.exit(1)

    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    class_path = config.get("classPath", ["RuneLite.jar"])
    if isinstance(class_path, list):
        cp = os.pathsep.join(os.path.join(root, p) for p in class_path)
    else:
        cp = os.path.join(root, class_path)

    main_class = config.get("mainClass", "net.runelite.launcher.Launcher")
    vm_args = list(config.get("vmArgs", []))

    # Remove DisableAttachMechanism so the agent (and future attach) can work
    vm_args = [a for a in vm_args if "-XX:+DisableAttachMechanism" not in a and "DisableAttachMechanism" not in a]

    agent_jar = os.path.join(root, "MiddleMan", "agent", "build", "MiddleManAgent.jar")
    if not os.path.isfile(agent_jar):
        print("Error: Agent JAR not found. Build it first: cd MiddleMan/agent && ./build.ps1 or build.bat", file=sys.stderr)
        sys.exit(1)

    vm_args.append("-javaagent:" + os.path.normpath(agent_jar))

    # Prefer RuneLite's bundled JRE if present
    jre_bin = os.path.join(root, "jre", "bin", "java.exe")
    if not os.path.isfile(jre_bin):
        jre_bin = os.path.join(root, "jre", "bin", "java")
    if not os.path.isfile(jre_bin):
        jre_bin = "java"

    # REFLECT = run client in same JVM as launcher so the MiddleMan agent can see the Client
    cmd = [jre_bin] + vm_args + ["-cp", cp, main_class, "--launch-mode=REFLECT"]
    print("Starting RuneLite with MiddleMan agent (launch-mode=REFLECT)...")
    subprocess.run(cmd, cwd=root)

if __name__ == "__main__":
    main()
