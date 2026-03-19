import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

/**
 * JInternalFrame — Playlist Editor
 *
 * Left panel  : album browser (pick an album, see its songs)
 * Right panel : the currently edited playlist (add / remove / save)
 *
 * The user can:
 *   1. Type a name and click "New" to create an empty playlist.
 *   2. Select an album, browse its songs, double-click (or click "Add →") to
 *      append them to the active playlist.
 *   3. Select a song in the playlist panel and click "Remove" to drop it.
 *   4. Changes are auto-saved to disk via SongManagement.savePlaylist().
 */
public class PlaylistEditorFrame extends JInternalFrame {

    // ── palette ───────────────────────────────────────────────────────────────
    private static final Color TRANS_CONTROL = new Color(10, 10, 12, 160);
    private static final Color TRANS_MID     = new Color(20, 20, 23, 140);
    private static final Color ACCENT_BLUE   = new Color(70, 130, 180);
    private static final Color FG_BRIGHT     = new Color(220, 220, 220);
    private static final Color FG_DIM        = new Color(180, 180, 180);
    private static final Color BORDER_COLOR  = new Color(90, 90, 95, 100);

    private final PlayerUI      ui;
    private final SongManagement sm;

    // ── album browser ─────────────────────────────────────────────────────────
    private final DefaultListModel<String> albumListModel  = new DefaultListModel<>();
    private final JList<String>            albumList        = new JList<>(albumListModel);
    private final DefaultListModel<String> albumSongModel  = new DefaultListModel<>();
    private final JList<String>            albumSongList   = new JList<>(albumSongModel);

    // ── playlist editor ───────────────────────────────────────────────────────
    private final JComboBox<String>        playlistPicker  = new JComboBox<>();
    private final JTextField               newNameField    = new JTextField("New Playlist");
    private final DefaultListModel<String> plSongModel     = new DefaultListModel<>();
    private final JList<String>            plSongList      = new JList<>(plSongModel);

    private final JButton btnNew    = new JButton("New");
    private final JButton btnAdd    = new JButton("Add →");
    private final JButton btnRemove = new JButton("Remove");
    private final JButton btnDelete = new JButton("Delete Playlist");

    private final JLabel statusLabel = new JLabel(" ");

    // ─────────────────────────────────────────────────────────────────────────
    public PlaylistEditorFrame(PlayerUI ui) {
        super("Playlist Editor", true, true, true, true);
        this.ui = ui;
        this.sm = ui.sm;

        buildUi();
        refresh();

        setSize(680, 420);
        setFrameIcon(null);
        setOpaque(false);
        setVisible(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI construction
    // ─────────────────────────────────────────────────────────────────────────

    private void buildUi() {
        JPanel root = paintedPanel(TRANS_CONTROL, new BorderLayout(8, 4));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        root.add(buildAlbumPanel(),    BorderLayout.WEST);
        root.add(buildMidButtons(),    BorderLayout.CENTER);
        root.add(buildPlaylistPanel(), BorderLayout.EAST);
        root.add(buildStatusBar(),     BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ── Left: album browser ──

    private JPanel buildAlbumPanel() {
        JPanel p = paintedPanel(TRANS_MID, new BorderLayout(0, 4));
        p.setPreferredSize(new Dimension(220, 0));
        p.setBorder(new EmptyBorder(4, 4, 4, 4));

        styleList(albumList);
        albumList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        albumList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onAlbumSelected();
        });

        styleList(albumSongList);
        albumSongList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        albumSongList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent ev) {
                if (ev.getClickCount() == 2) addSelectedSongs();
            }
        });

        JLabel albumHeader = dimLabel("Albums");
        JLabel songHeader  = dimLabel("Songs in Album");

        JScrollPane albumScroll = styledScroll(albumList);
        JScrollPane songScroll  = styledScroll(albumSongList);

        // top half: album list; bottom half: songs in album
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, albumScroll, songScroll);
        split.setResizeWeight(0.35);
        split.setOpaque(false);
        split.setDividerSize(4);
        split.setBorder(null);
        split.getTopComponent().setPreferredSize(new Dimension(0, 110));

        JPanel header = paintedPanel(TRANS_MID, new BorderLayout(0, 2));
        header.add(albumHeader, BorderLayout.NORTH);

        p.add(header, BorderLayout.NORTH);
        p.add(split,  BorderLayout.CENTER);
        p.add(songHeader, BorderLayout.SOUTH);

        return p;
    }

    // ── Centre: add/remove buttons ──

    private JPanel buildMidButtons() {
        styleButton(btnAdd);
        styleButton(btnRemove);
        btnAdd.setToolTipText("Add selected song(s) to the active playlist");
        btnRemove.setToolTipText("Remove selected song from the playlist");

        btnAdd.addActionListener(e -> addSelectedSongs());
        btnRemove.addActionListener(e -> removeSelectedSong());

        JPanel p = paintedPanel(TRANS_CONTROL, new GridBagLayout());
        p.setPreferredSize(new Dimension(90, 0));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = 0; g.insets = new Insets(6, 0, 6, 0);
        g.fill  = GridBagConstraints.HORIZONTAL;
        p.add(btnAdd,    g);
        g.gridy = 1;
        p.add(btnRemove, g);
        return p;
    }

    // ── Right: playlist editor ──

    private JPanel buildPlaylistPanel() {
        JPanel p = paintedPanel(TRANS_MID, new BorderLayout(0, 4));
        p.setPreferredSize(new Dimension(220, 0));
        p.setBorder(new EmptyBorder(4, 4, 4, 4));

        // ── top controls ──
        styleCombo(playlistPicker);
        playlistPicker.addActionListener(e -> onPlaylistPicked());

        styleTextField(newNameField);
        styleButton(btnNew);
        styleButton(btnDelete);
        btnNew.setToolTipText("Create a new playlist with this name");
        btnDelete.setToolTipText("Delete the selected playlist permanently");

        btnNew.addActionListener(e -> createNewPlaylist());
        btnDelete.addActionListener(e -> deleteSelectedPlaylist());

        JPanel newRow = paintedPanel(TRANS_MID, new BorderLayout(4, 0));
        newRow.add(newNameField,  BorderLayout.CENTER);
        newRow.add(btnNew,        BorderLayout.EAST);

        JPanel topControls = paintedPanel(TRANS_MID, new BorderLayout(0, 3));
        topControls.add(dimLabel("Active Playlist:"), BorderLayout.NORTH);
        topControls.add(playlistPicker,               BorderLayout.CENTER);
        topControls.add(newRow,                       BorderLayout.SOUTH);

        // ── song list ──
        styleList(plSongList);
        plSongList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel bottomControls = paintedPanel(TRANS_MID, new BorderLayout(4, 0));
        btnDelete.setFont(new Font("Default", Font.PLAIN, 11));
        bottomControls.add(btnDelete, BorderLayout.CENTER);

        p.add(topControls,         BorderLayout.NORTH);
        p.add(styledScroll(plSongList), BorderLayout.CENTER);
        p.add(bottomControls,      BorderLayout.SOUTH);

        return p;
    }

    private JPanel buildStatusBar() {
        statusLabel.setForeground(FG_DIM);
        statusLabel.setFont(new Font("Default", Font.ITALIC, 11));
        JPanel p = paintedPanel(TRANS_CONTROL, new BorderLayout());
        p.add(statusLabel, BorderLayout.WEST);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Logic
    // ─────────────────────────────────────────────────────────────────────────

    /** Reload album list and playlist picker from SongManagement. */
    public void refresh() {
        // Albums
        albumListModel.clear();
        for (String name : sm.getAlbumNames()) albumListModel.addElement(name);

        // Playlist picker
        String prev = (String) playlistPicker.getSelectedItem();
        playlistPicker.removeAllItems();
        for (String name : sm.getPlaylistNames()) playlistPicker.addItem(name);

        if (prev != null && sm.getPlaylistNames().contains(prev)) {
            playlistPicker.setSelectedItem(prev);
        } else if (playlistPicker.getItemCount() > 0) {
            playlistPicker.setSelectedIndex(0);
        }

        onPlaylistPicked(); // refresh right panel
    }

    private void onAlbumSelected() {
        albumSongModel.clear();
        String album = albumList.getSelectedValue();
        if (album == null) return;

        for (Song s : sm.getSongsInAlbum(album)) {
            albumSongModel.addElement(s.name);
        }
        status("Browsing: " + album + "  (" + albumSongModel.getSize() + " tracks)");
    }

    private void onPlaylistPicked() {
        plSongModel.clear();
        String pl = (String) playlistPicker.getSelectedItem();
        if (pl == null) return;

        for (Song s : sm.getSongsInPlaylist(pl)) {
            plSongModel.addElement(s.name);
        }
        status("Editing: " + pl + "  (" + plSongModel.getSize() + " songs)");
    }

    private void addSelectedSongs() {
        String pl = (String) playlistPicker.getSelectedItem();
        if (pl == null) {
            status("✖ No playlist selected. Create one first.");
            return;
        }
        String album = albumList.getSelectedValue();
        if (album == null) {
            status("✖ Select an album first.");
            return;
        }

        int[] indices = albumSongList.getSelectedIndices();
        if (indices.length == 0) {
            status("✖ Select at least one song to add.");
            return;
        }

        ArrayList<Song> albumSongs = sm.getSongsInAlbum(album);
        int added = 0;
        for (int i : indices) {
            if (i < albumSongs.size()) {
                sm.addSongToPlaylist(pl, albumSongs.get(i));
                added++;
            }
        }

        onPlaylistPicked(); // refresh list
        ui.refreshPlaylistMenu();
        status("✔ Added " + added + " song(s) to \"" + pl + "\".");
        ui.log("Playlist editor: added " + added + " song(s) to \"" + pl + "\"");
    }

    private void removeSelectedSong() {
        String pl = (String) playlistPicker.getSelectedItem();
        if (pl == null) return;

        int idx = plSongList.getSelectedIndex();
        if (idx < 0) {
            status("✖ Select a song in the playlist to remove.");
            return;
        }

        String songName = plSongModel.getElementAt(idx);
        sm.removeSongFromPlaylist(pl, idx);
        onPlaylistPicked();
        ui.refreshPlaylistMenu();
        status("✔ Removed \"" + songName + "\" from \"" + pl + "\".");
    }

    private void createNewPlaylist() {
        String name = newNameField.getText().trim();
        if (name.isEmpty()) {
            status("✖ Enter a name for the new playlist.");
            return;
        }
        // Sanitise: keep alphanumerics, spaces, hyphens, underscores
        String safe = name.replaceAll("[^\\w\\s\\-]", "_").trim();

        if (!sm.createPlaylist(safe)) {
            status("✖ A playlist named \"" + safe + "\" already exists.");
            return;
        }

        refresh();
        playlistPicker.setSelectedItem(safe);
        ui.refreshPlaylistMenu();
        ui.log("Created playlist: " + safe);
        status("✔ Created playlist \"" + safe + "\".");
    }

    private void deleteSelectedPlaylist() {
        String pl = (String) playlistPicker.getSelectedItem();
        if (pl == null) return;

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete playlist \"" + pl + "\"? This cannot be undone.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        sm.deletePlaylist(pl);
        refresh();
        ui.refreshPlaylistMenu();
        ui.log("Deleted playlist: " + pl);
        status("✔ Deleted \"" + pl + "\".");
    }

    private void status(String msg) {
        statusLabel.setText(msg);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Style helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static JPanel paintedPanel(Color fill, LayoutManager layout) {
        JPanel p = new JPanel(layout) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(fill);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    private static void styleList(JList<String> list) {
        list.setBackground(new Color(20, 20, 23, 180));
        list.setForeground(FG_BRIGHT);
        list.setSelectionBackground(new Color(70, 130, 180, 200));
        list.setSelectionForeground(Color.WHITE);
        list.setFont(new Font("Default", Font.PLAIN, 12));
        list.setOpaque(true);
    }

    private static JScrollPane styledScroll(JList<String> list) {
        JScrollPane sp = new JScrollPane(list);
        sp.getViewport().setBackground(new Color(20, 20, 23, 180));
        sp.getViewport().setOpaque(true);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        sp.setOpaque(false);
        return sp;
    }

    private static void styleButton(JButton b) {
        b.setBackground(new Color(40, 40, 45, 200));
        b.setForeground(FG_BRIGHT);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setFont(new Font("Default", Font.PLAIN, 12));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(90, 28));
    }

    private static void styleTextField(JTextField f) {
        f.setBackground(new Color(30, 30, 33));
        f.setForeground(FG_BRIGHT);
        f.setCaretColor(FG_BRIGHT);
        f.setFont(new Font("Monospaced", Font.PLAIN, 12));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(2, 5, 2, 5)));
    }

    private static void styleCombo(JComboBox<String> box) {
        box.setBackground(new Color(30, 30, 33));
        box.setForeground(FG_BRIGHT);
        box.setFont(new Font("Default", Font.PLAIN, 12));
        box.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        box.setOpaque(true);
    }

    private static JLabel dimLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(FG_DIM);
        l.setFont(new Font("Default", Font.PLAIN, 12));
        return l;
    }
}