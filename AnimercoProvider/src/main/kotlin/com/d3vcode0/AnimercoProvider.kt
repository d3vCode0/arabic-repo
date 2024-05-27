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
    val now = LocalDate.now()
    val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()

    override val mainPage = mainPageOf(
        "$mainUrl/schedule/" to "يعرض اليوم ${weekday.toDayar()}",
        "$mainUrl/" to "الحلقات المثبتة",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.name.contains("schedule")) {
            val document = app.get(request.data).document
            val home = document.select("div.tabs-wraper div#$weekday div.box-5x1").mapNotNull {
                it.toSearchSchedule()
            }
            return newHomePageResponse(
                name = request.name,
                list = home,
                hasNext = false
            )
        } else if (request.name.contains("episodes")) {
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
        }
    }

    private fun Element.toSearchSchedule(): SearchResponse? {
        val title = this.selectFirst("div.info h3")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a")?.attr("data-src")) ?: return null
        val season = this.selectFirst("div.info a.extra h4")?.text()?.trim()?.replace("الموسم ", "") ?: return null

        return newAnimeSearchResponse("${title} S${season}", href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus()
        }
    }

    private fun Element.toSearchEpisode(): SearchResponse? {
        title = this.selectFirst("div.info h3")?.text()?.trim() ?: return null
        href = fixUrlNull(this.selectFirst("div.info a")?.attr("href")) ?: return null
        poster = fixUrlNull(this.selectFirst("a.image")?.attr("data-src")) ?: return null
        episode = this.selectFirst("div.info a.badge")?.text()?.trim()?.replace("الحلقة ", "") ?: return null
        season = this.selectFirst("div.info span.anime-type")?.text()?.trim()?.replace("الموسم ", "") ?: return null

        newAnimeSearchResponse("${title} S${season}-E${episode}", href, TvType.Anime) {
            this.posterUrl = poster
            addDubStatus(false, episode?.toIntOrNull())
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
}