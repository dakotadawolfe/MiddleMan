package middleman.agent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native Swing dashboard for game state. Polls the agent HTTP API and displays
 * state; action buttons POST directly so there is no browser delay.
 */
public final class DashboardFrame {

    private static final int REFRESH_MS = 250;
    private static final int RECONNECT_MS = 2000;
    private static final String BASE = "http://127.0.0.1";

    private final int port;
    private JFrame frame;
    private JTextField portField;
    private JLabel statusLabel;
    private JCheckBox autoCheck;
    private JPanel contentPanel;
    private JScrollPane mainScrollPane;
    private javax.swing.Timer refreshTimer;
    private javax.swing.Timer reconnectTimer;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private volatile String npcSearchFilter = "";
    private volatile String worldObjectSearchFilter = "";
    /** Section id -> true if expanded, false if collapsed. If absent, use section default. */
    private final Map<String, Boolean> sectionExpanded = new HashMap<>();

    private DashboardFrame(int port) {
        this.port = port;
    }

    public static void show(int port) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(port));
            return;
        }
        DashboardFrame d = new DashboardFrame(port);
        d.build();
        d.frame.setVisible(true);
        d.refreshOnce();
    }

    private static void setUIFont() {
        Font uiFont = null;
        String[] preferred = { "Segoe UI", "SF Pro Text", "Ubuntu", "Helvetica Neue", "Arial" };
        for (String name : preferred) {
            Font f = new Font(name, Font.PLAIN, 13);
            if (name.equals(f.getFamily())) {
                uiFont = f;
                break;
            }
        }
        if (uiFont == null) uiFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        UIManager.put("Label.font", uiFont);
        UIManager.put("Button.font", uiFont);
        UIManager.put("TextField.font", uiFont);
        UIManager.put("CheckBox.font", uiFont);
        UIManager.put("TitledBorder.font", uiFont.deriveFont(Font.BOLD));
    }

    private String base() {
        try {
            int p = Integer.parseInt(portField.getText().trim());
            return BASE + ":" + p;
        } catch (NumberFormatException e) {
            return BASE + ":" + port;
        }
    }

    private void build() {
        setUIFont();
        frame = new JFrame("MiddleMan – Game State");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(720, 700);
        frame.setLocationRelativeTo(null);

        Color bg = new Color(0x1a, 0x1a, 0x1e);
        Color panelBg = new Color(0x25, 0x25, 0x2b);
        Color fg = new Color(0xe0, 0xe0, 0xe0);
        Color accent = new Color(0x7d, 0xd3, 0xfc);
        Color muted = new Color(0x94, 0xa3, 0xb8);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setBackground(bg);
        portField = new JTextField(String.valueOf(port), 6);
        portField.putClientProperty("focus.id", "port");
        portField.setBackground(new Color(0x2d, 0x2d, 0x33));
        portField.setForeground(fg);
        portField.setCaretColor(fg);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setBackground(new Color(0x0e, 0xa5, 0xe9));
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> refreshOnce());
        autoCheck = new JCheckBox("Auto", true);
        autoCheck.setBackground(bg);
        autoCheck.setForeground(fg);
        autoCheck.addActionListener(e -> toggleAuto());
        statusLabel = new JLabel("Loading…");
        statusLabel.setForeground(muted);
        toolbar.add(new JLabel("Port:"));
        toolbar.add(portField);
        toolbar.add(refreshBtn);
        toolbar.add(autoCheck);
        toolbar.add(statusLabel);
        root.add(toolbar, BorderLayout.NORTH);

        contentPanel = new ScrollablePanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(bg);
        contentPanel.setForeground(fg);
        mainScrollPane = new JScrollPane(contentPanel);
        mainScrollPane.setBorder(null);
        mainScrollPane.getViewport().setBackground(bg);
        mainScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.getVerticalScrollBar().setUnitIncrement(WHEEL_SCROLL_STEP);
        root.add(mainScrollPane, BorderLayout.CENTER);
        contentPanel.addMouseWheelListener(e -> scrollScrollPane(mainScrollPane, e));

        frame.setContentPane(root);
        frame.getContentPane().setBackground(bg);

        refreshTimer = new javax.swing.Timer(REFRESH_MS, e -> refreshOnce());
        reconnectTimer = new javax.swing.Timer(RECONNECT_MS, e -> refreshOnce());
        reconnectTimer.setRepeats(false);
        if (autoCheck.isSelected()) {
            refreshTimer.start();
        }
    }

    private void toggleAuto() {
        if (autoCheck.isSelected()) {
            reconnectTimer.stop();
            refreshTimer.start();
            refreshOnce();
        } else {
            refreshTimer.stop();
            reconnectTimer.stop();
        }
    }

    private void refreshOnce() {
        if (loading.getAndSet(true)) return;
        String url = base() + "/game/state?t=" + System.currentTimeMillis();
        new Thread(() -> {
            try {
                String json = fetch(url, "GET", null);
                SwingUtilities.invokeLater(() -> {
                    loading.set(false);
                    if (json != null) {
                        applyState(json);
                        setStatus(true, "OK");
                        if (autoCheck.isSelected()) {
                            reconnectTimer.stop();
                            if (!refreshTimer.isRunning()) refreshTimer.start();
                        }
                    } else {
                        onConnectionFailed("Connection failed");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    loading.set(false);
                    onConnectionFailed(e.getMessage());
                });
            }
        }).start();
    }

    private void onConnectionFailed(String message) {
        setStatus(false, "Disconnected – reconnecting…");
        if (autoCheck.isSelected()) {
            refreshTimer.stop();
            reconnectTimer.stop();
            reconnectTimer.start();
        }
    }

    private void applyState(String json) {
        String focusId = getCurrentFocusId();
        contentPanel.removeAll();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        try {
            JsonObj root = JsonObj.parse(json);
            addSection("Game state", gameStateSection(root), true);
            addSection("Player", playerSection(root), false);
            addSection("Players", listSection(root.getArray("players"), Arrays.asList("name", "worldX", "worldY", "combatLevel")), true);
            addSection("NPCs", entitySectionWithSearch(root.getArray("npcs"), "npcId", "name", "worldX", "worldY", "plane", "actions", "npc"), true);
            addSection("World objects", worldObjectSectionWithSearch(root.getArray("worldObjects")), true);
            addSection("Ground items", groundItemSection(root.getArray("groundItems")), true);
            setStatus(true, "OK");
        } catch (Exception e) {
            addSection("Error", new JLabel("Parse error: " + e.getMessage()), false);
            setStatus(false, e.getMessage());
        }
        contentPanel.revalidate();
        contentPanel.repaint();
        if (focusId != null) {
            SwingUtilities.invokeLater(() -> restoreFocus(focusId));
        }
    }

    private String getCurrentFocusId() {
        Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        while (c != null) {
            if (c instanceof JComponent) {
                Object id = ((JComponent) c).getClientProperty("focus.id");
                if (id instanceof String) return (String) id;
            }
            c = c.getParent();
        }
        return null;
    }

    private void restoreFocus(String focusId) {
        Component target = findComponentWithFocusId(frame, focusId);
        if (target != null && target.isDisplayable()) target.requestFocusInWindow();
    }

    private Component findComponentWithFocusId(Container root, String focusId) {
        if (root instanceof JComponent) {
            Object id = ((JComponent) root).getClientProperty("focus.id");
            if (focusId.equals(id)) return root;
        }
        for (Component child : root.getComponents()) {
            if (child instanceof Container) {
                Component found = findComponentWithFocusId((Container) child, focusId);
                if (found != null) return found;
            } else if (child instanceof JComponent) {
                Object id = ((JComponent) child).getClientProperty("focus.id");
                if (focusId.equals(id)) return child;
            }
        }
        return null;
    }

    private JPanel gameStateSection(JsonObj root) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setBackground(new Color(0x25, 0x25, 0x2b));
        p.setBorder(new EmptyBorder(2, 6, 2, 6));
        String state = root.getString("gameState");
        String regionId = root.getString("regionId");
        String mapName = root.getString("mapName");
        String regionPart = (regionId != null && !regionId.isEmpty()) ? "Region: " + regionId + (mapName != null && !mapName.isEmpty() ? " (" + mapName + ")" : "") : "";
        String text = (state != null ? state : "—") + (regionPart.isEmpty() ? "" : "  |  " + regionPart);
        JLabel l = new JLabel(text);
        l.setForeground(new Color(0xe0, 0xe0, 0xe0));
        p.add(l);
        return p;
    }

    private JPanel playerSection(JsonObj root) {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBackground(new Color(0x25, 0x25, 0x2b));
        outer.setBorder(new EmptyBorder(4, 6, 4, 6));
        JsonObj player = root.getObject("localPlayer");
        if (player == null) {
            outer.add(new JLabel("Not in game"));
            return outer;
        }
        String name = player.getString("name");
        int wx = player.getInt("worldX", 0), wy = player.getInt("worldY", 0), plane = player.getInt("plane", 0);
        JsonObj stats = root.getObject("playerStats");
        String hp = stats != null ? stats.getInt("hitpoints", 0) + " / " + stats.getInt("maxHitpoints", 0) : "—";
        String pray = stats != null ? stats.getInt("prayerPoints", 0) + " / " + stats.getInt("maxPrayer", 0) : "—";
        int run = stats != null ? stats.getInt("runEnergy", 0) : 0;
        outer.add(labelRow("Name", name != null ? name : "—"));
        outer.add(labelRow("Position", wx + ", " + wy + ", " + plane));
        outer.add(labelRow("Hitpoints", hp));
        outer.add(labelRow("Prayer", pray));
        outer.add(labelRow("Run energy", run + "%"));
        JsonObj camera = root.getObject("camera");
        if (camera != null) {
            String cx = camera.getString("x"), cy = camera.getString("y"), cz = camera.getString("z");
            String cpitch = camera.getString("pitch"), cyaw = camera.getString("yaw");
            String camStr = (cx != null ? cx : "—") + " " + (cy != null ? cy : "") + " " + (cz != null ? cz : "") + "  pitch " + (cpitch != null ? cpitch : "—") + " yaw " + (cyaw != null ? cyaw : "—");
            outer.add(labelRow("Camera", camStr.trim().isEmpty() ? "—" : camStr.trim()));
        }
        addCollapsibleSubSection(outer, "Player:Equipped", "Equipped", equipmentGrid(root.getArray("equipment")), true);
        addCollapsibleSubSection(outer, "Player:Inventory", "Inventory", inventoryGrid(root.getArray("inventory")), true);
        return outer;
    }

    private JPanel labelRow(String key, String value) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setBackground(new Color(0x25, 0x25, 0x2b));
        JLabel k = new JLabel(key + ": ");
        k.setForeground(new Color(0x94, 0xa3, 0xb8));
        JLabel v = new JLabel(value);
        v.setForeground(new Color(0xe0, 0xe0, 0xe0));
        p.add(k);
        p.add(v);
        return p;
    }

    private void addCollapsibleSubSection(JPanel parent, String sectionKey, String title, JComponent content, boolean collapsedByDefault) {
        boolean expanded = sectionExpanded.containsKey(sectionKey) ? sectionExpanded.get(sectionKey) : !collapsedByDefault;
        Color bg = new Color(0x25, 0x25, 0x2b);
        Color titleFg = new Color(0x94, 0xa3, 0xb8);
        parent.add(Box.createVerticalStrut(4));
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        header.setBackground(bg);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel arrow = new JLabel(expanded ? "\u25BC" : "\u25B2");
        arrow.setForeground(titleFg);
        arrow.setFont(arrow.getFont().deriveFont(10f));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(titleFg);
        header.add(arrow);
        header.add(titleLabel);
        content.setVisible(expanded);
        final boolean defaultExpanded = !collapsedByDefault;
        Runnable toggle = () -> {
            boolean nowExpanded = sectionExpanded.getOrDefault(sectionKey, defaultExpanded);
            boolean newExpanded = !nowExpanded;
            sectionExpanded.put(sectionKey, newExpanded);
            content.setVisible(newExpanded);
            arrow.setText(newExpanded ? "\u25BC" : "\u25B2");
            parent.revalidate();
            parent.repaint();
        };
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) { toggle.run(); }
        };
        header.addMouseListener(adapter);
        arrow.addMouseListener(adapter);
        titleLabel.addMouseListener(adapter);
        parent.add(header);
        parent.add(content);
    }

    private JPanel equipmentGrid(JsonArr arr) {
        JPanel grid = new JPanel(new GridLayout(0, 4, 4, 4));
        grid.setBackground(new Color(0x25, 0x25, 0x2b));
        if (arr == null) return grid;
        String[] slots = { "Head", "Cape", "Amulet", "Weapon", "Body", "Shield", "Legs", "Boots", "Gloves", "Ring", "Ammo" };
        for (int i = 0; i < arr.size() && i < 14; i++) {
            JsonObj o = arr.getObject(i);
            String slot = i < slots.length ? slots[i] : ("Slot " + i);
            String name = o != null ? o.getString("name") : null;
            int id = o != null ? o.getInt("id", 0) : 0;
            int qty = o != null ? o.getInt("quantity", 1) : 1;
            grid.add(cardLabel(slot, name != null ? id + " " + name : "—", qty));
        }
        return grid;
    }

    private JPanel inventoryGrid(JsonArr arr) {
        JPanel grid = new JPanel(new GridLayout(0, 4, 4, 4));
        grid.setBackground(new Color(0x25, 0x25, 0x2b));
        if (arr == null) return grid;
        for (int i = 0; i < arr.size(); i++) {
            JsonObj o = arr.getObject(i);
            String name = o != null ? o.getString("name") : null;
            int id = o != null ? o.getInt("id", 0) : 0;
            int qty = o != null ? o.getInt("quantity", 0) : 0;
            grid.add(cardLabel("Slot " + i, name != null ? id + " " + name : "—", qty));
        }
        return grid;
    }

    private JLabel cardLabel(String title, String content, int qty) {
        JLabel l = new JLabel("<html><b>" + title + "</b><br>" + content + (qty > 1 ? " × " + qty : "") + "</html>");
        l.setBorder(new EmptyBorder(4, 6, 4, 6));
        l.setBackground(new Color(0x1e, 0x1e, 0x24));
        l.setOpaque(true);
        l.setForeground(new Color(0xe0, 0xe0, 0xe0));
        return l;
    }

    private JPanel listSection(JsonArr arr, List<String> keys) {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setBackground(new Color(0x25, 0x25, 0x2b));
        Color fg = new Color(0xe0, 0xe0, 0xe0);
        if (arr == null) return p;
        for (int i = 0; i < Math.min(arr.size(), 50); i++) {
            JsonObj o = arr.getObject(i);
            if (o == null) continue;
            StringBuilder line = new StringBuilder();
            for (String k : keys) {
                String v = o.getString(k);
                if (v != null && !v.isEmpty()) line.append(k).append(": ").append(v).append("  ");
            }
            JLabel lbl = new JLabel(line.length() > 0 ? line.toString() : "—");
            lbl.setForeground(fg);
            p.add(lbl);
        }
        return p;
    }

    private static boolean matchesFilter(String filter, String id, String name) {
        if (filter == null || filter.isEmpty()) return true;
        String q = filter.toLowerCase();
        return (id != null && id.toLowerCase().contains(q)) || (name != null && name.toLowerCase().contains(q));
    }

    private JPanel entitySectionWithSearch(JsonArr arr, String idKey, String nameKey, String wxKey, String wyKey, String planeKey, String actionsKey, String actionType) {
        JPanel outer = new JPanel(new BorderLayout(2, 2));
        outer.setBackground(new Color(0x25, 0x25, 0x2b));
        JTextField search = new JTextField(14);
        search.putClientProperty("focus.id", "npcSearch");
        search.setText(npcSearchFilter);
        search.setToolTipText("Filter by ID or name");
        search.setBackground(new Color(0x2d, 0x2d, 0x33));
        search.setForeground(new Color(0xe0, 0xe0, 0xe0));
        search.setCaretColor(new Color(0xe0, 0xe0, 0xe0));
        JPanel cardsPanel = new JPanel();
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setBackground(new Color(0x25, 0x25, 0x2b));
        Runnable updateCards = () -> {
            String q = search.getText();
            if (q != null) q = q.trim();
            npcSearchFilter = q != null ? q : "";
            cardsPanel.removeAll();
            if (arr != null) {
                for (int i = 0; i < Math.min(arr.size(), 80); i++) {
                    JsonObj o = arr.getObject(i);
                    if (o == null) continue;
                    String id = o.getString(idKey);
                    String name = o.getString(nameKey);
                    if (!matchesFilter(npcSearchFilter, id, name)) continue;
                    cardsPanel.add(buildNpcCard(o, idKey, nameKey, wxKey, wyKey, planeKey, actionsKey, actionType));
                }
            }
            cardsPanel.revalidate();
            cardsPanel.repaint();
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateCards.run(); }
            @Override public void removeUpdate(DocumentEvent e) { updateCards.run(); }
            @Override public void changedUpdate(DocumentEvent e) { updateCards.run(); }
        });
        updateCards.run();
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        searchRow.setBackground(new Color(0x25, 0x25, 0x2b));
        JLabel searchLbl = new JLabel("Search:");
        searchLbl.setForeground(new Color(0x94, 0xa3, 0xb8));
        searchRow.add(searchLbl);
        searchRow.add(search);
        outer.add(searchRow, BorderLayout.NORTH);
        outer.add(new JScrollPane(cardsPanel), BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildNpcCard(JsonObj o, String idKey, String nameKey, String wxKey, String wyKey, String planeKey, String actionsKey, String actionType) {
        String id = o.getString(idKey);
        String name = o.getString(nameKey);
        int wx = o.getInt(wxKey, 0), wy = o.getInt(wyKey, 0), plane = o.getInt(planeKey, 0);
        JsonArr actions = o.getArray(actionsKey);
        JsonArr actionSlots = o.getArray("actionSlots");
        JPanel card = new JPanel(new BorderLayout(2, 2));
        card.setBackground(new Color(0x1e, 0x1e, 0x24));
        card.setBorder(BorderFactory.createLineBorder(new Color(0x33, 0x33, 0x33)));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setBackground(new Color(0x1e, 0x1e, 0x24));
        JLabel sum = new JLabel((id != null ? id : "") + (name != null ? " " + name : "") + "  @ " + wx + "," + wy + " " + plane);
        sum.setForeground(new Color(0xe0, 0xe0, 0xe0));
        top.add(sum);
        card.add(top, BorderLayout.NORTH);
        if (actions != null && actions.size() > 0) {
            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
            btnRow.setBackground(new Color(0x1e, 0x1e, 0x24));
            for (int a = 0; a < actions.size(); a++) {
                String action = actions.getString(a);
                if (action == null || action.isEmpty()) continue;
                final int actionIndex = a;
                final int rawActionIndex = getActionSlot(actionSlots, a, a);
                JButton btn = new JButton(action);
                btn.setFocusPainted(false);
                btn.setBackground(new Color(0x0e, 0xa5, 0xe9));
                btn.setForeground(Color.WHITE);
                btn.addActionListener(ev -> postNpcOrGroundAction(actionType, id, wx, wy, plane, actionIndex, rawActionIndex, btn));
                btnRow.add(btn);
            }
            card.add(btnRow, BorderLayout.CENTER);
        }
        return card;
    }

    private void postNpcOrGroundAction(String type, String id, int wx, int wy, int plane, int actionIndex, int rawActionIndex, JButton btn) {
        String path = type.equals("npc") ? "/game/npc/action" : "/game/grounditem/action";
        String body = "id=" + id + "&worldX=" + wx + "&worldY=" + wy + "&plane=" + plane + "&actionIndex=" + actionIndex + "&rawActionIndex=" + rawActionIndex;
        btn.setEnabled(false);
        new Thread(() -> {
            try {
                String resp = fetch(base() + path, "POST", body);
                boolean ok = resp != null && resp.contains("\"ok\":true");
                SwingUtilities.invokeLater(() -> {
                    btn.setEnabled(true);
                    setStatus(ok, ok ? "OK" : "Action failed");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    btn.setEnabled(true);
                    setStatus(false, e.getMessage());
                });
            }
        }).start();
    }

    private JPanel worldObjectSectionWithSearch(JsonArr arr) {
        JPanel outer = new JPanel(new BorderLayout(2, 2));
        outer.setBackground(new Color(0x25, 0x25, 0x2b));
        JTextField search = new JTextField(14);
        search.putClientProperty("focus.id", "worldObjectSearch");
        search.setText(worldObjectSearchFilter);
        search.setToolTipText("Filter by ID or name");
        search.setBackground(new Color(0x2d, 0x2d, 0x33));
        search.setForeground(new Color(0xe0, 0xe0, 0xe0));
        search.setCaretColor(new Color(0xe0, 0xe0, 0xe0));
        JPanel cardsPanel = new JPanel();
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setBackground(new Color(0x25, 0x25, 0x2b));
        Runnable updateCards = () -> {
            String q = search.getText();
            if (q != null) q = q.trim();
            worldObjectSearchFilter = q != null ? q : "";
            cardsPanel.removeAll();
            if (arr != null) {
                for (int i = 0; i < Math.min(arr.size(), 80); i++) {
                    JsonObj o = arr.getObject(i);
                    if (o == null) continue;
                    String id = o.getString("id");
                    String name = o.getString("name");
                    if (!matchesFilter(worldObjectSearchFilter, id, name)) continue;
                    cardsPanel.add(buildWorldObjectCard(o));
                }
            }
            cardsPanel.revalidate();
            cardsPanel.repaint();
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateCards.run(); }
            @Override public void removeUpdate(DocumentEvent e) { updateCards.run(); }
            @Override public void changedUpdate(DocumentEvent e) { updateCards.run(); }
        });
        updateCards.run();
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        searchRow.setBackground(new Color(0x25, 0x25, 0x2b));
        JLabel searchLbl = new JLabel("Search:");
        searchLbl.setForeground(new Color(0x94, 0xa3, 0xb8));
        searchRow.add(searchLbl);
        searchRow.add(search);
        outer.add(searchRow, BorderLayout.NORTH);
        outer.add(new JScrollPane(cardsPanel), BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildWorldObjectCard(JsonObj o) {
        String type = o.getString("type");
        String id = o.getString("id");
        String name = o.getString("name");
        int wx = o.getInt("worldX", 0), wy = o.getInt("worldY", 0), plane = o.getInt("plane", 0);
        JsonArr actions = o.getArray("actions");
        JsonArr actionSlots = o.getArray("actionSlots");
        JPanel card = new JPanel(new BorderLayout(2, 2));
        card.setBackground(new Color(0x1e, 0x1e, 0x24));
        card.setBorder(BorderFactory.createLineBorder(new Color(0x33, 0x33, 0x33)));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setBackground(new Color(0x1e, 0x1e, 0x24));
        JLabel sum = new JLabel((type != null ? type : "") + " " + (id != null ? id : "") + (name != null ? " " + name : "") + "  @ " + wx + "," + wy + " " + plane);
        sum.setForeground(new Color(0xe0, 0xe0, 0xe0));
        top.add(sum);
        card.add(top, BorderLayout.NORTH);
        if (actions != null && actions.size() > 0) {
            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
            btnRow.setBackground(new Color(0x1e, 0x1e, 0x24));
            String typeStr = type != null ? type : "gameObject";
            for (int a = 0; a < actions.size(); a++) {
                String action = actions.getString(a);
                if (action == null || action.isEmpty()) continue;
                final int actionIndex = a;
                final int rawActionIndex = getActionSlot(actionSlots, a, a);
                JButton btn = new JButton(action);
                btn.setFocusPainted(false);
                btn.setBackground(new Color(0x0e, 0xa5, 0xe9));
                btn.setForeground(Color.WHITE);
                btn.addActionListener(ev -> postWorldObjectAction(id, wx, wy, plane, typeStr, actionIndex, rawActionIndex, btn));
                btnRow.add(btn);
            }
            card.add(btnRow, BorderLayout.CENTER);
        }
        return card;
    }

    private void postWorldObjectAction(String id, int wx, int wy, int plane, String type, int actionIndex, int rawActionIndex, JButton btn) {
        String body = "id=" + id + "&worldX=" + wx + "&worldY=" + wy + "&plane=" + plane + "&type=" + type + "&actionIndex=" + actionIndex + "&rawActionIndex=" + rawActionIndex;
        btn.setEnabled(false);
        new Thread(() -> {
            try {
                String resp = fetch(base() + "/game/worldobject/action", "POST", body);
                boolean ok = resp != null && resp.contains("\"ok\":true");
                SwingUtilities.invokeLater(() -> {
                    btn.setEnabled(true);
                    setStatus(ok, ok ? "OK" : "Action failed");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    btn.setEnabled(true);
                    setStatus(false, e.getMessage());
                });
            }
        }).start();
    }

    /** Format price for display (e.g. 1500 -> "1.5k", 1500000 -> "1.5m"). */
    private static String formatPrice(long n) {
        if (n <= 0) return "—";
        if (n >= 1_000_000) return (n / 1_000_000.0) == (long)(n / 1_000_000.0) ? (n / 1_000_000) + "m" : String.format("%.1fm", n / 1_000_000.0).replace(".0m", "m");
        if (n >= 1_000) return (n / 1_000.0) == (long)(n / 1_000.0) ? (n / 1_000) + "k" : String.format("%.1fk", n / 1_000.0).replace(".0k", "k");
        return String.valueOf(n);
    }

    private JPanel groundItemSection(JsonArr arr) {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBackground(new Color(0x25, 0x25, 0x2b));
        if (arr == null) return outer;
        String[] actions = { "Take", "Take-5", "Take-10", "Take-All" };
        for (int i = 0; i < Math.min(arr.size(), 80); i++) {
            JsonObj o = arr.getObject(i);
            if (o == null) continue;
            String id = o.getString("id");
            String name = o.getString("name");
            int qty = o.getInt("quantity", 1);
            int wx = o.getInt("worldX", 0), wy = o.getInt("worldY", 0), plane = o.getInt("plane", 0);
            long geTotal = o.getLong("geTotal", -1);
            if (geTotal < 0) {
                int gePrice = o.getInt("gePrice", 0);
                geTotal = gePrice > 0 && qty > 0 ? (long) gePrice * qty : 0;
            }
            long haTotal = o.getLong("haTotal", -1);
            if (haTotal < 0) {
                int haPrice = o.getInt("haPrice", 0);
                haTotal = haPrice > 0 && qty > 0 ? (long) haPrice * qty : 0;
            }
            JPanel card = new JPanel(new BorderLayout(2, 2));
            card.setBackground(new Color(0x1e, 0x1e, 0x24));
            card.setBorder(BorderFactory.createLineBorder(new Color(0x33, 0x33, 0x33)));
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.setBackground(new Color(0x1e, 0x1e, 0x24));
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            JLabel sum = new JLabel((id != null ? id : "") + (name != null ? " " + name : "") + " × " + qty + "  @ " + wx + "," + wy + " " + plane);
            sum.setForeground(new Color(0xe0, 0xe0, 0xe0));
            top.add(sum);
            if (geTotal > 0 || haTotal > 0) {
                StringBuilder valueLine = new StringBuilder();
                if (geTotal > 0) valueLine.append("GE: ").append(formatPrice(geTotal));
                if (haTotal > 0) {
                    if (valueLine.length() > 0) valueLine.append("   ");
                    valueLine.append("HA: ").append(formatPrice(haTotal));
                }
                JLabel valueLbl = new JLabel(valueLine.toString());
                valueLbl.setForeground(new Color(0x7d, 0xd3, 0xfc));
                valueLbl.setFont(valueLbl.getFont().deriveFont(11f));
                top.add(valueLbl);
            }
            card.add(top, BorderLayout.NORTH);
            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
            btnRow.setBackground(new Color(0x1e, 0x1e, 0x24));
            for (int a = 0; a < actions.length; a++) {
                final int actionIndex = a;
                JButton btn = new JButton(actions[a]);
                btn.setFocusPainted(false);
                btn.setBackground(new Color(0x0e, 0xa5, 0xe9));
                btn.setForeground(Color.WHITE);
                btn.addActionListener(ev -> postNpcOrGroundAction("grounditem", id, wx, wy, plane, actionIndex, actionIndex, btn));
                btnRow.add(btn);
            }
            card.add(btnRow, BorderLayout.CENTER);
            outer.add(card);
        }
        return outer;
    }

    private JPanel simpleSection(JsonObj o, String... keys) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setBackground(new Color(0x25, 0x25, 0x2b));
        p.setBorder(new EmptyBorder(2, 6, 2, 6));
        if (o == null) {
            p.add(new JLabel("—"));
            return p;
        }
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            String v = o.getString(k);
            if (v != null) sb.append(k).append(": ").append(v).append("  ");
        }
        JLabel lbl = new JLabel(sb.length() > 0 ? sb.toString() : "—");
        lbl.setForeground(new Color(0xe0, 0xe0, 0xe0));
        p.add(lbl);
        return p;
    }

    private int getActionSlot(JsonArr actionSlots, int displayIndex, int fallbackRaw) {
        if (actionSlots == null) return fallbackRaw;
        return actionSlots.getInt(displayIndex, fallbackRaw);
    }

    private void addSection(String title, JComponent content, boolean collapsedByDefault) {
        boolean expanded = sectionExpanded.containsKey(title) ? sectionExpanded.get(title) : !collapsedByDefault;
        Color bg = new Color(0x1a, 0x1a, 0x1e);
        Color border = new Color(0x33, 0x33, 0x33);
        Color titleFg = new Color(0x94, 0xa3, 0xb8);
        JPanel wrap = new JPanel(new BorderLayout(0, 0));
        wrap.setBackground(bg);
        wrap.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, border));
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        header.setBackground(bg);
        header.setBorder(new EmptyBorder(1, 4, 1, 4));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel arrow = new JLabel(expanded ? "\u25BC" : "\u25B2");
        arrow.setForeground(titleFg);
        arrow.setFont(arrow.getFont().deriveFont(9f));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(titleFg);
        titleLabel.setFont(titleLabel.getFont().deriveFont(11f));
        header.add(arrow);
        header.add(titleLabel);
        final boolean defaultExpanded = !collapsedByDefault;
        Runnable toggle = () -> {
            boolean nowExpanded = sectionExpanded.getOrDefault(title, defaultExpanded);
            boolean newExpanded = !nowExpanded;
            sectionExpanded.put(title, newExpanded);
            arrow.setText(newExpanded ? "\u25BC" : "\u25B2");
            if (newExpanded) {
                wrap.setMaximumSize(null);
                wrap.setPreferredSize(null);
                wrap.add(content, BorderLayout.CENTER);
            } else {
                wrap.remove(content);
                int h = header.getPreferredSize().height;
                wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
                wrap.setPreferredSize(new Dimension(0, h));
            }
            wrap.revalidate();
            wrap.repaint();
        };
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) { toggle.run(); }
        };
        header.addMouseListener(adapter);
        arrow.addMouseListener(adapter);
        titleLabel.addMouseListener(adapter);
        wrap.add(header, BorderLayout.NORTH);
        if (expanded) {
            wrap.add(content, BorderLayout.CENTER);
        } else {
            int h = header.getPreferredSize().height;
            wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            wrap.setPreferredSize(new Dimension(0, h));
        }
        contentPanel.add(wrap);
        contentPanel.add(Box.createVerticalStrut(0));
        enableWheelScroll(wrap, mainScrollPane);
    }

    private static final int WHEEL_SCROLL_STEP = 28;

    private static void scrollScrollPane(JScrollPane scrollPane, java.awt.event.MouseWheelEvent e) {
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        if (bar != null && bar.isEnabled()) {
            int delta = -e.getUnitsToScroll() * WHEEL_SCROLL_STEP;
            bar.setValue(Math.max(bar.getMinimum(), Math.min(bar.getMaximum() - bar.getVisibleAmount(), bar.getValue() + delta)));
            e.consume();
        }
    }

    /** Recursively add mouse wheel listener so scrolling works over any content, not just the scroll bar. */
    private void enableWheelScroll(Container c, JScrollPane scrollPane) {
        c.addMouseWheelListener(e -> scrollScrollPane(scrollPane, e));
        for (Component ch : c.getComponents()) {
            if (ch instanceof JScrollPane) {
                JScrollPane inner = (JScrollPane) ch;
                inner.getVerticalScrollBar().setUnitIncrement(WHEEL_SCROLL_STEP);
                Component view = inner.getViewport().getView();
                if (view instanceof Container) enableWheelScroll((Container) view, inner);
            } else if (ch instanceof Container) {
                enableWheelScroll((Container) ch, scrollPane);
            }
        }
    }

    private void setStatus(boolean ok, String msg) {
        statusLabel.setText(msg != null ? msg : (ok ? "OK" : "Error"));
        statusLabel.setForeground(ok ? new Color(0x4a, 0xde, 0x80) : new Color(0xf8, 0x71, 0x71));
    }

    private static String fetch(String urlStr, String method, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(3000);
        c.setReadTimeout(5000);
        if (body != null && method.equals("POST")) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        java.io.InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String resp = in != null ? new String(in.readAllBytes(), StandardCharsets.UTF_8) : "";
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + (resp.isEmpty() ? "" : ": " + resp));
        return resp;
    }

    /** Panel that does not stretch in height when inside a JScrollPane, so no huge blank area below content. */
    private static final class ScrollablePanel extends JPanel implements javax.swing.Scrollable {
        @Override
        public boolean getScrollableTracksViewportHeight() { return false; }
        @Override
        public boolean getScrollableTracksViewportWidth() { return true; }
        @Override
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 28; }
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return visibleRect.height; }
    }

    // --- Minimal JSON parsing (no external deps) ---
    private static class JsonObj {
        final Map<String, Object> map = new HashMap<>();

        static JsonObj parse(String json) {
            return (JsonObj) new JsonParser(json).parseValue();
        }

        String getString(String key) {
            Object v = map.get(key);
            if (v == null) return null;
            return v.toString();
        }

        int getInt(String key, int def) {
            Object v = map.get(key);
            if (v == null) return def;
            if (v instanceof Number) return ((Number) v).intValue();
            try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
        }

        long getLong(String key, long def) {
            Object v = map.get(key);
            if (v == null) return def;
            if (v instanceof Number) return ((Number) v).longValue();
            try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return def; }
        }

        JsonObj getObject(String key) {
            Object v = map.get(key);
            return v instanceof JsonObj ? (JsonObj) v : null;
        }

        JsonArr getArray(String key) {
            Object v = map.get(key);
            return v instanceof JsonArr ? (JsonArr) v : null;
        }
    }

    private static class JsonArr {
        final List<Object> list = new ArrayList<>();

        int size() { return list.size(); }

        Object get(int i) {
            return i >= 0 && i < list.size() ? list.get(i) : null;
        }

        JsonObj getObject(int i) {
            Object v = get(i);
            return v instanceof JsonObj ? (JsonObj) v : null;
        }

        String getString(int i) {
            Object v = get(i);
            return v != null ? v.toString() : null;
        }

        int getInt(int i, int def) {
            Object v = get(i);
            if (v == null) return def;
            if (v instanceof Number) return ((Number) v).intValue();
            try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
        }
    }

    private static class JsonParser {
        final String s;
        int i = 0;

        JsonParser(String s) { this.s = s; }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) return null;
            char c = s.charAt(i);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == '-' || Character.isDigit(c)) return parseNumber();
            if (s.regionMatches(i, "true", 0, 4)) { i += 4; return Boolean.TRUE; }
            if (s.regionMatches(i, "false", 0, 5)) { i += 5; return Boolean.FALSE; }
            if (s.regionMatches(i, "null", 0, 4)) { i += 4; return null; }
            return null;
        }

        JsonObj parseObject() {
            JsonObj o = new JsonObj();
            i++; // {
            while (true) {
                skipWs();
                if (i >= s.length() || s.charAt(i) == '}') { i++; return o; }
                String key = parseString();
                if (key == null) return o;
                skipWs();
                if (i < s.length() && s.charAt(i) == ':') i++;
                skipWs();
                o.map.put(key, parseValue());
                skipWs();
                if (i < s.length() && s.charAt(i) == ',') i++;
            }
        }

        JsonArr parseArray() {
            JsonArr a = new JsonArr();
            i++; // [
            while (true) {
                skipWs();
                if (i >= s.length() || s.charAt(i) == ']') { i++; return a; }
                a.list.add(parseValue());
                skipWs();
                if (i < s.length() && s.charAt(i) == ',') i++;
            }
        }

        String parseString() {
            if (i >= s.length() || s.charAt(i) != '"') return null;
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\' && i < s.length()) {
                    char n = s.charAt(i++);
                    if (n == 'n') sb.append('\n');
                    else if (n == 't') sb.append('\t');
                    else if (n == 'r') sb.append('\r');
                    else sb.append(n);
                } else sb.append(c);
            }
            return sb.toString();
        }

        Number parseNumber() {
            int start = i;
            if (i < s.length() && s.charAt(i) == '-') i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            if (i < s.length() && s.charAt(i) == '.') {
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            String num = s.substring(start, i);
            try {
                if (num.contains(".")) return Double.parseDouble(num);
                return Integer.parseInt(num);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
