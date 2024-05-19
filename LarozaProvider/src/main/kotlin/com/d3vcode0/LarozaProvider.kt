package com.d3vcode0

import com.lagradost.cloudstream3.*
import org.jsoup.nodes.Element


class LarozaProvider : MainAPI() {
    override var mainUrl = "https://g.laroza.net"
    override var name = "Laroza"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/category.php?cat=all_movies&page=" to "افلام اجنبية",
        "$mainUrl/category.php?cat=arabic-movies17&page=" to "افلام عربية",
        "$mainUrl/moslslat.php?&page=" to "المسلسلات",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + "$page&order=DESC").document
        val home = document.select("ul#pm-grid li").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(HomePageList(
            name = request.name,
            home,
            isHorizontalImages = true
        ))
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.caption h3")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("div.caption a.ellipsis")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img.img-responsive")?.attr("data-echo")) ?: return null

        return if (href.contains("video")) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }
}