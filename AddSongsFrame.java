import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;

/**
 * JInternalFrame that lets the user download a YouTube video or playlist
 * as .wav files into an existing or new playlist folder.
 *
 * Spawns yt-dlp in a SwingWorker so the UI never freezes.
 * Console output is streamed live into the embedded log area,
 * and also forwarded to PlayerUI.log().
 */
public class AddSongsFrame extends JInternalFrame {

    // ── palette (mirrors PlayerUI constants) ──────────────────────────────────
    private static final Color TRANS_CONTROL = new Color(10, 10, 12, 160);
    private static final Color TRANS_MID     = new Color(20, 20, 23, 140);
    private static final Color TRANS_CONSOLE = new Color(10, 10, 12, 160);
    private static final Color ACCENT_BLUE   = new Color(70, 130, 180);
    private static final Color FG_BRIGHT     = new Color(220, 220, 220);
    private static final Color FG_DIM        = new Color(180, 180, 180);
    private static final Color FG_GREEN      = new Color(180, 220, 180);
    private static final Color BORDER_COLOR  = new Color(90, 90, 95, 100);

    // ── back-references ───────────────────────────────────────────────────────
    private final PlayerUI      ui;
    private final SongManagement sm;

    // ── widgets ───────────────────────────────────────────────────────────────
    private final JTextField    urlField        = new JTextField();
    private final JComboBox<String> playlistBox = new JComboBox<>();
    private final JTextField    newNameField    = new JTextField();
    private final JCheckBox     newPlaylistCheck = new JCheckBox("New album folder");
    private final JButton       downloadBtn     = new JButton("Download");
    private final JProgressBar  progressBar     = new JProgressBar();
    private final JTextArea     logArea         = new JTextArea();
    private final JLabel        statusLabel     = new JLabel(" ");

    // ── active worker ─────────────────────────────────────────────────────────
    private SwingWorker<Void, String> activeWorker = null;

    // ─────────────────────────────────────────────────────────────────────────
    public AddSongsFrame(PlayerUI ui) {
        super("Add Songs", true, true, true, true);
        this.ui = ui;
        this.sm = ui.sm;

        buildUi();
        refreshPlaylistBox();

        setSize(480, 420);
        setFrameIcon(null);
        setOpaque(false);
        setVisible(false);
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUi() {
        // root panel — same translucent paint trick as playerFrame
        JPanel root = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(TRANS_CONTROL);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setLayout(new BorderLayout(0, 8));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(10, 12, 10, 12));

        root.add(buildFormPanel(),   BorderLayout.NORTH);
        root.add(buildLogPanel(),    BorderLayout.CENTER);
        root.add(buildBottomPanel(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildFormPanel() {
        JPanel p = transPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets  = new Insets(3, 3, 3, 3);
        g.fill    = GridBagConstraints.HORIZONTAL;
        g.weightx = 0;

        // ── URL row ──
        g.gridx = 0; g.gridy = 0;
        p.add(label("YouTube URL:"), g);

        g.gridx = 1; g.weightx = 1;
        styleTextField(urlField);
        urlField.setToolTipText("Paste a video or playlist URL");
        p.add(urlField, g);

        // ── Playlist selector row ──
        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        p.add(label("Playlist:"), g);

        g.gridx = 1; g.weightx = 1;
        styleComboBox(playlistBox);
        p.add(playlistBox, g);

        // ── New playlist checkbox + name field row ──
        g.gridx = 0; g.gridy = 2; g.weightx = 0;
        newPlaylistCheck.setOpaque(false);
        newPlaylistCheck.setForeground(FG_BRIGHT);
        newPlaylistCheck.setFocusPainted(false);
        newPlaylistCheck.setFont(new Font("Default", Font.PLAIN, 12));
        newPlaylistCheck.addActionListener(e -> toggleNewPlaylist());
        p.add(newPlaylistCheck, g);

        g.gridx = 1; g.weightx = 1;
        styleTextField(newNameField);
        newNameField.setEnabled(false);
        newNameField.setToolTipText("Name for the new playlist folder");
        newNameField.setText("New Playlist");
        p.add(newNameField, g);

        return p;
    }

    private JPanel buildLogPanel() {
        logArea.setEditable(false);
        logArea.setBackground(new Color(10, 10, 12, 200));
        logArea.setForeground(FG_GREEN);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setCaretColor(FG_GREEN);
        logArea.setBorder(new EmptyBorder(4, 6, 4, 6));
        logArea.setOpaque(false);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.getViewport().setBackground(new Color(10, 10, 12, 200));
        scroll.getViewport().setOpaque(true);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scroll.setOpaque(false);

        JPanel wrapper = transPanel(new BorderLayout());
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildBottomPanel() {
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setForeground(ACCENT_BLUE);
        progressBar.setBackground(new Color(30, 30, 33));
        progressBar.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        progressBar.setOpaque(true);
        progressBar.setPreferredSize(new Dimension(0, 18));

        statusLabel.setForeground(FG_DIM);
        statusLabel.setFont(new Font("Default", Font.ITALIC, 11));

        styleButton(downloadBtn);
        downloadBtn.setPreferredSize(new Dimension(110, 34));
        downloadBtn.addActionListener(e -> handleDownloadClick());

        JPanel btnRow = transPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnRow.add(downloadBtn);

        JPanel p = transPanel(new BorderLayout(0, 4));
        p.add(progressBar,  BorderLayout.NORTH);
        p.add(statusLabel,  BorderLayout.CENTER);
        p.add(btnRow,       BorderLayout.SOUTH);
        return p;
    }

    // ── logic ─────────────────────────────────────────────────────────────────

    /**
     * Repopulate the combo box with album names (physical download folders).
     * Downloads go into albums, not user playlists.
     */
    public void refreshPlaylistBox() {
        playlistBox.removeAllItems();
        ArrayList<String> names = sm.getAlbumNames();
        for (String name : names) playlistBox.addItem(name);
    }

    private void toggleNewPlaylist() {
        boolean checked = newPlaylistCheck.isSelected();
        newNameField.setEnabled(checked);
        playlistBox.setEnabled(!checked);
        if (checked) newNameField.requestFocus();
    }

    private void handleDownloadClick() {
        // If a download is running, cancel it
        if (activeWorker != null && !activeWorker.isDone()) {
            activeWorker.cancel(true);
            appendLog("⚠ Download cancelled.");
            setIdle("Cancelled.");
            return;
        }

        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            appendLog("✖ Please enter a YouTube URL.");
            return;
        }

        // Resolve destination folder
        String destFolder;
        String playlistLabel;

        if (newPlaylistCheck.isSelected()) {
            String rawName = newNameField.getText().trim();
            if (rawName.isEmpty()) {
                appendLog("✖ Please enter a name for the new album folder.");
                return;
            }
            // Sanitise: spaces → underscores, strip anything weird
            String safeName = rawName.replaceAll("[^\\w\\-]", "_");
            destFolder   = sm.musicDirectory + safeName;
            playlistLabel = safeName;

            File dir = new File(destFolder);
            if (!dir.exists() && !dir.mkdirs()) {
                appendLog("✖ Could not create folder: " + destFolder);
                return;
            }
            appendLog("✔ Created album folder: " + safeName);
        } else {
            String selected = (String) playlistBox.getSelectedItem();
            if (selected == null) {
                appendLog("✖ No playlist selected.");
                return;
            }
            destFolder    = sm.musicDirectory + selected;
            playlistLabel  = selected;
        }

        final String finalDest  = destFolder;
        final String finalLabel = playlistLabel;

        logArea.setText("");
        setRunning();
        appendLog("⬇ Downloading to: " + finalLabel);
        appendLog("  URL: " + url);
        appendLog("");

        activeWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                String[] cmd = {
                        "yt-dlp",
                        "--extract-audio",
                        "--audio-format", "wav",
                        "--audio-quality", "0",
                        "--output", finalDest + "/%(title)s.%(ext)s",
                        "--restrict-filenames",
                        "--no-playlist-reverse",
                        "--newline",        // one progress line per update — easier to parse
                        url
                };

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true); // merge stderr into stdout
                Process proc = pb.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (isCancelled()) {
                            proc.destroyForcibly();
                            break;
                        }
                        publish(line);
                    }
                }

                int exit = proc.waitFor();
                if (exit != 0 && !isCancelled()) {
                    publish("✖ yt-dlp exited with code " + exit);
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String line : chunks) {
                    appendLog(line);
                    // progress (for those with large nose heritage): yt-dlp prints "[download]  42.3% ..."
                    if (line.startsWith("[download]") && line.contains("%")) {
                        try {
                            String pct = line.replaceAll(".*?(\\d+\\.\\d+)%.*", "$1");
                            int val = (int) Double.parseDouble(pct);
                            progressBar.setValue(val);
                            progressBar.setString(val + "%");
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            @Override
            protected void done() {
                if (isCancelled()) return;

                // Reload playlists after download
                sm.refreshPlaylists();
                ui.refreshPlaylistMenu();
                refreshPlaylistBox();

                appendLog("");
                appendLog("✔ Done! Playlists refreshed.");
                ui.log("Add Songs: finished downloading to album \"" + finalLabel + "\"");
                setIdle("Done — playlists refreshed.");
                progressBar.setValue(100);
                progressBar.setString("100%");
            }
        };

        activeWorker.execute();
    }

    private void setRunning() {
        downloadBtn.setText("Cancel");
        progressBar.setValue(0);
        progressBar.setString("0%");
        progressBar.setIndeterminate(false);
        statusLabel.setText("Downloading…");
    }

    private void setIdle(String msg) {
        downloadBtn.setText("Download");
        statusLabel.setText(msg);
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // style n other goy shit

    private static JPanel transPanel(LayoutManager layout) {
        JPanel p = new JPanel(layout) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(TRANS_MID);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(FG_DIM);
        l.setFont(new Font("Default", Font.PLAIN, 12));
        return l;
    }

    private static void styleTextField(JTextField f) {
        f.setBackground(new Color(30, 30, 33));
        f.setForeground(FG_BRIGHT);
        f.setCaretColor(FG_BRIGHT);
        f.setFont(new Font("Monospaced", Font.PLAIN, 12));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(2, 5, 2, 5)
        ));
    }

    private static void styleComboBox(JComboBox<String> box) {
        box.setBackground(new Color(30, 30, 33));
        box.setForeground(FG_BRIGHT);
        box.setFont(new Font("Default", Font.PLAIN, 12));
        box.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        box.setOpaque(true);
    }

    private static void styleButton(JButton b) {
        b.setBackground(new Color(40, 40, 45, 200));
        b.setForeground(FG_BRIGHT);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setFont(new Font("Default", Font.PLAIN, 12));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
}