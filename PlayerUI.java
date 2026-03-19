import javax.swing.*;
import javax.sound.sampled.Clip;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    // Alpha colors — painted manually via paintComponent overrides
    private static final Color TRANS_CONTROL = new Color(10, 10, 12, 160);
    private static final Color TRANS_MENUBAR = new Color(10, 10, 12, 255); // solid black
    private static final Color TRANS_MID     = new Color(20, 20, 23, 140);
    private static final Color TRANS_CONSOLE = new Color(10, 10, 12, 160);
    private static final Color ACCENT_BLUE   = new Color(70, 130, 180);
    private static final Color FG_BRIGHT     = new Color(220, 220, 220);
    private static final Color FG_DIM        = new Color(180, 180, 180);
    private static final Color BORDER_COLOR  = new Color(90, 90, 95, 100);

    // Panel that paints a translucent fill then lets children draw on top
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

    // Menu bar with solid black paintComponent
    JMenuBar topMenuBar = new JMenuBar() {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(TRANS_MENUBAR);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    };

    // ── menus ─────────────────────────────────────────────────────────────────
    JMenu   menuAlbums    = new JMenu("Albums");     // NEW — physical folders
    JMenu   menuPlaylists = new JMenu("Playlists");  // user-created collections
    JMenu   menuHelp      = new JMenu("Help");
    JButton btnExit       = new JButton("X");

    JMenuItem menuItemAddSongs    = new JMenuItem("Add Songs");
    JMenuItem menuItemEditPlaylist = new JMenuItem("Edit Playlists…"); // NEW
    JMenuItem menuItemAbout       = new JMenuItem("About/Help");
    JMenuItem menuItemConsole     = new JMenuItem("Console");
    JMenuItem menuItemPlayer      = new JMenuItem("Player");

    JInternalFrame playlistFrame  = new JInternalFrame("Playlist",    true, true, true, true);
    JInternalFrame helpFrame      = new JInternalFrame("About/Help",  true, true, true, true);
    JInternalFrame consoleFrame   = new JInternalFrame("Console </>", true, true, true, true);
    JInternalFrame playerFrame    = new JInternalFrame("Player",      true, true, true, true);
    AddSongsFrame      addSongsFrame      = null; // created lazily in setUi()
    PlaylistEditorFrame playlistEditorFrame = null; // NEW — created lazily in setUi()

    JTextArea   consoleOutput  = new JTextArea();
    JScrollPane consoleScroll  = new JScrollPane(consoleOutput);
    JScrollPane playlistScroll = new JScrollPane(sm.displayedPlaylist);

    JButton btnPrevious  = new JButton("◁");
    JButton btnPlayPause = new JButton("▶");
    JButton btnNext      = new JButton("▷");

    JSlider volumeSlider   = new JSlider(0, 100);
    JLabel  volumeLabel    = new JLabel("50%");

    JSlider progressBar      = new JSlider(0, 100);
    JLabel  currentTimeLabel = new JLabel("0:00");
    JLabel  totalTimeLabel   = new JLabel("0:00");
    JLabel  nowPlayingLabel  = new JLabel("No song playing");

    Timer progressTimer;

    void main(String[] args) {
        setUi();
        sm.scrapeAndADD();
        populateAlbumMenu();
        populatePlaylistMenu();

        try {
            backgroundImage = ImageIO.read(new File("/home/nyx/Documents/music/walledcitytweak.png"));
            desktop.repaint();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    void setUi() {
        frameSetup();
        addSongsFrame       = new AddSongsFrame(this);
        playlistEditorFrame = new PlaylistEditorFrame(this);  // NEW
        configInternalFrames();
        configPlayerFrame();
        addActionListeners();
        addItemsToContainers();
    }

    void frameSetup() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        UIManager.put("InternalFrame.activeTitleBackground",   new Color(45, 80, 50, 180));
        UIManager.put("InternalFrame.activeTitleForeground",   new Color(180, 240, 190));
        UIManager.put("InternalFrame.inactiveTitleBackground", new Color(35, 60, 40, 150));
        UIManager.put("InternalFrame.inactiveTitleForeground", new Color(120, 170, 130));
        UIManager.put("InternalFrame.borderColor",             BORDER_COLOR);

        UIManager.put("MenuBar.background",           new Color(10, 10, 12));
        UIManager.put("Menu.background",              new Color(20, 20, 23));
        UIManager.put("Menu.foreground",              FG_BRIGHT);
        UIManager.put("Menu.selectionBackground",     ACCENT_BLUE);
        UIManager.put("Menu.selectionForeground",     Color.WHITE);
        UIManager.put("MenuItem.background",          new Color(20, 20, 23));
        UIManager.put("MenuItem.foreground",          FG_BRIGHT);
        UIManager.put("MenuItem.selectionBackground", ACCENT_BLUE);
        UIManager.put("MenuItem.selectionForeground", Color.WHITE);
        UIManager.put("PopupMenu.background",         new Color(20, 20, 23));
        UIManager.put("Separator.foreground",         BORDER_COLOR);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        mainFrame.setSize(screen.width - 300, screen.height - 100);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainContainer = new JPanel(new BorderLayout());
        mainContainer.setOpaque(true);
        mainContainer.setBackground(new Color(20, 20, 23));
        mainContainer.add(desktop, BorderLayout.CENTER);
        mainFrame.setContentPane(mainContainer);
        mainFrame.setJMenuBar(topMenuBar);

        topMenuBar.setOpaque(false);
        topMenuBar.setForeground(FG_BRIGHT);
        topMenuBar.setBorderPainted(false);

        desktop.setOpaque(true);
        desktop.setBackground(new Color(20, 20, 23));

        btnExit.setFont(new Font("Default", Font.PLAIN, 14));
        btnExit.setBackground(new Color(50, 50, 53));
        btnExit.setForeground(FG_BRIGHT);
        btnExit.setFocusPainted(false);
        btnExit.setBorderPainted(false);
        btnExit.setOpaque(true);

        menuAlbums.setForeground(FG_BRIGHT);
        menuAlbums.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 2));
        menuPlaylists.setForeground(FG_BRIGHT);
        menuPlaylists.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 2));
        menuHelp.setForeground(FG_BRIGHT);
        menuHelp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 2));

        mainFrame.setVisible(true);
    }

    void configInternalFrames() {
        // --- playlist / album song list ---
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

        JPanel playlistContent = transPanel(new BorderLayout(), new Color(15, 15, 18, 150));
        playlistContent.add(playlistScroll, BorderLayout.CENTER);
        playlistFrame.setContentPane(playlistContent);
        playlistFrame.setSize(300, 300);
        playlistFrame.setLocation(50, 50);
        playlistFrame.setOpaque(false);
        playlistFrame.setFrameIcon(null);
        playlistFrame.setVisible(false);

        // --- help ---
        JPanel helpContent = transPanel(new BorderLayout(), new Color(15, 15, 18, 150));
        JLabel helpText = new JLabel(
                "<html><center>Max's Music Player<br>"
                        + "Albums menu — browse downloaded albums<br>"
                        + "Playlists menu — your custom playlists<br>"
                        + "Use Console for debugging</center></html>",
                SwingConstants.CENTER
        );
        helpText.setForeground(FG_BRIGHT);
        helpContent.add(helpText, BorderLayout.CENTER);
        helpFrame.setContentPane(helpContent);
        helpFrame.setSize(300, 200);
        helpFrame.setLocation(80, 50);
        helpFrame.setOpaque(false);
        helpFrame.setFrameIcon(null);
        helpFrame.setVisible(false);

        // --- console ---
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

        JPanel consoleContent = transPanel(new BorderLayout(), TRANS_CONSOLE);
        consoleContent.add(consoleScroll, BorderLayout.CENTER);
        consoleFrame.setContentPane(consoleContent);
        consoleFrame.setSize(500, 200);
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
        btnPlayPause.setPreferredSize(new Dimension(50, 45));
        btnPlayPause.setFont(new Font("Default", Font.PLAIN, 14));

        buttonsPanel.add(btnPrevious);
        buttonsPanel.add(btnPlayPause);
        buttonsPanel.add(btnNext);

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

        playerContent.add(Box.createVerticalStrut(4));
        playerContent.add(nowPlayingLabel);
        playerContent.add(Box.createVerticalStrut(6));
        playerContent.add(progressPanel);
        playerContent.add(Box.createVerticalStrut(2));
        playerContent.add(buttonsPanel);
        playerContent.add(Box.createVerticalStrut(2));
        playerContent.add(volumePanel);

        playerFrame.setContentPane(playerContent);
        playerFrame.setSize(320, 185);
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

        volumeSlider.addChangeListener(e -> {
            volumeLabel.setText(volumeSlider.getValue() + "%");
            // Map 0–100 slider to 0.0–0.3 linear volume range
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

        // Tracks whether the user is actively dragging the progress bar
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

            nowPlayingLabel.setText("♪ " + selected.replace(".wav", ""));
            log("Now playing: " + selected);

            String fullPath = sm.getSongPath(selected);
            if (fullPath != null) {
                if (progressTimer != null) progressTimer.stop();
                if (ac.getcurrentClip() != null) ac.getcurrentClip().close();
                new Thread(() -> ac.playSound(fullPath)).start();
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

        // NEW — open playlist editor
        menuItemEditPlaylist.addActionListener(e -> {
            playlistEditorFrame.refresh();
            openInternalFrame(playlistEditorFrame);
            log("Playlist editor opened");
        });

        menuItemAbout.addActionListener(e -> openInternalFrame(helpFrame));
        menuItemConsole.addActionListener(e -> openInternalFrame(consoleFrame));
        menuItemPlayer.addActionListener(e -> openInternalFrame(playerFrame));

        progressTimer = new Timer(100, e -> updateProgressBar());

        ac.setOnSongEnd(() -> SwingUtilities.invokeLater(this::playNextSong));
    }

    // fill menus n shit

    /** Rebuilds the Albums menu from SongManagement. */
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
                item.addActionListener(e -> {
                    sm.switchToAlbum(name);
                    playlistFrame.setTitle("Album — " + name);
                    nowPlayingLabel.setText("No song playing");
                    openInternalFrame(playlistFrame);
                    log("Browsing album: " + name);
                });
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

    /** Rebuilds the Playlists menu from SongManagement. */
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
                item.addActionListener(e -> {
                    sm.switchToPlaylist(name);
                    playlistFrame.setTitle("Playlist — " + name);
                    nowPlayingLabel.setText("No song playing");
                    openInternalFrame(playlistFrame);
                    log("Switched to playlist: " + name);
                });
                menuPlaylists.add(item);
            }
        }

        menuPlaylists.addSeparator();
        menuPlaylists.add(menuItemEditPlaylist); // open editor
    }

    /** Called after a download or playlist edit to rebuild both menus live. */
    void refreshPlaylistMenu() {
        populateAlbumMenu();
        populatePlaylistMenu();
        menuAlbums.revalidate();   menuAlbums.repaint();
        menuPlaylists.revalidate(); menuPlaylists.repaint();
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    void addItemsToContainers() {
        topMenuBar.add(menuAlbums);    // NEW — first
        topMenuBar.add(menuPlaylists);
        topMenuBar.add(menuHelp);
        topMenuBar.add(Box.createHorizontalGlue());
        topMenuBar.add(btnExit);

        menuHelp.add(menuItemAbout);
        menuHelp.addSeparator();
        menuHelp.add(menuItemConsole);
        menuHelp.addSeparator();
        menuHelp.add(menuItemPlayer);

        desktop.add(playlistFrame);
        desktop.add(helpFrame);
        desktop.add(consoleFrame);
        desktop.add(playerFrame);
        desktop.add(addSongsFrame);
        desktop.add(playlistEditorFrame); // NEW
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
        int i = sm.displayedPlaylist.getSelectedIndex();
        if (i > 0) sm.displayedPlaylist.setSelectedIndex(i - 1);
    }

    void playNextSong() {
        int i = sm.displayedPlaylist.getSelectedIndex();
        if (i < sm.displayedPlaylist.getModel().getSize() - 1)
            sm.displayedPlaylist.setSelectedIndex(i + 1);
    }
}