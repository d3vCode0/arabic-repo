version = 2



cloudstream {
    language = "ar"
    // All of these properties are optional, you can safely remove them

    description = "انمي ركو"
    authors = listOf("d3vCode0")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AnimeMovie",
        "Anime",
    )

    iconUrl = "https://ww3.animerco.org/wp-content/uploads/2024/03/cropped-android-chrome-512x512-1-192x192.png"
}