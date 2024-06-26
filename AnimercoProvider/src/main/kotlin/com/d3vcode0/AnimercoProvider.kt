package com.d3vcode0

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.nicehttp.NiceResponse
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
    private val interceptor = CloudflareKiller()
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
        val headers = mapOf(
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Safari/537.36",
            "referer" to "$mainUrl/"
        )
        if (request.name.contains("الحلقات المثبتة")) {
            val document = app.get(request.data, headers = headers, timeout = 40).document
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
            val document = app.get(request.data, headers = headers, timeout = 40).document
            val home = document.select("div.tabs-wraper div#$weekday div.box-5x1").mapNotNull {
                it.toSearchSchedule()
            }
            return newHomePageResponse(
                name = request.name,
                list = home,
                hasNext = false
            )
        } else {
            val document = app.get(request.data + "page/${page}/", headers = headers, timeout = 40).document
            val home = document.select("div.page-content .row div.box-5x1").mapNotNull {
                it.toSearchResult()
            }
            return newHomePageResponse(request.name, home)
        }
    }

       override suspend fun load(url: String): LoadResponse? {
        val document  = avoidCloudflare(url).document

        val title     = document.selectFirst("div.head-box div.media-title h3")?.text()?.trim() ?: document.selectFirst("div.head-box div.media-title h1")?.text()?.trim() ?: return null
        val bgImage   = fixUrlNull(document.selectFirst("div.banner")?.attr("data-src")) ?: return null
        val posterUrl = fixUrlNull(document.selectFirst("div.anime-card div.image")?.attr("data-src")) ?: fixUrlNull(document.selectFirst("div.head-box div.banner")?.attr("data-src")) ?: return null
        val tags      = document.select("div.genres a").mapNotNull{ it?.text()?.trim() } ?: return null
        val plot      = document.selectFirst("div.content p")?.text()?.trim() ?: return null
        val trailer   = fixUrlNull(document.selectFirst("button#btn-trailer")?.attr("data-href")) ?: return null
        val rating    = document.selectFirst("span.score")?.text()?.toRatingInt() ?: return null
        val year      = document.selectFirst("ul.media-info li:contains(بداية العرض:) a")?.text()?.toIntOrNull() ?: return null
        val duration  = document.selectFirst("ul.media-info li:contains(مدة الحلقة:) span")?.text()?.getIntFromText() ?: return null

        Log.d("D3V > title",title)
        Log.d("D3V > bg",bgImage)
        Log.d("D3V > poster",posterUrl)
        Log.d("D3V > tags",tags)
        Log.d("D3V > plot",plot)
        Log.d("D3V > trailer",trailer)
        Log.d("D3V > rating",rating)
        Log.d("D3V > year", year)
        Log.d("D3V > dur", duration)
        return newAnimeLoadResponse(title, url, TvType.Anime) {
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

    suspend fun avoidCloudflare(url: String): NiceResponse {
        if (!app.get(url).isSuccessful) {
            return app.get(url, interceptor = interceptor)
        } else {
            return app.get(url)
        }
    }
}