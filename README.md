# MiddleMan: RuneLite Game State Bridge

MiddleMan exposes game state from a running RuneLite client (game state, entities, inventory, camera, etc.) over HTTP **without recompiling RuneLite** and **without changing any files** in the main project. It runs as a Java agent inside the RuneLite JVM and serves JSON over a local port.

## How it works

1. **Launcher** – You start RuneLite via the MiddleMan launcher instead of the default shortcut. The launcher passes `-javaagent:MiddleManAgent.jar` and omits `-XX:+DisableAttachMechanism` so the agent can load.
2. **Agent** – When the client starts, the agent discovers the RuneLite `Client` via reflection (Guice injector) and starts a small HTTP server on `127.0.0.1`.
3. **Your app** – Any application can request game state by calling the HTTP endpoints below.

The default RuneLite launcher and `config.json` are **not modified**; you simply use a different entry point when you want the bridge.

## Quick start

### 1. Build the agent

From the **RuneLite project root** (the folder that contains `config.json` and `RuneLite.jar`):

```powershell
cd MiddleMan\agent
.\build.ps1
```

Or on Windows CMD: `build.bat`  
Or with Gradle: `gradle jar` (if Gradle is installed). The JAR is produced at `MiddleMan/agent/build/MiddleManAgent.jar`.

### 2. Start RuneLite with MiddleMan

From the **RuneLite project root**:

**Batch (recommended; avoids PowerShell execution policy):**
```cmd
.\MiddleMan\launcher\launch.bat
```

**PowerShell** (if scripts are allowed):
```powershell
.\MiddleMan\launcher\launch.ps1
```

**Standalone exe (same as launch.bat):** To get a single exe that starts RuneLite with the agent and opens the dashboard (for use on another PC):
```cmd
cd MiddleMan
build-exe.bat
```
This creates `MiddleMan.exe` in the MiddleMan folder. Run it from that folder. It runs the full launcher (same as `launch.bat`): RuneLite starts with the MiddleMan agent and the dashboard opens. To **only open the dashboard** when RuneLite is already running with the agent (e.g. after closing the browser), run `MiddleMan.exe -d` or `MiddleMan.exe --dashboard-only`. No extra windows; requires .NET Framework (standard on Windows). On another PC, copy the whole **MiddleMan** folder (exe, `launcher\`, `agent\`, `dashboard\`) next to the RuneLite files (`config.json`, `jre\`, etc.) and run `MiddleMan.exe`.

**Python 3:**
```bash
python MiddleMan/launcher/launch.py
```

RuneLite will start as usual. The launcher passes `--launch-mode=REFLECT` so the **client runs in the same JVM** as the launcher; that way the MiddleMan agent can see the RuneLite `Client`. Once the client is up and you are in game (or at least past the launcher), the agent will attach and print:

```
[MiddleMan] Agent thread started.
[MiddleMan] Waiting for RuneLite Client (port 8766)...
[MiddleMan] Client found, starting HTTP server.
[MiddleMan] Game state API listening on http://127.0.0.1:8766
```

**Log file:** The same messages are written to **`MiddleMan/middleman.log`**. If the console is flooded with RuneLite DEBUG output, open that file to see whether the agent started and the API is listening.

**If you never see these messages / dashboard says Failed to connect:** (1) Start RuneLite **only** via `MiddleMan\launcher\launch.bat` (not the normal RuneLite shortcut). (2) Check `MiddleMan/middleman.log` for agent status. (3) The game client must run in the same JVM (the launcher uses `--launch-mode=REFLECT`). Rebuild the agent if needed: `MiddleMan\agent\build.bat`.

### 3. View game state in the dashboard (simple GUI)

With RuneLite running via the MiddleMan launcher, open the dashboard in your browser:

- **Open:** `MiddleMan/dashboard/index.html` (double-click the file or drag it into Chrome/Edge/Firefox).

The dashboard shows live **game state**, **local player**, **players**, **NPCs**, **world view**, **inventory**, and **camera**. Use **Refresh** to poll once, or check **Auto (2s)** to update every 2 seconds. Change the port if you use `MIDDLEMAN_PORT` or agent args.

### 4. Change the API without restarting RuneLite

The agent runs inside RuneLite, so changing the agent JAR means restarting the game. To change **your** API (custom endpoints, response shape, etc.) without restarting RuneLite:

1. **API proxy** – Run the Node proxy so clients talk to it instead of the agent:
   ```bash
   cd MiddleMan
   node api-server.js
   ```
   It listens on **8765** and forwards to the agent on **8766**. Edit **`api-server.js`** to add routes or transform responses; when you save, **restart the Node process** (Ctrl+C, then `node api-server.js` again). RuneLite can keep running. Point the dashboard port to **8765** when using the proxy.

2. **Direct** – The agent listens on **8766**. The dashboard defaults to 8766 so it works without the proxy. Use 8766 when you don’t need to customize the API.

### 5. Call the API from your app

Base URL: **http://127.0.0.1:8766** (agent) or **http://127.0.0.1:8765** (proxy, if running). Dashboard port input is configurable.

| Endpoint | Description |
|----------|-------------|
| `GET /` | Service info and list of endpoints |
| `GET /game/state` | Full snapshot: game state, local player, players, NPCs, world view, inventory, camera |
| `GET /game/state/simple` | Current game state only (e.g. `LOGGED_IN`, `LOGIN_SCREEN`, `LOADING`) |
| `GET /game/players` | List of players in the current world view |
| `GET /game/npcs` | List of NPCs in the current world view |

All responses are **JSON** with `Content-Type: application/json` and `Access-Control-Allow-Origin: *` for use in browsers or other tools.

#### Example: full state

```bash
curl http://127.0.0.1:8766/game/state
```

Example shape (fields may be null if not in game):

```json
{
  "gameState": "LOGGED_IN",
  "localPlayer": { "name": "Player", "worldX": 3222, "worldY": 3222, "plane": 0, "animation": -1 },
  "players": [ { "name": "...", "worldX": 0, "worldY": 0, "plane": 0, "animation": -1, "combatLevel": 3 } ],
  "npcs": [ { "name": "...", "worldX": 0, "worldY": 0, "plane": 0, "animation": -1, "npcId": 123 } ],
  "worldView": { "baseX": 0, "baseY": 0, "plane": 0 },
  "inventory": [ { "id": 0, "quantity": 0 }, ... ],
  "camera": { "x": 0, "y": 0, "z": 0, "pitch": 0, "yaw": 0 }
}
```

#### Example: simple game state

```bash
curl http://127.0.0.1:8766/game/state/simple
```

```json
{ "gameState": "LOGGED_IN" }
```

## Configuration

- **Agent port** – Default is **8766** (set by the launcher). Override with env `MIDDLEMAN_PORT=9999` or agent arg: `-javaagent:MiddleManAgent.jar=9999`.
- **API proxy** – Optional. Run `node api-server.js` to listen on **8765** and forward to the agent. Edit `api-server.js` and restart the Node process to change the API without restarting RuneLite.

## Requirements

- **Java 11+** to build and run the agent. The RuneLite JRE in the project is used by the launcher if present; otherwise the system `java` is used.
- **Python 3** only if you use `launch.py`; the batch and PowerShell launchers do not need Python.
- RuneLite must be started **via the MiddleMan launcher** (so that `-javaagent` is set and `DisableAttachMechanism` is not). The standard RuneLite shortcut does **not** load the agent.

**Windows execution policy:** If you get "running scripts is disabled" when running `launch.ps1`, use **`launch.bat`** instead. It starts PowerShell with `-ExecutionPolicy Bypass` for that run only, so no admin rights or system policy change is required.

## Thread safety note

Game state is read from the RuneLite client from the agent’s HTTP handler threads. The client is designed to be used on the game thread. The API is intended for best-effort reads (e.g. for AI or tooling); avoid heavy polling from many clients.

## Folder layout

- `MiddleMan/agent/` – Java agent source and build. Output: `MiddleMan/agent/build/MiddleManAgent.jar`.
- `MiddleMan/launcher/` – Scripts to start RuneLite with the agent (`launch.ps1`, `launch.py`).
- `MiddleMan/README.md` – This file.

No files outside `MiddleMan/` are created or edited by this bridge.
