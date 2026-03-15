package middleman.agent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serializes game state from the RuneLite Client (via reflection) to JSON.
 * All RuneLite types are accessed by name; no compile-time dependency.
 */
final class StateSerializer {

    /** Region ID to display name (from player world position). Region ID = ((x>>6)<<8)|(y>>6). */
    private static final Map<Integer, String> REGION_NAMES = new HashMap<>();
    static {
        REGION_NAMES.put(12850, "Lumbridge");
        REGION_NAMES.put(13105, "Al Kharid"); REGION_NAMES.put(13106, "Al Kharid");
        REGION_NAMES.put(12596, "Varrock"); REGION_NAMES.put(12597, "Varrock");
        REGION_NAMES.put(12852, "Varrock"); REGION_NAMES.put(12853, "Varrock"); REGION_NAMES.put(12854, "Varrock");
        REGION_NAMES.put(13108, "Varrock"); REGION_NAMES.put(13109, "Varrock"); REGION_NAMES.put(13110, "Varrock");
        REGION_NAMES.put(11828, "Falador"); REGION_NAMES.put(11572, "Falador"); REGION_NAMES.put(11827, "Falador"); REGION_NAMES.put(12084, "Falador");
        REGION_NAMES.put(12342, "Edgeville");
        REGION_NAMES.put(12338, "Draynor"); REGION_NAMES.put(12339, "Draynor");
        REGION_NAMES.put(12341, "Barbarian Village");
        REGION_NAMES.put(12081, "Port Sarim"); REGION_NAMES.put(12082, "Port Sarim");
        REGION_NAMES.put(11826, "Rimmington"); REGION_NAMES.put(11570, "Rimmington");
        REGION_NAMES.put(11574, "Taverley"); REGION_NAMES.put(11573, "Taverley");
        REGION_NAMES.put(11319, "Burthorpe"); REGION_NAMES.put(11575, "Burthorpe");
        REGION_NAMES.put(11317, "Catherby"); REGION_NAMES.put(11318, "Catherby"); REGION_NAMES.put(11061, "Catherby");
        REGION_NAMES.put(10806, "Seers' Village");
        REGION_NAMES.put(10297, "Rellekka"); REGION_NAMES.put(10553, "Rellekka");
        REGION_NAMES.put(9779, "Ardougne"); REGION_NAMES.put(9780, "Ardougne"); REGION_NAMES.put(10035, "Ardougne"); REGION_NAMES.put(10036, "Ardougne");
        REGION_NAMES.put(10291, "Ardougne"); REGION_NAMES.put(10292, "Ardougne"); REGION_NAMES.put(10547, "Ardougne"); REGION_NAMES.put(10548, "Ardougne");
        REGION_NAMES.put(11057, "Brimhaven"); REGION_NAMES.put(11058, "Brimhaven");
        REGION_NAMES.put(13878, "Canifis");
        REGION_NAMES.put(9525, "Tree Gnome Stronghold"); REGION_NAMES.put(9526, "Tree Gnome Stronghold"); REGION_NAMES.put(9782, "Tree Gnome Stronghold"); REGION_NAMES.put(9781, "Tree Gnome Stronghold");
        REGION_NAMES.put(10033, "Tree Gnome Village");
        REGION_NAMES.put(10288, "Yanille"); REGION_NAMES.put(10032, "Yanille");
        REGION_NAMES.put(9265, "Lletya"); REGION_NAMES.put(11103, "Lletya");
        REGION_NAMES.put(8253, "Lunar Isle"); REGION_NAMES.put(8252, "Lunar Isle"); REGION_NAMES.put(8509, "Lunar Isle"); REGION_NAMES.put(8508, "Lunar Isle");
        REGION_NAMES.put(8499, "Prifddinas"); REGION_NAMES.put(8500, "Prifddinas"); REGION_NAMES.put(8755, "Prifddinas"); REGION_NAMES.put(8756, "Prifddinas");
        REGION_NAMES.put(9011, "Prifddinas"); REGION_NAMES.put(9012, "Prifddinas"); REGION_NAMES.put(9013, "Prifddinas");
        REGION_NAMES.put(12894, "Prifddinas"); REGION_NAMES.put(12895, "Prifddinas"); REGION_NAMES.put(13150, "Prifddinas"); REGION_NAMES.put(13151, "Prifddinas");
        REGION_NAMES.put(6458, "Arceuus"); REGION_NAMES.put(6459, "Arceuus"); REGION_NAMES.put(6460, "Arceuus"); REGION_NAMES.put(6714, "Arceuus"); REGION_NAMES.put(6715, "Arceuus");
        REGION_NAMES.put(6710, "Hosidius"); REGION_NAMES.put(6711, "Hosidius"); REGION_NAMES.put(6712, "Hosidius"); REGION_NAMES.put(6455, "Hosidius"); REGION_NAMES.put(6456, "Hosidius");
        REGION_NAMES.put(6969, "Port Piscarilius"); REGION_NAMES.put(6971, "Port Piscarilius"); REGION_NAMES.put(7227, "Port Piscarilius"); REGION_NAMES.put(6970, "Port Piscarilius"); REGION_NAMES.put(7225, "Port Piscarilius"); REGION_NAMES.put(7226, "Port Piscarilius");
        REGION_NAMES.put(5692, "Lovakengj"); REGION_NAMES.put(5691, "Lovakengj"); REGION_NAMES.put(5947, "Lovakengj"); REGION_NAMES.put(6203, "Lovakengj"); REGION_NAMES.put(6202, "Lovakengj"); REGION_NAMES.put(5690, "Lovakengj"); REGION_NAMES.put(5946, "Lovakengj");
        REGION_NAMES.put(5944, "Shayzien"); REGION_NAMES.put(5943, "Shayzien"); REGION_NAMES.put(6200, "Shayzien"); REGION_NAMES.put(6199, "Shayzien"); REGION_NAMES.put(5686, "Shayzien"); REGION_NAMES.put(5687, "Shayzien"); REGION_NAMES.put(5688, "Shayzien"); REGION_NAMES.put(5689, "Shayzien"); REGION_NAMES.put(5945, "Shayzien");
        REGION_NAMES.put(6462, "Wintertodt");
        REGION_NAMES.put(9023, "Vorkath");
        REGION_NAMES.put(9007, "Zulrah");
        REGION_NAMES.put(14679, "Motherlode Mine"); REGION_NAMES.put(14680, "Motherlode Mine"); REGION_NAMES.put(14681, "Motherlode Mine"); REGION_NAMES.put(14935, "Motherlode Mine"); REGION_NAMES.put(14936, "Motherlode Mine"); REGION_NAMES.put(14937, "Motherlode Mine");
        REGION_NAMES.put(11347, "General Graardor"); REGION_NAMES.put(11602, "Commander Zilyana"); REGION_NAMES.put(11603, "K'ril Tsutsaroth"); REGION_NAMES.put(11346, "Kree'arra");
        REGION_NAMES.put(11588, "Dagannoth Kings"); REGION_NAMES.put(11589, "Dagannoth Kings");
        REGION_NAMES.put(11842, "Corporeal Beast"); REGION_NAMES.put(11844, "Corporeal Beast");
        REGION_NAMES.put(12843, "Menaphos");
        REGION_NAMES.put(13874, "Burgh de Rott"); REGION_NAMES.put(13873, "Burgh de Rott"); REGION_NAMES.put(14130, "Burgh de Rott"); REGION_NAMES.put(14129, "Burgh de Rott");
        REGION_NAMES.put(14646, "Port Phasmatys");
        REGION_NAMES.put(14388, "Darkmeyer"); REGION_NAMES.put(14644, "Darkmeyer");
        REGION_NAMES.put(12076, "Tempoross");
        REGION_NAMES.put(12700, "Ferox Enclave");
        REGION_NAMES.put(12590, "Bandit Camp");
        REGION_NAMES.put(10039, "Barbarian Outpost");
        REGION_NAMES.put(12598, "Grand Exchange"); REGION_NAMES.put(13104, "Grand Exchange");
        REGION_NAMES.put(12851, "Lumbridge Swamp");
        REGION_NAMES.put(12340, "Draynor Manor");
        REGION_NAMES.put(12083, "Falador Farm");
        REGION_NAMES.put(11571, "Crafting Guild");
        REGION_NAMES.put(13365, "Digsite");
        REGION_NAMES.put(13613, "Nardah");
        REGION_NAMES.put(13358, "Pollnivneach");
        REGION_NAMES.put(10545, "Port Khazard");
        REGION_NAMES.put(10044, "Miscellania");
        REGION_NAMES.put(11310, "Shilo Village");
        REGION_NAMES.put(11056, "Tai Bwo Wannai"); REGION_NAMES.put(11055, "Tai Bwo Wannai");
        REGION_NAMES.put(9035, "Chaos Temple");
        REGION_NAMES.put(12619, "Drill Sergeant's Camp");
        REGION_NAMES.put(13107, "Al Kharid Mine");
        REGION_NAMES.put(12105, "Agility Pyramid"); REGION_NAMES.put(13356, "Agility Pyramid");
        REGION_NAMES.put(11562, "Crash Island");
        REGION_NAMES.put(11314, "Crandor"); REGION_NAMES.put(11315, "Crandor");
        REGION_NAMES.put(11825, "Asgarnia"); REGION_NAMES.put(11829, "Asgarnia"); REGION_NAMES.put(11830, "Asgarnia"); REGION_NAMES.put(12085, "Asgarnia"); REGION_NAMES.put(12086, "Asgarnia");
        REGION_NAMES.put(9773, "Feldip Hills"); REGION_NAMES.put(9774, "Feldip Hills"); REGION_NAMES.put(10029, "Feldip Hills"); REGION_NAMES.put(10030, "Feldip Hills");
        REGION_NAMES.put(10285, "Feldip Hills"); REGION_NAMES.put(10286, "Feldip Hills"); REGION_NAMES.put(10287, "Feldip Hills"); REGION_NAMES.put(10542, "Feldip Hills"); REGION_NAMES.put(10543, "Feldip Hills");
        REGION_NAMES.put(4922, "Farming Guild");
        REGION_NAMES.put(10569, "Fisher Realm");
        REGION_NAMES.put(12621, "Clan Wars"); REGION_NAMES.put(12622, "Clan Wars"); REGION_NAMES.put(12623, "Clan Wars");
        REGION_NAMES.put(14131, "Barrows"); REGION_NAMES.put(14231, "Barrows");
        REGION_NAMES.put(7508, "Barbarian Assault"); REGION_NAMES.put(7509, "Barbarian Assault"); REGION_NAMES.put(10322, "Barbarian Assault");
        REGION_NAMES.put(9520, "Castle Wars"); REGION_NAMES.put(9620, "Castle Wars");
        REGION_NAMES.put(9033, "Nightmare Zone");
        REGION_NAMES.put(10536, "Pest Control");
        REGION_NAMES.put(12889, "Chambers of Xeric"); REGION_NAMES.put(13136, "Chambers of Xeric"); REGION_NAMES.put(13137, "Chambers of Xeric");
        REGION_NAMES.put(12611, "Theatre of Blood"); REGION_NAMES.put(12612, "Theatre of Blood"); REGION_NAMES.put(12613, "Theatre of Blood");
        REGION_NAMES.put(9043, "The Inferno");
        REGION_NAMES.put(12127, "The Gauntlet"); REGION_NAMES.put(7512, "The Gauntlet");
        REGION_NAMES.put(14484, "Guardians of the Rift");
        REGION_NAMES.put(13491, "Giants' Foundry");
        REGION_NAMES.put(7222, "Tithe Farm");
        REGION_NAMES.put(7757, "Blast Furnace");
        REGION_NAMES.put(12954, "Varrock Sewers"); REGION_NAMES.put(13210, "Varrock Sewers");
        REGION_NAMES.put(12693, "Lumbridge Swamp Caves"); REGION_NAMES.put(12949, "Lumbridge Swamp Caves");
        REGION_NAMES.put(12441, "Edgeville Dungeon"); REGION_NAMES.put(12442, "Edgeville Dungeon"); REGION_NAMES.put(12443, "Edgeville Dungeon");
        REGION_NAMES.put(14679, "Motherlode Mine"); REGION_NAMES.put(14935, "Motherlode Mine"); REGION_NAMES.put(15191, "Motherlode Mine");
    }

    /** Max varp index to serialize (getVarps() array may be large). */
    private static final int VARPS_LIMIT = 2048;

    /** Curated varbit IDs for minigames/combat/effects (Barrows, doors, puzzle, poison, etc.). */
    private static final int[] CURATED_VARBIT_IDS = {
        457, 458, 459, 460, 461, 462, 463, 464,  /* Barrows: brothers killed, monster count */
        469, 470, 471, 472, 473, 474, 475, 476, 477, 478, 479, 480, 481, 482, 483, 484,  /* Barrows doors */
        1394,  /* BARROWS_CHEST_OPEN */
        4734, 4736, 4737, 4738, 4739, 4740, 4742, 4743,  /* Barrows old entrance, chambers, puzzle, ladder */
        6251,  /* POISON_TYPE */
    };

    /** Interface group IDs we care about for containers (Barrows reward, bank). */
    private static final int INTERFACE_BARROWS_REWARD = 155;
    private static final int INTERFACE_BANK_MAIN = 12;

    /** Inventory ID for Barrows reward chest (same as TRAIL_REWARDINV). */
    private static final int INVENTORY_ID_BARROWS_REWARD = 141;
    private static final int INVENTORY_ID_BANK = 95;

    /** Max recent game messages to include. */
    private static final int GAME_MESSAGES_LIMIT = 20;

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
        appendMapInfo(sb);
        appendPlayerStats(sb);
        appendVarps(sb);
        appendVarbits(sb);
        appendOpenWidgets(sb);
        appendSkills(sb);
        appendContainers(sb);
        appendGameMessages(sb);
        sb.append(",\"localPlayer\":");
        appendLocalPlayer(sb);
        sb.append(",\"players\":");
        appendPlayers(sb);
        sb.append(",\"npcs\":");
        appendNpcs(sb);
        sb.append(",\"worldObjects\":");
        appendWorldObjects(sb);
        sb.append(",\"groundItems\":");
        appendGroundItems(sb);
        sb.append(",\"worldView\":");
        appendWorldView(sb);
        sb.append(",\"equipment\":");
        appendEquipment(sb);
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

    String serializeGroundItems() {
        StringBuilder sb = new StringBuilder();
        appendGroundItems(sb);
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

    /** Appends ,"regionId":N,"mapName":"..." from local player's world position. */
    private void appendMapInfo(StringBuilder sb) {
        try {
            Method getPlayer = client.getClass().getMethod("getLocalPlayer");
            Object player = getPlayer.invoke(client);
            if (player == null) {
                sb.append(",\"regionId\":null,\"mapName\":null");
                return;
            }
            Method getLoc = player.getClass().getMethod("getWorldLocation");
            Object worldLoc = getLoc.invoke(player);
            if (worldLoc == null) {
                sb.append(",\"regionId\":null,\"mapName\":null");
                return;
            }
            int x = ((Number) worldLoc.getClass().getMethod("getX").invoke(worldLoc)).intValue();
            int y = ((Number) worldLoc.getClass().getMethod("getY").invoke(worldLoc)).intValue();
            int regionId = ((x >> 6) << 8) | (y >> 6);
            String mapName = REGION_NAMES.get(regionId);
            sb.append(",\"regionId\":").append(regionId).append(",\"mapName\":");
            if (mapName != null) {
                sb.append("\"").append(escape(mapName)).append("\"");
            } else {
                sb.append("null");
            }
        } catch (Exception e) {
            sb.append(",\"regionId\":null,\"mapName\":null");
        }
    }

    /** Appends ,"playerStats":{"prayerPoints":N,"maxPrayer":N,"hitpoints":N,"maxHitpoints":N,"runEnergy":N}. */
    private void appendPlayerStats(StringBuilder sb) {
        try {
            ClassLoader loader = client.getClass().getClassLoader();
            Class<?> skillClass = Class.forName("net.runelite.api.Skill", false, loader);
            Object hitpoints = skillClass.getField("HITPOINTS").get(null);
            Object prayer = skillClass.getField("PRAYER").get(null);
            Method getBoosted = client.getClass().getMethod("getBoostedSkillLevel", skillClass);
            Method getReal = client.getClass().getMethod("getRealSkillLevel", skillClass);
            Method getEnergy = client.getClass().getMethod("getEnergy");
            int hp = ((Number) getBoosted.invoke(client, hitpoints)).intValue();
            int maxHp = ((Number) getReal.invoke(client, hitpoints)).intValue();
            int pray = ((Number) getBoosted.invoke(client, prayer)).intValue();
            int maxPray = ((Number) getReal.invoke(client, prayer)).intValue();
            int energyRaw = ((Number) getEnergy.invoke(client)).intValue();
            int runEnergy = (energyRaw >= 0 && energyRaw <= 10000) ? (energyRaw / 100) : energyRaw;
            sb.append(",\"playerStats\":{");
            sb.append("\"hitpoints\":").append(hp).append(",\"maxHitpoints\":").append(maxHp);
            sb.append(",\"prayerPoints\":").append(pray).append(",\"maxPrayer\":").append(maxPray);
            sb.append(",\"runEnergy\":").append(runEnergy).append("}");
        } catch (Exception e) {
            sb.append(",\"playerStats\":null");
        }
    }

    /** Appends ,"varps":[ ... ] from Client#getVarps() (capped at VARPS_LIMIT). */
    private void appendVarps(StringBuilder sb) {
        try {
            Method getVarps = client.getClass().getMethod("getVarps");
            Object arr = getVarps.invoke(client);
            if (arr == null || !arr.getClass().isArray()) {
                sb.append(",\"varps\":null");
                return;
            }
            int len = Math.min(Array.getLength(arr), VARPS_LIMIT);
            sb.append(",\"varps\":[");
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                Object v = Array.get(arr, i);
                sb.append(v instanceof Number ? ((Number) v).intValue() : 0);
            }
            sb.append("]");
        } catch (Exception e) {
            sb.append(",\"varps\":null");
        }
    }

    /** Appends ,"varbits":{ "id": value, ... } for curated varbit IDs. */
    private void appendVarbits(StringBuilder sb) {
        try {
            Method getVarbitValue = client.getClass().getMethod("getVarbitValue", int.class);
            sb.append(",\"varbits\":{");
            boolean first = true;
            for (int id : CURATED_VARBIT_IDS) {
                if (!first) sb.append(",");
                first = false;
                int value = ((Number) getVarbitValue.invoke(client, id)).intValue();
                sb.append("\"").append(id).append("\":").append(value);
            }
            sb.append("}");
        } catch (Exception e) {
            sb.append(",\"varbits\":{}");
        }
    }

    /** Appends ,"openWidgetGroupIds":[ ... ] from getWidgetRoots() (unique group IDs). */
    private void appendOpenWidgets(StringBuilder sb) {
        try {
            Method getRoots = client.getClass().getMethod("getWidgetRoots");
            Object roots = getRoots.invoke(client);
            if (roots == null || !roots.getClass().isArray()) {
                sb.append(",\"openWidgetGroupIds\":[]");
                return;
            }
            Set<Integer> groupIds = new HashSet<>();
            int len = Array.getLength(roots);
            for (int i = 0; i < len; i++) {
                Object w = Array.get(roots, i);
                if (w == null) continue;
                try {
                    Method getId = w.getClass().getMethod("getId");
                    int packed = ((Number) getId.invoke(w)).intValue();
                    int groupId = (packed >> 16) & 0xFFFF;
                    groupIds.add(groupId);
                } catch (Exception ignored) { }
            }
            sb.append(",\"openWidgetGroupIds\":[");
            List<Integer> sorted = new ArrayList<>(groupIds);
            Collections.sort(sorted);
            for (int j = 0; j < sorted.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append(sorted.get(j));
            }
            sb.append("]");
        } catch (Exception e) {
            sb.append(",\"openWidgetGroupIds\":[]");
        }
    }

    /** Appends ,"skills":[ { "name", "boosted", "real" }, ... ] for all Skill enum values. */
    private void appendSkills(StringBuilder sb) {
        try {
            ClassLoader loader = client.getClass().getClassLoader();
            Class<?> skillClass = Class.forName("net.runelite.api.Skill", false, loader);
            Method valuesMethod = skillClass.getMethod("values");
            Object[] skills = (Object[]) valuesMethod.invoke(null);
            Method getBoosted = client.getClass().getMethod("getBoostedSkillLevel", skillClass);
            Method getReal = client.getClass().getMethod("getRealSkillLevel", skillClass);
            Method getNameMethod = skillClass.getMethod("getName");
            sb.append(",\"skills\":[");
            for (int i = 0; i < skills.length; i++) {
                if (i > 0) sb.append(",");
                Object skill = skills[i];
                if (skill == null) continue;
                String name = getNameMethod.invoke(skill) != null ? String.valueOf(getNameMethod.invoke(skill)) : skill.toString();
                int boosted = ((Number) getBoosted.invoke(client, skill)).intValue();
                int real = ((Number) getReal.invoke(client, skill)).intValue();
                sb.append("{\"name\":\"").append(escape(name)).append("\",\"boosted\":").append(boosted).append(",\"real\":").append(real).append("}");
            }
            sb.append("]");
        } catch (Exception e) {
            sb.append(",\"skills\":[]");
        }
    }

    /** Appends ,"containers":{ "barrowsReward": [...], "bank": [...] } when those interfaces are open. */
    private void appendContainers(StringBuilder sb) {
        sb.append(",\"containers\":{");
        try {
            Method getRoots = client.getClass().getMethod("getWidgetRoots");
            Object roots = getRoots.invoke(client);
            Set<Integer> openGroups = new HashSet<>();
            if (roots != null && roots.getClass().isArray()) {
                for (int i = 0; i < Array.getLength(roots); i++) {
                    Object w = Array.get(roots, i);
                    if (w != null) {
                        try {
                            int packed = ((Number) w.getClass().getMethod("getId").invoke(w)).intValue();
                            openGroups.add((packed >> 16) & 0xFFFF);
                        } catch (Exception ignored) { }
                    }
                }
            }
            boolean needComma = false;
            if (openGroups.contains(INTERFACE_BARROWS_REWARD)) {
                String items = buildContainerItemsJson(INVENTORY_ID_BARROWS_REWARD);
                if (items != null) {
                    sb.append("\"barrowsReward\":").append(items);
                    needComma = true;
                }
            }
            if (openGroups.contains(INTERFACE_BANK_MAIN)) {
                String items = buildContainerItemsJson(INVENTORY_ID_BANK);
                if (items != null) {
                    if (needComma) sb.append(",");
                    sb.append("\"bank\":").append(items);
                }
            }
        } catch (Exception ignored) { }
        sb.append("}");
    }

    /** Builds JSON array of items for a container by inventory id; null on error. */
    private String buildContainerItemsJson(int inventoryId) {
        try {
            Method getContainer = client.getClass().getMethod("getItemContainer", int.class);
            Object container = getContainer.invoke(client, inventoryId);
            if (container == null) return "[]";
            Method getItems = container.getClass().getMethod("getItems");
            Object items = getItems.invoke(container);
            if (items == null || !items.getClass().isArray()) return "[]";
            StringBuilder out = new StringBuilder("[");
            int len = Array.getLength(items);
            for (int i = 0; i < len; i++) {
                Object item = Array.get(items, i);
                if (i > 0) out.append(",");
                if (item == null) {
                    out.append("{\"id\":0,\"quantity\":0,\"name\":\"\"}");
                } else {
                    int id = ((Number) item.getClass().getMethod("getId").invoke(item)).intValue();
                    int qty = ((Number) item.getClass().getMethod("getQuantity").invoke(item)).intValue();
                    if (id <= 0) { out.append("{\"id\":0,\"quantity\":0,\"name\":\"\"}"); continue; }
                    String name = resolveItemName(id);
                    out.append("{\"id\":").append(id).append(",\"quantity\":").append(qty).append(",\"name\":\"").append(escape(name)).append("\"}");
                }
            }
            out.append("]");
            return out.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /** Appends ,"gameMessages":[ { "type", "name", "value", "timestamp" }, ... ] (last N messages). */
    private void appendGameMessages(StringBuilder sb) {
        try {
            Method getMessages = client.getClass().getMethod("getMessages");
            Object table = getMessages.invoke(client);
            if (table == null) {
                sb.append(",\"gameMessages\":[]");
                return;
            }
            Method iteratorMethod = table.getClass().getMethod("iterator");
            Iterator<?> it = (Iterator<?>) iteratorMethod.invoke(table);
            List<Object[]> list = new ArrayList<>();
            Method getType = null;
            Method getName = null;
            Method getValue = null;
            Method getTimestamp = null;
            while (it.hasNext()) {
                Object node = it.next();
                if (node == null) continue;
                try {
                    if (getType == null) {
                        getType = node.getClass().getMethod("getType");
                        getName = node.getClass().getMethod("getName");
                        getValue = node.getClass().getMethod("getValue");
                        getTimestamp = node.getClass().getMethod("getTimestamp");
                    }
                    Object type = getType.invoke(node);
                    String typeStr = type != null ? String.valueOf(type) : "";
                    Object nameObj = getName.invoke(node);
                    String name = nameObj != null ? String.valueOf(nameObj) : "";
                    Object valueObj = getValue.invoke(node);
                    String value = valueObj != null ? String.valueOf(valueObj) : "";
                    Object ts = getTimestamp.invoke(node);
                    int timestamp = ts instanceof Number ? ((Number) ts).intValue() : 0;
                    list.add(new Object[]{ typeStr, name, value, timestamp });
                } catch (Exception ignored) { }
            }
            int from = Math.max(0, list.size() - GAME_MESSAGES_LIMIT);
            sb.append(",\"gameMessages\":[");
            for (int i = from; i < list.size(); i++) {
                if (i > from) sb.append(",");
                Object[] row = list.get(i);
                String typeStr = (String) row[0];
                String name = (String) row[1];
                String value = (String) row[2];
                int timestamp = (Integer) row[3];
                sb.append("{\"type\":\"").append(escape(typeStr)).append("\",\"name\":\"").append(escape(name)).append("\",\"value\":\"").append(escape(value)).append("\",\"timestamp\":").append(timestamp).append("}");
            }
            sb.append("]");
        } catch (Exception e) {
            sb.append(",\"gameMessages\":[]");
        }
    }

    /**
     * Returns PNG bytes for the given item ID, or null if unavailable.
     * Must be called from a context that can block; runs sprite creation on client thread.
     */
    byte[] getItemSpritePng(int itemId) {
        return getItemSpritePng(itemId, false);
    }

    byte[] getItemSpritePng(int itemId, boolean noted) {
        if (itemId <= 0) return null;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> result = new AtomicReference<>();
        Runnable runOnClient = () -> {
            try {
                if (!isGameStateReadyForSprites()) { latch.countDown(); return; }
                Object sprite = createItemSprite(itemId, noted);
                if (sprite == null) { latch.countDown(); return; }
                Object img = sprite.getClass().getMethod("toBufferedImage").invoke(sprite);
                if (!(img instanceof BufferedImage)) { latch.countDown(); return; }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write((BufferedImage) img, "PNG", baos);
                result.set(baos.toByteArray());
            } catch (Throwable ignored) { }
            latch.countDown();
        };
        try {
            clientThread.getClass().getMethod("invoke", Runnable.class).invoke(clientThread, runOnClient);
            if (latch.await(3, TimeUnit.SECONDS)) return result.get();
        } catch (Throwable ignored) { }
        return null;
    }

    /** True when game state is at least LOGIN_SCREEN so item cache is available. */
    private boolean isGameStateReadyForSprites() {
        try {
            Method getState = client.getClass().getMethod("getGameState");
            Object state = getState.invoke(client);
            if (state == null) return false;
            ClassLoader loader = client.getClass().getClassLoader();
            Class<?> gameStateClass = Class.forName("net.runelite.api.GameState", false, loader);
            Object loginScreen = gameStateClass.getField("LOGIN_SCREEN").get(null);
            Method ordinal = state.getClass().getMethod("ordinal");
            int current = ((Number) ordinal.invoke(state)).intValue();
            int required = ((Number) ordinal.invoke(loginScreen)).intValue();
            return current >= required;
        } catch (Throwable e) { return false; }
    }

    private Object createItemSprite(int itemId, boolean noted) {
        try {
            Method m = client.getClass().getMethod("createItemSprite", int.class, int.class, int.class, int.class, int.class, boolean.class, int.class);
            int shadow = 3153952; // SpritePixels.DEFAULT_SHADOW_COLOR
            int scale = 512;      // Constants.CLIENT_DEFAULT_ZOOM - required for correct sprite size
            int border = 1;       // match ItemManager: draw border
            int stackable = 0;    // ItemQuantityMode.NEVER
            return m.invoke(client, itemId, 1, border, shadow, stackable, noted, scale);
        } catch (Throwable e) { return null; }
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
            Set<String> seen = new HashSet<>();
            for (Object actor : npcList) {
                if (actor == null) continue;
                int npcId = -1;
                try {
                    Object idObj = actor.getClass().getMethod("getId").invoke(actor);
                    if (idObj != null) npcId = ((Number) idObj).intValue();
                } catch (Exception ignored) { }
                if (npcId <= 0 || !isNpcInteractable(client, npcId)) continue;
                int wx = playerX, wy = playerY, plane = 0;
                try {
                    Method getWorldLocation = actor.getClass().getMethod("getWorldLocation");
                    Object loc = getWorldLocation.invoke(actor);
                    if (loc != null) {
                        wx = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                        wy = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                        try {
                            plane = (Integer) loc.getClass().getMethod("getPlane").invoke(loc);
                        } catch (Exception ignored) { }
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
                String key = npcId + "_" + wx + "_" + wy + "_" + plane;
                if (!seen.add(key)) continue;
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
                // Resolve NPC name and actions from client's composition
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
                        sb.append(",\"actions\":").append(getNpcActionsJson(client, id));
                    }
                } catch (Exception e) {
                    sb.append(",\"actions\":[]");
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Returns JSON array of NPC action strings (e.g. ["Talk-to", "Attack"]). */
    private String getNpcActionsJson(Object client, int npcId) {
        if (npcId <= 0) return "[]";
        try {
            Method getDef = findMethod(client.getClass(), "getNpcDefinition", "getNpcComposition");
            if (getDef == null) return "[]";
            getDef.setAccessible(true);
            Object comp = getDef.invoke(client, npcId);
            if (comp == null) return "[]";
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
            Set<String> seen = new HashSet<>();
            int lenX = Array.getLength(planeRow);
            for (int x = 0; x < lenX; x++) {
                Object col = Array.get(planeRow, x);
                if (col == null || !col.getClass().isArray()) continue;
                int lenY = Array.getLength(col);
                for (int y = 0; y < lenY; y++) {
                    Object tile = Array.get(col, y);
                    if (tile == null) continue;
                    appendTileObjects(tile, client, collected, playerX, playerY, seen);
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

    private void appendGroundItems(StringBuilder sb) {
        if (clientThread != null) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> holder = new AtomicReference<>();
            Runnable runOnClient = () -> {
                try {
                    holder.set(buildGroundItemsJson());
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

    /** Must be called on client thread. Returns JSON array of ground items (dropped items on tiles), sorted by distance. */
    private String buildGroundItemsJson() {
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
            Set<String> seen = new HashSet<>();
            int lenX = Array.getLength(planeRow);
            for (int x = 0; x < lenX; x++) {
                Object col = Array.get(planeRow, x);
                if (col == null || !col.getClass().isArray()) continue;
                int lenY = Array.getLength(col);
                for (int y = 0; y < lenY; y++) {
                    Object tile = Array.get(col, y);
                    if (tile == null) continue;
                    appendTileGroundItems(tile, collected, playerX, playerY, seen);
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

    private void appendTileGroundItems(Object tile, List<Object[]> collected, int playerX, int playerY, Set<String> seen) {
        try {
            Method getGroundItems = tile.getClass().getMethod("getGroundItems");
            Object itemsObj = getGroundItems.invoke(tile);
            if (itemsObj == null) return;
            Method getWorldLocation = tile.getClass().getMethod("getWorldLocation");
            Object loc = getWorldLocation.invoke(tile);
            int wx = 0, wy = 0, plane = 0;
            if (loc != null) {
                wx = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                wy = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                plane = (Integer) loc.getClass().getMethod("getPlane").invoke(loc);
            }
            double dist = Math.hypot(wx - playerX, wy - playerY);
            List<Object> itemList = new ArrayList<>();
            if (itemsObj instanceof List) {
                for (Object o : (List<?>) itemsObj) itemList.add(o);
            } else {
                try {
                    Method size = itemsObj.getClass().getMethod("size");
                    Method get = itemsObj.getClass().getMethod("get", int.class);
                    int n = ((Number) size.invoke(itemsObj)).intValue();
                    for (int i = 0; i < n; i++) itemList.add(get.invoke(itemsObj, i));
                } catch (NoSuchMethodException e) {
                    return;
                }
            }
            for (Object item : itemList) {
                if (item == null) continue;
                try {
                    int id = (Integer) item.getClass().getMethod("getId").invoke(item);
                    int qty = (Integer) item.getClass().getMethod("getQuantity").invoke(item);
                    if (id <= 0) continue;
                    String key = id + "_" + wx + "_" + wy + "_" + plane + "_" + qty;
                    if (!seen.add(key)) continue;
                    String name = resolveItemName(id);
                    int gePrice = resolveItemGePrice(id);
                    int haPrice = resolveItemHaPrice(id);
                    long geTotal = (long) gePrice * qty;
                    long haTotal = (long) haPrice * qty;
                    String json = "{\"id\":" + id + ",\"quantity\":" + qty + ",\"name\":\"" + escape(name) + "\",\"worldX\":" + wx + ",\"worldY\":" + wy + ",\"plane\":" + plane + ",\"gePrice\":" + gePrice + ",\"haPrice\":" + haPrice + ",\"geTotal\":" + geTotal + ",\"haTotal\":" + haTotal + "}";
                    collected.add(new Object[]{ Double.valueOf(dist), json });
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    private void appendTileObjects(Object tile, Object client, List<Object[]> collected, int playerX, int playerY, Set<String> seen) {
        try {
            Method getWall = tile.getClass().getMethod("getWallObject");
            Object wall = getWall.invoke(tile);
            if (wall != null) appendTileObjectJson(collected, wall, "wallObject", client, playerX, playerY, seen);
            Method getGround = tile.getClass().getMethod("getGroundObject");
            Object ground = getGround.invoke(tile);
            if (ground != null) appendTileObjectJson(collected, ground, "groundObject", client, playerX, playerY, seen);
            Method getDeco = tile.getClass().getMethod("getDecorativeObject");
            Object deco = getDeco.invoke(tile);
            if (deco != null) appendTileObjectJson(collected, deco, "decorativeObject", client, playerX, playerY, seen);
            Method getGameObjs = tile.getClass().getMethod("getGameObjects");
            Object gameObjs = getGameObjs.invoke(tile);
            if (gameObjs != null && gameObjs.getClass().isArray()) {
                int n = Array.getLength(gameObjs);
                for (int i = 0; i < n; i++) {
                    Object go = Array.get(gameObjs, i);
                    if (go != null) appendTileObjectJson(collected, go, "gameObject", client, playerX, playerY, seen);
                }
            }
        } catch (Exception ignored) { }
    }

    /** Null/placeholder object IDs to always omit (even if client gives them a name). */
    private static final int[] SKIP_OBJECT_IDS = { 0, 20731, 20737 };

    private void appendTileObjectJson(List<Object[]> collected, Object tileObj, String type, Object client, int playerX, int playerY, Set<String> seen) {
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
            String key = id + "_" + wx + "_" + wy + "_" + plane;
            if (!seen.add(key)) return;
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

    private void appendEquipment(StringBuilder sb) {
        if (clientThread != null) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> holder = new AtomicReference<>();
            Runnable runOnClient = () -> {
                try {
                    holder.set(buildEquipmentJson());
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

    /** Must be called on client thread. Returns JSON array of equipment slots with slot name, id, quantity, name. */
    private String buildEquipmentJson() {
        try {
            ClassLoader loader = client.getClass().getClassLoader();
            Class<?> invIdClass = Class.forName("net.runelite.api.InventoryID", false, loader);
            Object equipmentId = invIdClass.getField("EQUIPMENT").get(null);
            Method getContainer = client.getClass().getMethod("getItemContainer", invIdClass);
            Object container = getContainer.invoke(client, equipmentId);
            if (container == null) return "[]";
            Method getItems = container.getClass().getMethod("getItems");
            Object items = getItems.invoke(container);
            if (items == null || !items.getClass().isArray()) return "[]";
            String[] slotNames = {"HEAD", "CAPE", "AMULET", "WEAPON", "BODY", "SHIELD", "ARMS", "LEGS", "HAIR", "GLOVES", "BOOTS", "JAW", "RING", "AMMO"};
            int len = Math.min(Array.getLength(items), slotNames.length);
            StringBuilder out = new StringBuilder();
            out.append("[");
            for (int i = 0; i < len; i++) {
                if (i > 0) out.append(",");
                Object item = Array.get(items, i);
                String slotName = i < slotNames.length ? slotNames[i] : ("SLOT" + i);
                if (item == null) {
                    out.append("{\"slot\":\"").append(escape(slotName)).append("\",\"slotIndex\":").append(i).append(",\"id\":0,\"quantity\":0,\"name\":\"\"}");
                } else {
                    int id = (Integer) item.getClass().getMethod("getId").invoke(item);
                    int qty = (Integer) item.getClass().getMethod("getQuantity").invoke(item);
                    String name = resolveItemName(id);
                    out.append("{\"slot\":\"").append(escape(slotName)).append("\",\"slotIndex\":").append(i).append(",\"id\":").append(id).append(",\"quantity\":").append(qty).append(",\"name\":\"").append(escape(name)).append("\"}");
                }
            }
            out.append("]");
            return out.toString();
        } catch (Exception e) {
            return "[]";
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
                    boolean noted = resolveItemNoted(id);
                    out.append("{\"slot\":").append(i).append(",\"id\":").append(id).append(",\"quantity\":").append(qty).append(",\"name\":\"").append(escape(name)).append("\",\"noted\":").append(noted).append("}");
                }
            }
            out.append("]");
            return out.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /** True if item is noted (getNote() == 799). Must be called on client thread. */
    private boolean resolveItemNoted(int itemId) {
        if (itemManager == null || itemId <= 0) return false;
        try {
            Method getComp = itemManager.getClass().getMethod("getItemComposition", int.class);
            Object comp = getComp.invoke(itemManager, itemId);
            if (comp == null) return false;
            Object note = comp.getClass().getMethod("getNote").invoke(comp);
            return note != null && ((Number) note).intValue() == 799;
        } catch (Exception e) { return false; }
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

    /** GE (or wiki) price per item via ItemManager (must be called on client thread). Returns 0 if unavailable. */
    private int resolveItemGePrice(int itemId) {
        if (itemManager == null || itemId <= 0) return 0;
        try {
            Method getPrice = itemManager.getClass().getMethod("getItemPrice", int.class);
            Object price = getPrice.invoke(itemManager, itemId);
            return price != null ? ((Number) price).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** High alchemy price per item from ItemComposition (must be called on client thread). Returns 0 if unavailable. */
    private int resolveItemHaPrice(int itemId) {
        if (itemManager == null || itemId <= 0) return 0;
        try {
            Method getComp = itemManager.getClass().getMethod("getItemComposition", int.class);
            Object comp = getComp.invoke(itemManager, itemId);
            if (comp == null) return 0;
            Method getHaPrice = comp.getClass().getMethod("getHaPrice");
            Object price = getHaPrice.invoke(comp);
            return price != null ? ((Number) price).intValue() : 0;
        } catch (Exception e) {
            return 0;
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

    /**
     * Must be called on client thread. Finds a TileObject in the scene matching (objectId, worldX, worldY, plane, type)
     * and returns [centerLocalX, centerLocalY, swTileLocalX, swTileLocalY] for menuAction, or null if not found.
     * Object center is tried first (fixes partial-tile objects); SW tile center is fallback (full/multi-tile).
     */
    private int[] findWorldTileObjectLocalCoords(Object view, int objectId, int worldX, int worldY, int plane, String type) {
        try {
            int baseX = (Integer) view.getClass().getMethod("getBaseX").invoke(view);
            int baseY = (Integer) view.getClass().getMethod("getBaseY").invoke(view);
            final int LOCAL_COORD_BITS = 7;
            Method getScene = view.getClass().getMethod("getScene");
            Object scene = getScene.invoke(view);
            if (scene == null) return null;
            Object planeObj = view.getClass().getMethod("getPlane").invoke(view);
            int currentPlane = planeObj != null ? ((Number) planeObj).intValue() : 0;
            if (currentPlane != plane) return null;
            Method getTiles = scene.getClass().getMethod("getTiles");
            Object tilesObj = getTiles.invoke(scene);
            if (tilesObj == null || !tilesObj.getClass().isArray()) return null;
            int planes = Array.getLength(tilesObj);
            if (currentPlane < 0 || currentPlane >= planes) return null;
            Object planeRow = Array.get(tilesObj, currentPlane);
            if (planeRow == null || !planeRow.getClass().isArray()) return null;
            int lenX = Array.getLength(planeRow);
            for (int x = 0; x < lenX; x++) {
                Object col = Array.get(planeRow, x);
                if (col == null || !col.getClass().isArray()) continue;
                int lenY = Array.getLength(col);
                for (int y = 0; y < lenY; y++) {
                    Object tile = Array.get(col, y);
                    if (tile == null) continue;
                    Object tileObj = null;
                    if ("wallObject".equals(type)) {
                        Method m = tile.getClass().getMethod("getWallObject");
                        tileObj = m.invoke(tile);
                    } else if ("groundObject".equals(type)) {
                        Method m = tile.getClass().getMethod("getGroundObject");
                        tileObj = m.invoke(tile);
                    } else if ("decorativeObject".equals(type)) {
                        Method m = tile.getClass().getMethod("getDecorativeObject");
                        tileObj = m.invoke(tile);
                    } else if ("gameObject".equals(type)) {
                        Method getGameObjs = tile.getClass().getMethod("getGameObjects");
                        Object gameObjs = getGameObjs.invoke(tile);
                        if (gameObjs != null && gameObjs.getClass().isArray()) {
                            int n = Array.getLength(gameObjs);
                            for (int i = 0; i < n; i++) {
                                Object go = Array.get(gameObjs, i);
                                if (go != null) {
                                    Object idObj = go.getClass().getMethod("getId").invoke(go);
                                    int id = idObj != null ? ((Number) idObj).intValue() : -1;
                                    if (id == objectId) {
                                        Object loc = go.getClass().getMethod("getWorldLocation").invoke(go);
                                        if (loc != null) {
                                            int wx = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                                            int wy = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                                            int p = (Integer) loc.getClass().getMethod("getPlane").invoke(loc);
                                            if (wx == worldX && wy == worldY && p == plane) {
                                                tileObj = go;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (tileObj == null) continue;
                    if (!"gameObject".equals(type)) {
                        Object idObj = tileObj.getClass().getMethod("getId").invoke(tileObj);
                        int id = idObj != null ? ((Number) idObj).intValue() : -1;
                        if (id != objectId) continue;
                        Object loc = tileObj.getClass().getMethod("getWorldLocation").invoke(tileObj);
                        if (loc != null) {
                            int wx = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                            int wy = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                            int p = (Integer) loc.getClass().getMethod("getPlane").invoke(loc);
                            if (wx != worldX || wy != worldY || p != plane) continue;
                        }
                    }
                    Method getLocalLoc = tileObj.getClass().getMethod("getLocalLocation");
                    Object localPoint = getLocalLoc.invoke(tileObj);
                    if (localPoint == null) return null;
                    int centerX = (Integer) localPoint.getClass().getMethod("getX").invoke(localPoint);
                    int centerY = (Integer) localPoint.getClass().getMethod("getY").invoke(localPoint);
                    Object wl = tileObj.getClass().getMethod("getWorldLocation").invoke(tileObj);
                    if (wl == null) return new int[] { centerX, centerY, centerX, centerY };
                    int wx = (Integer) wl.getClass().getMethod("getX").invoke(wl);
                    int wy = (Integer) wl.getClass().getMethod("getY").invoke(wl);
                    int sceneSwX = wx - baseX;
                    int sceneSwY = wy - baseY;
                    int swLocalX = (sceneSwX << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
                    int swLocalY = (sceneSwY << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
                    return new int[] { centerX, centerY, swLocalX, swLocalY };
                }
            }
        } catch (Exception ignored) { }
        return null;
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
        // Prefer exact TileObject from scene; try object center first (partial-tile), then SW tile (full/multi-tile).
        int[] fromScene = findWorldTileObjectLocalCoords(view, objectId, worldX, worldY, plane, type);
        int localSwX = localX;
        int localSwY = localY;
        if (fromScene != null) {
            localX = fromScene[0];   // object center
            localY = fromScene[1];
            if (fromScene.length >= 4) {
                localSwX = fromScene[2];
                localSwY = fromScene[3];
            }
        }
        // World objects: use direct mapping (no +1 shift).
        String menuActionName;
        if ("gameObject".equals(type)) {
            String[] names = { "GAME_OBJECT_FIRST_OPTION", "GAME_OBJECT_SECOND_OPTION", "GAME_OBJECT_THIRD_OPTION", "GAME_OBJECT_FOURTH_OPTION", "GAME_OBJECT_FIFTH_OPTION" };
            menuActionName = actionIndex < names.length ? names[actionIndex] : names[0];
        } else {
            String[] names = { "WORLD_ENTITY_FIRST_OPTION", "WORLD_ENTITY_SECOND_OPTION", "WORLD_ENTITY_THIRD_OPTION", "WORLD_ENTITY_FOURTH_OPTION", "WORLD_ENTITY_FIFTH_OPTION" };
            menuActionName = actionIndex < names.length ? names[actionIndex] : names[0];
        }
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
        Method menuActionMethod = client.getClass().getMethod("menuAction", int.class, int.class, menuActionClass, int.class, int.class, String.class, String.class);
        // When we resolved the TileObject: try object center first (partial-tile), then SW tile local, then scene coords.
        int[] pFirst = fromScene != null ? new int[] { localX, localY } : new int[] { sceneX, sceneY };
        int[] pSecond = fromScene != null ? new int[] { localSwX, localSwY } : new int[] { localX, localY };
        int[] pThird = fromScene != null ? new int[] { sceneX, sceneY } : new int[] { sceneX, sceneY };
        String err = invokeViaMenuEntry(client, clientLoader, menuActionClass, pFirst[0], pFirst[1], menuActionEnum, objectId, -1, option, target, menuActionMethod);
        if (err == null) return null;
        err = invokeViaMenuEntry(client, clientLoader, menuActionClass, pSecond[0], pSecond[1], menuActionEnum, objectId, -1, option, target, menuActionMethod);
        if (err == null) return null;
        err = invokeViaMenuEntry(client, clientLoader, menuActionClass, pThird[0], pThird[1], menuActionEnum, objectId, -1, option, target, menuActionMethod);
        if (err == null) return null;
        try {
            menuActionMethod.invoke(client, pFirst[0], pFirst[1], menuActionEnum, objectId, -1, option, target);
            return null;
        } catch (java.lang.reflect.InvocationTargetException e1) {
            try {
                menuActionMethod.invoke(client, pSecond[0], pSecond[1], menuActionEnum, objectId, -1, option, target);
                return null;
            } catch (java.lang.reflect.InvocationTargetException e2) {
                try {
                    menuActionMethod.invoke(client, pThird[0], pThird[1], menuActionEnum, objectId, -1, option, target);
                    return null;
                } catch (java.lang.reflect.InvocationTargetException e3) {
                    Throwable cause = e3.getCause();
                    if (cause != null) throw new RuntimeException(cause);
                    throw e3;
                }
            }
        }
    }

    /** Set a single menu entry then call menuAction so client sees consistent menu state (like a real click). */
    private String invokeViaMenuEntry(Object client, ClassLoader clientLoader, Class<?> menuActionClass,
            int param0, int param1, Object menuActionEnum, int identifier, int itemId, String option, String target,
            Method menuActionMethod) {
        try {
            Method getMenu = client.getClass().getMethod("getMenu");
            Object menu = getMenu.invoke(client);
            if (menu == null) return "No menu";
            Method createEntry = menu.getClass().getMethod("createMenuEntry", int.class);
            Object entry = createEntry.invoke(menu, 0);
            if (entry == null) return "createMenuEntry failed";
            entry.getClass().getMethod("setParam0", int.class).invoke(entry, param0);
            entry.getClass().getMethod("setParam1", int.class).invoke(entry, param1);
            entry.getClass().getMethod("setIdentifier", int.class).invoke(entry, identifier);
            entry.getClass().getMethod("setOption", String.class).invoke(entry, option);
            entry.getClass().getMethod("setTarget", String.class).invoke(entry, target);
            entry.getClass().getMethod("setType", menuActionClass).invoke(entry, menuActionEnum);
            entry.getClass().getMethod("setItemId", int.class).invoke(entry, itemId);
            Class<?> entryArrayClass = Array.newInstance(entry.getClass(), 0).getClass();
            Method setEntries = menu.getClass().getMethod("setMenuEntries", entryArrayClass);
            Object entryArray = Array.newInstance(entry.getClass(), 1);
            Array.set(entryArray, 0, entry);
            setEntries.invoke(menu, entryArray);
            int p0 = (Integer) entry.getClass().getMethod("getParam0").invoke(entry);
            int p1 = (Integer) entry.getClass().getMethod("getParam1").invoke(entry);
            menuActionMethod.invoke(client, p0, p1, menuActionEnum, identifier, itemId, option, target);
            return null;
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }

    /**
     * Invoke an NPC menu action on the client thread.
     * @return null on success, or an error message.
     */
    String invokeNpcAction(int npcId, int worldX, int worldY, int plane, int actionIndex) {
        if (clientThread == null) return "Client not ready";
        if (npcId <= 0) return "Invalid npc id";
        if (actionIndex < 0 || actionIndex > 4) return "Invalid action index";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        Runnable runOnClient = () -> {
            try {
                String err = doInvokeNpcAction(npcId, worldX, worldY, plane, actionIndex);
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

    /**
     * Must be called on client thread. Finds an NPC in the scene matching (npcId, worldX, worldY, plane)
     * and returns [npcIndex, localX, localY] for menuAction. The client uses the NPC's index in its
     * cached array as the menu identifier. Returns null if not found.
     */
    private int[] findNpcInScene(Object view, int npcId, int worldX, int worldY, int plane) {
        try {
            Method getNpcs = client.getClass().getMethod("getNpcs");
            Object npcsObj = getNpcs.invoke(client);
            if (npcsObj == null) return null;
            Method sizeMethod = npcsObj.getClass().getMethod("size");
            int size = ((Number) sizeMethod.invoke(npcsObj)).intValue();
            for (int i = 0; i < size; i++) {
                Object npc = npcsObj.getClass().getMethod("get", int.class).invoke(npcsObj, i);
                if (npc == null) continue;
                Object idObj = npc.getClass().getMethod("getId").invoke(npc);
                int id = idObj != null ? ((Number) idObj).intValue() : -1;
                if (id != npcId) continue;
                Object loc = npc.getClass().getMethod("getWorldLocation").invoke(npc);
                if (loc == null) continue;
                int wx = (Integer) loc.getClass().getMethod("getX").invoke(loc);
                int wy = (Integer) loc.getClass().getMethod("getY").invoke(loc);
                int p = (Integer) loc.getClass().getMethod("getPlane").invoke(loc);
                if (wx != worldX || wy != worldY || p != plane) continue;
                Object indexObj = npc.getClass().getMethod("getIndex").invoke(npc);
                int npcIndex = indexObj != null ? ((Number) indexObj).intValue() : i;
                Object localPoint = npc.getClass().getMethod("getLocalLocation").invoke(npc);
                if (localPoint == null) continue;
                int lx = (Integer) localPoint.getClass().getMethod("getX").invoke(localPoint);
                int ly = (Integer) localPoint.getClass().getMethod("getY").invoke(localPoint);
                return new int[] { npcIndex, lx, ly };
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Must be called on client thread. Returns null on success. */
    private String doInvokeNpcAction(int npcId, int worldX, int worldY, int plane, int actionIndex) throws Exception {
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
            return "NPC not in current view";
        }
        if (viewPlane != plane) return "Wrong plane";
        final int LOCAL_COORD_BITS = 7;
        int localX = (sceneX << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
        int localY = (sceneY << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
        int identifier = npcId;
        int[] fromScene = findNpcInScene(view, npcId, worldX, worldY, plane);
        if (fromScene != null) {
            identifier = fromScene[0];
            localX = fromScene[1];
            localY = fromScene[2];
        }
        // Client menu order can differ from composition getActions() order (e.g. GE: Talk-to works as FIRST,
        // but Exchange/History/Set are shifted — SECOND does nothing, THIRD→Exchange, FOURTH→History, FIFTH→Set).
        // Map composition index 0→FIRST, 1→THIRD, 2→FOURTH, 3→FIFTH so the intended option fires.
        String[] names = { "NPC_FIRST_OPTION", "NPC_SECOND_OPTION", "NPC_THIRD_OPTION", "NPC_FOURTH_OPTION", "NPC_FIFTH_OPTION" };
        int menuActionIndex = actionIndex == 0 ? 0 : Math.min(actionIndex + 1, names.length - 1);
        String menuActionName = names[menuActionIndex];
        ClassLoader clientLoader = client.getClass().getClassLoader();
        Class<?> menuActionClass = clientLoader.loadClass("net.runelite.api.MenuAction");
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Object menuActionEnum = Enum.valueOf((Class<Enum>) menuActionClass, menuActionName);
        Method getDef = findMethod(client.getClass(), "getNpcDefinition", "getNpcComposition");
        if (getDef == null) return "No npc definition method";
        Object comp = getDef.invoke(client, npcId);
        if (comp == null) return "Unknown npc id";
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
        if (actionsObj == null || !actionsObj.getClass().isArray()) return "No actions";
        int len = Array.getLength(actionsObj);
        if (actionIndex >= len) return "Invalid action index";
        Object a = Array.get(actionsObj, actionIndex);
        String option = (a != null && !String.valueOf(a).trim().isEmpty()) ? String.valueOf(a).trim() : "Unknown";
        String target = resolveNpcName(client, npcId);
        if (target == null) target = "";
        Method menuActionMethod = client.getClass().getMethod("menuAction", int.class, int.class, menuActionClass, int.class, int.class, String.class, String.class);
        boolean useLocalFirst = (fromScene != null);
        try {
            if (useLocalFirst)
                menuActionMethod.invoke(client, localX, localY, menuActionEnum, identifier, -1, option, target);
            else
                menuActionMethod.invoke(client, sceneX, sceneY, menuActionEnum, identifier, -1, option, target);
            return null;
        } catch (java.lang.reflect.InvocationTargetException e1) {
            try {
                if (!useLocalFirst)
                    menuActionMethod.invoke(client, localX, localY, menuActionEnum, identifier, -1, option, target);
                else
                    menuActionMethod.invoke(client, sceneX, sceneY, menuActionEnum, identifier, -1, option, target);
                return null;
            } catch (java.lang.reflect.InvocationTargetException e2) {
                String err = invokeViaMenuEntry(client, clientLoader, menuActionClass, sceneX, sceneY, menuActionEnum, identifier, -1, option, target, menuActionMethod);
                if (err == null) return null;
                err = invokeViaMenuEntry(client, clientLoader, menuActionClass, localX, localY, menuActionEnum, identifier, -1, option, target, menuActionMethod);
                if (err == null) return null;
                Throwable cause = e2.getCause();
                if (cause != null) throw new RuntimeException(cause);
                throw e2;
            }
        }
    }

    /**
     * Invoke a ground item menu action (e.g. Take, Take-5) on the client thread.
     * @return null on success, or an error message.
     */
    String invokeGroundItemAction(int itemId, int worldX, int worldY, int plane, int actionIndex) {
        if (clientThread == null) return "Client not ready";
        if (itemId <= 0) return "Invalid item id";
        if (actionIndex < 0 || actionIndex > 4) return "Invalid action index";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        Runnable runOnClient = () -> {
            try {
                String err = doInvokeGroundItemAction(itemId, worldX, worldY, plane, actionIndex);
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

    /**
     * Must be called on client thread. Verifies the tile at (worldX, worldY, plane) has a ground item
     * with the given itemId and returns that tile's local coords [localX, localY] for menuAction, or null.
     */
    private int[] findGroundItemTileLocalCoords(Object view, int itemId, int worldX, int worldY, int plane) {
        try {
            Method getScene = view.getClass().getMethod("getScene");
            Object scene = getScene.invoke(view);
            if (scene == null) return null;
            Object planeObj = view.getClass().getMethod("getPlane").invoke(view);
            int currentPlane = planeObj != null ? ((Number) planeObj).intValue() : 0;
            if (currentPlane != plane) return null;
            int baseX = (Integer) view.getClass().getMethod("getBaseX").invoke(view);
            int baseY = (Integer) view.getClass().getMethod("getBaseY").invoke(view);
            int sceneX = worldX - baseX;
            int sceneY = worldY - baseY;
            Method getTiles = scene.getClass().getMethod("getTiles");
            Object tilesObj = getTiles.invoke(scene);
            if (tilesObj == null || !tilesObj.getClass().isArray()) return null;
            Object planeRow = Array.get(tilesObj, currentPlane);
            if (planeRow == null || !planeRow.getClass().isArray()) return null;
            Object col = Array.get(planeRow, sceneX);
            if (col == null || !col.getClass().isArray()) return null;
            Object tile = Array.get(col, sceneY);
            if (tile == null) return null;
            Method getGroundItems = tile.getClass().getMethod("getGroundItems");
            Object itemsObj = getGroundItems.invoke(tile);
            if (itemsObj == null) return null;
            int n;
            if (itemsObj instanceof List) {
                n = ((List<?>) itemsObj).size();
            } else {
                Method size = itemsObj.getClass().getMethod("size");
                n = ((Number) size.invoke(itemsObj)).intValue();
            }
            for (int i = 0; i < n; i++) {
                Object item = itemsObj instanceof List ? ((List<?>) itemsObj).get(i) : itemsObj.getClass().getMethod("get", int.class).invoke(itemsObj, i);
                if (item == null) continue;
                Object idObj = item.getClass().getMethod("getId").invoke(item);
                int id = idObj != null ? ((Number) idObj).intValue() : -1;
                if (id == itemId) {
                    final int LOCAL_COORD_BITS = 7;
                    int localX = (sceneX << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
                    int localY = (sceneY << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
                    return new int[] { localX, localY };
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String doInvokeGroundItemAction(int itemId, int worldX, int worldY, int plane, int actionIndex) throws Exception {
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
            return "Item not in current view";
        }
        if (viewPlane != plane) return "Wrong plane";
        final int LOCAL_COORD_BITS = 7;
        int localX = (sceneX << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
        int localY = (sceneY << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
        int[] fromScene = findGroundItemTileLocalCoords(view, itemId, worldX, worldY, plane);
        if (fromScene != null) {
            localX = fromScene[0];
            localY = fromScene[1];
        }
        String[] names = { "GROUND_ITEM_FIRST_OPTION", "GROUND_ITEM_SECOND_OPTION", "GROUND_ITEM_THIRD_OPTION", "GROUND_ITEM_FOURTH_OPTION", "GROUND_ITEM_FIFTH_OPTION" };
        String menuActionName = actionIndex < names.length ? names[actionIndex] : names[0];
        ClassLoader clientLoader = client.getClass().getClassLoader();
        Class<?> menuActionClass = clientLoader.loadClass("net.runelite.api.MenuAction");
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Object menuActionEnum = Enum.valueOf((Class<Enum>) menuActionClass, menuActionName);
        String option = actionIndex == 0 ? "Take" : (actionIndex == 1 ? "Take-5" : (actionIndex == 2 ? "Take-10" : "Take-All"));
        String target = resolveItemName(itemId);
        if (target == null) target = "";
        Method menuActionMethod = client.getClass().getMethod("menuAction", int.class, int.class, menuActionClass, int.class, int.class, String.class, String.class);
        boolean useLocalFirst = (fromScene != null);
        try {
            if (useLocalFirst)
                menuActionMethod.invoke(client, localX, localY, menuActionEnum, itemId, -1, option, target);
            else
                menuActionMethod.invoke(client, sceneX, sceneY, menuActionEnum, itemId, -1, option, target);
            return null;
        } catch (java.lang.reflect.InvocationTargetException e1) {
            try {
                if (!useLocalFirst)
                    menuActionMethod.invoke(client, localX, localY, menuActionEnum, itemId, -1, option, target);
                else
                    menuActionMethod.invoke(client, sceneX, sceneY, menuActionEnum, itemId, -1, option, target);
                return null;
            } catch (java.lang.reflect.InvocationTargetException e2) {
                String err = invokeViaMenuEntry(client, clientLoader, menuActionClass, localX, localY, menuActionEnum, itemId, -1, option, target, menuActionMethod);
                if (err == null) return null;
                err = invokeViaMenuEntry(client, clientLoader, menuActionClass, sceneX, sceneY, menuActionEnum, itemId, -1, option, target, menuActionMethod);
                if (err == null) return null;
                Throwable cause = e2.getCause();
                if (cause != null) throw new RuntimeException(cause);
                throw e2;
            }
        }
    }

    private String resolveNpcName(Object client, int npcId) {
        if (npcId <= 0) return "";
        try {
            Method getDef = findMethod(client.getClass(), "getNpcDefinition", "getNpcComposition");
            if (getDef == null) return "";
            getDef.setAccessible(true);
            Object comp = getDef.invoke(client, npcId);
            if (comp == null) return "";
            Object effective = comp;
            try {
                Method transform = comp.getClass().getMethod("transform");
                Object transformed = transform.invoke(comp);
                if (transformed != null) effective = transformed;
            } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException ignored) { }
            return getNameFromComposition(effective);
        } catch (Exception e) {
            return "";
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
