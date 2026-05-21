import javax.swing.*;
import javax.sound.sampled.Clip;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import javax.imageio.ImageIO;
import java.time.*;

class PlayerUI {

    AudioController ac = new AudioController();
    SongManagement sm = new SongManagement();

    JFrame mainFrame = new JFrame("Max's Music Player");

    JDesktopPane desktop = new JDesktopPane() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (backgroundImage != null)
                g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        }
    };

    Image backgroundImage;

    ConnectorLayer connectorLayer = null;

    static final Color TRANS_CONTROL = new Color(10, 10, 12, 160);
    static final Color TRANS_MENUBAR = new Color(10, 10, 12, 255);
    static final Color TRANS_MID     = new Color(20, 20, 23, 140);
    static final Color TRANS_CONSOLE = new Color(10, 10, 12, 160);
    static final Color ACCENT_BLUE   = new Color(70, 130, 180);
    static final Color FG_BRIGHT     = new Color(220, 220, 220);
    static final Color FG_DIM        = new Color(180, 180, 180);
    static final Color BORDER_COLOR  = new Color(90, 90, 95, 100);

    static JPanel transPanel(LayoutManager layout, Color fillColor) {
        JPanel p = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(fillColor);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    JMenuBar topMenuBar = new JMenuBar() {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(TRANS_MENUBAR);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    };

    JMenu   menuAlbums    = new JMenu("Albums");
    JMenu   menuPlaylists = new JMenu("Playlists");
    JMenu   menuHelp      = new JMenu("Help");
    JButton btnExit       = new JButton("X");

    JMenuItem menuItemAddSongs     = new JMenuItem("Add Songs");
    JMenuItem menuItemEditPlaylist = new JMenuItem("Edit Playlists…");
    JMenuItem menuItemAbout        = new JMenuItem("About/Help");
    JMenuItem menuItemConsole      = new JMenuItem("Console");
    JMenuItem menuItemPlayer       = new JMenuItem("Player");

    // Per-playlist frames: name → frame. Allows multiple open at once, and reopening closed ones.
    private final HashMap<String, JInternalFrame> playlistFrames = new HashMap<>();

    JInternalFrame helpFrame    = new JInternalFrame("About/Help",  true, true, true, true);
    JInternalFrame consoleFrame = new JInternalFrame("Console </>", true, true, true, true);
    JInternalFrame playerFrame  = new JInternalFrame("Player",      true, true, true, true);

    AddSongsFrame       addSongsFrame       = null;
    PlaylistEditorFrame playlistEditorFrame = null;

    ThemeManager themeManager = null;

    JTextArea   consoleOutput  = new JTextArea();
    JScrollPane consoleScroll  = new JScrollPane(consoleOutput);
    JScrollPane playlistScroll = new JScrollPane(sm.displayedPlaylist);

    ConsoleCommandHandler consoleHandler = null;   // initialised in setUi()

    JButton btnPrevious  = new JButton("◁");
    JButton btnPlayPause = new JButton("▶");
    JButton btnNext      = new JButton("▷");
    JButton btnShuffle   = new JButton("⇄");
    JButton btnLoop      = new JButton("↻");
    JButton btnPopOut    = new JButton("⊡");

    /** The detached player window; null when docked. */
    PopOutPlayerWindow popOutWindow = null;

    /** Opens (or focuses) the pop-out player window. */
    void popOutPlayer() {
        if (popOutWindow == null) popOutWindow = new PopOutPlayerWindow(this);
        popOutWindow.show();
    }

    boolean shuffleEnabled = false;
    boolean loopEnabled    = false;
    // Tracks shuffle history so Previous works sensibly in shuffle mode
    private final java.util.ArrayDeque<Integer> shuffleHistory = new java.util.ArrayDeque<>();

    JSlider volumeSlider   = new JSlider(0, 100);
    JLabel  volumeLabel    = new JLabel("20%");

    JSlider progressBar      = new JSlider(0, 100);
    JLabel  currentTimeLabel = new JLabel("0:00");
    JLabel  totalTimeLabel   = new JLabel("0:00");
    JLabel  nowPlayingLabel  = new JLabel("No song playing");

    /** Displays the album cover art in the player frame. */
    JLabel  albumArtLabel    = new JLabel();
    /** The album name whose cover is currently loaded (avoids redundant disk reads). */
    private String loadedCoverAlbum = null;

    Timer progressTimer;

    void main(String[] args) {
        setUi();
        sm.scrapeAndADD();
        populateAlbumMenu();
        populatePlaylistMenu();


        try {
            backgroundImage = ImageIO.read(new File("/home/nyx/Documents/wallpapers/oilref.jpg"));
            desktop.repaint();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    void setUi() {
        frameSetup();
        themeManager            = new ThemeManager(this);
        consoleHandler          = new ConsoleCommandHandler(this);
        addSongsFrame           = new AddSongsFrame(this);
        playlistEditorFrame     = new PlaylistEditorFrame(this);
        configInternalFrames();
        configPlayerFrame();
        addActionListeners();
        addItemsToContainers();
        // Apply default theme AFTER all components exist and are in containers
        themeManager.apply(ThemeManager.THEMES[0]);
        wireConnectors();
    }
    void frameSetup() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Consistent dark theme across all UI components
        UIManager.put("InternalFrame.activeTitleBackground",   new Color(45, 80, 50, 180));
        UIManager.put("InternalFrame.activeTitleForeground",   new Color(180, 240, 190));
        UIManager.put("InternalFrame.inactiveTitleBackground", new Color(35, 60, 40, 150));
        UIManager.put("InternalFrame.inactiveTitleForeground", new Color(120, 170, 130));
        UIManager.put("InternalFrame.borderColor",             BORDER_COLOR);

        // Menu bar and menu styling - consistent dark theme
        UIManager.put("MenuBar.background",           new Color(10, 10, 12));
        UIManager.put("MenuBar.foreground",           FG_BRIGHT);
        UIManager.put("Menu.background",              new Color(15, 15, 18));
        UIManager.put("Menu.foreground",              FG_DIM);
        UIManager.put("Menu.selectionBackground",     ACCENT_BLUE);
        UIManager.put("Menu.selectionForeground",     Color.WHITE);
        UIManager.put("MenuItem.background",          new Color(15, 15, 18));
        UIManager.put("MenuItem.foreground",          FG_DIM);
        UIManager.put("MenuItem.selectionBackground", ACCENT_BLUE);
        UIManager.put("MenuItem.selectionForeground", Color.WHITE);
        UIManager.put("PopupMenu.background",         new Color(15, 15, 18));
        UIManager.put("PopupMenu.border",             BorderFactory.createLineBorder(BORDER_COLOR));
        UIManager.put("Separator.foreground",         BORDER_COLOR);
        UIManager.put("Separator.background",         new Color(15, 15, 18));

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        mainFrame.setSize(screen.width - 300, screen.height - 100);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainContainer = new JPanel(new BorderLayout());
        mainContainer.setOpaque(true);
        mainContainer.setBackground(new Color(20, 20, 23));
        mainContainer.add(desktop, BorderLayout.CENTER);
        mainFrame.setContentPane(mainContainer);
        mainFrame.setJMenuBar(topMenuBar);

        topMenuBar.setBackground(new Color(10, 10, 12)); // Match the UIManager color
        topMenuBar.setOpaque(true);
        topMenuBar.setBorderPainted(false);

        desktop.setOpaque(true);
        desktop.setBackground(new Color(20, 20, 23));

        btnExit.setFont(new Font("Default", Font.PLAIN, 14));
        btnExit.setBackground(new Color(50, 50, 53));
        btnExit.setForeground(FG_BRIGHT);
        btnExit.setFocusPainted(false);
        btnExit.setBorderPainted(false);
        btnExit.setOpaque(true);

        styleMenuHeader(menuAlbums);
        styleMenuHeader(menuPlaylists);
        styleMenuHeader(menuHelp);

        mainFrame.setVisible(true);
    }
    /** Uniform dark styling for top-level menu headers. */
    private static void styleMenuHeader(JMenu menu) {
        menu.setForeground(FG_DIM);
        menu.setOpaque(false);
        menu.setBorderPainted(false);
        menu.setFont(new Font("Default", Font.PLAIN, 13));
    }

    void configInternalFrames() {
        sm.displayedPlaylist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sm.displayedPlaylist.setVisibleRowCount(15);
        sm.displayedPlaylist.setFont(new Font("Default", Font.PLAIN, 14));
        sm.displayedPlaylist.setBackground(TRANS_MID);
        sm.displayedPlaylist.setForeground(FG_BRIGHT);
        sm.displayedPlaylist.setSelectionBackground(new Color(70, 130, 180, 200));
        sm.displayedPlaylist.setSelectionForeground(Color.WHITE);
        sm.displayedPlaylist.setOpaque(true);

        playlistScroll.getViewport().setBackground(TRANS_MID);
        playlistScroll.getViewport().setOpaque(true);
        playlistScroll.setBorder(BorderFactory.createEmptyBorder());
        playlistScroll.setOpaque(false);

        // help
        String[][] HELP_ENTRIES = {
                { "Max's Music Player", "A not-so-lightweight desktop music player for your local library." },
                { "Albums",            "Browse your downloaded albums via the Albums menu. Albums are stored in ~/Documents/music. Each folder becomes an album." },
                { "Playlists",         "Create and manage custom playlists from the Playlists menu. Use 'Edit Playlists…' to add or remove songs. Stored as .m3u files in ~/Documents/music/.playlists/" },
                { "Console",           "The Console allows you to manipulate many aspects of this music player. Such as settings and more. Start by typing help to find commands." },
                { "Adding Songs",      "Use the add songs menu to download songs from youtube videos and playlists. " },
                { "How it works",      "Built with Java Swing. yt-dlp handles YouTube downloads." },
        };

        JTextPane helpTextPane = new JTextPane();
        helpTextPane.setEditable(false);
        helpTextPane.setOpaque(false);
        helpTextPane.setForeground(FG_BRIGHT);
        helpTextPane.setFont(new Font("Default", Font.PLAIN, 13));
        helpTextPane.setBackground(new Color(0, 0, 0, 0));
        helpTextPane.setContentType("text/html");

        StringBuilder helpHtml = new StringBuilder(
                "<html><body style='font-family:sans-serif;font-size:12px;color:#dcdcdc;margin:8px 10px;'>"
        );
        for (String[] entry : HELP_ENTRIES) {
            helpHtml.append("<p><b style='color:#aed6f1;font-size:13px;'>")
                    .append(entry[0]).append("</b><br>")
                    .append(entry[1].replace("\n", "<br>"))
                    .append("</p>");
        }
        helpHtml.append("</body></html>");
        helpTextPane.setText(helpHtml.toString());
        helpTextPane.setCaretPosition(0);

        JScrollPane helpScroll = new JScrollPane(helpTextPane);
        helpScroll.setBorder(BorderFactory.createEmptyBorder());
        helpScroll.setOpaque(false);
        helpScroll.getViewport().setOpaque(false);
        helpScroll.getViewport().setBackground(new Color(0, 0, 0, 0));
        helpScroll.getVerticalScrollBar().setUnitIncrement(12);

        JPanel helpContent = transPanel(new BorderLayout(), new Color(15, 15, 18, 150));
        helpContent.add(helpScroll, BorderLayout.CENTER);
        helpFrame.setContentPane(helpContent);
        helpFrame.setSize(380, 280);
        helpFrame.setLocation(80, 50);
        helpFrame.setOpaque(false);
        helpFrame.setFrameIcon(null);
        helpFrame.setVisible(false);

        consoleOutput.setEditable(false);
        consoleOutput.setBackground(TRANS_CONSOLE);
        consoleOutput.setForeground(new Color(180, 220, 180));
        consoleOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleOutput.setCaretColor(new Color(180, 220, 180));
        consoleOutput.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        consoleOutput.setOpaque(false);

        consoleScroll.getViewport().setBackground(TRANS_CONSOLE);
        consoleScroll.getViewport().setOpaque(false);
        consoleScroll.setBorder(BorderFactory.createEmptyBorder());
        consoleScroll.setOpaque(false);

        // ── Terminal input row ───────────────────────────────────────────────
        JLabel promptLabel = new JLabel("❯ ");
        promptLabel.setForeground(new Color(100, 200, 120));
        promptLabel.setFont(new Font("Monospaced", Font.BOLD, 13));

        JTextField cmdInput = new JTextField();
        cmdInput.setBackground(new Color(14, 14, 17));
        cmdInput.setForeground(new Color(220, 220, 220));
        cmdInput.setCaretColor(new Color(200, 220, 200));
        cmdInput.setFont(new Font("Monospaced", Font.PLAIN, 13));
        cmdInput.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        cmdInput.setOpaque(true);

        // history navigation with ↑ / ↓
        final int[] histIdx = {-1};
        cmdInput.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                var hist = consoleHandler.history;
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_UP) {
                    if (hist.isEmpty()) return;
                    histIdx[0] = Math.min(histIdx[0] + 1, hist.size() - 1);
                    cmdInput.setText(hist.get(hist.size() - 1 - histIdx[0]));
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN) {
                    if (histIdx[0] <= 0) { histIdx[0] = -1; cmdInput.setText(""); return; }
                    histIdx[0]--;
                    cmdInput.setText(hist.get(hist.size() - 1 - histIdx[0]));
                }
            }
        });

        // submit on Enter
        cmdInput.addActionListener(e -> {
            String input = cmdInput.getText().trim();
            if (input.isEmpty()) return;
            histIdx[0] = -1;
            cmdInput.setText("");
            // echo the command
            log("❯ " + input);
            // execute and print result
            String result = consoleHandler.dispatch(input);
            if (!result.isEmpty()) log(result);
        });

        JPanel inputRow = new JPanel(new BorderLayout(2, 0));
        inputRow.setOpaque(true);
        inputRow.setBackground(new Color(14, 14, 17));
        inputRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));
        inputRow.add(promptLabel, BorderLayout.WEST);
        inputRow.add(cmdInput,    BorderLayout.CENTER);

        JPanel consoleContent = transPanel(new BorderLayout(), TRANS_CONSOLE);
        consoleContent.add(consoleScroll, BorderLayout.CENTER);
        consoleContent.add(inputRow,      BorderLayout.SOUTH);
        consoleFrame.setContentPane(consoleContent);
        consoleFrame.setSize(560, 260);
        consoleFrame.setLocation(110, 50);
        consoleFrame.setOpaque(false);
        consoleFrame.setFrameIcon(null);
        consoleFrame.setVisible(false);
    }

    void configPlayerFrame() {
        JPanel playerContent = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(TRANS_CONTROL);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        playerContent.setLayout(new BoxLayout(playerContent, BoxLayout.Y_AXIS));
        playerContent.setOpaque(false);
        playerContent.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        // Album art
        albumArtLabel.setHorizontalAlignment(SwingConstants.CENTER);
        albumArtLabel.setVerticalAlignment(SwingConstants.CENTER);
        albumArtLabel.setText("♪");
        albumArtLabel.setFont(new Font("Default", Font.PLAIN, 48));
        albumArtLabel.setForeground(new Color(80, 80, 90));
        albumArtLabel.setOpaque(true);
        albumArtLabel.setBackground(new Color(18, 18, 21));
        albumArtLabel.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        albumArtLabel.setPreferredSize(new Dimension(180, 180));
        albumArtLabel.setMinimumSize(new Dimension(180, 180));
        albumArtLabel.setMaximumSize(new Dimension(180, 180));
        albumArtLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        nowPlayingLabel.setFont(new Font("Default", Font.BOLD, 13));
        nowPlayingLabel.setForeground(FG_BRIGHT);
        nowPlayingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel progressPanel = transPanel(new BorderLayout(5, 0), TRANS_CONTROL);
        progressPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        currentTimeLabel.setForeground(FG_DIM);
        currentTimeLabel.setFont(new Font("Default", Font.PLAIN, 11));
        totalTimeLabel.setForeground(FG_DIM);
        totalTimeLabel.setFont(new Font("Default", Font.PLAIN, 11));

        progressBar.setValue(0);
        progressBar.setOpaque(false);
        progressBar.setForeground(ACCENT_BLUE);
        progressBar.setPaintTicks(false);
        progressBar.setPaintLabels(false);

        progressPanel.add(currentTimeLabel, BorderLayout.WEST);
        progressPanel.add(progressBar,      BorderLayout.CENTER);
        progressPanel.add(totalTimeLabel,   BorderLayout.EAST);

        JPanel buttonsPanel = transPanel(new FlowLayout(FlowLayout.CENTER, 10, 4), TRANS_CONTROL);
        buttonsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));

        styleControlButton(btnPrevious);
        styleControlButton(btnPlayPause);
        styleControlButton(btnNext);
        styleControlButton(btnShuffle);
        styleControlButton(btnLoop);
        btnPlayPause.setPreferredSize(new Dimension(50, 45));
        btnPlayPause.setFont(new Font("Default", Font.PLAIN, 14));
        btnShuffle.setPreferredSize(new Dimension(55, 34));
        btnShuffle.setMinimumSize(new Dimension(55, 34));
        btnShuffle.setToolTipText("Shuffle");
        btnLoop.setPreferredSize(new Dimension(55, 34));
        btnLoop.setMinimumSize(new Dimension(55, 34));
        btnLoop.setToolTipText("Loop current song");

        buttonsPanel.add(btnShuffle);
        buttonsPanel.add(btnPrevious);
        buttonsPanel.add(btnPlayPause);
        buttonsPanel.add(btnNext);
        buttonsPanel.add(btnLoop);

        JPanel volumePanel = transPanel(new FlowLayout(FlowLayout.CENTER, 8, 4), TRANS_CONTROL);
        volumePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel volumeIcon = new JLabel("🔊");
        volumeIcon.setFont(new Font("Default", Font.PLAIN, 14));
        volumeSlider.setValue(20);
        volumeSlider.setPreferredSize(new Dimension(130, 25));
        volumeSlider.setOpaque(false);
        volumeSlider.setForeground(ACCENT_BLUE);
        volumeLabel.setFont(new Font("Default", Font.PLAIN, 11));
        volumeLabel.setForeground(FG_DIM);

        volumePanel.add(volumeIcon);
        volumePanel.add(volumeSlider);
        volumePanel.add(volumeLabel);

        // Pop-out button
        btnPopOut.setToolTipText("Pop out player to a separate window");
        btnPopOut.setBackground(new Color(30, 30, 35, 180));
        btnPopOut.setForeground(FG_DIM);
        btnPopOut.setFocusPainted(false);
        btnPopOut.setBorderPainted(false);
        btnPopOut.setOpaque(true);
        btnPopOut.setFont(new Font("Default", Font.PLAIN, 13));
        btnPopOut.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnPopOut.setPreferredSize(new Dimension(28, 22));
        btnPopOut.setMaximumSize(new Dimension(28, 22));
        btnPopOut.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel popOutRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        popOutRow.setOpaque(false);
        popOutRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        popOutRow.add(btnPopOut);

        playerContent.add(popOutRow);
        playerContent.add(Box.createVerticalStrut(2));
        playerContent.add(albumArtLabel);
        playerContent.add(Box.createVerticalStrut(6));
        playerContent.add(nowPlayingLabel);
        playerContent.add(Box.createVerticalStrut(6));
        playerContent.add(progressPanel);
        playerContent.add(Box.createVerticalStrut(2));
        playerContent.add(buttonsPanel);
        playerContent.add(Box.createVerticalStrut(2));
        playerContent.add(volumePanel);

        playerFrame.setContentPane(playerContent);
        playerFrame.setSize(320, 390);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        playerFrame.setLocation((screen.width - 600) / 2, screen.height - 400);
        playerFrame.setOpaque(false);
        playerFrame.setFrameIcon(null);
        playerFrame.setVisible(true);
    }

    void styleControlButton(JButton button) {
        button.setBackground(new Color(40, 40, 45, 180));
        button.setForeground(FG_BRIGHT);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(50, 40));
        button.setFont(new Font("Default", Font.PLAIN, 12));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
    void addActionListeners() {
        btnExit.addActionListener(e -> {
            if (progressTimer != null) progressTimer.stop();
            if (ac.getcurrentClip() != null) ac.getcurrentClip().close();
            mainFrame.dispose();
        });

        btnPopOut.addActionListener(e -> popOutPlayer());

        volumeSlider.addChangeListener(e -> {
            volumeLabel.setText(volumeSlider.getValue() + "%");
            ac.setVolume(((float) volumeSlider.getValue() * 0.3f) / 100f);
        });

        btnPlayPause.addActionListener(e -> {
            LocalTime time = LocalTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
            String formattedTime = time.format(formatter);
            if (ac.clipPaused) {
                ac.resume();
                log("Resumed at " + formattedTime);
                btnPlayPause.setText("⏸");
                progressTimer.start();
            } else {
                ac.pause();
                log("Paused at " + formattedTime);
                btnPlayPause.setText("▶");
                progressTimer.stop();
            }
        });

        btnPrevious.addActionListener(e -> playPreviousSong());
        btnNext.addActionListener(e -> playNextSong());

        btnShuffle.addActionListener(e -> {
            shuffleEnabled = !shuffleEnabled;
            shuffleHistory.clear();
            btnShuffle.setBackground(shuffleEnabled
                    ? new Color(70, 130, 180, 220)
                    : new Color(40, 40, 45, 180));
            log("Shuffle " + (shuffleEnabled ? "on" : "off"));
        });

        btnLoop.addActionListener(e -> {
            loopEnabled = !loopEnabled;
            btnLoop.setBackground(loopEnabled
                    ? new Color(70, 130, 180, 220)
                    : new Color(40, 40, 45, 180));
            log("Loop " + (loopEnabled ? "on" : "off"));
        });

        final boolean[] isScrubbing = {false};

        progressBar.addChangeListener(e -> {
            Clip clip = ac.getcurrentClip();
            if (clip == null) return;
            if (progressBar.getValueIsAdjusting()) {
                isScrubbing[0] = true;
                ac.setVolume(0.0001f);
                long total = clip.getMicrosecondLength();
                ac.setPosition((total * progressBar.getValue()) / 100);
            } else if (isScrubbing[0]) {
                isScrubbing[0] = false;
                long total = clip.getMicrosecondLength();
                ac.setPosition((total * progressBar.getValue()) / 100);
                ac.setVolume(((float) volumeSlider.getValue() * 0.3f) / 100f);
            }
        });

        sm.displayedPlaylist.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String selected = sm.displayedPlaylist.getSelectedValue();
            if (selected == null) return;

            // Track index in shuffle history for backwards navigation
            int selIdx = sm.displayedPlaylist.getSelectedIndex();
            if (selIdx >= 0) shuffleHistory.addLast(selIdx);

            nowPlayingLabel.setText("♪ " + selected);
            log("Now playing: " + selected);

            // Resolve cover art via the Song object so per-song coverPath overrides
            // (set by yt-dlp thumbnail download) are respected.  Both album and
            // playlist songs are handled: for albums coverPath is null and
            // getSongCover() falls back to the album-folder cover automatically.
            Song matchedSong = null;
            String currentName = sm.getCurrentName();
            if (sm.currentIsAlbum) {
                ArrayList<Song> albSongs = sm.getSongsInAlbum(currentName);
                for (Song s : albSongs) {
                    if (s.name.equals(selected)) { matchedSong = s; break; }
                }
            } else {
                ArrayList<Song> plSongs = sm.getSongsInPlaylist(currentName);
                for (Song s : plSongs) {
                    if (s.name.equals(selected)) { matchedSong = s; break; }
                }
            }
            if (matchedSong != null) {
                updateAlbumArtForSong(matchedSong);
            } else {
                updateAlbumArt(currentName);
            }

            String fullPath = sm.getSongPath(selected);
            if (fullPath != null) {
                if (progressTimer != null) progressTimer.stop();
                ac.playSound(fullPath);
                Timer startDelay = new Timer(150, evt -> {
                    btnPlayPause.setText("⏸");
                    progressTimer.start();
                });
                startDelay.setRepeats(false);
                startDelay.start();
            } else {
                log("ERROR: Could not find path for: " + selected);
            }
        });

        menuItemAddSongs.addActionListener(e -> {
            addSongsFrame.refreshPlaylistBox();
            openInternalFrame(addSongsFrame);
            log("Add Songs opened");
        });

        menuItemEditPlaylist.addActionListener(e -> {
            playlistEditorFrame.refresh();
            openInternalFrame(playlistEditorFrame);
            log("Playlist editor opened");
        });

        menuItemAbout.addActionListener(e -> openInternalFrame(helpFrame));
        menuItemConsole.addActionListener(e -> {
            openInternalFrame(consoleFrame);
            log("goyOS verstion 3.12.1");}
        );
        menuItemPlayer.addActionListener(e -> {
            if (popOutWindow != null && popOutWindow.isActive()) {
                popOutWindow.dock();
            } else {
                openInternalFrame(playerFrame);
            }
        });

        progressTimer = new Timer(100, e -> updateProgressBar());

        ac.setOnSongEnd(() -> SwingUtilities.invokeLater(this::playNextSong));
    }
    void populateAlbumMenu() {
        menuAlbums.removeAll();
        ArrayList<String> names = sm.getAlbumNames();

        if (names.isEmpty()) {
            JMenuItem none = new JMenuItem("No albums found");
            none.setEnabled(false);
            menuAlbums.add(none);
        } else {
            for (String name : names) {
                JMenuItem item = new JMenuItem(name);
                item.addActionListener(e -> openOrRaisePlaylistFrame(name, false));
                menuAlbums.add(item);
            }
        }

        menuAlbums.addSeparator();
        JMenuItem addItem = new JMenuItem("Add Songs (Download)");
        addItem.addActionListener(e -> {
            addSongsFrame.refreshPlaylistBox();
            openInternalFrame(addSongsFrame);
        });
        menuAlbums.add(addItem);
    }

    void populatePlaylistMenu() {
        menuPlaylists.removeAll();
        ArrayList<String> names = sm.getPlaylistNames();

        if (names.isEmpty()) {
            JMenuItem none = new JMenuItem("No playlists yet");
            none.setEnabled(false);
            menuPlaylists.add(none);
        } else {
            for (String name : names) {
                JMenuItem item = new JMenuItem(name);
                item.addActionListener(e -> openOrRaisePlaylistFrame(name, true));
                menuPlaylists.add(item);
            }
        }

        menuPlaylists.addSeparator();
        menuPlaylists.add(menuItemEditPlaylist);
    }

    /**
     * Opens a playlist/album in its own JInternalFrame.
     * If a frame for that name already exists and is just closed/iconified, it re-shows it.
     * Multiple different playlists/albums can be open simultaneously.
     */
    void openOrRaisePlaylistFrame(String name, boolean isPlaylist) {
        String key = (isPlaylist ? "pl:" : "al:") + name;

        JInternalFrame frame = playlistFrames.get(key);

        if (frame == null || !frame.isDisplayable()) {
            frame = buildPlaylistFrame(name, isPlaylist);
            playlistFrames.put(key, frame);
            desktop.add(frame);
        }

        // Switch the shared list model to this album/playlist so the frame shows the right songs
        if (isPlaylist) {
            sm.switchToPlaylist(name);
        } else {
            sm.switchToAlbum(name);
        }

        nowPlayingLabel.setText("No song playing");
        log((isPlaylist ? "Switched to playlist: " : "Browsing album: ") + name);

        frame.setVisible(true);
        if (frame.isIcon()) {
            try { frame.setIcon(false); } catch (Exception ignored) {}
        }
        openInternalFrame(frame);
    }

    /** Builds a fresh playlist/album JInternalFrame with its own song list. */
    private JInternalFrame buildPlaylistFrame(String name, boolean isPlaylist) {
        String title = (isPlaylist ? "Playlist — " : "Album — ") + name;
        JInternalFrame frame = new JInternalFrame(title, true, true, true, true);

        // Give each frame its own independent JList backed by the sm model for this name
        DefaultListModel<String> model = isPlaylist
                ? sm.playlistModels.get(name)
                : sm.albumModels.get(name);

        JList<String> songList = new JList<>(model != null ? model : new DefaultListModel<>());
        songList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        songList.setFont(new Font("Default", Font.PLAIN, 14));
        songList.setBackground(TRANS_MID);
        songList.setForeground(FG_BRIGHT);
        songList.setSelectionBackground(new Color(70, 130, 180, 200));
        songList.setSelectionForeground(Color.WHITE);
        songList.setOpaque(true);

        songList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String selected = songList.getSelectedValue();
            if (selected == null) return;

            // Switch the shared SM state to this frame's source, then look up the path
            if (isPlaylist) sm.switchToPlaylist(name);
            else sm.switchToAlbum(name);

            // Also sync the shared displayedPlaylist so prev/next work correctly
            sm.displayedPlaylist.setModel(model != null ? model : new DefaultListModel<>());
            sm.displayedPlaylist.setSelectedValue(selected, false);

            nowPlayingLabel.setText("♪ " + selected);
            log("Now playing: " + selected);

            // Resolve cover art: prefer the song's own coverPath override, then
            // the album-folder cover.  getSongCover() handles both cases.
            Song matchedSong = null;
            if (isPlaylist) {
                ArrayList<Song> plSongs = sm.getSongsInPlaylist(name);
                for (Song s : plSongs) {
                    if (s.name.equals(selected)) { matchedSong = s; break; }
                }
            } else {
                ArrayList<Song> albSongs = sm.getSongsInAlbum(name);
                for (Song s : albSongs) {
                    if (s.name.equals(selected)) { matchedSong = s; break; }
                }
            }
            if (matchedSong != null) {
                updateAlbumArtForSong(matchedSong);
            } else {
                // Fallback: no Song object found, use album-folder cover
                updateAlbumArt(name);
            }

            String fullPath = sm.getSongPath(selected);
            if (fullPath != null) {
                if (progressTimer != null) progressTimer.stop();
                ac.playSound(fullPath);
                Timer startDelay = new Timer(150, evt -> {
                    btnPlayPause.setText("⏸");
                    progressTimer.start();
                });
                startDelay.setRepeats(false);
                startDelay.start();
            } else {
                log("ERROR: Could not find path for: " + selected);
            }
        });

        JScrollPane scroll = new JScrollPane(songList);
        scroll.getViewport().setBackground(TRANS_MID);
        scroll.getViewport().setOpaque(true);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);

        JPanel content = transPanel(new BorderLayout(), new Color(15, 15, 18, 150));
        content.add(scroll, BorderLayout.CENTER);

        frame.setContentPane(content);
        frame.setSize(300, 300);
        // Offset each new frame slightly so they don't stack perfectly on top of each other
        int offset = (playlistFrames.size() % 8) * 22;
        frame.setLocation(50 + offset, 50 + offset);
        frame.setOpaque(false);
        frame.setFrameIcon(null);

        // Apply the current theme to this new frame right away
        if (themeManager != null) themeManager.applyToFrame(frame);

        if (connectorLayer != null)
            connectorLayer.connect(playerFrame, frame);
        frame.addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
            @Override
            public void internalFrameClosed(javax.swing.event.InternalFrameEvent e) {
                if (connectorLayer != null) connectorLayer.disconnect(frame);
            }
        });
        return frame;
    }

    void refreshPlaylistMenu() {
        populateAlbumMenu();
        populatePlaylistMenu();
        menuAlbums.revalidate();    menuAlbums.repaint();
        menuPlaylists.revalidate(); menuPlaylists.repaint();
    }

    void addItemsToContainers() {
        topMenuBar.add(menuAlbums);
        topMenuBar.add(menuPlaylists);
        topMenuBar.add(menuHelp);
        topMenuBar.add(Box.createHorizontalGlue());
        topMenuBar.add(btnExit);

        menuHelp.add(menuItemAbout);
        menuHelp.addSeparator();
        menuHelp.add(menuItemConsole);
        menuHelp.addSeparator();
        menuHelp.add(menuItemPlayer);
        // Theme switcher lives at the bottom of the Help menu
        themeManager.buildThemeMenu(menuHelp);

        desktop.add(helpFrame);
        desktop.add(consoleFrame);
        desktop.add(playerFrame);
        desktop.add(addSongsFrame);
        desktop.add(playlistEditorFrame);
    }

    void openInternalFrame(JInternalFrame frame) {
        frame.setVisible(true);
        try {
            frame.setSelected(true);
        } catch (java.beans.PropertyVetoException ex) {
            ex.printStackTrace();
        }
    }

    void log(String message) {
        System.out.println(message);
        SwingUtilities.invokeLater(() -> {
            consoleOutput.append("> " + message + "\n");
            consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
        });
    }

    void updateProgressBar() {
        Clip clip = ac.getcurrentClip();
        if (clip != null && clip.isOpen()) {
            long current = clip.getMicrosecondPosition();
            long total   = clip.getMicrosecondLength();
            if (total > 0) {
                progressBar.setValue((int) ((current * 100) / total));
                currentTimeLabel.setText(formatTime(current / 1_000_000));
                totalTimeLabel.setText(formatTime(total   / 1_000_000));
            }
        }
    }

    String formatTime(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    void playPreviousSong() {
        if (shuffleEnabled && !shuffleHistory.isEmpty()) {
            // Pop the last played index from history
            shuffleHistory.pollLast(); // remove the current song we came from
            if (!shuffleHistory.isEmpty()) {
                int prev = shuffleHistory.peekLast();
                sm.displayedPlaylist.setSelectedIndex(prev);
                return;
            }
        }
        int i = sm.displayedPlaylist.getSelectedIndex();
        if (i > 0) sm.displayedPlaylist.setSelectedIndex(i - 1);
    }

    void playNextSong() {
        int size = sm.displayedPlaylist.getModel().getSize();
        int i    = sm.displayedPlaylist.getSelectedIndex();

        if (loopEnabled && i >= 0) {
            // Re-trigger playback of the same song by briefly deselecting then reselecting.
            // We do this by re-firing the same index via the selection listener path.
            sm.displayedPlaylist.clearSelection();
            sm.displayedPlaylist.setSelectedIndex(i);
            return;
        }

        if (shuffleEnabled && size > 1) {
            java.util.Random rng = new java.util.Random();
            int next;
            do { next = rng.nextInt(size); } while (next == i && size > 1);
            shuffleHistory.addLast(next);
            sm.displayedPlaylist.setSelectedIndex(next);
            return;
        }

        if (i < size - 1) sm.displayedPlaylist.setSelectedIndex(i + 1);
    }
    /**
     * Loads and displays the cover image for the given album in the player frame.
     * Scales the image to fill the art panel while preserving aspect ratio.
     * Skips the disk read if the same album cover is already showing.
     */
    void updateAlbumArt(String albumName) {
        // FIX: only skip if the same album is already showing AND the icon loaded successfully.
        // Previously, `equals(loadedCoverAlbum)` would skip the update even when the icon
        // was null (e.g. first song in a playlist where the cover had never actually loaded).
        if (albumName == null) return;
        if (albumName.equals(loadedCoverAlbum) && albumArtLabel.getIcon() != null) return;
        loadedCoverAlbum = albumName;

        java.io.File coverFile = sm.getAlbumCover(albumName);
        if (coverFile == null) {
            albumArtLabel.setIcon(null);
            albumArtLabel.setText("♪");
            return;
        }

        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(coverFile);
            if (img == null) {
                albumArtLabel.setIcon(null);
                albumArtLabel.setText("♪");
                return;
            }

            // Crop to centered square first so widescreen YT thumbnails don't
            // squash album art that lives in the middle of a 16:9 frame.
            int w = img.getWidth();
            int h = img.getHeight();
            if (w != h) {
                int side = Math.min(w, h);
                int x    = (w - side) / 2;
                int y    = (h - side) / 2;
                img = img.getSubimage(x, y, side, side);
            }

            int size = albumArtLabel.getWidth() > 0 ? albumArtLabel.getWidth() : 180;
            Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            albumArtLabel.setIcon(new ImageIcon(scaled));
            albumArtLabel.setText("");
        } catch (java.io.IOException ex) {
            albumArtLabel.setIcon(null);
            albumArtLabel.setText("♪");
            log("Could not load cover art: " + ex.getMessage());
        }
    }

    /**
     * Variant of {@link #updateAlbumArt} that accepts a full {@link Song} object.
     *
     * Resolution order (mirrors {@link SongManagement#getSongCover}):
     *   1. song.coverPath — a per-song image override stored in the playlist .m3u
     *   2. The album-folder cover for song.sourceName
     *   3. Nothing (label shows "♪" placeholder)
     *
     * A per-song cover is treated as its own cache key so switching between two
     * playlist tracks that share a sourceName album but have different individual
     * covers still triggers a redraw.
     */
    void updateAlbumArtForSong(Song song) {
        if (song == null) return;

        // Build a stable cache key that distinguishes per-song covers from the
        // album-level cover even when sourceName is the same.
        String cacheKey = (song.coverPath != null && !song.coverPath.isEmpty())
                ? "file:" + song.coverPath
                : "album:" + song.sourceName;

        if (cacheKey.equals(loadedCoverAlbum) && albumArtLabel.getIcon() != null) return;
        loadedCoverAlbum = cacheKey;

        java.io.File coverFile = sm.getSongCover(song);
        if (coverFile == null) {
            albumArtLabel.setIcon(null);
            albumArtLabel.setText("♪");
            return;
        }

        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(coverFile);
            if (img == null) {
                albumArtLabel.setIcon(null);
                albumArtLabel.setText("♪");
                return;
            }

            // Crop to centred square (same logic as updateAlbumArt)
            int w = img.getWidth(), h = img.getHeight();
            if (w != h) {
                int side = Math.min(w, h);
                img = img.getSubimage((w - side) / 2, (h - side) / 2, side, side);
            }

            int size = albumArtLabel.getWidth() > 0 ? albumArtLabel.getWidth() : 180;
            Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            albumArtLabel.setIcon(new ImageIcon(scaled));
            albumArtLabel.setText("");
        } catch (java.io.IOException ex) {
            albumArtLabel.setIcon(null);
            albumArtLabel.setText("♪");
            log("Could not load cover art: " + ex.getMessage());
        }
    }
    // 1000 :o
    void wireConnectors() {
        connectorLayer = ConnectorLayer.install(desktop);
        connectorLayer.connect(playerFrame, consoleFrame);
        connectorLayer.connect(playerFrame, addSongsFrame);
        connectorLayer.connect(playerFrame, playlistEditorFrame);
        connectorLayer.connect(playerFrame, helpFrame);
        themeManager.setConnectorLayer(connectorLayer);
        connectorLayer.setCableColor(themeManager.getCurrent().currentConnColor);
    }
}