import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages two distinct concepts:
 *
 *   ALBUMS   — folders that exist on disk under musicDirectory.
 *              Each folder is an album (downloaded via yt-dlp or otherwise).
 *              These are read-only from the player's perspective.
 *
 *   PLAYLISTS — user-created ordered lists of songs drawn from one or more albums.
 *               Stored as plain .m3u files under playlistDirectory so they survive
 *               app restarts.  Each line in the .m3u is an absolute file path.
 *
 * The displayedPlaylist / currentPlaylistModel pair is shared between both views:
 * switching to an album loads that album's songs into the model;
 * switching to a playlist loads the playlist's songs.
 */
public class SongManagement {

    // ── directories ──────────────────────────────────────────────────────────
    String musicDirectory    = "/home/nyx/Documents/music/";        // album folders live here
    String playlistDirectory = "/home/nyx/Documents/music/.playlists/"; // .m3u files live here

    // ── albums (physical folders) ─────────────────────────────────────────────
    /** album name → list of songs */
    HashMap<String, ArrayList<Song>> albums      = new HashMap<>();
    HashMap<String, DefaultListModel<String>> albumModels = new HashMap<>();

    // ── user playlists ────────────────────────────────────────────────────────
    /** playlist name → list of songs (may span multiple albums) */
    HashMap<String, ArrayList<Song>> playlists      = new HashMap<>();
    HashMap<String, DefaultListModel<String>> playlistModels = new HashMap<>();

    // ── shared display state ──────────────────────────────────────────────────
    String currentName  = null;   // whichever album or playlist is active
    boolean currentIsAlbum = true;

    DefaultListModel<String> currentPlaylistModel = new DefaultListModel<>();
    JList<String> displayedPlaylist = new JList<>(currentPlaylistModel);

    // ─────────────────────────────────────────────────────────────────────────
    //  Initialisation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scans the music directory for album folders and loads saved playlists.
     *
     * @param autoSelectFirst if true, selects the first album found automatically.
     */
    void scrapeAndADD(boolean autoSelectFirst) {
        File baseDir = new File(musicDirectory);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            System.err.println("Music directory does not exist: " + musicDirectory);
            return;
        }

        // Ensure the hidden playlist folder exists
        File plDir = new File(playlistDirectory);
        if (!plDir.exists()) plDir.mkdirs();

        // Load albums — every sub-directory that isn't .playlists
        File[] dirs = baseDir.listFiles(f -> f.isDirectory() && !f.getName().equals(".playlists"));
        if (dirs != null) {
            for (File dir : dirs) {
                loadAlbum(dir.getName());
                System.out.println("Found album: " + dir.getName());
            }
        }

        // Load saved playlists from .m3u files
        File[] m3uFiles = plDir.listFiles((d, n) -> n.toLowerCase().endsWith(".m3u"));
        if (m3uFiles != null) {
            for (File m3u : m3uFiles) {
                loadPlaylistFile(m3u);
                System.out.println("Loaded playlist: " + m3u.getName());
            }
        }

        if (autoSelectFirst && !albums.isEmpty()) {
            String first = albums.keySet().iterator().next();
            switchToAlbum(first);
        }
    }

    void scrapeAndADD() { scrapeAndADD(true); }

    // ─────────────────────────────────────────────────────────────────────────
    //  Album loading
    // ─────────────────────────────────────────────────────────────────────────

    void loadAlbum(String albumName) {
        File folder = new File(musicDirectory + albumName);
        if (!folder.exists() || !folder.isDirectory()) return;

        File[] wavFiles = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".wav"));

        ArrayList<Song> songs = new ArrayList<>();
        DefaultListModel<String> model = new DefaultListModel<>();

        if (wavFiles != null) {
            // Sort alphabetically so track order is deterministic
            Arrays.sort(wavFiles, Comparator.comparing(File::getName));
            for (File f : wavFiles) {
                Song s = new Song();
                s.setFilePath(f.getAbsolutePath());
                s.name       = f.getName();
                s.sourceName = albumName;
                songs.add(s);
                model.addElement(f.getName());
                System.out.println("  " + f.getName());
            }
        }

        albums.put(albumName, songs);
        albumModels.put(albumName, model);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Playlist persistence  (.m3u)
    // ─────────────────────────────────────────────────────────────────────────

    /** Load a single .m3u file and register it as a user playlist. */
    private void loadPlaylistFile(File m3u) {
        String name = m3u.getName().replaceAll("\\.m3u$", "");
        ArrayList<Song> songs = new ArrayList<>();
        DefaultListModel<String> model = new DefaultListModel<>();

        try (BufferedReader br = new BufferedReader(new FileReader(m3u))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                File f = new File(line);
                if (f.exists()) {
                    Song s = new Song();
                    s.setFilePath(f.getAbsolutePath());
                    s.name         = f.getName();
                    s.sourceName   = f.getParentFile().getName();
                    s.playlistName = name;
                    songs.add(s);
                    model.addElement(f.getName());
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read playlist " + m3u.getName() + ": " + e.getMessage());
        }

        playlists.put(name, songs);
        playlistModels.put(name, model);
    }

    /**
     * Saves a user playlist to disk as a .m3u file.
     * Call this every time songs are added/removed/reordered.
     */
    void savePlaylist(String playlistName) {
        ArrayList<Song> songs = playlists.get(playlistName);
        if (songs == null) return;

        File m3u = new File(playlistDirectory + playlistName + ".m3u");
        try (PrintWriter pw = new PrintWriter(new FileWriter(m3u))) {
            pw.println("#EXTM3U");
            for (Song s : songs) pw.println(s.filePath);
        } catch (IOException e) {
            System.err.println("Could not save playlist " + playlistName + ": " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Playlist CRUD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new empty user playlist and saves it to disk.
     *
     * @return false if a playlist with that name already exists.
     */
    boolean createPlaylist(String name) {
        if (playlists.containsKey(name)) return false;
        playlists.put(name, new ArrayList<>());
        playlistModels.put(name, new DefaultListModel<>());
        savePlaylist(name);
        return true;
    }

    /**
     * Adds a song (by its Song object) to a user playlist.
     * Does nothing if the song is already in the playlist.
     */
    void addSongToPlaylist(String playlistName, Song song) {
        ArrayList<Song> songs = playlists.get(playlistName);
        DefaultListModel<String> model = playlistModels.get(playlistName);
        if (songs == null || model == null) return;

        // Avoid duplicates
        for (Song existing : songs) {
            if (existing.filePath.equals(song.filePath)) return;
        }

        Song copy = new Song();
        copy.setFilePath(song.filePath);
        copy.name         = song.name;
        copy.sourceName   = song.sourceName;
        copy.playlistName = playlistName;

        songs.add(copy);
        model.addElement(song.name);
        savePlaylist(playlistName);
    }

    /**
     * Removes the song at the given index from a user playlist.
     */
    void removeSongFromPlaylist(String playlistName, int index) {
        ArrayList<Song> songs = playlists.get(playlistName);
        DefaultListModel<String> model = playlistModels.get(playlistName);
        if (songs == null || model == null) return;
        if (index < 0 || index >= songs.size()) return;

        songs.remove(index);
        model.remove(index);
        savePlaylist(playlistName);
    }

    /**
     * Deletes a user playlist from memory and from disk.
     */
    void deletePlaylist(String playlistName) {
        playlists.remove(playlistName);
        playlistModels.remove(playlistName);
        File m3u = new File(playlistDirectory + playlistName + ".m3u");
        if (m3u.exists()) m3u.delete();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Switching what is displayed
    // ─────────────────────────────────────────────────────────────────────────

    void switchToAlbum(String albumName) {
        if (!albums.containsKey(albumName)) {
            System.err.println("Album not found: " + albumName);
            return;
        }
        currentName    = albumName;
        currentIsAlbum = true;
        currentPlaylistModel = albumModels.get(albumName);
        displayedPlaylist.setModel(currentPlaylistModel);
        System.out.println("Switched to album: " + albumName);
    }

    void switchToPlaylist(String playlistName) {
        if (!playlists.containsKey(playlistName)) {
            System.err.println("Playlist not found: " + playlistName);
            return;
        }
        currentName    = playlistName;
        currentIsAlbum = false;
        currentPlaylistModel = playlistModels.get(playlistName);
        displayedPlaylist.setModel(currentPlaylistModel);
        System.out.println("Switched to playlist: " + playlistName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Queries
    // ─────────────────────────────────────────────────────────────────────────

    ArrayList<String> getAlbumNames() {
        ArrayList<String> names = new ArrayList<>(albums.keySet());
        Collections.sort(names);
        return names;
    }

    ArrayList<String> getPlaylistNames() {
        ArrayList<String> names = new ArrayList<>(playlists.keySet());
        Collections.sort(names);
        return names;
    }

    /** Returns all songs in a given album. */
    ArrayList<Song> getSongsInAlbum(String albumName) {
        return albums.getOrDefault(albumName, new ArrayList<>());
    }

    /** Returns all songs in a given user playlist. */
    ArrayList<Song> getSongsInPlaylist(String playlistName) {
        return playlists.getOrDefault(playlistName, new ArrayList<>());
    }

    String getCurrentName() { return currentName; }

    /**
     * Looks up the file path for a song by display name in whichever
     * collection is currently active (album or playlist).
     */
    String getSongPath(String songName) {
        if (currentName == null) return null;

        ArrayList<Song> songs = currentIsAlbum
                ? albums.get(currentName)
                : playlists.get(currentName);

        if (songs != null) {
            for (Song s : songs) {
                if (s.name.equals(songName)) return s.filePath;
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Refresh
    // ─────────────────────────────────────────────────────────────────────────

    void refreshPlaylists() {
        albums.clear();
        albumModels.clear();
        playlists.clear();
        playlistModels.clear();
        currentPlaylistModel.clear();
        currentName    = null;
        currentIsAlbum = true;
        scrapeAndADD(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Legacy stubs  (keep old call-sites compiling)
    // ─────────────────────────────────────────────────────────────────────────

    /** @deprecated PlayerUI now calls switchToAlbum / switchToPlaylist directly. */
    @Deprecated
    void AddSongs(PlayerUI ui) { /* no-op — replaced by AddSongsFrame */ }
}