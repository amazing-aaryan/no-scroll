package com.noscroll.quote

import android.app.Dialog
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.noscroll.ui.NoScrollTheme
import com.noscroll.ui.PaperColors

class ShareBottomSheet : BottomSheetDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext())
        val view = ComposeView(requireContext()).apply {
            setContent {
                NoScrollTheme {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(PaperColors.Raised)
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        ShareRow("Instagram Stories") {
                            BitmapHolder.bitmap?.let { bitmap -> InstagramShareHelper.shareToStories(requireActivity(), bitmap) }
                            dismiss()
                        }
                        ShareRow("Instagram Feed") {
                            BitmapHolder.bitmap?.let { bitmap -> InstagramShareHelper.shareToFeed(requireActivity(), bitmap) }
                            dismiss()
                        }
                        ShareRow("Instagram Direct") {
                            BitmapHolder.bitmap?.let { bitmap -> InstagramShareHelper.shareToDirect(requireActivity(), bitmap) }
                            dismiss()
                        }
                        ShareRow("Messages") {
                            BitmapHolder.bitmap?.let { bitmap ->
                                InstagramShareHelper.shareMessages(requireActivity(), bitmap, BitmapHolder.shareText.orEmpty())
                            }
                            dismiss()
                        }
                        ShareRow("More") {
                            BitmapHolder.bitmap?.let { bitmap ->
                                InstagramShareHelper.shareGeneric(requireActivity(), bitmap, BitmapHolder.shareText)
                            }
                            dismiss()
                        }
                    }
                }
            }
        }
        dialog.setContentView(view)
        return dialog
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        BitmapHolder.shareText = null
        super.onDismiss(dialog)
    }

    companion object {
        fun newInstance(bitmap: android.graphics.Bitmap, shareText: String? = null): ShareBottomSheet {
            BitmapHolder.bitmap = bitmap
            BitmapHolder.shareText = shareText
            return ShareBottomSheet()
        }
    }
}

@androidx.compose.runtime.Composable
private fun ShareRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
