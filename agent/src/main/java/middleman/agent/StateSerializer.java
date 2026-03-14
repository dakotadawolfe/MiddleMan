package middleman.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Iterator;
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
            StringBuilder out = new StringBuilder();
            out.append("[");
            boolean[] first = { true };
            int lenX = Array.getLength(planeRow);
            for (int x = 0; x < lenX; x++) {
                Object col = Array.get(planeRow, x);
                if (col == null || !col.getClass().isArray()) continue;
                int lenY = Array.getLength(col);
                for (int y = 0; y < lenY; y++) {
                    Object tile = Array.get(col, y);
                    if (tile == null) continue;
                    appendTileObjects(tile, client, out, first);
                }
            }
            out.append("]");
            return out.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private void appendTileObjects(Object tile, Object client, StringBuilder out, boolean[] first) {
        try {
            Method getWall = tile.getClass().getMethod("getWallObject");
            Object wall = getWall.invoke(tile);
            if (wall != null) {
                if (!first[0]) out.append(",");
                appendTileObjectJson(out, wall, "wallObject", client);
                first[0] = false;
            }
            Method getGround = tile.getClass().getMethod("getGroundObject");
            Object ground = getGround.invoke(tile);
            if (ground != null) {
                if (!first[0]) out.append(",");
                appendTileObjectJson(out, ground, "groundObject", client);
                first[0] = false;
            }
            Method getDeco = tile.getClass().getMethod("getDecorativeObject");
            Object deco = getDeco.invoke(tile);
            if (deco != null) {
                if (!first[0]) out.append(",");
                appendTileObjectJson(out, deco, "decorativeObject", client);
                first[0] = false;
            }
            Method getGameObjs = tile.getClass().getMethod("getGameObjects");
            Object gameObjs = getGameObjs.invoke(tile);
            if (gameObjs != null && gameObjs.getClass().isArray()) {
                int n = Array.getLength(gameObjs);
                for (int i = 0; i < n; i++) {
                    Object go = Array.get(gameObjs, i);
                    if (go != null) {
                        if (!first[0]) out.append(",");
                        appendTileObjectJson(out, go, "gameObject", client);
                        first[0] = false;
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    private void appendTileObjectJson(StringBuilder out, Object tileObj, String type, Object client) {
        try {
            Method getId = tileObj.getClass().getMethod("getId");
            Object idObj = getId.invoke(tileObj);
            int id = idObj != null ? ((Number) idObj).intValue() : -1;
            String name = resolveObjectName(client, id);
            Method getWorldLoc = tileObj.getClass().getMethod("getWorldLocation");
            Object loc = getWorldLoc.invoke(tileObj);
            int wx = 0, wy = 0, plane = 0;
            if (loc != null) {
                wx = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                wy = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                plane = (Integer) loc.getClass().getMethod("getPlane").invoke(loc);
            }
            out.append("{\"type\":\"").append(escape(type)).append("\",\"id\":").append(id);
            if (name != null && !name.isEmpty()) out.append(",\"name\":\"").append(escape(name)).append("\"");
            out.append(",\"worldX\":").append(wx).append(",\"worldY\":").append(wy).append(",\"plane\":").append(plane);
            out.append("}");
        } catch (Exception e) {
            out.append("{\"type\":\"").append(escape(type)).append("\",\"error\":\"").append(escape(e.getMessage() != null ? e.getMessage() : "")).append("\"}");
        }
    }

    private String resolveObjectName(Object client, int objectId) {
        if (objectId <= 0) return "";
        try {
            Method getDef = client.getClass().getMethod("getObjectDefinition", int.class);
            Object comp = getDef.invoke(client, objectId);
            if (comp == null) return "";
            Method getName = comp.getClass().getMethod("getName");
            Object name = getName.invoke(comp);
            if (name != null) {
                String s = String.valueOf(name).trim();
                if (!s.isEmpty()) return s;
            }
            try {
                Method getImpostor = comp.getClass().getMethod("getImpostor");
                Object impostor = getImpostor.invoke(comp);
                if (impostor != null) {
                    name = getName.invoke(impostor);
                    if (name != null) {
                        String s = String.valueOf(name).trim();
                        if (!s.isEmpty()) return s;
                    }
                }
            } catch (NoSuchMethodException ignored) { }
            return "";
        } catch (Exception e) {
            return "";
        }
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
