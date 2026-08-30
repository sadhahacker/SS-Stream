// Use an integer for version numbers
version = 4

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "Universal multi-server streaming engine combining VidCore, VidLink, Videasy and more, with in-app priority switching and automatic failover"
    authors = listOf("StreamCore")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     */
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=vidcore.org&sz=%size%"

    // Not cross-platform: uses android.content.Context / AlertDialog for the in-app settings UI.
}
