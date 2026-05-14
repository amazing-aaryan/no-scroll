package com.noscroll

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfPageAdapter(
    private val renderer: PdfRenderer,
    private val screenWidth: Int
) : RecyclerView.Adapter<PdfPageAdapter.PageHolder>() {

    private val renderLock = Any()

    override fun getItemCount(): Int = renderer.pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_page, parent, false)
        return PageHolder(view)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.bind(position)
    }

    override fun onViewRecycled(holder: PageHolder) {
        holder.cancel()
    }

    inner class PageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.page_image)
        private var renderJob: Job? = null

        fun bind(pageIndex: Int) {
            imageView.setImageBitmap(null)
            renderJob?.cancel()
            renderJob = CoroutineScope(Dispatchers.IO).launch {
                val bitmap = renderPage(pageIndex)
                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }

        fun cancel() {
            renderJob?.cancel()
            renderJob = null
        }

        private fun renderPage(index: Int): Bitmap {
            synchronized(renderLock) {
                renderer.openPage(index).use { page ->
                    val scale = screenWidth.toFloat() / page.width
                    val bitmap = Bitmap.createBitmap(
                        screenWidth, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }
}
