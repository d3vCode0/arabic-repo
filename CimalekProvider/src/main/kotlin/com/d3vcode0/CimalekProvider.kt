package com.d3vcode0

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CimalekProvider : MainAPI() {
    override var mainUrl = "https://cimalek.art"
    override var name = "Cimalek"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf(
        "${mainUrl}/recent/movies/page/" to "Movies",
        "${mainUrl}/recent/series/page/" to "Series",
        "${mainUrl}/recent/animes/page/" to "Animes",
        // "${mainUrl}/recent/episodes/" to "Episodes",
        // "${mainUrl}/recent/anime-episodes/" to "Anime Episodes",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse  {
        val doc = if(page == 1){
            app.get(request.data.replace("page/", ""), timeout = 120).document
        } else {
            app.get(request.data + page, timeout = 120).document
        }

        val home = if (request.data.contains(Regex("(series|animes)"))) {
            doc.select("div.film_list-wrap div.item").mapNotNull { it.toSearchResultTv() }
        } else {
            doc.select("div.film_list-wrap div.item").mapNotNull { it.toSearchResult() }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.film_list-wrap div.item").mapNotNull {
            val title = it.selectFirst("div.data div.title")?.text()?.trim()
            val href = fixUrlNull(it.selectFirst("a")?.attr("href"))
            val posterUrl = fixUrlNull(it.selectFirst("a img.film-poster-img")?.attr("data-src"))

            if (title.contains("فيلم")) {
                val quality = it.selectFirst("div.quality")?.text()?.trim()
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.quality = convertToQuality(quality)
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("div.anisc-detail h2")?.text()?.trim() ?: return null
        val poster = doc.selectFirst("div.film-poster img")?.attr("src")
        // val year = convertDateStringToYearInt(doc.selectFirst("div.film-description div")?.text()?.trim())
        val desc = doc.selectFirst("div.film-description div")?.text()?.trim() ?: return null
        val rating = doc.selectFirst("div.anisc-detail .rating span.text span")?.text()?.trim()?.toRatingInt()
        val tags = doc.select("div.item-list a").map { it.text() }
        // val duration = doc.selectFirst("div.anisc-more-info div:contains(المدة:) span:nth-child(3)")?.text()?.trim()
        
        if (url.contains("movies")) {
            return newMovieLoadResponse(title, url + "watch/", TvType.AnimeMovie, url + "watch/") {
                this.posterUrl = poster
                // this.year = year.toIntOrNull()
                this.plot = desc
                this.rating = rating
                this.tags = tags
                // this.duration = duration
            }
        } else {
            val episodes = doc.select("ul.episodios li").map {
                it.toEpisode()
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.rating = rating
                this.tags = tags
            }
        }       
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.data div.title")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img.film-poster-img")?.attr("data-src")) ?: fixUrlNull(this.selectFirst("a img.film-poster-img")?.attr("src"))
        val quality = this.selectFirst("div.quality")?.text()?.trim() ?: return null

        return newMovieSearchResponse(title.replace("فيلم ", ""), href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = convertToQuality(quality)
            }
    }

    private fun Element.toSearchResultTv(): SearchResponse? {
        val title = this.selectFirst("div.data div.title")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img.film-poster-img")?.attr("data-src")) ?: fixUrlNull(this.selectFirst("a img.film-poster-img")?.attr("src"))

        return newTvSeriesSearchResponse(title.replace("مسلسل ", ""), href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
    }

    private fun Element.toEpisode(): Episode {
        val url = select("a").attr("href")
        val title = select("a span.serie").text().trim()
        // val thumbUrl = select("a").attr("data-src")
        return newEpisode(url) {
            name = title
            episode = title.getIntFromText()
            // posterUrl = thumbUrl
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
            "HDRIP" -> SearchQuality.HD
            "HDCAM" -> SearchQuality.HdCam
            "CAM" -> SearchQuality.Cam
            else -> null
        }
    }

    fun convertDateStringToYearInt(dateString: String): Int {
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val date = LocalDate.parse(dateString, formatter)
        return date.year
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }
}