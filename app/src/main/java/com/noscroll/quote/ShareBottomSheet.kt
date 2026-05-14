package com.noscroll.quote

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.noscroll.R

class ShareBottomSheet : BottomSheetDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_share, null)
        dialog.setContentView(view)
        view.findViewById<MaterialButton>(R.id.share_stories_btn).setOnClickListener {
            BitmapHolder.bitmap?.let { bitmap -> InstagramShareHelper.shareToStories(requireActivity(), bitmap) }
            dismiss()
        }
        view.findViewById<MaterialButton>(R.id.share_feed_btn).setOnClickListener {
            BitmapHolder.bitmap?.let { bitmap -> InstagramShareHelper.shareToFeed(requireActivity(), bitmap) }
            dismiss()
        }
        view.findViewById<MaterialButton>(R.id.share_generic_btn).setOnClickListener {
            BitmapHolder.bitmap?.let { bitmap -> InstagramShareHelper.shareGeneric(requireActivity(), bitmap) }
            dismiss()
        }
        return dialog
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        BitmapHolder.bitmap = null
        super.onDismiss(dialog)
    }

    companion object {
        fun newInstance(bitmap: android.graphics.Bitmap): ShareBottomSheet {
            BitmapHolder.bitmap = bitmap
            return ShareBottomSheet()
        }
    }
}
