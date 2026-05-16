package com.noscroll.metadata

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GroqVisionResult(val title: String, val author: String)

object GroqVisionClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val API_KEY get() = com.noscroll.BuildConfig.GROQ_API_KEY
    private const val MODEL = "llama-3.2-11b-vision-preview"

    fun identify(bitmap: Bitmap): GroqVisionResult? {
        val base64 = bitmapToBase64(bitmap) ?: return null
        val body = buildBody(base64)
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $API_KEY")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GroqVision", "HTTP ${response.code}: ${response.message}")
                    return null
                }
                parseResponse(response.body?.string() ?: return null)
            }
        } catch (e: Exception) {
            Log.e("GroqVision", "Network error: ${e.message}")
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String? = try {
        val maxWidth = 768
        val scaled = if (bitmap.width > maxWidth) {
            val scale = maxWidth.toFloat() / bitmap.width
            Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * scale).toInt(), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (scaled !== bitmap) scaled.recycle()
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { null }

    private fun buildBody(base64: String): String = JSONObject().apply {
        put("model", MODEL)
        put("max_tokens", 100)
        put("temperature", 0)
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64"))
                    })
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", "This is a book cover. What is the title and author?\nReply with exactly:\nTitle: <title>\nAuthor: <author>\nIf author is unknown write Author: Unknown")
                    })
                })
            })
        })
    }.toString()

    private fun parseResponse(json: String): GroqVisionResult? = try {
        val text = JSONObject(json)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
        var title: String? = null
        var author: String? = null
        for (line in text.lines()) {
            when {
                line.startsWith("Title:", ignoreCase = true) ->
                    title = line.substringAfter(":").trim().takeIf { it.isNotBlank() }
                line.startsWith("Author:", ignoreCase = true) ->
                    author = line.substringAfter(":").trim()
                        .takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            }
        }
        title?.let { GroqVisionResult(it, author ?: "Unknown Author") }
    } catch (_: Exception) { null }
}
