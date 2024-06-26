version = 2



cloudstream {
    language = "ar"
    // All of these properties are optional, you can safely remove them

    description = "مشاهدة وتحميل جميع الافلام الاجنبية الجديدة والحصرية بجودة عالية"
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
        "Anime",
    )

    iconUrl = "https://g.laroza.net/uploads/custom-logo.png"
}