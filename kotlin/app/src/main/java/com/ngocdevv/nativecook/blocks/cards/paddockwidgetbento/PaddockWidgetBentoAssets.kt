package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal class PaddockWidgetBentoAssets(context: Context) {
    val statsStack: ImageBitmap = context.assets.open("paddockwidgetbento/paddockwidgetbento_stats_stack.png").use {
        BitmapFactory.decodeStream(it).asImageBitmap()
    }
    val bannerPhoto: ImageBitmap = context.assets.open("paddockwidgetbento/paddockwidgetbento_banner_photo.jpg").use {
        BitmapFactory.decodeStream(it).asImageBitmap()
    }
}
