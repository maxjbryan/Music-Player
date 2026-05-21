import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;

/**
 * ConsoleCommandHandler
 *
 * Processes terminal-style commands typed into the console frame.
 * Each public method corresponds to a command group; dispatch() is the entry point.
 *
 * COMMAND REFERENCE
 * ─────────────────
 *  SYSTEM
 *    help [group]          — list commands, or detail a specific group
 *    clear                 — wipe the console output
 *    refresh               — re-scan music directory and rebuild menus
 *
 *  CONNECTIONS  (conn)
 *    conn on               — enable the Bézier connector layer
 *    conn off              — disable / hide the connector layer
 *    conn list             — print every registered cable
 *    conn clear            — remove all cables
 *    conn add <a> <b> [color]  — add cable between two frame keywords
 *    conn rm  <a> <b>      — remove cable(s) touching both frames
 *
 *  PLAYER  (player / pl)
 *    player play <song>    — play by partial name match in current list
 *    player pause          — pause
 *    player resume         — resume
 *    player stop           — stop and reset
 *    player next           — next track
 *    player prev           — previous track
 *    player seek <sec>     — seek to absolute position in seconds
 *    player vol <0-100>    — set volume
 *    player status         — print current song, position, volume
 *
 *  DATABASE  (db)
 *    db albums             — list all albums
 *    db playlists          — list all user playlists
 *    db songs <album>      — list songs in album
 *    db pl-songs <playlist>— list songs in playlist
 *    db add-playlist <name>            — create empty playlist
 *    db del-playlist <name>            — delete playlist + its .m3u
 *    db add-song <playlist> <album> <song>  — add song to playlist
 *    db rm-song  <playlist> <index>    — remove song at index from playlist
 *    db rename-album <old> <new>       — rename album folder on disk + reload
 *    db set-dir <path>                 — change music directory at runtime
 *
 *  UI
 *    ui frames             — list all internal frames and their visibility
 *    ui focus  <frame>     — bring a frame to front / un-iconify
 *    ui show   <frame>     — make a frame visible
 *    ui hide   <frame>     — hide a frame
 *    ui move   <frame> <x> <y>  — reposition a frame
 *    ui size   <frame> <w> <h>  — resize a frame
 *    ui bg     <path>      — set desktop background image
 *    ui bg     off         — clear background image
 *    ui nowplaying <text>  — override the now-playing label
 */
public class ConsoleCommandHandler {

    private final PlayerUI ui;

    // Command history for ↑/↓ navigation (managed by ConsoleInputField)
    final ArrayList<String> history = new ArrayList<>();

    // Whether the connector layer is active
    private boolean connectorsEnabled = true;

    public ConsoleCommandHandler(PlayerUI ui) {
        this.ui = ui;
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    /**
     * parse and execute one line of input. returns the text to append to the
     * console (may be multi-line).  never throws errors are returned as text.
     */
    public String dispatch(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";

        history.add(trimmed);

        String[] parts = trimmed.split("\\s+", 3);
        String   cmd   = parts[0].toLowerCase();

        try {
            return switch (cmd) {
                case "help"   -> cmdHelp(parts);
                case "clear"  -> { clearConsole(); yield ""; }
                case "refresh"-> cmdRefresh();
                case "conn", "connections" -> cmdConn(parts);
                case "player", "pl"        -> cmdPlayer(parts);
                case "db"                  -> cmdDb(parts);
                case "ui"                  -> cmdUi(parts);
                case "slowfetch"           -> slowfetch();
                default -> err("Unknown command: '" + cmd + "'.  Type 'help' for a list.");
            };
        } catch (Exception ex) {
            return err("Exception: " + ex.getMessage());
        }
    }

    // ── SYSTEM ───────────────────────────────────────────────────────────────

    private String slowfetch() {
        String fetch =
                "    ___           ___           ___           ___           ___              goy OS version 3.12.1\n" +
                        "     /\\  \\         /\\  \\         |\\__\\         /\\  \\         /\\  \\             CPU: intel - tel271k\n" +
                        "    /::\\  \\       /::\\  \\        |:|  |       /::\\  \\       /::\\  \\            RAM 6.00 PB\n" +
                        "   /:/\\:\\  \\     /:/\\:\\  \\       |:|  |      /:/\\:\\  \\     /:/\\ \\  \\           KERNEL: Iron Dome 2.6\n" +
                        "  /:/  \\:\\  \\   /:/  \\:\\  \\      |:|__|__   /:/  \\:\\  \\   _\\:\\~\\ \\  \\          \n" +
                        " /:/__/_\\:\\__\\ /:/__/ \\:\\__\\     /::::\\__\\ /:/__/ \\:\\__\\ /\\ \\:\\ \\ \\__\\         \n" +
                        " \\:\\  /\\ \\/__/ \\:\\  \\ /:/  /    /:/~~/~    \\:\\  \\ /:/  / \\:\\ \\:\\ \\/__/         \n" +
                        "  \\:\\ \\:\\__\\    \\:\\  /:/  /    /:/  /       \\:\\  /:/  /   \\:\\ \\:\\__\\           \n" +
                        "   \\:\\/:/  /     \\:\\/:/  /     \\/__/         \\:\\/:/  /     \\:\\/:/  /           \n" +
                        "    \\::/  /       \\::/  /                     \\::/  /       \\::/  /            \n" +
                        "     \\/__/         \\/__/                       \\/__/         \\/__/              "
                ;
        return fetch;
    }
    private String cmdHelp(String[] parts) {
        if (parts.length >= 2) {
            return switch (parts[1].toLowerCase()) {
                case "conn", "connections" -> HELP_CONN;
                case "player", "pl"        -> HELP_PLAYER;
                case "db"                  -> HELP_DB;
                case "ui"                  -> HELP_UI;
                default -> err("No help for '" + parts[1] + "'");
            };
        }
        return HELP_MAIN;
    }

    private String cmdRefresh() {
        ui.sm.refreshPlaylists();
        ui.refreshPlaylistMenu();
        return ok("Music directory re-scanned.  Albums: " + ui.sm.getAlbumNames().size()
                + "  Playlists: " + ui.sm.getPlaylistNames().size());
    }

    private void clearConsole() {
        SwingUtilities.invokeLater(() -> ui.consoleOutput.setText(""));
    }

    // ── CONNECTIONS ──────────────────────────────────────────────────────────

    private String cmdConn(String[] parts) {
        if (parts.length < 2) return HELP_CONN;
        return switch (parts[1].toLowerCase()) {
            case "on"   -> connSetEnabled(true);
            case "off"  -> connSetEnabled(false);
            case "list" -> connList();
            case "clear"-> connClear();
            case "add"  -> connAdd(parts.length >= 3 ? parts[2] : "");
            case "rm"   -> connRm(parts.length >= 3 ? parts[2] : "");
            default     -> err("Unknown conn sub-command: " + parts[1]);
        };
    }

    private String connSetEnabled(boolean on) {
        connectorsEnabled = on;
        if (ui.connectorLayer == null) return err("Connector layer not initialised.");
        ui.connectorLayer.setVisible(on);
        return ok("Connector layer " + (on ? "enabled" : "disabled") + ".");
    }

    private String connList() {
        if (ui.connectorLayer == null) return err("Connector layer not initialised.");
        var conns = ui.connectorLayer.getConnections();
        if (conns.isEmpty()) return info("No cables registered.");
        StringBuilder sb = new StringBuilder();
        sb.append("Cables (").append(conns.size()).append("):\n");
        for (int i = 0; i < conns.size(); i++) {
            var c = conns.get(i);
            sb.append("  [").append(i).append("] ")
                    .append(c.from.getTitle()).append("  →  ").append(c.to.getTitle()).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private String connClear() {
        if (ui.connectorLayer == null) return err("Connector layer not initialised.");
        ui.connectorLayer.disconnectAll();
        return ok("All cables removed.");
    }

    private String connAdd(String argStr) {
        String[] args = argStr.trim().split("\\s+", 3);
        if (args.length < 2) return err("Usage: conn add <frameA> <frameB> [color]");
        JInternalFrame a = resolveFrame(args[0]);
        JInternalFrame b = resolveFrame(args[1]);
        if (a == null) return err("Unknown frame: " + args[0]);
        if (b == null) return err("Unknown frame: " + args[1]);
        if (args.length >= 3) ui.connectorLayer.setCableColor(resolveColor(args[2]));
        ui.connectorLayer.connect(a, b);
        return ok("Cable added: " + a.getTitle() + " → " + b.getTitle());
    }

    private String connRm(String argStr) {
        String[] args = argStr.trim().split("\\s+", 2);
        if (args.length < 2) return err("Usage: conn rm <frameA> <frameB>");
        JInternalFrame a = resolveFrame(args[0]);
        JInternalFrame b = resolveFrame(args[1]);
        if (a == null) return err("Unknown frame: " + args[0]);
        if (b == null) return err("Unknown frame: " + args[1]);
        int removed = ui.connectorLayer.disconnectPair(a, b);
        return removed > 0 ? ok("Removed " + removed + " cable(s).") : info("No cable found between those frames.");
    }

    // ── PLAYER ───────────────────────────────────────────────────────────────

    private String cmdPlayer(String[] parts) {
        if (parts.length < 2) return HELP_PLAYER;
        String sub  = parts[1].toLowerCase();
        String rest = parts.length >= 3 ? parts[2] : "";
        return switch (sub) {
            case "play"   -> playerPlay(rest);
            case "pause"  -> playerPause();
            case "resume" -> playerResume();
            case "stop"   -> playerStop();
            case "next"   -> playerNext();
            case "prev"   -> playerPrev();
            case "seek"   -> playerSeek(rest);
            case "vol"    -> playerVol(rest);
            case "status" -> playerStatus();
            default       -> err("Unknown player sub-command: " + sub);
        };
    }

    private String playerPlay(String query) {
        if (query.isEmpty()) return err("Usage: player play <partial song name>");
        // search current list first, then all albums
        String found = findSongPath(query);
        if (found == null) return err("No song matching '" + query + "' found in current view.");
        String finalFound = found;
        SwingUtilities.invokeLater(() -> {
            if (ui.progressTimer != null) ui.progressTimer.stop();
            ui.ac.playSound(finalFound);
            javax.swing.Timer t = new javax.swing.Timer(150, e -> {
                ui.btnPlayPause.setText("⏸");
                if (ui.progressTimer != null) ui.progressTimer.start();
            });
            t.setRepeats(false); t.start();
            // update now-playing label
            String name = new File(finalFound).getName().replace(".wav", "");
            ui.nowPlayingLabel.setText("♪ " + name);
        });
        return ok("Playing: " + new File(found).getName());
    }

    private String playerPause() {
        if (ui.ac.getcurrentClip() == null) return err("Nothing is playing.");
        SwingUtilities.invokeLater(() -> {
            ui.ac.pause();
            ui.btnPlayPause.setText("▶");
            if (ui.progressTimer != null) ui.progressTimer.stop();
        });
        return ok("Paused.");
    }

    private String playerResume() {
        if (ui.ac.getcurrentClip() == null) return err("No clip loaded.");
        SwingUtilities.invokeLater(() -> {
            ui.ac.resume();
            ui.btnPlayPause.setText("⏸");
            if (ui.progressTimer != null) ui.progressTimer.start();
        });
        return ok("Resumed.");
    }

    private String playerStop() {
        SwingUtilities.invokeLater(() -> {
            ui.ac.pause();
            ui.ac.setPosition(0);
            ui.btnPlayPause.setText("▶");
            if (ui.progressTimer != null) ui.progressTimer.stop();
            ui.nowPlayingLabel.setText("No song playing");
        });
        return ok("Stopped.");
    }

    private String playerNext() {
        SwingUtilities.invokeLater(ui::playNextSong);
        return ok("Next track.");
    }

    private String playerPrev() {
        SwingUtilities.invokeLater(ui::playPreviousSong);
        return ok("Previous track.");
    }

    private String playerSeek(String arg) {
        try {
            long secs = Long.parseLong(arg.trim());
            ui.ac.setPosition(secs * 1_000_000L);
            return ok("Seeked to " + secs + "s.");
        } catch (NumberFormatException e) {
            return err("Usage: player seek <seconds>");
        }
    }

    private String playerVol(String arg) {
        try {
            int pct = Integer.parseInt(arg.trim());
            if (pct < 0 || pct > 100) return err("Volume must be 0–100.");
            SwingUtilities.invokeLater(() -> {
                ui.volumeSlider.setValue(pct);
                ui.volumeLabel.setText(pct + "%");
                ui.ac.setVolume((pct * 0.3f) / 100f);
            });
            return ok("Volume set to " + pct + "%.");
        } catch (NumberFormatException e) {
            return err("Usage: player vol <0-100>");
        }
    }

    private String playerStatus() {
        var clip = ui.ac.getcurrentClip();
        if (clip == null || !clip.isOpen()) return info("No clip loaded.");
        long cur   = clip.getMicrosecondPosition() / 1_000_000L;
        long total = clip.getMicrosecondLength()   / 1_000_000L;
        String state = ui.ac.clipPaused ? "paused" : "playing";
        float vol  = ui.ac.getVolume();
        int   volPct = Math.round(vol / 0.3f * 100f);
        return info("Status : " + state + "\n"
                + "Track  : " + ui.nowPlayingLabel.getText() + "\n"
                + "Pos    : " + fmt(cur) + " / " + fmt(total) + "\n"
                + "Volume : " + volPct + "%");
    }

    // db bullshit (why did i spend so much time on ts useless bs)

    private String cmdDb(String[] parts) {
        if (parts.length < 2) return HELP_DB;
        String sub  = parts[1].toLowerCase();
        String rest = parts.length >= 3 ? parts[2] : "";
        return switch (sub) {
            case "albums"       -> dbListAlbums();
            case "playlists"    -> dbListPlaylists();
            case "songs"        -> dbListSongs(rest, false);
            case "pl-songs"     -> dbListSongs(rest, true);
            case "add-playlist" -> dbAddPlaylist(rest.trim());
            case "del-playlist" -> dbDelPlaylist(rest.trim());
            case "add-song"     -> dbAddSong(rest);
            case "rm-song"      -> dbRmSong(rest);
            case "rename-album" -> dbRenameAlbum(rest);
            case "set-dir"      -> dbSetDir(rest.trim());
            default             -> err("Unknown db sub-command: " + sub);
        };
    }

    private String dbListAlbums() {
        var names = ui.sm.getAlbumNames();
        if (names.isEmpty()) return info("No albums found.");
        StringBuilder sb = new StringBuilder("Albums (" + names.size() + "):\n");
        for (String n : names) {
            int count = ui.sm.getSongsInAlbum(n).size();
            sb.append("  ").append(n).append("  (").append(count).append(" tracks)\n");
        }
        return sb.toString().stripTrailing();
    }

    private String dbListPlaylists() {
        var names = ui.sm.getPlaylistNames();
        if (names.isEmpty()) return info("No playlists.");
        StringBuilder sb = new StringBuilder("Playlists (" + names.size() + "):\n");
        for (String n : names) {
            int count = ui.sm.getSongsInPlaylist(n).size();
            sb.append("  ").append(n).append("  (").append(count).append(" tracks)\n");
        }
        return sb.toString().stripTrailing();
    }

    private String dbListSongs(String name, boolean isPlaylist) {
        if (name.isEmpty()) return err("Usage: db " + (isPlaylist ? "pl-songs" : "songs") + " <name>");
        ArrayList<Song> songs = isPlaylist
                ? ui.sm.getSongsInPlaylist(name)
                : ui.sm.getSongsInAlbum(name);
        if (songs == null || songs.isEmpty())
            return info("No songs found in '" + name + "'.");
        StringBuilder sb = new StringBuilder(
                (isPlaylist ? "Playlist" : "Album") + " '" + name + "' (" + songs.size() + " tracks):\n");
        for (int i = 0; i < songs.size(); i++) {
            sb.append("  [").append(i).append("] ").append(songs.get(i).name).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private String dbAddPlaylist(String name) {
        if (name.isEmpty()) return err("Usage: db add-playlist <name>");
        boolean created = ui.sm.createPlaylist(name);
        if (!created) return err("Playlist '" + name + "' already exists.");
        SwingUtilities.invokeLater(ui::refreshPlaylistMenu);
        return ok("Playlist '" + name + "' created.");
    }

    private String dbDelPlaylist(String name) {
        if (name.isEmpty()) return err("Usage: db del-playlist <name>");
        if (!ui.sm.getPlaylistNames().contains(name)) return err("Playlist '" + name + "' not found.");
        ui.sm.deletePlaylist(name);
        SwingUtilities.invokeLater(ui::refreshPlaylistMenu);
        return ok("Playlist '" + name + "' deleted.");
    }

    private String dbAddSong(String rest) {
        // add-song <playlist> <album> <partial-song-name>
        String[] args = rest.trim().split("\\s+", 3);
        if (args.length < 3) return err("Usage: db add-song <playlist> <album> <song>");
        String playlist = args[0], album = args[1], query = args[2];
        if (!ui.sm.getPlaylistNames().contains(playlist))
            return err("Playlist '" + playlist + "' not found.");
        ArrayList<Song> songs = ui.sm.getSongsInAlbum(album);
        if (songs == null || songs.isEmpty()) return err("Album '" + album + "' not found or empty.");
        Song match = null;
        for (Song s : songs) {
            if (s.name.toLowerCase().contains(query.toLowerCase())) { match = s; break; }
        }
        if (match == null) return err("No song matching '" + query + "' in album '" + album + "'.");
        ui.sm.addSongToPlaylist(playlist, match);
        return ok("Added '" + match.name + "' to playlist '" + playlist + "'.");
    }

    private String dbRmSong(String rest) {
        // rm-song <playlist> <index>
        String[] args = rest.trim().split("\\s+", 2);
        if (args.length < 2) return err("Usage: db rm-song <playlist> <index>");
        String playlist = args[0];
        int idx;
        try { idx = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) { return err("Index must be an integer."); }
        ArrayList<Song> songs = ui.sm.getSongsInPlaylist(playlist);
        if (songs == null) return err("Playlist '" + playlist + "' not found.");
        if (idx < 0 || idx >= songs.size()) return err("Index out of range (0–" + (songs.size()-1) + ").");
        String removed = songs.get(idx).name;
        ui.sm.removeSongFromPlaylist(playlist, idx);
        return ok("Removed [" + idx + "] '" + removed + "' from '" + playlist + "'.");
    }

    private String dbRenameAlbum(String rest) {
        String[] args = rest.trim().split("\\s+", 2);
        if (args.length < 2) return err("Usage: db rename-album <old> <new>");
        String oldName = args[0], newName = args[1];
        File oldDir = new File(ui.sm.musicDirectory + oldName);
        File newDir = new File(ui.sm.musicDirectory + newName);
        if (!oldDir.exists()) return err("Album folder '" + oldName + "' not found on disk.");
        if (newDir.exists()) return err("A folder named '" + newName + "' already exists.");
        if (!oldDir.renameTo(newDir)) return err("Rename failed (OS refused).");
        ui.sm.refreshPlaylists();
        SwingUtilities.invokeLater(ui::refreshPlaylistMenu);
        return ok("Renamed '" + oldName + "' → '" + newName + "' and reloaded library.");
    }

    private String dbSetDir(String path) {
        if (path.isEmpty()) return err("Usage: db set-dir <absolute-path>");
        File dir = new File(path.endsWith("/") ? path : path + "/");
        if (!dir.exists() || !dir.isDirectory()) return err("Path does not exist: " + path);
        ui.sm.musicDirectory    = dir.getAbsolutePath() + "/";
        ui.sm.playlistDirectory = dir.getAbsolutePath() + "/.playlists/";
        ui.sm.refreshPlaylists();
        SwingUtilities.invokeLater(ui::refreshPlaylistMenu);
        return ok("Music directory set to: " + ui.sm.musicDirectory + "\nLibrary reloaded.");
    }

    // ── UI ───────────────────────────────────────────────────────────────────

    private String cmdUi(String[] parts) {
        if (parts.length < 2) return HELP_UI;
        String sub  = parts[1].toLowerCase();
        String rest = parts.length >= 3 ? parts[2] : "";
        return switch (sub) {
            case "frames"     -> uiFrames();
            case "focus"      -> uiFrameOp(rest, "focus");
            case "show"       -> uiFrameOp(rest, "show");
            case "hide"       -> uiFrameOp(rest, "hide");
            case "move"       -> uiMove(rest);
            case "size"       -> uiSize(rest);
            case "bg"         -> uiBg(rest.trim());
            case "nowplaying" -> uiNowPlaying(rest.trim());
            default           -> err("Unknown ui sub-command: " + sub);
        };
    }

    private String uiFrames() {
        StringBuilder sb = new StringBuilder("Internal frames:\n");
        for (JInternalFrame f : ui.desktop.getAllFrames()) {
            String vis  = f.isVisible()   ? "visible" : "hidden";
            String icon = f.isIcon()      ? " [iconified]" : "";
            sb.append("  '").append(f.getTitle()).append("'  ")
                    .append(vis).append(icon)
                    .append("  pos=(").append(f.getX()).append(",").append(f.getY()).append(")")
                    .append("  size=").append(f.getWidth()).append("×").append(f.getHeight())
                    .append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private String uiFrameOp(String query, String op) {
        if (query.isEmpty()) return err("Usage: ui " + op + " <frame-keyword>");
        JInternalFrame f = resolveFrame(query);
        if (f == null) return err("No frame matching '" + query + "'.");
        SwingUtilities.invokeLater(() -> {
            switch (op) {
                case "focus" -> {
                    f.setVisible(true);
                    if (f.isIcon()) try { f.setIcon(false); } catch (Exception ignored) {}
                    ui.openInternalFrame(f);
                }
                case "show" -> f.setVisible(true);
                case "hide" -> f.setVisible(false);
            }
        });
        return ok(op.substring(0,1).toUpperCase() + op.substring(1) + "ed: '" + f.getTitle() + "'.");
    }

    private String uiMove(String rest) {
        String[] args = rest.trim().split("\\s+", 3);
        if (args.length < 3) return err("Usage: ui move <frame> <x> <y>");
        JInternalFrame f = resolveFrame(args[0]);
        if (f == null) return err("No frame matching '" + args[0] + "'.");
        try {
            int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]);
            SwingUtilities.invokeLater(() -> f.setLocation(x, y));
            return ok("Moved '" + f.getTitle() + "' to (" + x + ", " + y + ").");
        } catch (NumberFormatException e) { return err("x and y must be integers."); }
    }

    private String uiSize(String rest) {
        String[] args = rest.trim().split("\\s+", 3);
        if (args.length < 3) return err("Usage: ui size <frame> <width> <height>");
        JInternalFrame f = resolveFrame(args[0]);
        if (f == null) return err("No frame matching '" + args[0] + "'.");
        try {
            int w = Integer.parseInt(args[1]), h = Integer.parseInt(args[2]);
            SwingUtilities.invokeLater(() -> f.setSize(w, h));
            return ok("Resized '" + f.getTitle() + "' to " + w + "×" + h + ".");
        } catch (NumberFormatException e) { return err("width and height must be integers."); }
    }

    private String uiBg(String arg) {
        if (arg.equalsIgnoreCase("off")) {
            SwingUtilities.invokeLater(() -> { ui.backgroundImage = null; ui.desktop.repaint(); });
            return ok("Background image cleared.");
        }
        File f = new File(arg);
        if (!f.exists()) return err("File not found: " + arg);
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            if (img == null) return err("Could not decode image: " + arg);
            SwingUtilities.invokeLater(() -> { ui.backgroundImage = img; ui.desktop.repaint(); });
            return ok("Background set to: " + f.getName());
        } catch (java.io.IOException e) {
            return err("IO error: " + e.getMessage());
        }
    }

    private String uiNowPlaying(String text) {
        if (text.isEmpty()) return err("Usage: ui nowplaying <text>");
        SwingUtilities.invokeLater(() -> ui.nowPlayingLabel.setText(text));
        return ok("Now-playing label set to: " + text);
    }

    // helpahs

    /** Find the first song whose name contains the query in the current active list. */
    private String findSongPath(String query) {
        String q = query.toLowerCase();
        String current = ui.sm.getCurrentName();
        if (current == null) return null;
        ArrayList<Song> songs = ui.sm.currentIsAlbum
                ? ui.sm.getSongsInAlbum(current)
                : ui.sm.getSongsInPlaylist(current);
        if (songs != null) {
            for (Song s : songs) {
                if (s.name.toLowerCase().contains(q)) return s.filePath;
            }
        }
        // broaden to all albums
        for (String album : ui.sm.getAlbumNames()) {
            for (Song s : ui.sm.getSongsInAlbum(album)) {
                if (s.name.toLowerCase().contains(q)) return s.filePath;
            }
        }
        return null;
    }

    /**
     * Resolve a short keyword to one of the known internal frames.
     * Matches against frame title (case-insensitive substring).
     */
    JInternalFrame resolveFrame(String keyword) {
        String kw = keyword.toLowerCase();
        // check named frames first for speed
        if (kw.equals("player"))   return ui.playerFrame;
        if (kw.equals("console"))  return ui.consoleFrame;
        if (kw.equals("help") || kw.equals("about")) return ui.helpFrame;
        if (kw.contains("add") || kw.contains("songs")) return ui.addSongsFrame;
        if (kw.contains("edit") || kw.contains("playlist")) return ui.playlistEditorFrame;
        // fall back to title substring match across all desktop frames
        for (JInternalFrame f : ui.desktop.getAllFrames()) {
            if (f.getTitle().toLowerCase().contains(kw)) return f;
        }
        return null;
    }

    private Color resolveColor(String name) {
        return switch (name.toLowerCase()) {
            case "blue",   "audio"   -> new Color(100, 200, 255, 200);
            case "amber",  "data"    -> new Color(255, 180,  80, 200);
            case "green",  "log"     -> new Color(160, 230, 160, 200);
            case "purple", "control" -> new Color(220, 120, 220, 200);
            case "red"               -> new Color(220,  80,  80, 200);
            case "white"             -> new Color(220, 220, 220, 200);
            default                  -> new Color(100, 200, 255, 200);
        };
    }

    private String fmt(long totalSeconds) {
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static String ok(String msg)   { return "[OK]  " + msg; }
    private static String err(String msg)  { return "[ERR] " + msg; }
    private static String info(String msg) { return "[INF] " + msg; }

    // ── Help strings ─────────────────────────────────────────────────────────

    private static final String HELP_MAIN = """
            Commands — type 'help <group>' for details
              help [group]        this message
              clear               wipe console output
              refresh             re-scan music directory
              conn  …             Bézier connector controls
              player / pl  …      playback controls
              db  …               library / database controls
              ui  …               window and UI controls""";

    private static final String HELP_CONN = """
            conn — Bézier cable controls
              conn on              enable connector layer
              conn off             hide connector layer
              conn list            print all cables
              conn clear           remove all cables
              conn add <a> <b> [color]
                                   add cable (colors: blue amber green purple red white)
              conn rm  <a> <b>     remove cable between two frames""";

    private static final String HELP_PLAYER = """
            player / pl — playback controls
              player play <song>   play by partial name match
              player pause         pause playback
              player resume        resume playback
              player stop          stop and rewind
              player next          skip to next track
              player prev          go to previous track
              player seek <sec>    seek to position in seconds
              player vol <0-100>   set volume
              player status        show current song / position / volume""";

    private static final String HELP_DB = """
            db — library controls
              db albums                         list all albums
              db playlists                      list all user playlists
              db songs <album>                  list songs in album
              db pl-songs <playlist>            list songs in playlist
              db add-playlist <name>            create empty playlist
              db del-playlist <name>            delete playlist
              db add-song <playlist> <album> <song>
                                                add song to playlist
              db rm-song  <playlist> <index>    remove song at index
              db rename-album <old> <new>       rename album folder on disk
              db set-dir <path>                 change music directory""";

    private static final String HELP_UI = """
            ui — window / interface controls
              ui frames               list all internal frames
              ui focus  <frame>       bring frame to front
              ui show   <frame>       make frame visible
              ui hide   <frame>       hide frame
              ui move   <frame> <x> <y>
                                      reposition frame
              ui size   <frame> <w> <h>
                                      resize frame
              ui bg     <path>        set desktop background image
              ui bg     off           clear background image
              ui nowplaying <text>    override now-playing label""";
}