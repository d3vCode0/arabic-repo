package com.d3vcode0

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class CimawbasProvider : MainAPI() {
    override var mainUrl = "https://cimawbas.tv"
    override var name = "Cimawbas"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    // ! CloudFlare bypass
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 50L
    override var sequentialMainPageScrollDelay = 50L

    override val mainPage = mainPageOf(
        "${mainUrl}/category.php?cat=1-cimawbas-movies-online&page=" to "Movies",
        "${mainUrl}/category.php?cat=watch-series-online-cimawbas&page=" to "Series Ar",
        "${mainUrl}/category.php?cat=english-series-1&page=" to "Series En",
        "${mainUrl}/category.php?cat=animition&page=" to "Anime",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}&order=DESC").document
        val home = document.select("ul#pm-grid li").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                home,
                isHorizontalImages = true
            )
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.caption h3")?.text() ?: return null
        val href = this.selectFirst("div.caption h3 a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img.img-responsive")?.attr("data-echo") ?: return null

        return if (title.contains("فيلم")) {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        } else if (title.contains("مسلسل")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else if (title.contains("انمي") ||  title.contains("انمى")) {
            newTvSeriesSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/search.php?keywords=${query}&video-id=").document
        return document.select("ul#pm-grid li").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1[itemprop=name]")?.text()?.trim() ?: return null

        val ep = document.select("div.AiredEPS a")
        Log.d("Episode", "If True » ${ep}")
        val episodes : List<Episode> = if (ep.toString().length > 1) {
            ep.map {
                Episode(
                    data = it.attr("href").replace("watch", "see"),
                    name = it.text()
                )
            }
        } else {
            emptyList()
        }
        
        return if (title.contains("فيلم")) {
            newMovieLoadResponse(title, url, TvType.Movie, url.replace("watch", "see")) {
                this.posterUrl = posterUrl
            }
        } else if (title.contains("مسلسل")) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
            }
        } else if (title.contains("انمي") ||  title.contains("انمى")) {
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url.replace("watch", "see")) {
                this.posterUrl = posterUrl
            }
        }
    }
}