/**
 * Data class
 * sourceName   the album folder the file lives in  (always set)
 * playlistName the user playlist this song belongs to (null when browsing albums directly)
 * coverPath    optional per-song override for album art (null = fall back to album folder cover).
 *              Stored in playlist .m3u files via a  #COVER:<path>  comment line so the format
 *              stays valid M3U and existing parsers ignore the extra comment.
 */
public class Song {
    public String filePath;
    public String name;
    public String sourceName;    // album folder name (the physical folder on disk)
    public String playlistName;  // user-created playlist name, or null

    /**
     * Absolute path to an image file that should be used as cover art for this
     * specific song inside a playlist.  When null the player falls back to the
     * album-folder cover returned by {@link SongManagement#getAlbumCover}.
     *
     * This field is intentionally ignored for album songs (where sourceName is
     * always the authoritative source of truth for cover art).
     */
    public String coverPath;     // per-song cover override, or null

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