package com.noscroll.metadata

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class GoogleBooksResult(val title: String, val author: String)

object GoogleBooksClient {
    private val client = OkHttpClient()

    fun search(query: String): GoogleBooksResult? {
        val cleanQuery = query.trim().take(100)
        if (cleanQuery.isBlank()) return null
        return try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val request = Request.Builder()
                .url("https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=3&printType=books")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val items = JSONObject(body).optJSONArray("items") ?: return null
                val volumeInfo = items.optJSONObject(0)?.optJSONObject("volumeInfo") ?: return null
                val title = volumeInfo.optString("title").trim()
                val authors = volumeInfo.optJSONArray("authors")
                val author = authors?.optString(0)?.trim().orEmpty()
                if (title.isBlank()) null else GoogleBooksResult(title, author.ifBlank { "Unknown Author" })
            }
        } catch (_: Exception) {
            null
        }
    }
}
