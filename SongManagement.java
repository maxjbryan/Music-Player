import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages two distinct types of files:
 *
 *   ALBUMS   — folders that exist on disk under ~/music Directory.
 *              Each folder is an album (downloaded via yt-dlp or otherwise).
 *              These are read-only from the player's perspective.
 *
 *   PLAYLISTS — user-created ordered lists of songs drawn from one or more albums.
 *               Stored as plain .m3u files under playlistDirectory so they survive
 *               app restarts.  Each line in the .m3u is an absolute file path.
 *               user-created playlist .m3u files are stored in ~/music/.playlists
 *
 * The displayedPlaylist / currentPlaylistModel pair is shared between both views:
 * switching to an album loads that album's songs into the model;
 * switching to a playlist loads the playlist's songs.
 */
public class SongManagement {

    // directories
    String musicDirectory    = "/home/nyx/Documents/music/";        // album folders live here
    String playlistDirectory = "/home/nyx/Documents/music/.playlists/"; // .m3u files live here

    // albums (physical folders)
    /** album name → list of songs */
    HashMap<String, ArrayList<Song>> albums      = new HashMap<>();
    HashMap<String, DefaultListModel<String>> albumModels = new HashMap<>();

    // user playlists
    /** playlist name → list of songs (may span multiple albums) */
    HashMap<String, ArrayList<Song>> playlists      = new HashMap<>();
    HashMap<String, DefaultListModel<String>> playlistModels = new HashMap<>();

    // shared display state
    String currentName  = null;   // whichever album or playlist is active
    boolean currentIsAlbum = true;

    DefaultListModel<String> currentPlaylistModel = new DefaultListModel<>();
    JList<String> displayedPlaylist = new JList<>(currentPlaylistModel);

    //  Initialisation

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

        // Load albums every sub-directory that isn't .playlists
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


    //  Album loading


    void loadAlbum(String albumName) {
        File folder = new File(musicDirectory + albumName);
        if (!folder.exists() || !folder.isDirectory()) return;

        // Build a stem→cover path lookup from the covers/ sub-folder.
        // This lets us assign coverPath at load time so getSongCover() returns
        // the right per-track thumbnail without needing a playlist .m3u save first.
        HashMap<String, String> stemToJpg = new HashMap<>();
        File coversDir = new File(folder, "covers");
        if (coversDir.isDirectory()) {
            File[] coverFiles = coversDir.listFiles((d, n) -> {
                String low = n.toLowerCase();
                return (low.endsWith(".jpg") || low.endsWith(".png"))
                        && !low.equals("cover.jpg") && !low.equals("cover.png");
            });
            if (coverFiles != null) {
                for (File jpg : coverFiles) {
                    // Key is the filename stem, lower-cased for case-insensitive matching
                    String stem = jpg.getName().replaceAll("(?i)\\.(jpg|jpeg|png)$", "").toLowerCase();
                    stemToJpg.put(stem, jpg.getAbsolutePath());
                }
            }
        }

        File[] wavFiles = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".wav"));

        ArrayList<Song> songs = new ArrayList<>();
        DefaultListModel<String> model = new DefaultListModel<>();

        if (wavFiles != null) {
            // Sort alphabetically so track order is deterministic
            Arrays.sort(wavFiles, Comparator.comparing(File::getName));
            for (File f : wavFiles) {
                Song s = new Song();
                s.setFilePath(f.getAbsolutePath());
                s.name       = toDisplayName(f.getName());
                s.sourceName = albumName;

                // Match wav stem to per-song cover (case-insensitive)
                String stem = f.getName().replaceAll("(?i)\\.wav$", "").toLowerCase();
                String coverPath = stemToJpg.get(stem);
                if (coverPath != null) {
                    s.coverPath = coverPath;
                    System.out.println("  " + f.getName() + " -> cover: " + coverPath);
                } else {
                    System.out.println("  " + f.getName() + " (no per-song cover)");
                }

                songs.add(s);
                model.addElement(s.name);
            }
        }

        albums.put(albumName, songs);
        albumModels.put(albumName, model);
    }


    //  Playlist persistence  (.m3u)


    /**
     * Load a single .m3u file and register it as a user playlist.
     *
     * Extended M3U format used here:
     *   #EXTM3U
     *   #COVER:/absolute/path/to/cover.jpg   <- optional per-song cover (our custom tag)
     *   /absolute/path/to/song.wav
     *
     * The #COVER: line immediately before a file-path line sets the cover for that
     * specific song. All other # lines are ignored so the file stays valid M3U and
     * third-party players just skip the extra comments.
     * Songs without a preceding #COVER: line fall back to the album-folder cover.
     */
    private void loadPlaylistFile(File m3u) {
        String name = m3u.getName().replaceAll("\\.m3u$", "");
        ArrayList<Song> songs = new ArrayList<>();
        DefaultListModel<String> model = new DefaultListModel<>();

        try (BufferedReader br = new BufferedReader(new FileReader(m3u))) {
            String line;
            String pendingCover = null; // set by #COVER: line, consumed by the next file path
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#COVER:")) {
                    // Our custom per-song cover tag
                    String cp = line.substring("#COVER:".length()).trim();
                    pendingCover = cp.isEmpty() ? null : cp;
                    continue;
                }

                if (line.startsWith("#")) {
                    // Any other M3U directive (e.g. #EXTM3U, #EXTINF) — skip but keep pendingCover
                    continue;
                }

                // Regular file-path line
                File f = new File(line);
                if (f.exists()) {
                    Song s = new Song();
                    s.setFilePath(f.getAbsolutePath());
                    s.name         = toDisplayName(f.getName());
                    s.sourceName   = f.getParentFile().getName();
                    s.playlistName = name;

                    // 1. Use the #COVER: path from the .m3u if it still exists on disk
                    if (pendingCover != null && new File(pendingCover).exists()) {
                        s.coverPath = pendingCover;
                    } else {
                        // 2. Fallback: auto-detect from covers/ sub-folder by stem name.
                        //    Handles songs whose .m3u was written before covers/ existed,
                        //    or where the stored path has moved / been deleted.
                        String stem = f.getName().replaceAll("(?i)\\.wav$", "");
                        File coversDir = new File(f.getParentFile(), "covers");
                        File jpgCandidate  = new File(coversDir, stem + ".jpg");
                        File jpegCandidate = new File(coversDir, stem + ".jpeg");
                        File pngCandidate  = new File(coversDir, stem + ".png");
                        if (jpgCandidate.exists())       s.coverPath = jpgCandidate.getAbsolutePath();
                        else if (jpegCandidate.exists()) s.coverPath = jpegCandidate.getAbsolutePath();
                        else if (pngCandidate.exists())  s.coverPath = pngCandidate.getAbsolutePath();
                    }
                    songs.add(s);
                    model.addElement(s.name);
                }
                pendingCover = null; // consumed — reset for the next song
            }
        } catch (IOException e) {
            System.err.println("Could not read playlist " + m3u.getName() + ": " + e.getMessage());
        }

        playlists.put(name, songs);
        playlistModels.put(name, model);
    }

    /**
     * Saves a user playlist to disk as a .m3u file.
     * When a song has a non-null coverPath a "#COVER:" comment line is written
     * immediately before that song's path so it is reloaded on next startup.
     * The file is still valid standard M3U — players that don't understand
     * "#COVER:" simply ignore it as a comment.
     * Call this every time songs are added/removed/reordered.
     */
    void savePlaylist(String playlistName) {
        ArrayList<Song> songs = playlists.get(playlistName);
        if (songs == null) return;

        File m3u = new File(playlistDirectory + playlistName + ".m3u");
        try (PrintWriter pw = new PrintWriter(new FileWriter(m3u))) {
            pw.println("#EXTM3U");
            for (Song s : songs) {
                // Write per-song cover tag only when a custom cover is set
                if (s.coverPath != null && !s.coverPath.isEmpty()) {
                    pw.println("#COVER:" + s.coverPath);
                }
                pw.println(s.filePath);
            }
        } catch (IOException e) {
            System.err.println("Could not save playlist " + playlistName + ": " + e.getMessage());
        }
    }


    //  Playlist CRUD


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
        copy.coverPath    = song.coverPath;  // preserve any per-song cover override

        songs.add(copy);
        model.addElement(copy.name);
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
     * Moves a song within a playlist from {@code fromIndex} to {@code toIndex}.
     * Both the backing ArrayList and the Swing model are updated atomically and
     * the change is persisted to disk.
     *
     * @param playlistName the playlist to reorder
     * @param fromIndex    current position of the song
     * @param toIndex      desired position (after the move)
     */
    void moveSong(String playlistName, int fromIndex, int toIndex) {
        ArrayList<Song> songs = playlists.get(playlistName);
        DefaultListModel<String> model = playlistModels.get(playlistName);
        if (songs == null || model == null) return;
        int size = songs.size();
        if (fromIndex < 0 || fromIndex >= size) return;
        if (toIndex   < 0 || toIndex   >= size) return;
        if (fromIndex == toIndex) return;

        // Move in backing list
        Song song = songs.remove(fromIndex);
        songs.add(toIndex, song);

        // Mirror in Swing model
        String name = model.remove(fromIndex);
        model.add(toIndex, name);

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


    //  Switching what is displayed


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

    //  queries

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
     * Scans the album folder for the first .jpg or .png file and returns it,
     * or null if none is found. Used for displaying album art in the player.
     */
    File getAlbumCover(String albumName) {
        if (albumName == null) return null;
        File folder = new File(musicDirectory + albumName);
        if (!folder.exists() || !folder.isDirectory()) return null;
        File[] images = folder.listFiles((d, n) -> {
            String low = n.toLowerCase();
            return low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png");
        });
        if (images == null || images.length == 0) return null;
        // Prefer a file literally named "cover.*" or "thumbnail.*" if present
        for (File f : images) {
            String low = f.getName().toLowerCase();
            if (low.startsWith("cover") || low.startsWith("thumbnail") || low.startsWith("folder")) return f;
        }
        return images[0];  // fallback: first image found
    }

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

    /**
     * Returns the cover art file for a specific song, respecting per-song overrides.
     *
     * Resolution order:
     *   1. song.coverPath  — a per-song image set explicitly (e.g. from a playlist edit)
     *   2. getAlbumCover(song.sourceName) — the album folder's cover image
     *   3. null — no cover art found anywhere
     *
     * This is the single place PlayerUI should call for cover resolution; it works
     * for both album songs (coverPath is always null) and playlist songs.
     */
    File getSongCover(Song song) {
        if (song == null) return null;
        // Per-song override wins
        if (song.coverPath != null && !song.coverPath.isEmpty()) {
            File f = new File(song.coverPath);
            if (f.exists()) return f;
            // Override path broken — fall through to album cover
            System.err.println("Warning: coverPath missing for " + song.name + ": " + song.coverPath);
        }
        // Fall back to album folder cover (existing behaviour)
        return getAlbumCover(song.sourceName);
    }

    /**
     * Sets a per-song cover image path for the given song inside a playlist and
     * immediately persists the change to disk.
     *
     * @param playlistName the playlist that contains the song
     * @param songIndex    index of the song within that playlist
     * @param coverPath    absolute path to the image, or null to clear the override
     */
    void setSongCover(String playlistName, int songIndex, String coverPath) {
        ArrayList<Song> songs = playlists.get(playlistName);
        if (songs == null || songIndex < 0 || songIndex >= songs.size()) return;
        songs.get(songIndex).coverPath = (coverPath == null || coverPath.isEmpty()) ? null : coverPath;
        savePlaylist(playlistName);
    }

    //  Refresh

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

    //  Legacy stubs  (keep old call-sites compiling)

    /** @deprecated PlayerUI now calls switchToAlbum / switchToPlaylist directly. */
    @Deprecated
    void AddSongs(PlayerUI ui) { /* no-op — replaced by AddSongsFrame */ }
    // ── Display helpers ───────────────────────────────────────────────────────

    /**
     * Converts a raw .wav filename into a human-readable display name:
     *   "My_Cool_Song.wav"  →  "My Cool Song"
     * Used everywhere a song name is shown in the UI.
     */
    static String toDisplayName(String filename) {
        return filename
                .replaceAll("(?i)\\.wav$", "")  // strip extension
                .replace('_', ' ');              // underscores → spaces
    }


}