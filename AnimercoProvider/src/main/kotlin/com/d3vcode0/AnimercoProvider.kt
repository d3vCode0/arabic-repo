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
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home = document.select("div.tabs-wraper div#$weekday div.box-5x1").mapNotNull {
            it.toSearchSchedule()
        }
        return newHomePageResponse(
            name = request.name,
            list = home,
            hasNext = false
        )
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