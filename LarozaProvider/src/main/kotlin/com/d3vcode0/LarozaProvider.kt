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
        val document        = app.get(url).document
        val title           = document.selectFirst("div[itemprop=video] h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("link[rel=image_src]")?.attr("href"))
        val description     = document.selectFirst("div.pm-video-info-contents p:nth-child(2)")?.text()?.trim() ?: return null
        val recommendations = document.select("div#pm-related ul li").mapNotNull {
            it.toSearchResult()
        }

        return if (url.contains("video")) {
            if (title.contains("فيلم")) {
                    newMovieLoadResponse(title.getCleaned(), url, TvType.Movie, url) {
                        this.posterUrl = poster
                        this.plot = description
                        this.recommendations = recommendations
                }
            } else {
                val episodes = document.select("div.SeasonsEpisodesMain div").map {
                    val href = fixUrlNull(it.selectFirst("a")?.attr("href"))
                    val name = it.selectFirst("a")?.text()?.trim()
                    // val image = null
                    val ep = it.selectFirst("a em")?.text()?.toIntOrNull()
                    val season = it.attr("data-serie")?.toIntOrNull()

                    Episode(
                        href,
                        name,
                        season,
                        ep
                    )
                }

                newTvSeriesLoadResponse(title.getCleaned(), url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                    this.recommendations = recommendations
                }
            }
        } else {
            val title = document.selectFirst("div.pm-series-meta h1")?.text()
            val poster = fixUrlNull(document.selectFirst("div.pm-poster-img img")?.attr("src"))
            val description = document.selectFirst("div.description p")?.text()

            newMovieLoadResponse(title.getCleaned(), url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
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