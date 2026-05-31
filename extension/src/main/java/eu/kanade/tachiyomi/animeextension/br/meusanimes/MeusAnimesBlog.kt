package eu.kanade.tachiyomi.animeextension.br.meusanimes

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

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
        val hasNext = doc.select("a.arrow_pag").any()
        return AnimesPage(animes, hasNext)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET("$baseUrl/page/$page/?s=$query")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/g/em-lancamento/page/$page/")

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("article.item.tvshows").map { el ->
            SAnime.create().apply {
                title = el.select("h3 a").text()
                setUrlWithoutDomain(el.select("h3 a").attr("href"))
                thumbnail_url = el.select("img").attr("src")
            }
        }
        val hasNext = doc.select("a.arrow_pag").any()
        return AnimesPage(animes, hasNext)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("div.result-item").mapNotNull { el ->
            val link = el.select("div.title a").first() ?: return@mapNotNull null
            val href = link.attr("href")
            if (href.isBlank()) return@mapNotNull null
            SAnime.create().apply {
                title = link.text()
                setUrlWithoutDomain(href)
                thumbnail_url = el.select("img").attr("src")
            }
        }
        val hasNext = doc.select("a.arrow_pag").any()
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
        return doc.select("#seasons ul.episodios li").mapNotNull { el ->
            val link = el.select(".episodiotitle a").first() ?: return@mapNotNull null
            val href = link.attr("href")
            if (href.isBlank()) return@mapNotNull null
            val epNum = extractEpNum(el)
            SEpisode.create().apply {
                setUrlWithoutDomain(href)
                name = link.text().ifBlank { "Episodio $epNum" }
                episode_number = epNum.toFloat()
                date_upload = 0L
            }
        }
            .sortedByDescending { it.episode_number }
    }

    private fun resolveEpisodeVideo(tmdb: String, season: String, episode: String): List<Video> {
        val apiUrl = "https://serv01.meusdoramas.club/posts/get-video.php?tmdb=$tmdb&season_number=$season&episode_number=$episode"
        try {
            val apiResponse = client.newCall(GET(apiUrl)).execute()
            val body = apiResponse.body!!.string()
            val json = JSONObject(body)
            if (json.optBoolean("success", false)) {
                val rawVideo = json.get("videoUrl")
                if (rawVideo is JSONArray) {
                    return (0 until rawVideo.length()).map { i ->
                        val source = rawVideo.getJSONObject(i)
                        val url = source.getString("file")
                        val label = source.optString("label", "Servidor ${i + 1}")
                        Video(videoUrl = url, videoTitle = label)
                    }
                }
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val doc = client.newCall(GET("$baseUrl${episode.url}")).execute().asJsoup()
        val iframe = doc.select("#playex iframe").first() ?: return emptyList()
        val src = iframe.attr("src")

        val hashMatch = Regex("#/video/(\\d+)/(\\d+)/(\\d+)/").find(src)
        if (hashMatch != null) {
            val (tmdb, season, ep) = hashMatch.destructured
            val direct = resolveEpisodeVideo(tmdb, season, ep)
            if (direct.isNotEmpty()) {
                return listOf(
                    Hoster(
                        hosterName = "MeusAnimes",
                        videoList = direct,
                    ),
                )
            }

            return listOf(
                Hoster(
                    hosterUrl = src,
                    hosterName = "Servidor 1",
                    lazy = true,
                ),
            )
            }
        }

        return listOf(
            Hoster(
                hosterUrl = src,
                hosterName = "Servidor 1",
                lazy = true,
            ),
        )
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        hoster.videoList?.let { return it }
        return emptyList()
    }

    override fun seasonListParse(response: Response): List<SAnime> = emptyList()

    override fun hosterListParse(response: Response): List<Hoster> = emptyList()

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

    private fun extractEpNum(el: org.jsoup.nodes.Element): String {
        val numerando = el.select(".numerando").text()
        val regex = Regex("""\d+(?:\.\d+)?""")
        val afterDash = numerando.substringAfter(" - ").trim()
        return regex.find(afterDash)?.value ?: "1"
    }
}
