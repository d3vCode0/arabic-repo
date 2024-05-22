package com.d3vcode0

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element


class LarozaProvider : MainAPI() {
    override var mainUrl = "https://g.laroza.net"
    private  val alternativeUrl = "https://laroza.net"
    override var name = "Laroza"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    private val cfKiller = CloudflareKiller()
    
    override val mainPage = mainPageOf(
        "$mainUrl/category.php?cat=all_movies&page=" to "افلام اجنبية",
        // "$mainUrl/category.php?cat=arabic-movies17&page=" to "افلام عربية",
        // "$mainUrl/category.php?cat=indian-movies3&page=" to "افلام هندية",
        // "$mainUrl/category.php?cat=asian-movies&page=" to "افلام اسيوي",
        // "$mainUrl/category.php?cat=anime-movies&page=" to "افلام انمي",
        // "$mainUrl/category.php?cat=aflammdblgh&page=" to "افلام مدبلجة",
        // "$mainUrl/category.php?cat=arabic-series30&page=" to "مسلسلات عربية",
        "$mainUrl/category.php?cat=english-series3&page=" to "مسلسلات اجنبية",
        "$mainUrl/category.php?cat=turkish-3isk-seriess30&page=" to "مسلسلات تركية",
        // "$mainUrl/category.php?cat=4indian-series&page=" to "مسلسلات هندية",
        // "$mainUrl/category.php?cat=tv-programs5&page=" to "برامج تلفزيون",
        "$mainUrl/category.php?cat=masrh1&page=" to "مسرحيات",
    )

    private var cookies: Map<String, String> = mapOf()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get(
            request.data + "$page&order=DESC",
            interceptor = cfKiller,
            timeout = 120
        )
        cookies = res.cookies
        val document = res.document
        val home = document.select("ul#pm-grid li").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(HomePageList(
            name = request.name,
            home,
            isHorizontalImages = true
        ))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search.php?keywords=$query", interceptor = cfKiller, timeout = 120).document
        return document.select("ul#pm-grid li").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val headers = mapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Connection" to "keep-alive",
            "Referer" to "https://google.com/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36"
        )
        val document = app.get(
            url,
            interceptor = cfKiller,
            cookies = cookies,
            timeout = 120
            ).document

        val title           = document.selectFirst("div[itemprop=video] h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("link[rel=image_src]")?.attr("href"))
        val description     = document.selectFirst("div.pm-video-info-contents p:nth-child(2)")?.text()?.trim() ?: return null
        val tvType = if(title.contains("فيلم")) TvType.Movie else TvType.TvSeries
        val recommendations = document.select("div#pm-related ul li").mapNotNull {
            it.toSearchResult()
        }

        return if(tvType == TvType.TvSeries) {
            val episodes = document.select("div.SeasonsEpisodesMain div a").map {
                val name = it.selectFirst("em")?.text()
                val href = it.attr("href")
                val season = it.parent()?.attr("data-serie")?.toIntOrNull()
                val episode = name?.toIntOrNull()

                Episode(
                    href,
                    "$name الحلقة",
                    season,
                    episode
                )
            }
            newTvSeriesLoadResponse(title.getCleaned(), url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title.getCleaned(), url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.recommendations = recommendations
                this.posterHeaders = cfKiller.getCookieHeaders(alternativeUrl).toMap()
            }
        }

    }


    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.caption h3")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("div.caption a.ellipsis")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img.img-responsive")?.attr("data-echo")) ?: return null

        return if (href.contains("video")) {
            newMovieSearchResponse(title.getCleaned(), href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title.getCleaned(), href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun String.getCleaned(): String {
        val keywordsRegex = (
            "\\b(الأولى|الثانية|الثالثة|الرابعة|الخامسة|السادسة|السابعة|الثامنة|التاسعة|العاشرة|عشر|الحادية|الاولى|" +
            "العشرون|والعشرون|الثلاثون|والثلاثون|الاربعون|والاربعون|الخمسون|والخمسون|الستون|والستون|السبعون|والسبعون|الثمانون|والثمانون|التسعون|والتسعون|المائة|" +
            "وستون|الحادي|الاولي|" +
            "وواحد|واثنين|وثلاثة|واربعة|وخمسة|وستة|وسبعة|وثمانية|وتسعة|وعشرة|والحادية|" +
            "مشاهدة|فيلم|كامل اون لاين|مسلسل|HD|مترجمة|اون لاين|مترجم|مدبلج|" +
            ")\\b"
        ).toRegex(RegexOption.IGNORE_CASE)
        val cleanedText = this.replace(keywordsRegex, "")
        return cleanedText.trim().replace(Regex("\\s+"), " ")
    }
}