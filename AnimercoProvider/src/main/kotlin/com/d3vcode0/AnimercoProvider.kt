package com.d3vcode0

import com.lagradost.cloudstream3.*
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class AnimercoProvider : MainAPI() {
    override var mainUrl = "https://ww3.animerco.org"
    override var name = "Animerco"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/animes/" to "Animes",
        "$mainUrl/seasons/" to "Seasons",
        "$mainUrl/episodes/" to "Episodes",
        "$mainUrl/schedule/" to "Schedule",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val regex = Regex("animes|seasons")
        val isTrue = regex.containsMatchIn(request.data)
        val home = if(isTrue){

            val document = app.get(request.data + "page/$page/").document
            document.select("div.page-content .row div.box-5x1").mapNotNull {
                it.toSearchResult()
            }

        } else if(request.data.contains("episodes")) {

            val document = app.get(request.data + "page/$page/").document
            document.select("div.page-content .row div.col-12").mapNotNull {
                it.toSearchResult()
            }

        }else {

            val now = LocalDate.now()
            val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
            val document = app.get(request.data).document

            document.select("div.tabs-wraper div#$weekday div.box-5x1").mapNotNull {
                val title = it.selectFirst("div.info h3")!!.text()
                val href = it.selectFirst("a")!!.attr("href")
                val posterUrl = it.selectFirst("a")!!.attr("data-src")

                newAnimeSearchResponse(title, href, TvType.Anime) {
                    this.posterUrl = posterUrl
                }
            }

        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.info h3")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("a")?.attr("data-src") ?: return null

        return if(href.contains("movies")) {
            newMovieSearchResponse(title, href, TvType.AnimeMovie) {
                this.posterUrl = posterUrl
            }
        } else {
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
    }

}