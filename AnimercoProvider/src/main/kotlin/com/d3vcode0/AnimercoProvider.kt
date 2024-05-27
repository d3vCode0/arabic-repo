package com.d3vcode0

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
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
    val now = LocalDate.now()
    val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()

    override val mainPage = mainPageOf(
        "$mainUrl/schedule/" to "يعرض اليوم ${weekday.toDayar()}",
        "$mainUrl/" to "الحلقات المثبتة",
        "$mainUrl/animes/" to "قائمة الأنمي",
        "$mainUrl/movies/" to "قائمة الأفلام",
        "$mainUrl/seasons/" to "قائمة المواسم"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.name.contains("الحلقات المثبتة")) {
            val document = app.get(request.data).document
            val home = document.select("div.media-section div.row div.col-12").mapNotNull {
                it.toSearchEpisode()
            }
            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = true
                ),
                hasNext = false
            )
        } else if (request.name.contains("يعرض")) {
            val document = app.get(request.data).document
            val home = document.select("div.tabs-wraper div#$weekday div.box-5x1").mapNotNull {
                it.toSearchSchedule()
            }
            return newHomePageResponse(
                name = request.name,
                list = home,
                hasNext = false
            )
        } else {
            val document = app.get(request.data + "page/${page}/").document
            val home = document.select("div.page-content .row div.box-5x1").mapNotNull {
                it.toSearchResult()
            }
            return newHomePageResponse(request.name, home)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document  = app.get(url).document
        val titleJap  = document.selectFirst("div.media-title h1")?.text()?.trim() ?: return null
        val titleEng  = document.selectFirst("div.media-title h3")?.text()?.trim() ?: return null
        val posterUrl = document.selectFirst("div.anime-card .image")?.attr("data-src") ?: return null
        val bgImage   = document.selectFirst("div.banner")?.attr("data-src") ?: return null
        val tags      = document.select("div.genres a").mapNotNull{ it?.text()?.trim() }
        val plot      = document.selectFirst("div.content p")?.text()?.trim() ?: return null
        val trailer   = document.selectFirst("button#btn-trailer")?.attr("data-href") ?: return null
        val rating    = document.selectFirst("span.score")?.text()?.toRatingInt() ?: return null
        val year      = document.selectFirst("ul.media-info li:contains(بداية العرض:) a")?.text()?.toIntOrNull() ?: return null
        val duration  = document.selectFirst("ul.media-info li:contains(مدة الحلقة:) span")?.text() ?: return null

        if (url.contains("movies")) {
            return newMovieLoadResponse(titleEng ?: titleJap, url, TvType.AnimeMovie, url) {
                this.name                = titleJap
                this.posterUrl           = posterUrl
                this.year                = year
                this.plot                = plot
                this.rating              = rating
                this.tags                = tags
                this.duration            = duration.getIntFromText()
                this.backgroundPosterUrl = bgImage
                addTrailer(trailer)
            }
        }
        else if (url.contains("animes")) {
            val episodes = mutableListOf<Episode>()
            document.select("ul.episodes-lists li").map { ele ->
                val page = ele.selectFirst("a.title")?.attr("href") ?: return@map
                val epsDoc = app.get(page).document
                epsDoc.select("ul.episodes-lists li").mapNotNull { eps ->
                    episodes.add(
                        Episode(
                            eps.selectFirst("a.title")?.attr("href") ?: return@mapNotNull null,
                            season = ele?.attr("data-number")?.toIntOrNull(),
                            episode = eps.selectFirst("a.title h3")?.text()?.getIntFromText(),
                            posterUrl = eps.selectFirst("a.image")?.attr("data-src") ?: return@mapNotNull null,
                        )
                    )
                }
            }
        }
        return newAnimeLoadResponse(titleEng ?: titleJap, url, TvType.Anime, true) {
            this.name                = titleJap
            this.posterUrl           = posterUrl
            this.year                = year
            this.plot                = plot
            this.rating              = rating
            this.tags                = tags
            this.duration            = duration.getIntFromText()
            this.backgroundPosterUrl = bgImage
            addTrailer(trailer)
            addEpisodes(DubStatus.Subbed, episodes)
        }
        // else if (url.contains("seasons")) {}
        // else if (url.contains("episodes")) {}
        else {
            return newMovieLoadResponse("NO FIND", url, TvType.AnimeMovie, url) {
                this.posterUrl = "https://img.freepik.com/premium-vector/search-result-find-illustration_585024-17.jpg"
                this.plot      = "NO DATA"
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

    private fun Element.toSearchEpisode(): SearchResponse? {
        val title = this.selectFirst("div.info h3")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("div.info a")?.attr("href")) ?: return null
        val poster = fixUrlNull(this.selectFirst("a.image")?.attr("data-src")) ?: return null
        val episode = this.selectFirst("div.info a.badge")?.text()?.trim()?.replace("الحلقة ", "") ?: return null
        val season = this.selectFirst("div.info span.anime-type")?.text()?.trim()?.replace("الموسم ", "") ?: return null

        return newAnimeSearchResponse("${title} S${season}", href, TvType.Anime) {
            this.posterUrl = poster
            addSub(episodes = episode?.toIntOrNull())
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

    fun String.toDayar(): String {
        return when(this) {
            "monday" -> "الإثنين"
            "tuesday" -> "الثلاثاء"
            "wednesday" -> "الأربعاء"
            "thursday" -> "الخميس"
            "friday" -> "الجمعة"
            "saturday" -> "السبت"
            "sunday" -> "الأحد"
            else -> "error"
        }
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }
}