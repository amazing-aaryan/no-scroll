package com.noscroll.metadata

import android.content.Context
import android.net.Uri
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noscroll.data.BookMetadataEntity
import kotlinx.coroutines.launch

object EditMetadataDialog {
    fun show(
        context: Context,
        uri: Uri,
        current: BookMetadataEntity?,
        onSaved: (BookMetadataEntity) -> Unit
    ) {
        val titleInput = EditText(context).apply {
            hint = "Title"
            setText(current?.title.orEmpty())
            setSingleLine(true)
        }
        val authorInput = EditText(context).apply {
            hint = "Author"
            setText(current?.author.orEmpty())
            setSingleLine(true)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0)
            addView(titleInput)
            addView(authorInput)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Edit book info")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    val saved = BookMetadataRepository.saveManual(
                        context,
                        uri,
                        titleInput.text.toString(),
                        authorInput.text.toString()
                    )
                    onSaved(saved)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
