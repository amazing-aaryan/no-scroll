package com.noscroll.metadata

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class OpenLibraryResult(
    val title: String,
    val author: String,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val coverUrl: String? = null
)

object OpenLibraryClient {
    private val client = OkHttpClient()

    fun lookupIsbn(isbn: String): OpenLibraryResult? {
        val clean = isbn.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
        if (clean.isBlank()) return null
        return try {
            val request = Request.Builder()
                .url("https://openlibrary.org/isbn/$clean.json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val title = json.optString("title").trim()
                if (title.isBlank()) return null
                val authorKey = json.optJSONArray("authors")
                    ?.optJSONObject(0)
                    ?.optString("key")
                    ?.takeIf { it.isNotBlank() }
                val author = if (authorKey != null) resolveAuthorName(authorKey) ?: "Unknown Author"
                             else "Unknown Author"
                val covers = json.optJSONArray("covers")
                val coverUrl = covers?.optLong(0)?.takeIf { it > 0L }?.let {
                    "https://covers.openlibrary.org/b/id/$it-M.jpg"
                }
                OpenLibraryResult(
                    title = title,
                    author = author,
                    isbn13 = clean.takeIf { it.length == 13 },
                    isbn10 = clean.takeIf { it.length == 10 },
                    coverUrl = coverUrl
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveAuthorName(key: String): String? {
        val path = if (key.startsWith("/")) key else "/$key"
        return try {
            val request = Request.Builder()
                .url("https://openlibrary.org$path.json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JSONObject(body).optString("name").trim().takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun search(query: String): OpenLibraryResult? {
        val clean = query.trim().take(100)
        if (clean.isBlank()) return null
        return try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val request = Request.Builder()
                .url("https://openlibrary.org/search.json?q=$encoded&limit=1")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val doc = JSONObject(body).optJSONArray("docs")?.optJSONObject(0) ?: return null
                val title = doc.optString("title").trim()
                if (title.isBlank()) return null
                val author = doc.optJSONArray("author_name")?.optString(0)?.trim().orEmpty()
                val isbn = doc.optJSONArray("isbn")
                OpenLibraryResult(
                    title = title,
                    author = author.ifBlank { "Unknown Author" },
                    isbn13 = firstIsbn(isbn, 13),
                    isbn10 = firstIsbn(isbn, 10)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun firstIsbn(array: org.json.JSONArray?, length: Int): String? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            val candidate = array.optString(i).filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
            if (candidate.length == length) return candidate
        }
        return null
    }
}
