package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

internal class PitStopReviewFonts(context: Context) {
    val families: Map<PitStopReviewFace, FontFamily> = PitStopReviewFace.entries.associateWith { face ->
        FontFamily(
            Font(
                "pitstopreview/pitstopreview_montserrat_${face.file}.ttf",
                context.assets,
                face.weight,
            ),
        )
    }
    val typefaces: Map<PitStopReviewFace, Typeface> = PitStopReviewFace.entries.associateWith { face ->
        Typeface.createFromAsset(
            context.assets,
            "pitstopreview/pitstopreview_montserrat_${face.file}.ttf",
        )
    }
}
