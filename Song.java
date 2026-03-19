/**
 * Data class representing a single audio file.
 *
 * sourceName  — the album folder the file lives in  (always set)
 * playlistName — the user playlist this song belongs to (null when browsing albums directly)
 */
public class Song {
    public String filePath;
    public String name;
    public String sourceName;    // album folder name (the physical folder on disk)
    public String playlistName;  // user-created playlist name, or null

    // Legacy field alias so existing code that references song.FilePath still compiles.
    /** @deprecated use {@link #filePath} */
    @Deprecated
    public String FilePath;

    /** Convenience: keep both fields in sync when set via the old name. */
    public void setFilePath(String path) {
        this.filePath = path;
        this.FilePath = path;
    }
}