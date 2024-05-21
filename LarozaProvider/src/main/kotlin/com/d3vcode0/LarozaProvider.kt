package com.d3vcode0

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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
        "$mainUrl/category.php?cat=indian-movies3&page=" to "افلام هندية",
        "$mainUrl/category.php?cat=asian-movies&page=" to "افلام اسيوي",
        "$mainUrl/category.php?cat=anime-movies&page=" to "افلام انمي",
        "$mainUrl/category.php?cat=aflammdblgh&page=" to "افلام مدبلجة",
        "$mainUrl/category.php?cat=arabic-series30&page=" to "مسلسلات عربية",
        "$mainUrl/category.php?cat=english-series3&page=" to "مسلسلات اجنبية",
        "$mainUrl/category.php?cat=turkish-3isk-seriess30&page=" to "مسلسلات تركية",
        "$mainUrl/category.php?cat=4indian-series&page=" to "مسلسلات هندية",
        "$mainUrl/category.php?cat=tv-programs5&page=" to "برامج تلفزيون",
        "$mainUrl/category.php?cat=masrh1&page=" to "مسرحيات",
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

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search.php?keywords=$query").document
        return document.select("ul#pm-grid li").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val headers = mapOf(
            "Cookie" to "cf_clearance=AGzjkrApVlNVztoL5dam._vK8aBKz4Vw_rwcY4M4ZAs-1716325046-1.0.1.1-adGoMrbK_neowLkFCWGAxa0MoMrInL2n4LpOKYp4LjQDaE3FXD5nx6WaiCLG319If8AcdCuUUVrsjTXqU49KFQ; _ga_JNMLXWW1J7=GS1.1.1716321739.12.1.1716322082.0.0.0; _ga=GA1.2.1433621871.1716127242; _ga_1E14BEYV8H=GS1.1.1716321739.12.1.1716322082.60.0.0; _gid=GA1.2.2114091540.1716127243; PHPSESSID=3eba63e1220a90f37d99c3ad10b03db1; pm_elastic_player=normal",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0"
        )

        val document = app.get(
            url,
            timeout=80,
            headers = headers,
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