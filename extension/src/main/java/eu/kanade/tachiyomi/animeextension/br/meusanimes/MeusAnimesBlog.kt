package eu.kanade.tachiyomi.animeextension.br.meusanimes

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class MeusAnimesBlog : AnimeHttpSource() {

    override val name = "Meus Animes"
    override val baseUrl = "https://meusanimes.blog"
    override val lang = "pt-BR"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
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

    private val bloggerExtractor by lazy { BloggerExtractor(client) }

    private fun resolveEpisodeVideo(tmdb: String, season: String, episode: String, depth: Int = 0): List<Video> {
        if (depth > 2) return emptyList()
        val apiUrl = "https://serv01.meusdoramas.club/posts/get-video.php?tmdb=$tmdb&season_number=$season&episode_number=$episode"
        return try {
            val body = client.newCall(GET(apiUrl)).execute().body!!.string()
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return emptyList()
            val rawVideo = json.get("videoUrl")
            when {
                rawVideo is JSONArray -> {
                    (0 until rawVideo.length()).flatMap { i ->
                        val obj = rawVideo.getJSONObject(i)
                        resolveVideoUrl(obj.getString("file"), obj.optString("label", "Servidor ${i + 1}"))
                    }
                }
                rawVideo is String && rawVideo.contains("/e/") -> {
                    val selectorUrl = rawVideo.replace("\\/", "/")
                        .let { if (it.startsWith("/")) "https://serv01.meusdoramas.club$it" else it }
                    parseSelectorPage(selectorUrl, "$tmdb/$season/$episode", depth)
                }
                else -> resolveVideoUrl(rawVideo.toString().replace("\\/", "/"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseSelectorPage(url: String, originalKey: String, depth: Int): List<Video> {
        return try {
            val html = client.newCall(GET(url)).execute().body!!.string()
            val servers = Regex("""iframe\.php\?[a-z]=(\d+)/(\d+)/(\d+)/""")
                .findAll(html)
                .map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
                .distinctBy { "${it.first}/${it.second}/${it.third}" }
                .filter { "${it.first}/${it.second}/${it.third}" != originalKey }
                .toList()
            servers.flatMap { (t, s, ep) ->
                resolveEpisodeVideo(t, s, ep, depth + 1)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun resolveVideoUrl(url: String, label: String = ""): List<Video> {
        val finalUrl = url.replace("\\/", "/")
        if (finalUrl.contains("blogger") || finalUrl.contains("googleusercontent") || finalUrl.contains("blogspot")) {
            val blogger = bloggerExtractor.videosFromUrl(finalUrl)
            if (blogger.isNotEmpty()) return blogger
        }
        if (finalUrl.contains("/embed/")) {
            return try {
                val embedHtml = client.newCall(GET(finalUrl)).execute().body!!.string()
                val filePattern = Regex(""""file":\s*"([^"]+)"""")
                val match = filePattern.find(embedHtml)
                if (match != null) {
                    val videoUrl = match.groupValues[1].replace("\\/", "/")
                    val quality = label.ifBlank { "Servidor" }
                    val videoHeaders = Headers.Builder()
                        .add("Referer", "https://video.meusdoramas.club/")
                        .build()
                    listOf(Video(videoUrl, quality, videoUrl, headers = videoHeaders))
                } else {
                    emptyList()
                }
            } catch (_: Exception) { emptyList() }
        }
        return listOf(Video(url = finalUrl, quality = label.ifBlank { "Servidor 1" }, videoUrl = finalUrl))
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val doc = client.newCall(GET("$baseUrl${episode.url}")).execute().asJsoup()
        val iframe = doc.select("#playex iframe").first() ?: return emptyList()
        val src = iframe.attr("abs:src")
        if (src.isBlank()) return emptyList()

        val hashMatch = Regex("#/video/(\\d+)/(\\d+)/(\\d+)/").find(src)
        if (hashMatch != null) {
            val (tmdb, season, ep) = hashMatch.destructured
            val direct = resolveEpisodeVideo(tmdb, season, ep)
            if (direct.isNotEmpty()) return direct
        }

        return try {
            val playerHtml = client.newCall(GET(src)).execute().body!!.string()

            val patterns = listOf(
                """file":\s*"([^"]+)""".toRegex(),
                """src":\s*"([^"]+)""".toRegex(),
                """videoUrl":\s*"([^"]+)""".toRegex(),
                """<source[^>]+src="([^"]+)""".toRegex(),
                """<iframe[^>]+src="([^"]+)""".toRegex(),
            )
            val urls = patterns.flatMap { regex ->
                regex.findAll(playerHtml).map { it.groupValues[1] }
            }.distinct().toList()

            if (urls.isNotEmpty()) {
                return urls.map { Video(it, "Servidor", it) }
            }

            if (playerHtml.contains("streams") || playerHtml.contains("blogger")) {
                return bloggerExtractor.videosFromUrl(src)
            }

            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
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

    private fun extractEpNum(el: org.jsoup.nodes.Element): String {
        val numerando = el.select(".numerando").text()
        val regex = Regex("""\d+(?:\.\d+)?""")
        val afterDash = numerando.substringAfter(" - ").trim()
        return regex.find(afterDash)?.value ?: "1"
    }
}
