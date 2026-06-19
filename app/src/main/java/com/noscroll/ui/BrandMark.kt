package com.noscroll.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noscroll.R

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 10.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .border(1.dp, PaperColors.Hairline, shape)
    ) {
        Image(
            painter = painterResource(R.drawable.noscroll_logo_inverted_128),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
