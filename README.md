# MiddleMan: RuneLite Game State Bridge

MiddleMan exposes game state from a running RuneLite client (game state, entities, inventory, camera, etc.) over HTTP **without recompiling RuneLite** and **without changing any files** in the main project. It runs as a Java agent inside the RuneLite JVM and serves JSON on port **8765**.

## Quick start: batch file (simplest)

1. **Start RuneLite** so it can be attached to:  
   `MiddleMan\launcher\launch-attachable.bat`

2. **When you want to attach or refresh the agent** (e.g. after code changes):  
   `MiddleMan\attach-and-open.bat`  
   This builds the agent, attaches to the running RuneLite (if found), and opens the dashboard. No exe, no JAVA_HOME needed in the exe—just run the bat from a terminal where Java is on PATH (or set JAVA_HOME so `build.bat` finds `javac`).

## Optional: run the exe

1. **Build once:** `MiddleMan\agent\build.bat`  
2. **Build the exe:** `MiddleMan\build-exe.bat` → creates `MiddleMan\MiddleMan.exe`.  
3. **Use it:** Double‑click **MiddleMan.exe** to start RuneLite with the agent (or attach if RuneLite is already running via launch-attachable.bat) and open the dashboard.

## Two ways to run RuneLite with MiddleMan

| You do this | MiddleMan.exe does |
|-------------|---------------------|
| Run **MiddleMan.exe** only | Starts RuneLite with the agent and opens the dashboard. |
| Run **launch-attachable.bat** (or `launch.ps1 -AttachableOnly`), then **MiddleMan.exe** | Attaches the agent to the already‑running RuneLite and opens the dashboard. |

To “launch RuneLite separately then MiddleMan.exe at any time,” start RuneLite with **MiddleMan\launcher\launch-attachable.bat** instead of `RuneLite.exe`. That starts RuneLite without `DisableAttachMechanism` so the exe can attach the agent later. If you start RuneLite with the normal `RuneLite.exe`, attach will fail; use the exe alone (it will start RuneLite with the agent) or use the attachable launcher first.

## Dashboard and API

- **Dashboard:** Open `MiddleMan/dashboard/index.html` (or let the exe open it). Port defaults to **8765**.
- **API base:** `http://127.0.0.1:8765`

| Endpoint | Description |
|----------|-------------|
| `GET /` | Service info and list of endpoints |
| `GET /game/state` | Full snapshot: game state, local player, players, NPCs, world view, inventory, camera |
| `GET /game/state/simple` | Current game state only (e.g. `LOGGED_IN`, `LOGIN_SCREEN`, `LOADING`) |
| `GET /game/players` | List of players in the current world view |
| `GET /game/npcs` | List of NPCs in the current world view |

All responses are JSON with `Content-Type: application/json` and `Access-Control-Allow-Origin: *`.

**Example:** `curl http://127.0.0.1:8765/game/state`

## Folder layout

- **MiddleMan/agent/** – Java agent source and build. Output: `MiddleMan/agent/build/MiddleManAgent.jar`.
- **MiddleMan/launcher/** – `launch.ps1` (with or without agent), `launch.bat`, `launch-attachable.bat`.
- **MiddleMan/dashboard/** – `index.html` (simple GUI).
- **MiddleMan/MiddleMan.exe** – One-click: attach or start RuneLite + agent, then open dashboard. Build with `build-exe.bat`.
- **MiddleMan/README.md** – This file.

Logs and any runtime files go under **MiddleMan/** (e.g. `middleman.log`), not the project root.

## Requirements

- **Java 11+** to build the agent. The RuneLite JRE (project `jre\`) or system `java` is used to run RuneLite and, when attaching, the injector (attach needs a JDK with `jdk.attach`; if missing, the exe falls back to starting RuneLite with the agent).
- **.NET Framework** for MiddleMan.exe (normal on Windows).
- For **attach** to work, RuneLite must be started without `-XX:+DisableAttachMechanism` (use `launch-attachable.bat` or the exe’s own launch).

**PowerShell:** If running `launch.ps1` directly fails due to execution policy, use **launch.bat** or **MiddleMan.exe**; they invoke PowerShell with `-ExecutionPolicy Bypass` for that run only.

## Optional: dashboard only

To only open the dashboard (e.g. RuneLite is already running with the agent):  
`MiddleMan.exe -d` or `MiddleMan.exe --dashboard-only`
