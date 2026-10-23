package com.ngocdevv.nativecook.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The registry is sorted by category, so each category's blocks are already together.
private val categories = blockRegistry.groupBy { it.category }

private val count = "${blockRegistry.size} ${if (blockRegistry.size == 1) "block" else "blocks"}"

private val RowShape = RoundedCornerShape(16.dp)

/** Every block in this app, by category. A row opens the block on its own screen. */
@Composable
fun GalleryScreen(onOpen: (Block) -> Unit) {
    val bars = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(GalleryColors.Background),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = bars.calculateTopPadding() + 24.dp,
            bottom = bars.calculateBottomPadding() + 32.dp,
        ),
    ) {
        item(key = "header") { Header() }

        if (blockRegistry.isEmpty()) {
            item(key = "empty") { Empty() }
        }

        categories.forEach { (title, blocks) ->
            item(key = "category:$title") {
                Text(
                    text = title.uppercase(),
                    color = GalleryColors.Accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
                )
            }
            items(blocks, key = { it.slug }) { block ->
                BlockRow(block = block, onOpen = { onOpen(block) })
            }
        }
    }
}

@Composable
private fun Header() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = "Native Cook",
            color = GalleryColors.Text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "$GALLERY_TIER · Jetpack Compose · $count".uppercase(),
            color = GalleryColors.TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun Empty() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 32.dp),
    ) {
        Text(text = "No blocks yet", color = GalleryColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Add the first one from the repository root, then build again:",
            color = GalleryColors.TextMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
        Text(
            text = "npm run new -- <category> <slug>",
            color = GalleryColors.Accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BlockRow(block: Block, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(RowShape)
            .background(if (pressed) GalleryColors.SurfacePressed else GalleryColors.Surface)
            .border(0.5.dp, GalleryColors.Border, RowShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(text = block.name, color = GalleryColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (block.spec.isNotEmpty()) {
                Text(
                    text = block.spec,
                    color = GalleryColors.TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(text = "›", color = GalleryColors.TextMuted, fontSize = 22.sp)
    }
}

@Preview
@Composable
private fun GalleryScreenPreview() {
    GalleryScreen(onOpen = {})
}
