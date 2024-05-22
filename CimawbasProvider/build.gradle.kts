version = 1



cloudstream {
    language = "ar"
    // All of these properties are optional, you can safely remove them

    description = "تحميل ومشاهدة افلام اون لاين اجنبية وعربية وهندية وكورية واسيوية وتركية"
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
        "Movie",
        "TvSeries",
        "Anime"
    )

    iconUrl = "https://mycima.cc/uploads/custom-logo.png"
}