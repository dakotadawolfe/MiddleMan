package middleman.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serializes game state from the RuneLite Client (via reflection) to JSON.
 * All RuneLite types are accessed by name; no compile-time dependency.
 */
final class StateSerializer {

    private final Object client;
    private final Object clientThread;
    private final Object itemManager;

    StateSerializer(Object client, Object clientThread, Object itemManager) {
        this.client = client;
        this.clientThread = clientThread;
        this.itemManager = itemManager;
    }

    /** Prefer version from runtime property (set by ReloadRunner from attach args). */
    static String getAgentVersion() { return System.getProperty("middleman.agent.version", AgentVersion.V); }

    String serializeFullState() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"agentVersion\":\"").append(escape(getAgentVersion())).append("\",");
        appendGameState(sb);
        sb.append(",\"localPlayer\":");
        appendLocalPlayer(sb);
        sb.append(",\"players\":");
        appendPlayers(sb);
        sb.append(",\"npcs\":");
        appendNpcs(sb);
        sb.append(",\"worldObjects\":");
        appendWorldObjects(sb);
        sb.append(",\"worldView\":");
        appendWorldView(sb);
        sb.append(",\"inventory\":");
        appendInventory(sb);
        sb.append(",\"camera\":");
        appendCamera(sb);
        sb.append("}");
        return sb.toString();
    }

    String serializeGameState() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendGameState(sb);
        sb.append("}");
        return sb.toString();
    }

    String serializePlayers() {
        StringBuilder sb = new StringBuilder();
        appendPlayers(sb);
        return sb.toString();
    }

    String serializeNpcs() {
        StringBuilder sb = new StringBuilder();
        appendNpcs(sb);
        return sb.toString();
    }

    String serializeWorldObjects() {
        StringBuilder sb = new StringBuilder();
        appendWorldObjects(sb);
        return sb.toString();
    }

    private void appendGameState(StringBuilder sb) {
        try {
            Method m = client.getClass().getMethod("getGameState");
            Object gs = m.invoke(client);
            String name = gs != null ? gs.toString() : "UNKNOWN";
            sb.append("\"gameState\":\"").append(escape(name)).append("\"");
        } catch (Exception e) {
            sb.append("\"gameState\":null,\"error\":\"").append(escape(e.getMessage())).append("\"");
        }
    }

    private void appendLocalPlayer(StringBuilder sb) {
        try {
            Method m = client.getClass().getMethod("getLocalPlayer");
            Object player = m.invoke(client);
            if (player == null) {
                sb.append("null");
                return;
            }
            sb.append("{");
            appendActorFields(sb, player, true);
            sb.append("}");
        } catch (Exception e) {
            sb.append("{\"error\":\"").append(escape(e.getMessage())).append("\"}");
        }
    }

    private void appendPlayers(StringBuilder sb) {
        try {
            Method getView = client.getClass().getMethod("getTopLevelWorldView");
            Object view = getView.invoke(client);
            if (view == null) {
                sb.append("[]");
                return;
            }
            Method players = view.getClass().getMethod("players");
            Object set = players.invoke(view);
            appendActorList(sb, set, true);
        } catch (Exception e) {
            sb.append("[]");
        }
    }

    private void appendNpcs(StringBuilder sb) {
        if (clientThread != null) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> holder = new AtomicReference<>();
            Runnable runOnClient = () -> {
                try {
                    holder.set(buildNpcsJson());
                } finally {
                    latch.countDown();
                }
            };
            try {
                clientThread.getClass().getMethod("invoke", Runnable.class).invoke(clientThread, runOnClient);
                if (latch.await(2, TimeUnit.SECONDS) && holder.get() != null) {
                    sb.append(holder.get());
                    return;
                }
            } catch (Throwable ignored) { }
            sb.append("[]");
            return;
        }
        try {
            Method getView = client.getClass().getMethod("getTopLevelWorldView");
            Object view = getView.invoke(client);
            if (view == null) {
                sb.append("[]");
                return;
            }
            Method npcs = view.getClass().getMethod("npcs");
            Object set = npcs.invoke(view);
            appendActorList(sb, set, false);
        } catch (Exception e) {
            sb.append("[]");
        }
    }

    /** Must be called on client thread. Returns JSON array of NPCs sorted by distance (closest first). */
    private String buildNpcsJson() {
        try {
            Method getView = client.getClass().getMethod("getTopLevelWorldView");
            Object view = getView.invoke(client);
            if (view == null) return "[]";
            Method npcsMethod = view.getClass().getMethod("npcs");
            Object set = npcsMethod.invoke(view);
            if (set == null) return "[]";

            int playerX = 0, playerY = 0;
            try {
                Method getLocalPlayer = client.getClass().getMethod("getLocalPlayer");
                Object player = getLocalPlayer.invoke(client);
                if (player != null) {
                    Method getWorldLoc = player.getClass().getMethod("getWorldLocation");
                    Object loc = getWorldLoc.invoke(player);
                    if (loc != null) {
                        playerX = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                        playerY = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                    }
                }
            } catch (Exception ignored) { }

            List<Object> npcList = new ArrayList<>();
            if (set instanceof Iterable) {
                for (Object a : (Iterable<?>) set) npcList.add(a);
            } else {
                try {
                    Method size = set.getClass().getMethod("size");
                    Method get = set.getClass().getMethod("get", int.class);
                    int n = ((Number) size.invoke(set)).intValue();
                    for (int i = 0; i < n; i++) npcList.add(get.invoke(set, i));
                } catch (NoSuchMethodException e) {
                    return "[]";
                }
            }

            List<Object[]> collected = new ArrayList<>();
            for (Object actor : npcList) {
                if (actor == null) continue;
                int npcId = -1;
                try {
                    Object idObj = actor.getClass().getMethod("getId").invoke(actor);
                    if (idObj != null) npcId = ((Number) idObj).intValue();
                } catch (Exception ignored) { }
                if (npcId <= 0 || !isNpcInteractable(client, npcId)) continue;
                int wx = playerX, wy = playerY;
                try {
                    Method getWorldLocation = actor.getClass().getMethod("getWorldLocation");
                    Object loc = getWorldLocation.invoke(actor);
                    if (loc != null) {
                        wx = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                        wy = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                    } else {
                        Method getLocalLocation = actor.getClass().getMethod("getLocalLocation");
                        Object local = getLocalLocation.invoke(actor);
                        if (local != null && view != null) {
                            int lx = (Integer) local.getClass().getMethod("getX").invoke(local);
                            int ly = (Integer) local.getClass().getMethod("getY").invoke(local);
                            int baseX = (Integer) view.getClass().getMethod("getBaseX").invoke(view);
                            int baseY = (Integer) view.getClass().getMethod("getBaseY").invoke(view);
                            final int LOCAL_COORD_BITS = 7;
                            wx = baseX + (lx >> LOCAL_COORD_BITS);
                            wy = baseY + (ly >> LOCAL_COORD_BITS);
                        }
                    }
                } catch (Exception ignored) { }
                double dist = Math.hypot(wx - playerX, wy - playerY);
                StringBuilder jsb = new StringBuilder();
                buildSingleNpcJson(jsb, actor);
                collected.add(new Object[]{ Double.valueOf(dist), jsb.toString() });
            }
            Collections.sort(collected, (a, b) -> Double.compare((Double) a[0], (Double) b[0]));
            StringBuilder out = new StringBuilder();
            out.append("[");
            for (int i = 0; i < collected.size(); i++) {
                if (i > 0) out.append(",");
                out.append(collected.get(i)[1]);
            }
            out.append("]");
            return out.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /** Appends a single NPC JSON object (with braces) to the given StringBuilder. */
    private void buildSingleNpcJson(StringBuilder sb, Object actor) {
        sb.append("{");
        appendActorFields(sb, actor, false);
        appendNpcFields(sb, actor);
        sb.append("}");
    }

    private void appendActorList(StringBuilder sb, Object collectionOrSet, boolean isPlayer) {
        sb.append("[");
        try {
            Iterator<?> it = null;
            if (collectionOrSet instanceof Iterable) {
                it = ((Iterable<?>) collectionOrSet).iterator();
            } else {
                try {
                    Method iteratorMethod = collectionOrSet.getClass().getMethod("iterator");
                    it = (Iterator<?>) iteratorMethod.invoke(collectionOrSet);
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (it != null) {
                boolean first = true;
                while (it.hasNext()) {
                    if (!first) sb.append(",");
                    first = false;
                    Object actor = it.next();
                    sb.append("{");
                    appendActorFields(sb, actor, isPlayer);
                    if (actor != null && !isPlayer) appendNpcFields(sb, actor);
                    sb.append("}");
                }
            } else {
                Method size = collectionOrSet.getClass().getMethod("size");
                int n = (Integer) size.invoke(collectionOrSet);
                Method get = collectionOrSet.getClass().getMethod("get", int.class);
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(",");
                    Object actor = get.invoke(collectionOrSet, i);
                    sb.append("{");
                    appendActorFields(sb, actor, isPlayer);
                    if (actor != null && !isPlayer) appendNpcFields(sb, actor);
                    sb.append("}");
                }
            }
        } catch (Exception ignored) {
        }
        sb.append("]");
    }

    private void appendActorFields(StringBuilder sb, Object actor, boolean isPlayer) {
        if (actor == null) {
            sb.append("\"name\":null");
            return;
        }
        boolean first = true;
        try {
            Method getName = actor.getClass().getMethod("getName");
            Object name = getName.invoke(actor);
            if (!first) sb.append(",");
            first = false;
            sb.append("\"name\":").append(name == null ? "null" : "\"" + escape(String.valueOf(name)) + "\"");

            Method getWorldLocation = actor.getClass().getMethod("getWorldLocation");
            Object loc = getWorldLocation.invoke(actor);
            if (loc != null) {
                int x = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                int y = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                int plane = (Integer) loc.getClass().getMethod("getPlane").invoke(loc);
                sb.append(",\"worldX\":").append(x).append(",\"worldY\":").append(y).append(",\"plane\":").append(plane);
            } else {
                // Fallback: world location can be null for some NPCs; use local position so dashboard can show something
                try {
                    Method getLocalLocation = actor.getClass().getMethod("getLocalLocation");
                    Object local = getLocalLocation.invoke(actor);
                    if (local != null) {
                        int lx = (Integer) local.getClass().getMethod("getX").invoke(local);
                        int ly = (Integer) local.getClass().getMethod("getY").invoke(local);
                        sb.append(",\"localX\":").append(lx).append(",\"localY\":").append(ly);
                        try {
                            Method getView = client.getClass().getMethod("getTopLevelWorldView");
                            Object view = getView.invoke(client);
                            if (view != null) {
                                int p = (Integer) view.getClass().getMethod("getPlane").invoke(view);
                                sb.append(",\"plane\":").append(p);
                            }
                        } catch (Exception ignored) { }
                    }
                } catch (Exception ignored) { }
            }

            Method getAnimation = actor.getClass().getMethod("getAnimation");
            Object anim = getAnimation.invoke(actor);
            sb.append(",\"animation\":").append(anim instanceof Number ? jsonNumber(anim) : "-1");

            if (isPlayer) {
                try {
                    Method getCombatLevel = actor.getClass().getMethod("getCombatLevel");
                    Object level = getCombatLevel.invoke(actor);
                    if (level != null) sb.append(",\"combatLevel\":").append(jsonNumber(level));
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Exception e) {
            if (!first) sb.append(",");
            sb.append("\"error\":\"").append(escape(e.getMessage() != null ? e.getMessage() : "")).append("\"");
        }
    }

    private void appendNpcFields(StringBuilder sb, Object npc) {
        try {
            Method getId = npc.getClass().getMethod("getId");
            Object idObj = getId.invoke(npc);
            if (idObj != null) {
                int id = ((Number) idObj).intValue();
                sb.append(",\"npcId\":").append(id);
                // Resolve NPC name from client's composition (getNpcDefinition / getComposition)
                try {
                    Method getDef = client.getClass().getMethod("getNpcDefinition", int.class);
                    Object comp = getDef.invoke(client, id);
                    if (comp != null) {
                        Method getName = comp.getClass().getMethod("getName");
                        Object name = getName.invoke(comp);
                        if (name != null) {
                            String nameStr = String.valueOf(name).trim();
                            if (!nameStr.isEmpty()) {
                                sb.append(",\"name\":\"").append(escape(nameStr)).append("\"");
                            }
                        }
                    }
                } catch (Exception e) {
                    // Client may use different method names; npcId is still present
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void appendWorldObjects(StringBuilder sb) {
        if (clientThread != null) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> holder = new AtomicReference<>();
            Runnable runOnClient = () -> {
                try {
                    holder.set(buildWorldObjectsJson());
                } finally {
                    latch.countDown();
                }
            };
            try {
                clientThread.getClass().getMethod("invoke", Runnable.class).invoke(clientThread, runOnClient);
                if (latch.await(2, TimeUnit.SECONDS) && holder.get() != null) {
                    sb.append(holder.get());
                    return;
                }
            } catch (Throwable ignored) { }
            sb.append("[]");
            return;
        }
        sb.append("[]");
    }

    /** Must be called on client thread. Returns JSON array of world objects (doors, stairs, game objects, etc.). */
    private String buildWorldObjectsJson() {
        try {
            Method getView = client.getClass().getMethod("getTopLevelWorldView");
            Object view = getView.invoke(client);
            if (view == null) return "[]";
            Method getScene = view.getClass().getMethod("getScene");
            Object scene = getScene.invoke(view);
            if (scene == null) return "[]";
            Method getPlane = view.getClass().getMethod("getPlane");
            Object planeObj = getPlane.invoke(view);
            int currentPlane = planeObj != null ? ((Number) planeObj).intValue() : 0;
            Method getTiles = scene.getClass().getMethod("getTiles");
            Object tilesObj = getTiles.invoke(scene);
            if (tilesObj == null || !tilesObj.getClass().isArray()) return "[]";
            int planes = Array.getLength(tilesObj);
            if (currentPlane < 0 || currentPlane >= planes) return "[]";
            Object planeRow = Array.get(tilesObj, currentPlane);
            if (planeRow == null || !planeRow.getClass().isArray()) return "[]";
            int playerX = 0, playerY = 0;
            try {
                Method getLocalPlayer = client.getClass().getMethod("getLocalPlayer");
                Object player = getLocalPlayer.invoke(client);
                if (player != null) {
                    Method getWorldLoc = player.getClass().getMethod("getWorldLocation");
                    Object loc = getWorldLoc.invoke(player);
                    if (loc != null) {
                        playerX = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                        playerY = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                    }
                }
            } catch (Exception ignored) { }
            List<Object[]> collected = new ArrayList<>();
            int lenX = Array.getLength(planeRow);
            for (int x = 0; x < lenX; x++) {
                Object col = Array.get(planeRow, x);
                if (col == null || !col.getClass().isArray()) continue;
                int lenY = Array.getLength(col);
                for (int y = 0; y < lenY; y++) {
                    Object tile = Array.get(col, y);
                    if (tile == null) continue;
                    appendTileObjects(tile, client, collected, playerX, playerY);
                }
            }
            Collections.sort(collected, (a, b) -> Double.compare((Double) a[0], (Double) b[0]));
            StringBuilder out = new StringBuilder();
            out.append("[");
            for (int i = 0; i < collected.size(); i++) {
                if (i > 0) out.append(",");
                out.append(collected.get(i)[1]);
            }
            out.append("]");
            return out.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private void appendTileObjects(Object tile, Object client, List<Object[]> collected, int playerX, int playerY) {
        try {
            Method getWall = tile.getClass().getMethod("getWallObject");
            Object wall = getWall.invoke(tile);
            if (wall != null) appendTileObjectJson(collected, wall, "wallObject", client, playerX, playerY);
            Method getGround = tile.getClass().getMethod("getGroundObject");
            Object ground = getGround.invoke(tile);
            if (ground != null) appendTileObjectJson(collected, ground, "groundObject", client, playerX, playerY);
            Method getDeco = tile.getClass().getMethod("getDecorativeObject");
            Object deco = getDeco.invoke(tile);
            if (deco != null) appendTileObjectJson(collected, deco, "decorativeObject", client, playerX, playerY);
            Method getGameObjs = tile.getClass().getMethod("getGameObjects");
            Object gameObjs = getGameObjs.invoke(tile);
            if (gameObjs != null && gameObjs.getClass().isArray()) {
                int n = Array.getLength(gameObjs);
                for (int i = 0; i < n; i++) {
                    Object go = Array.get(gameObjs, i);
                    if (go != null) appendTileObjectJson(collected, go, "gameObject", client, playerX, playerY);
                }
            }
        } catch (Exception ignored) { }
    }

    /** Null/placeholder object IDs to always omit (even if client gives them a name). */
    private static final int[] SKIP_OBJECT_IDS = { 0, 20731, 20737 };

    private void appendTileObjectJson(List<Object[]> collected, Object tileObj, String type, Object client, int playerX, int playerY) {
        try {
            Method getId = tileObj.getClass().getMethod("getId");
            Object idObj = getId.invoke(tileObj);
            int id = idObj != null ? ((Number) idObj).intValue() : -1;
            for (int skip : SKIP_OBJECT_IDS) if (id == skip) return;
            if (!isInteractable(client, id)) return;
            String name = resolveObjectName(client, id);
            if (name == null || name.isEmpty()) return;
            Method getWorldLoc = tileObj.getClass().getMethod("getWorldLocation");
            Object loc = getWorldLoc.invoke(tileObj);
            int wx = 0, wy = 0, plane = 0;
            if (loc != null) {
                wx = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                wy = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                plane = (Integer) loc.getClass().getMethod("getPlane").invoke(loc);
            }
            double dist = Math.hypot(wx - playerX, wy - playerY);
            String actionsJson = getObjectActionsJson(client, id);
            String json = "{\"type\":\"" + escape(type) + "\",\"id\":" + id +
                ",\"name\":\"" + escape(name) + "\"" +
                ",\"worldX\":" + wx + ",\"worldY\":" + wy + ",\"plane\":" + plane +
                ",\"actions\":" + actionsJson + "}";
            collected.add(new Object[]{ Double.valueOf(dist), json });
        } catch (Exception ignored) { }
    }

    private String getObjectActionsJson(Object client, int objectId) {
        if (objectId <= 0) return "[]";
        try {
            Method getDef = findMethod(client.getClass(), "getObjectDefinition", "getObjectComposition");
            if (getDef == null) return "[]";
            getDef.setAccessible(true);
            Object comp = getDef.invoke(client, objectId);
            if (comp == null) return "[]";
            Object effective = comp;
            try {
                Method getImpostorIds = comp.getClass().getMethod("getImpostorIds");
                Object ids = getImpostorIds.invoke(comp);
                if (ids != null && ids.getClass().isArray() && Array.getLength(ids) > 0) {
                    Method getImpostor = comp.getClass().getMethod("getImpostor");
                    Object imp = getImpostor.invoke(comp);
                    if (imp != null) effective = imp;
                }
            } catch (NoSuchMethodException ignored) { }
            Method getActions = effective.getClass().getMethod("getActions");
            Object actionsObj = getActions.invoke(effective);
            if (actionsObj == null || !actionsObj.getClass().isArray()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            int len = Array.getLength(actionsObj);
            boolean first = true;
            for (int i = 0; i < len; i++) {
                Object a = Array.get(actionsObj, i);
                if (a != null) {
                    String s = String.valueOf(a).trim();
                    if (!s.isEmpty()) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append("\"").append(escape(s)).append("\"");
                    }
                }
            }
            sb.append("]");
            return sb.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private boolean isInteractable(Object client, int objectId) {
        if (objectId <= 0) return false;
        try {
            Method getDef = findMethod(client.getClass(), "getObjectDefinition", "getObjectComposition");
            if (getDef == null) return false;
            getDef.setAccessible(true);
            Object comp = getDef.invoke(client, objectId);
            if (comp == null) return false;
            Object effective = comp;
            try {
                Method getImpostorIds = comp.getClass().getMethod("getImpostorIds");
                Object ids = getImpostorIds.invoke(comp);
                if (ids != null && ids.getClass().isArray() && Array.getLength(ids) > 0) {
                    Method getImpostor = comp.getClass().getMethod("getImpostor");
                    Object imp = getImpostor.invoke(comp);
                    if (imp != null) effective = imp;
                }
            } catch (NoSuchMethodException ignored) { }
            Method getActions = effective.getClass().getMethod("getActions");
            Object actionsObj = getActions.invoke(effective);
            if (actionsObj == null || !actionsObj.getClass().isArray()) return false;
            int len = Array.getLength(actionsObj);
            for (int i = 0; i < len; i++) {
                Object a = Array.get(actionsObj, i);
                if (a != null && String.valueOf(a).trim().length() > 0) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns true if the NPC has at least one menu action (like world objects: only show interactable). */
    private boolean isNpcInteractable(Object client, int npcId) {
        if (npcId <= 0) return false;
        try {
            Method getDef = findMethod(client.getClass(), "getNpcDefinition", "getNpcComposition");
            if (getDef == null) return false;
            getDef.setAccessible(true);
            Object comp = getDef.invoke(client, npcId);
            if (comp == null) return false;
            Object effective = comp;
            try {
                Method transform = comp.getClass().getMethod("transform");
                Object transformed = transform.invoke(comp);
                if (transformed != null) effective = transformed;
            } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException ignored) { }
            try {
                Method getImpostorIds = effective.getClass().getMethod("getImpostorIds");
                Object ids = getImpostorIds.invoke(effective);
                if (ids != null && ids.getClass().isArray() && Array.getLength(ids) > 0) {
                    Method getImpostor = effective.getClass().getMethod("getImpostor");
                    Object imp = getImpostor.invoke(effective);
                    if (imp != null) effective = imp;
                }
            } catch (NoSuchMethodException ignored) { }
            Method getActions = effective.getClass().getMethod("getActions");
            Object actionsObj = getActions.invoke(effective);
            if (actionsObj == null || !actionsObj.getClass().isArray()) return false;
            int len = Array.getLength(actionsObj);
            for (int i = 0; i < len; i++) {
                Object a = Array.get(actionsObj, i);
                if (a != null && String.valueOf(a).trim().length() > 0) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveObjectName(Object client, int objectId) {
        if (objectId <= 0) return "";
        try {
            Method getDef = findMethod(client.getClass(), "getObjectDefinition", "getObjectComposition");
            if (getDef == null) return "";
            getDef.setAccessible(true);
            Object comp = getDef.invoke(client, objectId);
            if (comp == null) return "";
            Object effective = comp;
            try {
                Method getImpostorIds = comp.getClass().getMethod("getImpostorIds");
                Object ids = getImpostorIds.invoke(comp);
                if (ids != null && ids.getClass().isArray() && Array.getLength(ids) > 0) {
                    Method getImpostor = comp.getClass().getMethod("getImpostor");
                    Object imp = getImpostor.invoke(comp);
                    if (imp != null) effective = imp;
                }
            } catch (NoSuchMethodException ignored) { }
            String s = getNameFromComposition(effective);
            if (s != null && !s.isEmpty()) return s;
            s = getNameFromComposition(comp);
            if (s != null && !s.isEmpty()) return s;
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String getNameFromComposition(Object comp) {
        if (comp == null) return "";
        Class<?> c = comp.getClass();
        try {
            Method getName = c.getMethod("getName");
            getName.setAccessible(true);
            Object name = getName.invoke(comp);
            if (name != null) {
                String s = String.valueOf(name).trim();
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignored) { }
        for (Method m : c.getMethods()) {
            if (m.getParameterCount() != 0 || m.getReturnType() != String.class) continue;
            if (!m.getName().toLowerCase().contains("name")) continue;
            try {
                m.setAccessible(true);
                Object name = m.invoke(comp);
                if (name != null) {
                    String s = String.valueOf(name).trim();
                    if (!s.isEmpty()) return s;
                }
            } catch (Exception ignored) { }
        }
        return "";
    }

    private static Method findMethod(Class<?> c, String... names) {
        Class<?>[] paramTypes = { int.class, Integer.class };
        for (Class<?> cls = c; cls != null; cls = cls.getSuperclass()) {
            for (String name : names) {
                for (Class<?> param : paramTypes) {
                    try {
                        return cls.getMethod(name, param);
                    } catch (NoSuchMethodException ignored) { }
                }
            }
        }
        for (Class<?> iface : c.getInterfaces()) {
            for (String name : names) {
                for (Class<?> param : paramTypes) {
                    try {
                        return iface.getMethod(name, param);
                    } catch (NoSuchMethodException ignored) { }
                }
            }
        }
        return null;
    }

    private void appendWorldView(StringBuilder sb) {
        try {
            Method getView = client.getClass().getMethod("getTopLevelWorldView");
            Object view = getView.invoke(client);
            if (view == null) {
                sb.append("null");
                return;
            }
            sb.append("{");
            int baseX = (Integer) view.getClass().getMethod("getBaseX").invoke(view);
            int baseY = (Integer) view.getClass().getMethod("getBaseY").invoke(view);
            int plane = (Integer) view.getClass().getMethod("getPlane").invoke(view);
            sb.append("\"baseX\":").append(baseX).append(",\"baseY\":").append(baseY).append(",\"plane\":").append(plane);
            sb.append("}");
        } catch (Exception e) {
            sb.append("null");
        }
    }

    private void appendInventory(StringBuilder sb) {
        if (clientThread != null) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> holder = new AtomicReference<>();
            Runnable runOnClient = () -> {
                try {
                    holder.set(buildInventoryJson());
                } finally {
                    latch.countDown();
                }
            };
            try {
                clientThread.getClass().getMethod("invoke", Runnable.class).invoke(clientThread, runOnClient);
                if (latch.await(2, TimeUnit.SECONDS) && holder.get() != null) {
                    sb.append(holder.get());
                    return;
                }
            } catch (Throwable ignored) { }
            sb.append("[]");
            return;
        }
        sb.append("[]");
    }

    /** Must be called on client thread (e.g. from ClientThread.invoke). Returns "[]" on error. */
    private String buildInventoryJson() {
        try {
            ClassLoader loader = client.getClass().getClassLoader();
            Class<?> invIdClass = Class.forName("net.runelite.api.InventoryID", false, loader);
            Object inventoryId = invIdClass.getField("INVENTORY").get(null);
            Method getContainer = client.getClass().getMethod("getItemContainer", invIdClass);
            Object container = getContainer.invoke(client, inventoryId);
            if (container == null) return "[]";
            Method getItems = container.getClass().getMethod("getItems");
            Object items = getItems.invoke(container);
            if (items == null || !items.getClass().isArray()) return "[]";
            int len = Array.getLength(items);
            StringBuilder out = new StringBuilder();
            out.append("[");
            for (int i = 0; i < len; i++) {
                if (i > 0) out.append(",");
                Object item = Array.get(items, i);
                if (item == null) {
                    out.append("{\"slot\":").append(i).append(",\"id\":0,\"quantity\":0,\"name\":\"\"}");
                } else {
                    int id = (Integer) item.getClass().getMethod("getId").invoke(item);
                    int qty = (Integer) item.getClass().getMethod("getQuantity").invoke(item);
                    String name = resolveItemName(id);
                    out.append("{\"slot\":").append(i).append(",\"id\":").append(id).append(",\"quantity\":").append(qty).append(",\"name\":\"").append(escape(name)).append("\"}");
                }
            }
            out.append("]");
            return out.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /** Resolve item name via ItemManager (must be called on client thread). Returns "" if unavailable. */
    private String resolveItemName(int itemId) {
        if (itemManager == null || itemId <= 0) return "";
        try {
            Method getComp = itemManager.getClass().getMethod("getItemComposition", int.class);
            Object comp = getComp.invoke(itemManager, itemId);
            if (comp == null) return "";
            Method getName = comp.getClass().getMethod("getName");
            Object name = getName.invoke(comp);
            return name != null ? String.valueOf(name) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void appendCamera(StringBuilder sb) {
        try {
            sb.append("{");
            try {
                Method x = client.getClass().getMethod("getCameraX");
                Method y = client.getClass().getMethod("getCameraY");
                Method z = client.getClass().getMethod("getCameraZ");
                Method pitch = client.getClass().getMethod("getCameraPitch");
                Method yaw = client.getClass().getMethod("getCameraYaw");
                sb.append("\"x\":").append(jsonNumber(x.invoke(client)));
                sb.append(",\"y\":").append(jsonNumber(y.invoke(client)));
                sb.append(",\"z\":").append(jsonNumber(z.invoke(client)));
                sb.append(",\"pitch\":").append(jsonNumber(pitch.invoke(client)));
                sb.append(",\"yaw\":").append(jsonNumber(yaw.invoke(client)));
            } catch (NoSuchMethodException e) {
                sb.append("\"error\":\"camera methods not found\"");
            }
            sb.append("}");
        } catch (Exception e) {
            sb.append("{}");
        }
    }

    /**
     * Invoke a world object menu action on the client thread.
     * @return null on success, or an error message.
     */
    String invokeWorldObjectAction(int objectId, int worldX, int worldY, int plane, String type, int actionIndex) {
        if (clientThread == null) return "Client not ready";
        if (objectId <= 0) return "Invalid object id";
        if (actionIndex < 0 || actionIndex > 4) return "Invalid action index";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        Runnable runOnClient = () -> {
            try {
                String err = doInvokeWorldObjectAction(objectId, worldX, worldY, plane, type, actionIndex);
                error.set(err);
            } catch (Throwable t) {
                error.set(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            } finally {
                latch.countDown();
            }
        };
        try {
            clientThread.getClass().getMethod("invoke", Runnable.class).invoke(clientThread, runOnClient);
            if (!latch.await(3, TimeUnit.SECONDS)) {
                return "Timeout";
            }
            return error.get();
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : "Invoke failed";
        }
    }

    /** Must be called on client thread. Returns null on success. */
    private String doInvokeWorldObjectAction(int objectId, int worldX, int worldY, int plane, String type, int actionIndex) throws Exception {
        Method getView = client.getClass().getMethod("getTopLevelWorldView");
        Object view = getView.invoke(client);
        if (view == null) return "No world view";
        int baseX = (Integer) view.getClass().getMethod("getBaseX").invoke(view);
        int baseY = (Integer) view.getClass().getMethod("getBaseY").invoke(view);
        Object planeObj = view.getClass().getMethod("getPlane").invoke(view);
        int viewPlane = planeObj != null ? ((Number) planeObj).intValue() : 0;
        int sizeX = 104;
        int sizeY = 104;
        try {
            Method getSizeX = view.getClass().getMethod("getSizeX");
            Method getSizeY = view.getClass().getMethod("getSizeY");
            sizeX = (Integer) getSizeX.invoke(view);
            sizeY = (Integer) getSizeY.invoke(view);
        } catch (NoSuchMethodException ignored) { }
        int sceneX = worldX - baseX;
        int sceneY = worldY - baseY;
        if (sceneX < 0 || sceneX >= sizeX || sceneY < 0 || sceneY >= sizeY) {
            return "Object not in current view";
        }
        if (viewPlane != plane) return "Wrong plane";
        final int LOCAL_COORD_BITS = 7;
        int localX = (sceneX << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
        int localY = (sceneY << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
        String menuActionName;
        if ("gameObject".equals(type)) {
            String[] names = { "GAME_OBJECT_FIRST_OPTION", "GAME_OBJECT_SECOND_OPTION", "GAME_OBJECT_THIRD_OPTION", "GAME_OBJECT_FOURTH_OPTION", "GAME_OBJECT_FIFTH_OPTION" };
            menuActionName = actionIndex < names.length ? names[actionIndex] : names[0];
        } else {
            String[] names = { "WORLD_ENTITY_FIRST_OPTION", "WORLD_ENTITY_SECOND_OPTION", "WORLD_ENTITY_THIRD_OPTION", "WORLD_ENTITY_FOURTH_OPTION", "WORLD_ENTITY_FIFTH_OPTION" };
            menuActionName = actionIndex < names.length ? names[actionIndex] : names[0];
        }
        // Load MenuAction from the client's classloader so it matches the Client interface (runelite_src/runelite-api/.../MenuAction.java)
        ClassLoader clientLoader = client.getClass().getClassLoader();
        Class<?> menuActionClass = clientLoader.loadClass("net.runelite.api.MenuAction");
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Object menuActionEnum = Enum.valueOf((Class<Enum>) menuActionClass, menuActionName);
        Method getDef = findMethod(client.getClass(), "getObjectDefinition", "getObjectComposition");
        if (getDef == null) return "No object definition method";
        Object comp = getDef.invoke(client, objectId);
        if (comp == null) return "Unknown object id";
        Object effective = comp;
        try {
            Method getImpostorIds = comp.getClass().getMethod("getImpostorIds");
            Object ids = getImpostorIds.invoke(comp);
            if (ids != null && ids.getClass().isArray() && Array.getLength(ids) > 0) {
                Method getImpostor = comp.getClass().getMethod("getImpostor");
                Object imp = getImpostor.invoke(comp);
                if (imp != null) effective = imp;
            }
        } catch (NoSuchMethodException ignored) { }
        Method getActions = effective.getClass().getMethod("getActions");
        Object actionsObj = getActions.invoke(effective);
        if (actionsObj == null || !actionsObj.getClass().isArray()) return "No actions";
        int len = Array.getLength(actionsObj);
        if (actionIndex >= len) return "Invalid action index";
        Object a = Array.get(actionsObj, actionIndex);
        String option = (a != null && !String.valueOf(a).trim().isEmpty()) ? String.valueOf(a).trim() : "Unknown";
        String target = resolveObjectName(client, objectId);
        if (target == null) target = "";
        Method menuAction = client.getClass().getMethod("menuAction", int.class, int.class, menuActionClass, int.class, int.class, String.class, String.class);
        menuAction.invoke(client, localX, localY, menuActionEnum, objectId, -1, option, target);
        return null;
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else if (c < 32) out.append("\\u").append(String.format("%04x", (int) c));
            else if (c == '\u2028') out.append("\\u2028");
            else if (c == '\u2029') out.append("\\u2029");
            else out.append(c);
        }
        return out.toString();
    }

    /** JSON-safe number: NaN/Infinity are not valid JSON, use null. */
    private static String jsonNumber(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return "null";
        }
        return String.valueOf(value);
    }
}
