package com.d3vcode0

import com.lagradost.cloudstream3.*
import org.jsoup.nodes.Element

class CimalekProvider : MainAPI() {
    override var mainUrl = "https://cimalek.art"
    override var name = "Cimalek"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf(
        "${mainUrl}/recent/movies/" to "Movies",
        "${mainUrl}/recent/series/" to "Series",
        "${mainUrl}/recent/animes/" to "Animes",
        // "${mainUrl}/recent/episodes/" to "Episodes",
        // "${mainUrl}/recent/anime-episodes/" to "Anime Episodes",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse  {
        val doc = if(page == 1){
            app.get(request.data).document
        } else {
            app.get(request.data + "page/$page").document
        }
        val list = doc.select("div.film_list-wrap div.item").mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(request.name, list)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = this.selectFirst("div.data div.title")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img.film-poster-img")?.attr("data-src")) ?: fixUrlNull(this.selectFirst("a img.film-poster-img")?.attr("src"))
        val quality = this.selectFirst("div.quality")?.text()?.trim() ?: return null

        return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = convertToQuality(quality)
            }
        // return if (href.contains("/movies/")) {
        //     newMovieSearchResponse(title, href, TvType.Movie) {
        //         this.posterUrl = posterUrl
        //         this.quality = convertToQuality(quality)
        //     }
        // } else if (href.contains("/series/")) {
        //     newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
        //         this.posterUrl = posterUrl
        //     }
        // } else if (href.contains("/animes/")) {
        //     newAnimeSearchResponse(title, href, TvType.Anime) {
        //         this.posterUrl = posterUrl
        //     }
        // } else {
        //     newMovieSearchResponse(title, href, TvType.Others) {
        //         this.posterUrl = posterUrl
        //     }
        // }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("div.anisc-detail h2")?.text()?.trim() ?: return null
        val poster = doc.selectFirst("div.film-poster img")?.attr("src")

        return newMovieLoadResponse(title, url + "watch/", TvType.AnimeMovie, url + "watch/") {
            this.posterUrl = poster
        }
    }

    fun convertToQuality(input: String): SearchQuality? {
        return when (input) {
            "1080P-WEB-DL" -> SearchQuality.WebRip
            "1080P-WEB" -> SearchQuality.WebRip
            "720P-WEB" -> SearchQuality.WebRip
            "1080P-BLURAY" -> SearchQuality.BlueRay
            "720P-BLURAY" -> SearchQuality.BlueRay
            "BLURAY" -> SearchQuality.BlueRay
            "HD" -> SearchQuality.HD
            "HDCAM" -> SearchQuality.HdCam
            "CAM" -> SearchQuality.Cam
            else -> null
        }
    }
}