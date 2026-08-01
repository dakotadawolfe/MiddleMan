# OSRS MiddleMan

OSRS MiddleMan is a local RuneLite bridge. It loads a Java agent into a RuneLite JVM, exposes Old School RuneScape state over a loopback HTTP API, and provides a dashboard for inspecting players, NPCs, world objects, ground items, inventory, equipment, camera, and game state.

The project is designed to work without recompiling RuneLite. It either starts RuneLite with the agent or attaches the agent to an already running RuneLite process that was launched with attach support enabled.

> **Security notice:** the API can inspect live gameplay and trigger supported in-game actions. It must remain bound to `127.0.0.1`; never expose it through a network, proxy, or port-forwarding service. Only use this tooling where it is permitted.

## Features

- Java agent loaded with `-javaagent` or through the JDK attach API.
- Local HTTP API on `127.0.0.1:8765`.
- JSON snapshots for game state, players, NPCs, world objects, ground items, inventory, equipment, camera, and map context.
- POST action endpoints for world objects, NPCs, and ground items.
- Item sprite endpoint for dashboard rendering.
- Swing dashboard that polls the local API and can trigger supported actions.
- Windows launcher scripts plus a small `MiddleMan.exe` wrapper.

## Requirements

- Windows.
- RuneLite installed or available in the parent project layout expected by `launcher/launch.ps1`.
- Java 11 or newer.
- A JDK, not only a JRE, when attaching to an already running RuneLite process. The attach path needs the `jdk.attach` module.
- .NET Framework to rebuild `MiddleMan.exe` from `OpenDashboard.cs`.

## Quick Start

Start RuneLite in an attachable mode:

```bat
launcher\launch-attachable.bat
```

Attach MiddleMan and open the dashboard:

```bat
attach-and-open.bat
```

If RuneLite is not running, `MiddleMan.exe` can also start RuneLite with the agent and open the dashboard.

## Build

Build the Java agent:

```bat
cd agent
build.bat
```

Build the Windows wrapper:

```bat
build-exe.bat
```

The wrapper compiles `OpenDashboard.cs` into `MiddleMan.exe` using the .NET Framework C# compiler.

## Running Modes

| Mode | Command | Notes |
| --- | --- | --- |
| Start RuneLite with the agent | `launcher\launch.bat` | Uses `launcher\launch.ps1` and adds `-javaagent`. |
| Start RuneLite attachable only | `launcher\launch-attachable.bat` | Starts RuneLite without `DisableAttachMechanism` so the agent can attach later. |
| Attach to running RuneLite | `attach-and-open.bat` | Builds a fresh agent JAR, attaches it, then opens the dashboard. |
| One-click wrapper | `MiddleMan.exe` | Builds the agent if possible, attaches or starts RuneLite, then opens the dashboard. |

## API

The API is intentionally loopback-only. Its POST endpoints can trigger in-game actions, so do not bind or relay this service beyond `127.0.0.1`.

Base URL:

```text
http://127.0.0.1:8765
```

Endpoints:

- `GET /` returns service information and endpoint names.
- `GET /game/state` returns a full state snapshot.
- `GET /game/state/simple` returns the current RuneLite game state.
- `GET /game/players` returns visible player data.
- `GET /game/npcs` returns visible NPC data.
- `GET /game/worldobjects` returns visible world object data.
- `GET /game/grounditems` returns visible ground item data.
- `POST /game/worldobject/action` triggers a supported world-object action.
- `POST /game/npc/action` triggers a supported NPC action.
- `POST /game/grounditem/action` triggers a supported ground-item action.
- `GET /game/sprite/item/{id}` returns item sprite data for dashboard display.
- `GET /shutdown` stops the local server.

Example:

```bash
curl http://127.0.0.1:8765/game/state
```

## Dashboard

The dashboard can be opened directly from `dashboard/index.html` or through `MiddleMan.exe` / `attach-and-open.bat`. It shows the current API state and includes searchable sections for NPCs and world objects.

The Java agent also contains a Swing dashboard implementation in `agent/src/main/java/middleman/agent/DashboardFrame.java`.

## Repository Layout

```text
.
|-- OpenDashboard.cs          # Windows wrapper source
|-- MiddleMan.exe             # Prebuilt wrapper
|-- attach-and-open.bat       # Build, attach, and open dashboard
|-- build-exe.bat             # Build MiddleMan.exe
|-- agent/                    # Java agent source and Gradle project
|-- dashboard/                # Browser dashboard
`-- launcher/                 # RuneLite launch scripts
```

## Troubleshooting

- If attach fails, install a JDK and set `JAVA_HOME` so `java --add-modules jdk.attach` works.
- If normal RuneLite was already started, close it and start with `launcher\launch-attachable.bat`.
- If port `8765` is busy, stop the old agent or call `/shutdown`.
- If the dashboard shows stale data, refresh after the agent reports that the API has started.

## Safety

This project introspects a live game client and can trigger in-game actions through local endpoints. Keep the API bound to `127.0.0.1`, do not expose it to a network, and only run it in environments where this kind of tooling is allowed.
