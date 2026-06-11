package com.noscroll

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class PdfEntry(val uri: String, val displayName: String)

object PdfStorage {

    private const val PREFS_NAME = "noscroll_prefs"
    private const val KEY_PDF_URI = "pdf_uri"
    private const val KEY_PDF_PAGE = "pdf_page"
    private const val KEY_PDF_LIBRARY = "pdf_library"
    private const val KEY_SELECTED_URI = "pdf_selected_uri"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedUri(context: Context): Uri? =
        prefs(context).getString(KEY_PDF_URI, null)?.let { Uri.parse(it) }

    fun saveUri(context: Context, uri: Uri) {
        prefs(context).edit().putString(KEY_PDF_URI, uri.toString()).apply()
    }

    fun clearUri(context: Context) {
        prefs(context).edit()
            .remove(KEY_PDF_URI)
            .remove(KEY_PDF_PAGE)
            .remove(KEY_SELECTED_URI)
            .apply()
    }

    fun getSavedPage(context: Context): Int =
        prefs(context).getInt(KEY_PDF_PAGE, 0)

    fun savePage(context: Context, page: Int) {
        prefs(context).edit().putInt(KEY_PDF_PAGE, page).apply()
    }

    fun getLibrary(context: Context): List<PdfEntry> {
        val raw = prefs(context).getString(KEY_PDF_LIBRARY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PdfEntry(obj.getString("uri"), obj.getString("displayName"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addToLibrary(context: Context, uri: String, displayName: String) {
        val current = getLibrary(context).toMutableList()
        if (current.none { it.uri == uri }) {
            current.add(PdfEntry(uri, displayName))
            saveLibrary(context, current)
        }
    }

    fun removeFromLibrary(context: Context, uri: String) {
        val updated = getLibrary(context).filter { it.uri != uri }
        saveLibrary(context, updated)
        val selected = prefs(context).getString(KEY_SELECTED_URI, null)
        if (selected == uri) {
            prefs(context).edit().remove(KEY_SELECTED_URI).apply()
            clearUri(context)
        }
    }

    fun setSelected(context: Context, uri: String) {
        prefs(context).edit().putString(KEY_SELECTED_URI, uri).apply()
        saveUri(context, Uri.parse(uri))
    }

    fun getSelectedUri(context: Context): Uri? =
        prefs(context).getString(KEY_SELECTED_URI, null)?.let { Uri.parse(it) }
            ?: getSavedUri(context)

    private fun saveLibrary(context: Context, entries: List<PdfEntry>) {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(JSONObject().apply {
                put("uri", entry.uri)
                put("displayName", entry.displayName)
            })
        }
        prefs(context).edit().putString(KEY_PDF_LIBRARY, arr.toString()).apply()
    }
}
