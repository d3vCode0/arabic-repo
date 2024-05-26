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
        if ("${request.data}".contains("episodes")) {
            val document = app.get(request.data + "page/${page}/").document
            val home = document.select("div.page-content .row div.col-12").mapNotNull {
                it.toSearchResult()
            }
            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = true
                ),
                hasNext = true
            )
        } else if("${request.data}".contains("schedule")) {
            val now = LocalDate.now()
            val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
            val document = app.get(request.data).document
            val home = document.select("div.tabs-wraper div#$weekday div.box-5x1").mapNotNull {
                it.toSearchSchedule()
            }
            return newHomePageResponse(request.name, home)
        } else {
            val document = app.get(request.data + "page/${page}/").document
            val home = document.select("div.page-content .row div.box-5x1").mapNotNull {
                it.toSearchResult()
            }
            return newHomePageResponse(request.name, home)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val titleJap = document.selectFirst("div.media-title h1")?.text()?.trim() ?: return null
        val titleEng = document.selectFirst("div.media-title h3")?.text()?.trim() ?: return null
        val posterUrl = document.selectFirst("div.anime-card .image")?.attr("data-src") ?: return null

        return if (url.contains("movies")) {
            newAnimeLoadResponse(titleEng, url, TvType.Anime, true) {
                this.engName = titleEng
                this.japName = titleJap
                this.posterUrl = posterUrl
            }
        } else if (url.contains("animes")) {
            //get list seasons > episodes
            newAnimeLoadResponse(titleEng, url, TvType.Anime, true) {
                this.engName = titleEng
                this.japName = titleJap
                this.posterUrl = posterUrl
            }
        } else if (url.contains("seasons")) {
            //list episodes
            newAnimeLoadResponse(titleEng, url, TvType.Anime, true) {
                this.engName = titleEng
                this.japName = titleJap
                this.posterUrl = posterUrl
            }
        } else {
            //episode
            val title = document.selectFirst("div.container h1")?.text()?.trim() ?: return null
            val posterUrl = document.selectFirst("a#click-player")?.attr("data-src") ?: return null
            newMovieLoadResponse(title, url, TvType.Anime, url) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.info h3")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("a")?.attr("data-src") ?: return null
        return if (href.contains("movies")) {
            newMovieSearchResponse(title, href, TvType.AnimeMovie) {
                this.posterUrl = posterUrl
            }
        } else if (href.contains("episodes")) {
            val e = this.selectFirst("a.episode")?.text()?.trim()?.replace("الحلقة ", "") ?: return null
            val s = this.selectFirst("a.extra")?.text()?.trim()?.replace("الموسم ", "") ?: return null
            newAnimeSearchResponse("${title} S${s}-E${e}", href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        } else if (href.contains("seasons")) {
            val s = this.selectFirst("div.info a.extra h4")?.text()?.trim()?.replace("الموسم ", "") ?: return null
            val t = if(s.isNullOrEmpty()) title else "${title} S${s}"
            newAnimeSearchResponse(t, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        } else {
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun Element.toSearchSchedule(): SearchResponse? {
        val title = this.selectFirst("div.info h3")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a")?.attr("data-src")) ?: return null
        val season = this.selectFirst("div.info a.extra h4")?.text()?.trim()?.replace("الموسم ", "") ?: return null

        return newAnimeSearchResponse("${title} S${season}", href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }
}