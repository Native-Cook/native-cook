package com.ngocdevv.nativecook.blocks.gestures.slidetoanswercord

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ngocdevv.nativecook.R

/** Generated handset layers are required parts of this block, never optional artwork. */
internal data class SlideToAnswerCordAssets(
    val handset: Bitmap,
    val handsetGreen: Bitmap,
    val shell: Bitmap,
    val shellGreen: Bitmap,
    val sourceA: Bitmap,
    val sourceRest: Bitmap,
    val sourceB: Bitmap,
)

@Composable
internal fun rememberSlideToAnswerCordAssets(): SlideToAnswerCordAssets {
    val resources = LocalContext.current.resources
    return remember(resources) {
        fun decode(resource: Int): Bitmap = requireNotNull(BitmapFactory.decodeResource(resources, resource)) {
            "SlideToAnswerCord required drawable failed to decode: $resource"
        }
        SlideToAnswerCordAssets(
            handset = decode(R.drawable.slidetoanswercord_handset),
            handsetGreen = decode(R.drawable.slidetoanswercord_handset_green),
            shell = decode(R.drawable.slidetoanswercord_handset_shell),
            shellGreen = decode(R.drawable.slidetoanswercord_handset_shell_green),
            sourceA = decode(R.drawable.slidetoanswercord_handset_src_a),
            sourceRest = decode(R.drawable.slidetoanswercord_handset_src_rest),
            sourceB = decode(R.drawable.slidetoanswercord_handset_src_b),
        )
    }
}
