package com.d3vcode0

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element
import android.util.Log
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
            val titleElement = it.selectFirst("div.data div.title")
            val hrefElement = it.selectFirst("a")
            val posterElement = it.selectFirst("a img.film-poster-img")
            
            val title = titleElement?.text()?.trim()
            val href = fixUrlNull(hrefElement?.attr("href"))
            val posterUrl = fixUrlNull(posterElement?.attr("data-src"))

            if (title != null && href != null) {
                if (title.contains("فيلم")) {
                    val qualityElement = it.selectFirst("div.quality")
                    val quality = qualityElement?.text()?.trim()
                    newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = posterUrl
                        this.quality = convertToQuality(quality ?: "")
                    }
                } else {
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                    }
                }
            } else {
                null
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

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        document.select("div.ps_-block.ajax_mode div.item").map {
            Triple(
                it.selectFirst("div").attr("data-type"),
                it.selectFirst("div").attr("data-post"),
                it.selectFirst("div").attr("data-nume"),
            )
        }.apmap {(id, post, nume) ->
            val script = document.selectFirst("script:contains(dtAjax)")?.text()
            val regex = Regex("""var dtAjax = (\{.*\});""")
            val ver = script?.let { regex.find(it) }
            val ran = generateRandomString(16)
            val source = app.get(
                url = "$mainUrl/wp-json/lalaplayer/v2/?p=$post&t=type&n=$nume&ver=$ver&rand=$ran",
                headers = mapOf(
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).parsed<ResponseHash>().embed_url

            when {
                !source.contains("youtube") -> loadCustomExtractor(
                    source,
                    "$mainUrl/",
                    subtitleCallback,
                    callback
                )
                else -> return@apmap
            }
        }
        return true
    }

    private suspend fun loadCustomExtractor(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            if(link.quality == Qualities.Unknown.value) {
                callback.invoke(
                    ExtractorLink(
                        link.source,
                        link.name,
                        link.url,
                        link.referer,
                        when (link.type) {
                            ExtractorLinkType.M3U8 -> link.quality
                            else -> quality ?: link.quality
                        },
                        link.type,
                        link.headers,
                        link.extractorData
                    )
                )
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

    fun generateRandomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var result = StringBuilder()
        for (i in 0 until length) {
            val randomIndex = (chars.indices).random()
            result.append(chars[randomIndex])
        }
        return result.toString()
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String?,
    )
}