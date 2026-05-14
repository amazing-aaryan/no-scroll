package com.noscroll

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.noscroll.databinding.ItemPdfAddBinding
import com.noscroll.databinding.ItemPdfEntryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfLibraryAdapter(
    private val onSelect: (PdfEntry) -> Unit,
    private val onDelete: (PdfEntry) -> Unit,
    private val onAddNew: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<PdfEntry>()
    private var selectedUri: String? = null

    companion object {
        private const val TYPE_BOOK = 0
        private const val TYPE_ADD = 1
    }

    fun submitList(newItems: List<PdfEntry>, selected: String?) {
        items.clear()
        items.addAll(newItems)
        selectedUri = selected
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position < items.size) TYPE_BOOK else TYPE_ADD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_BOOK) {
            BookHolder(ItemPdfEntryBinding.inflate(inflater, parent, false))
        } else {
            AddHolder(ItemPdfAddBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is BookHolder) holder.bind(items[position])
        else if (holder is AddHolder) holder.bind()
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is BookHolder) holder.cancelLoad()
        super.onViewRecycled(holder)
    }

    inner class BookHolder(private val binding: ItemPdfEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var loadJob: Job? = null

        fun cancelLoad() {
            loadJob?.cancel()
            loadJob = null
        }

        fun bind(entry: PdfEntry) {
            val isSelected = entry.uri == selectedUri
            binding.pdfTitle.text = entry.displayName
            binding.checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.root.strokeColor =
                if (isSelected) binding.root.context.getColor(R.color.colorAccent)
                else Color.TRANSPARENT
            binding.root.setCardBackgroundColor(
                if (isSelected) binding.root.context.getColor(R.color.colorPrimary)
                else binding.root.context.getColor(R.color.card_bg)
            )
            binding.root.setOnClickListener { onSelect(entry) }
            binding.deleteBtn.setOnClickListener { onDelete(entry) }

            // Load thumbnail
            cancelLoad()
            val ctx = binding.root.context
            val cached = PdfThumbnailCache.thumbnailFile(ctx, entry.uri)
            if (cached.exists()) {
                val bmp = BitmapFactory.decodeFile(cached.absolutePath)
                binding.bookIcon.setImageBitmap(bmp)
                binding.bookIcon.colorFilter = null
            } else {
                binding.bookIcon.setImageResource(R.drawable.ic_book)
                binding.bookIcon.setColorFilter(0xAAFFFFFF.toInt())
                loadJob = CoroutineScope(Dispatchers.Main).launch {
                    val file = PdfThumbnailCache.getOrCreate(ctx, entry.uri)
                    if (file != null) {
                        val bmp = withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(file.absolutePath)
                        }
                        binding.bookIcon.setImageBitmap(bmp)
                        binding.bookIcon.colorFilter = null
                    }
                }
            }
        }
    }

    inner class AddHolder(private val binding: ItemPdfAddBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.root.setOnClickListener { onAddNew() }
        }
    }
}
