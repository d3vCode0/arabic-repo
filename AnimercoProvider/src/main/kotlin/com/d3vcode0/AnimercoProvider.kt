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
        "$mainUrl/animes/" to "قائمة الأنمي",
        "$mainUrl/movies/" to "قائمة الأفلام",
        "$mainUrl/seasons/" to "قائمة المواسم",
        "$mainUrl/episodes/" to "قائمة الحلقات ",
        "$mainUrl/schedule/" to "يعرض اليوم",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val regex = Regex("animes|seasons|movies")
        val isTrue = regex.containsMatchIn(request.data)
        val home = if(isTrue){

            val document = app.get(request.data + "page/$page/").document
            val list = document.select("div.page-content .row div.box-5x1").mapNotNull {
                it.toSearchResult()
            }
            HomePageList(request.name, list, false)

        } else if(request.data.contains("episodes")) {

            val document = app.get(request.data + "page/$page/").document
            val list = document.select("div.page-content .row div.col-12").mapNotNull {
                it.toSearchResult()
            }
            HomePageList(request.name, list, true)

        }else {

            val now = LocalDate.now()
            val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
            val document = app.get(request.data).document

            val list = document.select("div.tabs-wraper div#$weekday div.box-5x1").mapNotNull {
                val title = it.selectFirst("div.info h3")!!.text()
                val href = it.selectFirst("a")!!.attr("href")
                val posterUrl = it.selectFirst("a")!!.attr("data-src")

                newAnimeSearchResponse(title, href, TvType.Anime) {
                    this.posterUrl = posterUrl
                }
            }
            HomePageList(request.name, list, false)

        }
        return newHomePageResponse(home, hasNext = !request.name.contains("Schedule"))
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val titleJap = document.selectFirst("div.media-title h1")?.text()?.trim() ?: return null
        val titleEng = document.selectFirst("div.media-title h3")?.text()?.trim() ?: return null
        val posterUrlBg = document.selectFirst("div.head-box div.banner")?.attr("data-src") ?: return null
        val posterUrl = document.selectFirst("div.anime-card .image")?.attr("data-src") ?: return null
        val tags = document.select("div.genres a").mapNotNull{ it?.text()?.trim() }
        val plot = document.selectFirst("div.content p")?.text()?.trim() ?: return null
        val tv = document.selectFirst("ul.media-info li:contains(النوع:) span")?.text() ?: return null
        val tvType = if(!tv?.contains("Movie")) TvType.AnimeMovie else TvType.Anime
        val status = document.selectFirst("div.status")?.text()?.trim() ?: return null
        val showStatus = if(!status?.contains("مكتمل")) ShowStatus.Completed else if(!status?.contains("يعرض الأن")) ShowStatus.Ongoing else null

        val regex = Regex("animes|seasons|movies")
        val txt = url.split("/")[3]
        val isTrue = regex.containsMatchIn(txt)
        return if(isTrue) {
            //Animes & Seasons
            //TODO: add episodes
            newAnimeLoadResponse(titleEng, url, TvType.Anime, true) {
                this.engName = titleEng
                this.japName = titleJap
                this.posterUrl = posterUrl
                this.plot = plot
                this.tags = tags
                this.showStatus = showStatus
                this.backgroundPosterUrl = posterUrlBg
            }
        } else {
            //Movies & Episodes
            //TODO: Add more
            newMovieLoadResponse(titleEng, url, TvType.Movie, url){
                this.posterUrl = posterUrl
                this.plot = plot
                this.tags = tags
                this.backgroundPosterUrl = posterUrlBg
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.info h3")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("a")?.attr("data-src") ?: return null
        val e = this.selectFirst("a.episode")?.text()?.trim()?.replace("الحلقة ", "") ?: return null
        val s = this.selectFirst("a.extra")?.text()?.trim()?.replace("الموسم ", "") ?: return null

        return if(href.contains("movies")) {
            newMovieSearchResponse(title, href, TvType.AnimeMovie) {
                this.posterUrl = posterUrl
            }
        } else {
            newAnimeSearchResponse("$title Se-$e", href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
    }

}