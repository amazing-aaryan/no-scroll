package com.noscroll.discovery

import com.noscroll.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

data class LegalBookSearchResult(
    val id: String?,
    val title: String,
    val author: String,
    val source: String? = null,
    val year: String? = null,
    val language: String? = null,
    val fileType: String? = null,
    val coverUrl: String? = null,
    val directDownloadUrl: String? = null
)

data class LegalBookDownloadLink(
    val label: String,
    val url: String,
    val mimeType: String? = null
)

object LegalBookApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    val configuredBaseUrl: String
        get() = BuildConfig.LEGAL_BOOKS_API_BASE_URL.trim().removeSuffix("/")

    fun isConfigured(): Boolean =
        configuredBaseUrl.isNotBlank() && configuredBaseUrl.startsWith("https://")

    /*
     Replace these endpoint paths and JSON field names with your legal backend.
     Structural equivalents only:
     - searchBooks(...) fills the role a search call would fill.
     - resolveDownloadLinks(...) fills the role a mirror/download-link resolver would fill.
     This client must only target authorized sources.
    */
    fun searchBooks(query: String): List<LegalBookSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank() || !isConfigured()) return emptyList()
        val baseUrl = configuredBaseUrl.toHttpUrlOrNull() ?: return emptyList()
        val url = baseUrl.newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", cleanQuery)
            .build()
        val payload = executeJson(url.toString()) ?: return emptyList()
        return parseSearchResults(payload)
    }

    fun resolveDownloadLinks(book: LegalBookSearchResult): List<LegalBookDownloadLink> {
        book.directDownloadUrl?.takeIf { it.isNotBlank() }?.let {
            return listOf(LegalBookDownloadLink(label = "PDF", url = it, mimeType = "application/pdf"))
        }
        val id = book.id?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (!isConfigured()) return emptyList()
        val baseUrl = configuredBaseUrl.toHttpUrlOrNull() ?: return emptyList()
        val url = baseUrl.newBuilder()
            .addPathSegment("download-links")
            .addQueryParameter("id", id)
            .build()
        val payload = executeJson(url.toString()) ?: return emptyList()
        return parseDownloadLinks(payload)
    }

    private fun executeJson(url: String): Any? =
        try {
            val request = Request.Builder()
                .url(url)
                .apply {
                    val apiKey = BuildConfig.LEGAL_BOOKS_API_KEY.trim()
                    if (apiKey.isNotBlank()) {
                        header("Authorization", "Bearer $apiKey")
                        header("X-API-Key", apiKey)
                    }
                }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()?.trim().orEmpty()
                if (body.isBlank()) return null
                JSONTokener(body).nextValue()
            }
        } catch (_: Exception) {
            null
        }

    private fun parseSearchResults(payload: Any): List<LegalBookSearchResult> {
        val results = extractArray(payload, "results", "books", "items") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                if (title.isBlank()) continue
                add(
                    LegalBookSearchResult(
                        id = item.optString("id").trim().ifBlank { null },
                        title = title,
                        author = item.optString("author").trim().ifBlank { "Unknown Author" },
                        source = item.optString("source").trim().ifBlank { null },
                        year = item.optString("year").trim().ifBlank { null },
                        language = item.optString("language").trim().ifBlank { null },
                        fileType = item.optString("extension").trim()
                            .ifBlank { item.optString("fileType").trim().ifBlank { null } },
                        coverUrl = item.optString("coverUrl").trim().ifBlank { null },
                        directDownloadUrl = item.optString("downloadUrl").trim()
                            .ifBlank { item.optString("pdfUrl").trim().ifBlank { null } }
                    )
                )
            }
        }
    }

    private fun parseDownloadLinks(payload: Any): List<LegalBookDownloadLink> {
        val results = extractArray(payload, "links", "downloads", "mirrors") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (url.isBlank()) continue
                add(
                    LegalBookDownloadLink(
                        label = item.optString("label").trim().ifBlank {
                            item.optString("name").trim().ifBlank { "Download" }
                        },
                        url = url,
                        mimeType = item.optString("mimeType").trim().ifBlank { null }
                    )
                )
            }
        }
    }

    private fun extractArray(payload: Any, vararg keys: String): JSONArray? =
        when (payload) {
            is JSONArray -> payload
            is JSONObject -> keys.firstNotNullOfOrNull { key -> payload.optJSONArray(key) }
            else -> null
        }
}
