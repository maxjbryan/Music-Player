import javax.swing.*;
import javax.sound.sampled.Clip;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 *
 * yea its the pop out playah twin
 * pop out at 1 in the morning ts kinda lifenever boring
 */
public class PopOutPlayerWindow {

    private final PlayerUI ui;
    private JFrame frame;

    // ── Mirrored controls (independent Swing components) ─────────────────────
    private JLabel  popAlbumArt;
    private JLabel  popNowPlaying;
    private JSlider popProgress;
    private JLabel  popCurrentTime;
    private JLabel  popTotalTime;
    private JButton popPlayPause;
    private JButton popPrev;
    private JButton popNext;
    private JButton popShuffle;
    private JButton popLoop;
    private JSlider popVolume;
    private JLabel  popVolumeLabel;

    /** Timer that syncs the mirrored progress bar with the real clip. */
    private Timer syncTimer;

    /** Whether the pop-out is currently showing. */
    private boolean active = false;

    public PopOutPlayerWindow(PlayerUI ui) {
        this.ui = ui;
    }

    public boolean isActive() { return active; }

    // ── Show / hide ───────────────────────────────────────────────────────────

    public void show() {
        if (active && frame != null && frame.isVisible()) {
            frame.toFront();
            return;
        }

        buildFrame();
        syncFromMain();   // pull current state before showing
        frame.setVisible(true);
        active = true;
        syncTimer.start();

        // Hide the internal playerFrame while popped out so there's no duplicate
        ui.playerFrame.setVisible(false);
        ui.log("Player popped out to separate window");
    }

    public void dock() {
        active = false;
        if (syncTimer != null) syncTimer.stop();
        if (frame != null) frame.dispose();
        frame = null;

        // Restore the internal frame
        ui.playerFrame.setVisible(true);
        try { ui.playerFrame.setSelected(true); } catch (Exception ignored) {}
        // Re-sync main player art label from the current song
        ui.albumArtLabel.repaint();
        ui.log("Player docked back to main window");
    }

    // ── Frame construction ────────────────────────────────────────────────────

    private void buildFrame() {
        frame = new JFrame("Player — Pop-out");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dock(); }
        });
        frame.setAlwaysOnTop(true);
        frame.setResizable(true);
        frame.setUndecorated(false);

        JPanel root = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(14, 14, 17));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // ── Album art ────────────────────────────────────────────────────────
        popAlbumArt = new JLabel("♪");
        popAlbumArt.setHorizontalAlignment(SwingConstants.CENTER);
        popAlbumArt.setVerticalAlignment(SwingConstants.CENTER);
        popAlbumArt.setFont(new Font("Default", Font.PLAIN, 48));
        popAlbumArt.setForeground(new Color(80, 80, 90));
        popAlbumArt.setOpaque(true);
        popAlbumArt.setBackground(new Color(18, 18, 21));
        popAlbumArt.setBorder(BorderFactory.createLineBorder(new Color(90, 90, 95, 100)));
        popAlbumArt.setPreferredSize(new Dimension(200, 200));
        popAlbumArt.setMinimumSize(new Dimension(200, 200));
        popAlbumArt.setMaximumSize(new Dimension(200, 200));
        popAlbumArt.setAlignmentX(Component.CENTER_ALIGNMENT);

        // ── Now playing ──────────────────────────────────────────────────────
        popNowPlaying = new JLabel("No song playing");
        popNowPlaying.setFont(new Font("Default", Font.BOLD, 13));
        popNowPlaying.setForeground(new Color(220, 220, 220));
        popNowPlaying.setAlignmentX(Component.CENTER_ALIGNMENT);

        // ── Progress bar ─────────────────────────────────────────────────────
        popCurrentTime = new JLabel("0:00");
        popCurrentTime.setForeground(new Color(180, 180, 180));
        popCurrentTime.setFont(new Font("Default", Font.PLAIN, 11));

        popTotalTime = new JLabel("0:00");
        popTotalTime.setForeground(new Color(180, 180, 180));
        popTotalTime.setFont(new Font("Default", Font.PLAIN, 11));

        popProgress = new JSlider(0, 100, 0);
        popProgress.setOpaque(false);
        popProgress.setForeground(PlayerUI.ACCENT_BLUE);
        popProgress.setPaintTicks(false);
        popProgress.setPaintLabels(false);

        final boolean[] scrubbing = {false};
        popProgress.addChangeListener(e -> {
            Clip clip = ui.ac.getcurrentClip();
            if (clip == null) return;
            if (popProgress.getValueIsAdjusting()) {
                scrubbing[0] = true;
                ui.ac.setVolume(0.0001f);
                ui.ac.setPosition((clip.getMicrosecondLength() * popProgress.getValue()) / 100);
                // keep main progress bar in sync visually during scrub
                ui.progressBar.setValue(popProgress.getValue());
            } else if (scrubbing[0]) {
                scrubbing[0] = false;
                ui.ac.setPosition((clip.getMicrosecondLength() * popProgress.getValue()) / 100);
                ui.ac.setVolume(((float) popVolume.getValue() * 0.3f) / 100f);
            }
        });

        JPanel progressRow = new JPanel(new BorderLayout(5, 0));
        progressRow.setOpaque(false);
        progressRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        progressRow.add(popCurrentTime, BorderLayout.WEST);
        progressRow.add(popProgress,    BorderLayout.CENTER);
        progressRow.add(popTotalTime,   BorderLayout.EAST);

        // ── Transport buttons ────────────────────────────────────────────────
        popPrev      = makeControlBtn("◁");
        popPlayPause = makeControlBtn("▶");
        popPlayPause.setPreferredSize(new Dimension(50, 45));
        popPlayPause.setFont(new Font("Default", Font.PLAIN, 14));
        popNext      = makeControlBtn("▷");
        popShuffle   = makeControlBtn("⇄");
        popShuffle.setPreferredSize(new Dimension(55, 34));
        popShuffle.setMinimumSize(new Dimension(55, 34));
        popShuffle.setToolTipText("Shuffle");
        popLoop      = makeControlBtn("↻");
        popLoop.setPreferredSize(new Dimension(55, 34));
        popLoop.setMinimumSize(new Dimension(55, 34));
        popLoop.setToolTipText("Loop current song");

        // Reflect current shuffle / loop state
        if (ui.shuffleEnabled) popShuffle.setBackground(new Color(70, 130, 180, 220));
        if (ui.loopEnabled)    popLoop.setBackground(new Color(70, 130, 180, 220));

        popPrev.addActionListener(e -> {
            ui.playPreviousSong();
            popPlayPause.setText("⏸");
        });
        popNext.addActionListener(e -> {
            ui.playNextSong();
            popPlayPause.setText("⏸");
        });
        popPlayPause.addActionListener(e -> {
            LocalTime time = LocalTime.now();
            String fmt = time.format(DateTimeFormatter.ofPattern("hh:mm a"));
            if (ui.ac.clipPaused) {
                ui.ac.resume();
                ui.log("Resumed at " + fmt);
                popPlayPause.setText("⏸");
                ui.btnPlayPause.setText("⏸");
                ui.progressTimer.start();
            } else {
                ui.ac.pause();
                ui.log("Paused at " + fmt);
                popPlayPause.setText("▶");
                ui.btnPlayPause.setText("▶");
                ui.progressTimer.stop();
            }
        });
        popShuffle.addActionListener(e -> {
            ui.btnShuffle.doClick();   // delegate to main button to keep state in sync
            popShuffle.setBackground(ui.shuffleEnabled
                    ? new Color(70, 130, 180, 220)
                    : new Color(40, 40, 45, 180));
        });
        popLoop.addActionListener(e -> {
            ui.btnLoop.doClick();
            popLoop.setBackground(ui.loopEnabled
                    ? new Color(70, 130, 180, 220)
                    : new Color(40, 40, 45, 180));
        });

        JPanel btnsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        btnsRow.setOpaque(false);
        btnsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        btnsRow.add(popShuffle);
        btnsRow.add(popPrev);
        btnsRow.add(popPlayPause);
        btnsRow.add(popNext);
        btnsRow.add(popLoop);

        // ── Volume ───────────────────────────────────────────────────────────
        JLabel volIcon = new JLabel("🔊");
        volIcon.setFont(new Font("Default", Font.PLAIN, 14));

        popVolume = new JSlider(0, 100, ui.volumeSlider.getValue());
        popVolume.setPreferredSize(new Dimension(130, 25));
        popVolume.setOpaque(false);
        popVolume.setForeground(PlayerUI.ACCENT_BLUE);

        popVolumeLabel = new JLabel(ui.volumeSlider.getValue() + "%");
        popVolumeLabel.setFont(new Font("Default", Font.PLAIN, 11));
        popVolumeLabel.setForeground(new Color(180, 180, 180));

        popVolume.addChangeListener(e -> {
            int v = popVolume.getValue();
            popVolumeLabel.setText(v + "%");
            ui.ac.setVolume((v * 0.3f) / 100f);
            // keep main slider in sync
            ui.volumeSlider.setValue(v);
            ui.volumeLabel.setText(v + "%");
        });

        JPanel volRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        volRow.setOpaque(false);
        volRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        volRow.add(volIcon);
        volRow.add(popVolume);
        volRow.add(popVolumeLabel);

        // ── Dock button ──────────────────────────────────────────────────────
        JButton dockBtn = new JButton("⊟ Dock");
        dockBtn.setBackground(new Color(40, 40, 45, 230));
        dockBtn.setForeground(new Color(200, 200, 200));
        dockBtn.setFocusPainted(false);
        dockBtn.setBorderPainted(false);
        dockBtn.setOpaque(true);
        dockBtn.setFont(new Font("Default", Font.PLAIN, 12));
        dockBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        dockBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        dockBtn.addActionListener(e -> dock());

        // ── Assemble ─────────────────────────────────────────────────────────
        root.add(Box.createVerticalStrut(4));
        root.add(popAlbumArt);
        root.add(Box.createVerticalStrut(6));
        root.add(popNowPlaying);
        root.add(Box.createVerticalStrut(6));
        root.add(progressRow);
        root.add(Box.createVerticalStrut(2));
        root.add(btnsRow);
        root.add(Box.createVerticalStrut(2));
        root.add(volRow);
        root.add(Box.createVerticalStrut(8));
        root.add(dockBtn);

        frame.setContentPane(root);
        frame.pack();
        frame.setMinimumSize(new Dimension(280, 380));

        // Position near top-right of screen
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation(screen.width - frame.getWidth() - 30, 40);

        // ── Sync timer: mirrors the main progress state at ~10 fps ───────────
        syncTimer = new Timer(100, e -> syncFromMain());
    }

    /** Pull current playback state from the main player into the pop-out controls. */
    private void syncFromMain() {
        // Now playing label
        String np = ui.nowPlayingLabel.getText();
        if (!np.equals(popNowPlaying.getText())) popNowPlaying.setText(np);

        // Play/pause button
        String ppText = ui.btnPlayPause.getText();
        if (!ppText.equals(popPlayPause.getText())) popPlayPause.setText(ppText);

        // Shuffle / loop tints
        boolean sh = ui.shuffleEnabled;
        boolean lp = ui.loopEnabled;
        Color activeTint   = new Color(70, 130, 180, 220);
        Color inactiveTint = new Color(40, 40,  45,  180);
        if (!popShuffle.getBackground().equals(sh ? activeTint : inactiveTint))
            popShuffle.setBackground(sh ? activeTint : inactiveTint);
        if (!popLoop.getBackground().equals(lp ? activeTint : inactiveTint))
            popLoop.setBackground(lp ? activeTint : inactiveTint);

        // Progress + time labels
        Clip clip = ui.ac.getcurrentClip();
        if (clip != null && clip.isOpen()) {
            long cur   = clip.getMicrosecondPosition();
            long total = clip.getMicrosecondLength();
            if (total > 0 && !popProgress.getValueIsAdjusting()) {
                int pct = (int) ((cur * 100) / total);
                popProgress.setValue(pct);
                popCurrentTime.setText(ui.formatTime(cur   / 1_000_000));
                popTotalTime.setText(ui.formatTime(total   / 1_000_000));
            }
        }

        // Volume (if main slider was changed externally)
        int mainVol = ui.volumeSlider.getValue();
        if (popVolume.getValue() != mainVol && !popVolume.getValueIsAdjusting()) {
            popVolume.setValue(mainVol);
            popVolumeLabel.setText(mainVol + "%");
        }

        // Album art — mirror the icon from the main label
        Icon mainIcon = ui.albumArtLabel.getIcon();
        if (mainIcon != popAlbumArt.getIcon()) {
            popAlbumArt.setIcon(mainIcon);
            popAlbumArt.setText(mainIcon == null ? "♪" : "");
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static JButton makeControlBtn(String label) {
        JButton b = new JButton(label);
        b.setBackground(new Color(40, 40, 45, 180));
        b.setForeground(new Color(220, 220, 220));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setPreferredSize(new Dimension(50, 40));
        b.setFont(new Font("Default", Font.PLAIN, 12));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }
}