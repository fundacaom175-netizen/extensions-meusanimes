package eu.kanade.tachiyomi.animeextension.br.meusanimes

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response

class MeusAnimesBlog : AnimeHttpSource() {

    override val name = "Meus Animes"
    override val baseUrl = "https://meusanimes.blog"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/a/page/$page/")

    override fun popularAnimeParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("article.item.tvshows").map { el ->
            SAnime.create().apply {
                title = el.select("h3 a").text()
                setUrlWithoutDomain(el.select("h3 a").attr("href"))
                thumbnail_url = el.select("img").attr("src")
            }
        }
        val hasNext = doc.select("a.next").any()
        return AnimesPage(animes, hasNext)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET("$baseUrl/page/$page/?s=$query")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/lancamento/page/$page/")

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("article.item.tvshows").map { el ->
            SAnime.create().apply {
                title = el.select("h3 a").text()
                setUrlWithoutDomain(el.select("h3 a").attr("href"))
                thumbnail_url = el.select("img").attr("src")
            }
        }
        val hasNext = doc.select("a.next").any()
        return AnimesPage(animes, hasNext)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("article.item.tvshows").mapNotNull { el ->
            val link = el.select("h3 a")
            if (link.text().isBlank()) return@mapNotNull null
            SAnime.create().apply {
                title = link.text()
                setUrlWithoutDomain(link.attr("href"))
                thumbnail_url = el.select("img").attr("src")
            }
        }
        val hasNext = doc.select("a.next").any()
        return AnimesPage(animes, hasNext)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.asJsoup()
        return SAnime.create().apply {
            title = doc.select("h1").text()
                .ifBlank { doc.select("title").text().replace(" - Meus Animes", "").trim() }
            thumbnail_url = doc.select("meta[property=og:image]").attr("content")
            description = doc.select("meta[property=og:description]").attr("content")
            genre = doc.select(".generos a").joinToString { it.text() }
            status = parseStatus(doc.text())
        }
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = response.asJsoup()
        return doc.select("#seasons article, .episodios li, li[class^=mark-]")
            .mapNotNull { el ->
                val link = el.select("a").first() ?: return@mapNotNull null
                val href = link.attr("href")
                if (href.isBlank()) return@mapNotNull null
                val epNum = extractEpNum(href, link.text())
                SEpisode.create().apply {
                    setUrlWithoutDomain(href)
                    name = link.text().ifBlank { "Episodio $epNum" }
                    episode_number = epNum.toFloat()
                    date_upload = 0L
                }
            }
            .sortedByDescending { it.episode_number }
    }

    override fun videoListParse(response: Response): List<Video> {
        val doc = response.asJsoup()
        val iframe = doc.select("iframe").first() ?: return emptyList()
        val src = iframe.attr("src")
        val quality = doc.select(".qualidade, .quality").text().ifBlank { "HD" }
        return listOf(Video(src, "Servidor 1 ($quality)", src))
    }

    override fun videoUrlParse(response: Response): String {
        return response.request.url.toString()
    }

    private fun parseStatus(text: String): Int {
        return when {
            text.contains("Em Lancamento", ignoreCase = true) -> SAnime.ONGOING
            text.contains("Completo", ignoreCase = true) -> SAnime.COMPLETED
            text.contains("Cancelado", ignoreCase = true) -> SAnime.CANCELLED
            else -> SAnime.UNKNOWN
        }
    }

    private fun extractEpNum(href: String, text: String): String {
        val regex = Regex("""\d+(?:\.\d+)?""")
        val fromHref = regex.find(href.substringAfterLast("-"))?.value
        val epText = text.substringAfter("Episodio").substringAfter("Episodio")
        val fromText = regex.find(epText)?.value
        return fromText ?: fromHref ?: "1"
    }
}
