import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ThemeManager
 *
 * Defines a set of named colour themes and applies them live to:
 *   - JInternalFrame title bars (active + inactive)
 *   - The top JMenuBar and all menus / menu items / popups
 *   - Console output text colour
 *   - Control buttons, sliders, playlist lists
 *   - Any extra components registered via registerExtra()
 *
 * HOW TO USE
 * ──────────
 *  1.  Create one instance:  ThemeManager tm = new ThemeManager(playerUI);
 *  2.  Call tm.apply(tm.THEMES[0]) at startup to set the initial theme.
 *  3.  Wire the "Themes" menu:  tm.buildThemeMenu(parentMenu);
 *      This appends a radio-button sub-menu; selecting an item calls apply().
 */

public class ThemeManager {

    // ── Theme record ─────────────────────────────────────────────────────────

    public static class Theme {
        public final String name;

        // Internal-frame title bars
        public final Color titleActiveBg;
        public final Color titleActiveFg;
        public final Color titleInactiveBg;
        public final Color titleInactiveFg;

        // Menu bar + menus
        public final Color menuBarBg;
        public final Color menuBarFg;
        public final Color menuBg;
        public final Color menuFg;
        public final Color menuSelBg;
        public final Color menuSelFg;

        // Desktop / main container
        public final Color desktopBg;

        // Panel fill colours (semi-transparent versions computed from desktopBg)
        public final Color panelControl;   // player control area
        public final Color panelMid;       // playlist / song list
        public final Color panelConsole;   // console background

        // Accent (sliders, selection highlight)
        public final Color accent;

        // Foreground text colours
        public final Color fgBright;
        public final Color fgDim;

        // Console text colour
        public final Color consoleFg;

        // Button background
        public final Color buttonBg;

        public Color currentConnColor;

        public Theme(
                String name,
                Color titleActiveBg,   Color titleActiveFg,
                Color titleInactiveBg, Color titleInactiveFg,
                Color menuBarBg,       Color menuBarFg,
                Color menuBg,          Color menuFg,
                Color menuSelBg,       Color menuSelFg,
                Color desktopBg,
                Color panelControl,    Color panelMid,    Color panelConsole,
                Color accent,
                Color fgBright,        Color fgDim,
                Color consoleFg,
                Color buttonBg,
                Color currentConnColor
        ) {
            this.name            = name;
            this.titleActiveBg   = titleActiveBg;
            this.titleActiveFg   = titleActiveFg;
            this.titleInactiveBg = titleInactiveBg;
            this.titleInactiveFg = titleInactiveFg;
            this.menuBarBg       = menuBarBg;
            this.menuBarFg       = menuBarFg;
            this.menuBg          = menuBg;
            this.menuFg          = menuFg;
            this.menuSelBg       = menuSelBg;
            this.menuSelFg       = menuSelFg;
            this.desktopBg       = desktopBg;
            this.panelControl    = panelControl;
            this.panelMid        = panelMid;
            this.panelConsole    = panelConsole;
            this.accent          = accent;
            this.fgBright        = fgBright;
            this.fgDim           = fgDim;
            this.consoleFg       = consoleFg;
            this.buttonBg        = buttonBg;
            this.currentConnColor = currentConnColor;
        }
    }

    // ── Built-in themes ──────────────────────────────────────────────────────

    public static final Theme[] THEMES = {

            new Theme("Green",
                    new Color(45,  80,  50,  200), new Color(180, 240, 190),     // active title
                    new Color(35,  60,  40,  160), new Color(120, 170, 130),     // inactive title
                    new Color(10,  10,  12),       new Color(220, 220, 220),        // menu bar
                    new Color(15,  15,  18),       new Color(180, 180, 180),         // menu
                    new Color(70,  130, 100),      Color.WHITE,                               // menu selection
                    new Color(20,  20,  23),                                                  // desktop bg
                    new Color(10,  10,  12,  160),                                        // panel control
                    new Color(20,  20,  23,  140),                                        // panel mid
                    new Color(10,  10,  12,  160),                                        // panel console
                    new Color(70,  130, 100),                                                // accent
                    new Color(220, 220, 220),      new Color(180, 180, 180),       // fg bright/dim
                    new Color(140, 220, 160),                                                // console fg
                    new Color(40,  40,  45,  180),
                    new Color(70,  130, 100) // button bg
            ),

            new Theme("Blue",
                    new Color(30,  70,  120, 210), new Color(180, 220, 255),
                    new Color(20,  50,  90,  160), new Color(100, 150, 200),
                    new Color(8,   12,  20),       new Color(200, 220, 255),
                    new Color(12,  16,  28),       new Color(160, 185, 220),
                    new Color(50,  110, 190),      Color.WHITE,
                    new Color(12,  16,  28),
                    new Color(8,   12,  20,  170),
                    new Color(15,  20,  35,  145),
                    new Color(8,   12,  20,  170),
                    new Color(70,  130, 200),
                    new Color(210, 225, 255),      new Color(160, 185, 220),
                    new Color(120, 190, 255),
                    new Color(25,  50,  90,  190),
                    new Color(50,  110, 190)
            ),
            new Theme("Ember",
                    new Color(100, 40,  20,  210), new Color(255, 210, 170),
                    new Color(75,  30,  15,  160), new Color(180, 130, 90),
                    new Color(18,  10,  8),        new Color(255, 210, 170),
                    new Color(22,  13,  10),       new Color(200, 160, 120),
                    new Color(180, 80,  30),       Color.WHITE,
                    new Color(18,  10,  8),
                    new Color(18,  10,  8,   170),
                    new Color(24,  14,  10,  145),
                    new Color(18,  10,  8,   170),
                    new Color(200, 90,  30),
                    new Color(255, 215, 180),      new Color(200, 160, 120),
                    new Color(255, 160, 80),
                    new Color(60,  25,  12,  190),
                    new Color(200, 90,  30)
            ),
            new Theme("Purple",
                    new Color(70,  40,  110, 210), new Color(220, 190, 255),
                    new Color(50,  28,  80,  160), new Color(160, 130, 200),
                    new Color(14,  10,  20),       new Color(220, 195, 255),
                    new Color(18,  12,  28),       new Color(175, 150, 210),
                    new Color(120, 60,  190),      Color.WHITE,
                    new Color(14,  10,  20),
                    new Color(14,  10,  20,  170),
                    new Color(20,  14,  30,  145),
                    new Color(14,  10,  20,  170),
                    new Color(140, 80,  210),
                    new Color(230, 210, 255),      new Color(175, 150, 210),
                    new Color(190, 140, 255),
                    new Color(55,  30,  85,  190),
                    new Color(120, 60,  190)
            ),

            new Theme("Monochrome",
                    new Color(55,  55,  60,  220), new Color(230, 230, 230),
                    new Color(38,  38,  42,  160), new Color(140, 140, 145),
                    new Color(12,  12,  14),       new Color(220, 220, 220),
                    new Color(18,  18,  20),       new Color(175, 175, 180),
                    new Color(90,  90,  100),      Color.WHITE,
                    new Color(18,  18,  20),
                    new Color(12,  12,  14,  170),
                    new Color(20,  20,  23,  145),
                    new Color(12,  12,  14,  170),
                    new Color(130, 130, 145),
                    new Color(225, 225, 225),      new Color(170, 170, 175),
                    new Color(170, 220, 170),
                    new Color(45,  45,  50,  190),
                    new Color(90,  90,  100)
            ),

            new Theme("Pink",
                    new Color(110, 50,  65,  210), new Color(255, 200, 210),
                    new Color(80,  35,  48,  160), new Color(190, 140, 155),
                    new Color(20,  10,  12),       new Color(255, 205, 215),
                    new Color(25,  13,  16),       new Color(200, 155, 165),
                    new Color(185, 75,  100),      Color.WHITE,
                    new Color(20,  10,  12),
                    new Color(20,  10,  12,  170),
                    new Color(26,  13,  16,  145),
                    new Color(20,  10,  12,  170),
                    new Color(200, 90,  115),
                    new Color(255, 210, 218),      new Color(200, 155, 165),
                    new Color(255, 160, 180),
                    new Color(65,  28,  38,  190),
                    new Color(185, 75,  100)
            ),
    };

    // ── State ────────────────────────────────────────────────────────────────

    private final PlayerUI ui;
    private Theme current = THEMES[0];
    private ConnectorLayer connectorLayer = null;

    public void setConnectorLayer(ConnectorLayer cl) { this.connectorLayer = cl; }

    /** Additional components to repaint after a theme switch. */
    private final List<JComponent> extras = new ArrayList<>();

    public ThemeManager(PlayerUI ui) {
        this.ui = ui;
    }

    /** Register any extra component that needs repaint() after a theme switch. */
    public void registerExtra(JComponent c) {
        extras.add(c);
    }

    public Theme getCurrent() { return current; }

    /** Applies the current theme to a single frame — call this when a new frame is created. */
    public void applyToFrame(JInternalFrame f) {
        updateInternalFrameUI(f);
    }

    // ── Apply ────────────────────────────────────────────────────────────────

    /**
     * Applies the given theme everywhere: UIManager defaults (for future
     * components), then re-styles every live component on the desktop.
     */
    public void apply(Theme t) {
        current = t;

        // ── UIManager defaults ────────────────────────────────────────────────
        // Basic L&F keys
        UIManager.put("InternalFrame.activeTitleBackground",   new ColorUIResource(t.titleActiveBg));
        UIManager.put("InternalFrame.activeTitleForeground",   new ColorUIResource(t.titleActiveFg));
        UIManager.put("InternalFrame.inactiveTitleBackground", new ColorUIResource(t.titleInactiveBg));
        UIManager.put("InternalFrame.inactiveTitleForeground", new ColorUIResource(t.titleInactiveFg));

        // Metal-specific title bar keys (Metal reads these instead of the Basic ones)
        // NOTE: do NOT set "InternalFrame.activeTitleGradient" here.
        // MetalUtils.drawGradient() expects that key to be a List<Object> of gradient
        // stops.  Putting a ColorUIResource there causes an endless stream of
        // ClassCastException: ColorUIResource cannot be cast to List on every repaint.
        // Leaving the key unset makes Metal fall back to a flat colour drawn from
        // the activeCaption / InternalFrame.activeTitleBackground keys below.
        UIManager.put("activeCaption",                             new ColorUIResource(t.titleActiveBg));
        UIManager.put("activeCaptionText",                         new ColorUIResource(t.titleActiveFg));
        UIManager.put("inactiveCaption",                           new ColorUIResource(t.titleInactiveBg));
        UIManager.put("inactiveCaptionText",                       new ColorUIResource(t.titleInactiveFg));
        UIManager.put("InternalFrame.paletteTitleHeight",          13);

        // Scrollbar keys
        UIManager.put("ScrollBar.background",        new ColorUIResource(t.panelMid));
        UIManager.put("ScrollBar.thumb",             new ColorUIResource(t.accent));
        UIManager.put("ScrollBar.thumbHighlight",    new ColorUIResource(withAlpha(t.accent, 180)));
        UIManager.put("ScrollBar.thumbDarkShadow",   new ColorUIResource(t.desktopBg));
        UIManager.put("ScrollBar.thumbShadow",       new ColorUIResource(withAlpha(t.accent, 120)));
        UIManager.put("ScrollBar.track",             new ColorUIResource(t.panelMid));
        UIManager.put("ScrollBar.trackHighlight",    new ColorUIResource(t.panelMid));
        UIManager.put("ScrollBar.foreground",        new ColorUIResource(t.fgDim));
        UIManager.put("ScrollPane.background",       new ColorUIResource(t.panelMid));
        UIManager.put("ScrollPane.foreground",       new ColorUIResource(t.fgDim));

        // Menu keys
        UIManager.put("MenuBar.background",           new ColorUIResource(t.menuBarBg));
        UIManager.put("MenuBar.foreground",           new ColorUIResource(t.menuBarFg));
        UIManager.put("Menu.background",              new ColorUIResource(t.menuBg));
        UIManager.put("Menu.foreground",              new ColorUIResource(t.menuFg));
        UIManager.put("Menu.selectionBackground",     new ColorUIResource(t.menuSelBg));
        UIManager.put("Menu.selectionForeground",     new ColorUIResource(t.menuSelFg));
        UIManager.put("MenuItem.background",          new ColorUIResource(t.menuBg));
        UIManager.put("MenuItem.foreground",          new ColorUIResource(t.menuFg));
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(t.menuSelBg));
        UIManager.put("MenuItem.selectionForeground", new ColorUIResource(t.menuSelFg));
        UIManager.put("PopupMenu.background",         new ColorUIResource(t.menuBg));

        // ── Live components ───────────────────────────────────────────────────

        // Menu bar
        ui.topMenuBar.setBackground(t.menuBarBg);
        ui.topMenuBar.setForeground(t.menuBarFg);
        styleMenuLive(ui.menuAlbums,    t);
        styleMenuLive(ui.menuPlaylists, t);
        styleMenuLive(ui.menuHelp,      t);
        styleMenuItemsRecursive(ui.menuAlbums,    t);
        styleMenuItemsRecursive(ui.menuPlaylists, t);
        styleMenuItemsRecursive(ui.menuHelp,      t);

        // Exit button
        ui.btnExit.setBackground(t.buttonBg);
        ui.btnExit.setForeground(t.fgBright);

        // Control buttons
        styleBtn(ui.btnPrevious,  t);
        styleBtn(ui.btnPlayPause, t);
        styleBtn(ui.btnNext,      t);

        // Labels
        ui.nowPlayingLabel.setForeground(t.fgBright);
        ui.currentTimeLabel.setForeground(t.fgDim);
        ui.totalTimeLabel.setForeground(t.fgDim);
        ui.volumeLabel.setForeground(t.fgDim);

        // Sliders
        ui.progressBar.setForeground(t.accent);
        ui.volumeSlider.setForeground(t.accent);

        // Playlist list
        ui.sm.displayedPlaylist.setBackground(t.panelMid);
        ui.sm.displayedPlaylist.setForeground(t.fgBright);
        ui.sm.displayedPlaylist.setSelectionBackground(withAlpha(t.accent, 200));
        ui.playlistScroll.getViewport().setBackground(t.panelMid);

        // Console
        ui.consoleOutput.setForeground(t.consoleFg);
        ui.consoleOutput.setBackground(t.panelConsole);
        ui.consoleScroll.getViewport().setBackground(t.panelConsole);
        styleScrollbarsIn(ui.consoleScroll, t);
        styleScrollbarsIn(ui.playlistScroll, t);

        // Desktop background colour (shows when no wallpaper)
        ui.desktop.setBackground(t.desktopBg);

        // Re-skin all open internal frames
        for (JInternalFrame f : ui.desktop.getAllFrames()) {
            updateInternalFrameUI(f);
        }

        // Repaint everything — do NOT call updateComponentTreeUI here.
        // That method re-installs the UI delegate on every component including
        // sliders, which resets BasicSliderUI.focusRect to null before the
        // component has been laid out, crashing BoxLayout with a NPE.
        // We've already updated every colour individually above, so a
        // revalidate + repaint is all that's needed.
        extras.forEach(JComponent::repaint);
        if (connectorLayer != null) connectorLayer.setCableColor(t.currentConnColor);
        ui.mainFrame.revalidate();
        ui.mainFrame.repaint();
    }

    // ── Menu building ─────────────────────────────────────────────────────────

    /**
     * Appends a "Themes ▶" sub-menu to the given parent menu.
     * Call this from PlayerUI.addItemsToContainers() or wherever the Help menu is built.
     */
    public void buildThemeMenu(JMenu parent) {
        JMenu themeMenu = new JMenu("Themes");
        ButtonGroup group = new ButtonGroup();

        for (Theme t : THEMES) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(t.name);
            item.setSelected(t == current);
            item.addActionListener(e -> apply(t));
            group.add(item);
            themeMenu.add(item);
        }

        parent.addSeparator();
        parent.add(themeMenu);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void styleMenuLive(JMenu menu, Theme t) {
        menu.setForeground(t.menuFg);
        menu.setBackground(t.menuBg);
    }

    /** Recursively style all items inside a menu (including sub-menus). */
    private static void styleMenuItemsRecursive(JMenu menu, Theme t) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item == null) continue;   // separator
            item.setBackground(t.menuBg);
            item.setForeground(t.menuFg);
            if (item instanceof JMenu sub) {
                styleMenuLive(sub, t);
                styleMenuItemsRecursive(sub, t);
            }
        }
    }

    private static void styleBtn(JButton b, Theme t) {
        b.setBackground(t.buttonBg);
        b.setForeground(t.fgBright);
    }

    /**
     * Applies theme colours to a JInternalFrame's title bar and border.
     *
     * Strategy: let the L&F install its normal UI (so all buttons and drag
     * handles keep working), then find the title pane JComponent that sits at
     * index 0 of the frame's root pane layered pane, replace only its
     * background/foreground via setBackground/setForeground (which the Basic
     * L&F DOES honour after updateUI), and set the border directly on the frame.
     *
     * We avoid replacing setUI() entirely because that disconnects the button
     * action listeners wired by BasicInternalFrameUI, breaking minimise etc.
     */
    private void updateInternalFrameUI(JInternalFrame f) {
        Theme t = current;

        // Border
        javax.swing.border.Border inner = BorderFactory.createLineBorder(t.titleActiveBg, 2);
        javax.swing.border.Border outer = BorderFactory.createLineBorder(withAlpha(t.titleActiveBg, 80), 1);
        f.setBorder(BorderFactory.createCompoundBorder(outer, inner));

        // Title pane — force it to re-read UIManager by cycling uninstall/install.
        // This works on both BasicInternalFrameTitlePane and MetalInternalFrameTitlePane
        // because both call installDefaults() which reads from UIManager.
        if (f.getUI() instanceof javax.swing.plaf.basic.BasicInternalFrameUI bui) {
            JComponent pane = bui.getNorthPane();
            if (pane != null) {
                // Cycle installDefaults via reflection so the pane re-reads UIManager
                forcePaneReinstall(pane);
                // Style the window control buttons
                for (Component c : pane.getComponents()) {
                    if (c instanceof JButton btn) {
                        btn.setBackground(withAlpha(t.titleActiveBg, 200));
                        btn.setForeground(t.titleActiveFg);
                        btn.setOpaque(true);
                        btn.setBorderPainted(false);
                        btn.setFocusPainted(false);
                    }
                }
                pane.repaint();
            }
        }

        // Restyle every scrollbar inside this frame
        styleScrollbarsIn(f, t);

        f.repaint();
    }

    /**
     * Forces the title pane to call uninstallDefaults() then installDefaults()
     * so it re-reads the UIManager colour values we just updated.
     * Works on Metal's MetalInternalFrameTitlePane as well as Basic's.
     */
    private static void forcePaneReinstall(JComponent pane) {
        try {
            java.lang.reflect.Method uninstall = pane.getClass().getMethod("uninstallDefaults");
            uninstall.setAccessible(true);
            uninstall.invoke(pane);
        } catch (Exception ignored) {}
        try {
            java.lang.reflect.Method install = pane.getClass().getMethod("installDefaults");
            install.setAccessible(true);
            install.invoke(pane);
        } catch (Exception ignored) {}
        // Also try the protected superclass methods by walking up
        Class<?> cls = pane.getClass().getSuperclass();
        while (cls != null && cls != Object.class) {
            try {
                java.lang.reflect.Method m = cls.getDeclaredMethod("installDefaults");
                m.setAccessible(true);
                m.invoke(pane);
                break;
            } catch (Exception ignored) {}
            cls = cls.getSuperclass();
        }
    }

    /**
     * Walks all components inside a container and restyles any JScrollBar
     * and JScrollPane found, so scrollbars inside internal frames match the theme.
     */
    private static void styleScrollbarsIn(Container root, Theme t) {
        for (Component c : root.getComponents()) {
            if (c instanceof JScrollBar sb) {
                sb.setBackground(t.panelMid);
                sb.setForeground(t.accent);
                sb.updateUI();
            } else if (c instanceof JScrollPane sp) {
                sp.getVerticalScrollBar().setBackground(t.panelMid);
                sp.getHorizontalScrollBar().setBackground(t.panelMid);
                sp.getVerticalScrollBar().updateUI();
                sp.getHorizontalScrollBar().updateUI();
            }
            if (c instanceof Container ct) {
                styleScrollbarsIn(ct, t);
            }
        }
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}