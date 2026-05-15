package com.noscroll.metadata

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class GoogleBooksResult(
    val title: String,
    val author: String,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val coverUrl: String? = null
)

object GoogleBooksClient {
    private val client = OkHttpClient()

    fun search(query: String): GoogleBooksResult? {
        val cleanQuery = query.trim().take(100)
        if (cleanQuery.isBlank()) return null
        return try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val request = Request.Builder()
                .url("https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=5&printType=books")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val items = JSONObject(body).optJSONArray("items") ?: return null
                val candidates = mutableListOf<GoogleBooksResult>()
                for (index in 0 until items.length()) {
                    val volumeInfo = items.optJSONObject(index)?.optJSONObject("volumeInfo") ?: continue
                    val title = volumeInfo.optString("title").trim()
                    if (title.isBlank()) continue
                    val authors = volumeInfo.optJSONArray("authors")
                    val author = authors?.optString(0)?.trim().orEmpty()
                    val identifiers = volumeInfo.optJSONArray("industryIdentifiers")
                    var isbn13: String? = null
                    var isbn10: String? = null
                    if (identifiers != null) {
                        for (i in 0 until identifiers.length()) {
                            val item = identifiers.optJSONObject(i) ?: continue
                            when (item.optString("type")) {
                                "ISBN_13" -> isbn13 = item.optString("identifier").takeIf { it.isNotBlank() }
                                "ISBN_10" -> isbn10 = item.optString("identifier").takeIf { it.isNotBlank() }
                            }
                        }
                    }
                    val coverUrl = volumeInfo.optJSONObject("imageLinks")
                        ?.optString("thumbnail")
                        ?.takeIf { it.isNotBlank() }
                    candidates += GoogleBooksResult(title, author.ifBlank { "Unknown Author" }, isbn13, isbn10, coverUrl)
                }
                candidates.maxByOrNull { score(query, it.title, it.author) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun score(query: String, title: String, author: String): Int {
        val haystack = "$title $author".lowercase()
        val tokenScore = query.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 }
            .distinct()
            .fold(0) { score, token -> score + if (haystack.contains(token)) 2 else 0 }
        return tokenScore + if (author != "Unknown Author") 1 else 0
    }
}
