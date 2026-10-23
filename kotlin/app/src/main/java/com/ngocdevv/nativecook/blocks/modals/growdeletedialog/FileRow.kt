package com.ngocdevv.nativecook.blocks.modals.growdeletedialog

import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.math.roundToInt

/**
 * A file of the list. [icon] is the glyph on the tile of colour [tint], drawn in [ink]; [detail] is
 * written under the name, after the size; [photo] is shown in place of the tile.
 */
class FileItem(
    val name: String,
    val size: String,
    val tint: Color,
    val icon: ImageVector,
    val ink: Color? = null,
    val detail: String? = null,
    val photo: String? = null,
)

internal const val ROW_HEIGHT = 72f
internal const val ROW_GAP = 12f
internal const val TRASH_SIZE = 36f
internal val TrashInk = Color(0xFFD14B5A)

// The trash button's distance from the row's right edge.
private const val TRASH_INSET = 16f
private const val COLLAPSE_MS = 350
private const val RETURN_MS = 500
private val RowShadow = Color(0xFF111318)

/**
 * One file in the list; its trash button is where the dialog grows from.
 *
 * @param removed True once the file is deleted: the row fades and closes up, and comes back when it is false.
 * @param pressed The trash button is pressed down (by a touch or by the loop).
 * @param onTrashPlaced Called with the trash button's place in the window, in px.
 */
@Composable
internal fun FileRow(
    file: FileItem,
    removed: Boolean,
    pressed: Boolean,
    onTrashPlaced: (Offset) -> Unit,
    onTrash: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val present = remember { Channel(1f, scope) }
    LaunchedEffect(removed) {
        present.go { animateTo(if (removed) 0f else 1f, tween(if (removed) COLLAPSE_MS else RETURN_MS, easing = EaseInOutQuad)) }
    }

    val context = LocalContext.current
    val photo = remember(file.photo, context) { file.photo?.let { ImageRequest.Builder(context).data(it).build() } }
    val rowShape = RoundedCornerShape(18.dp)
    val tile = RoundedCornerShape(13.dp)
    Box(
        Modifier
            .fillMaxWidth()
            // The row fades and shrinks a little faster than its slot closes, so it never overlaps the next one.
            .graphicsLayer {
                val p = present.value
                alpha = p * p * p
                scaleX = 0.94f + 0.06f * p
                scaleY = scaleX
                // An offscreen layer would clip the row to its closing slot.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .layout { measurable, constraints ->
                val p = measurable.measure(Constraints.fixed(constraints.maxWidth, (ROW_HEIGHT * density).roundToInt()))
                layout(p.width, ((ROW_HEIGHT + ROW_GAP) * present.value * density).roundToInt()) { p.place(0, 0) }
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT.dp)
                .dropShadow(rowShape, Shadow(1.dp, RowShadow.copy(alpha = 0.04f), offset = DpOffset(0.dp, 1.dp)))
                .dropShadow(rowShape, Shadow(9.dp, RowShadow.copy(alpha = 0.05f), offset = DpOffset(0.dp, 6.dp)))
                .background(Color.White, rowShape)
                .padding(start = 14.dp, end = TRASH_INSET.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(tile))
            } else {
                Box(Modifier.size(46.dp).background(file.tint, tile), contentAlignment = Alignment.Center) {
                    Icon(file.icon, null, Modifier.size(24.dp), tint = file.ink ?: Color.Black.copy(alpha = 0.55f))
                }
            }
            Column(Modifier.weight(1f)) {
                BasicText(file.name, style = gridText(16f, 21f, FontWeight.SemiBold, Color(0xFF111318), tracking = -0.2f), maxLines = 1)
                BasicText(
                    file.detail?.let { "${file.size} · $it" } ?: file.size,
                    Modifier.padding(top = 2.dp),
                    style = gridText(13f, 17f, FontWeight.Normal, Color(0xFF7C808A)),
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .size(TRASH_SIZE.dp)
                    .onGloballyPositioned { onTrashPlaced(it.positionInRoot()) }
                    .graphicsLayer {
                        val s = if (pressed) 0.9f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (pressed) Color(0xFFF7D6DA) else Color(0xFFFDF0F1))
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, enabled = !removed, onClick = onTrash)
                    .semantics { contentDescription = "Delete ${file.name}" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(22.dp), tint = TrashInk)
            }
        }
    }
}
