package com.ngocdevv.nativecook.blocks.sheets.morphingmodesheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
private val WORDS = listOf("I", "The", "I’m")

private val BED = Color(0xFFD3D6DC)
private val KEY = Color.White
private val SPECIAL = Color(0xFFAEB3BD)
private val KEY_INK = Color(0xFF111111)
private val GLYPH = Color(0xFF4D5057)
private val DIVIDER = Color(0x403C3C43)
private val SIDE = 3.dp
private val GAP = 6.dp
private val KEY_HEIGHT = 43.dp
private val ROW_GAP = 11.dp
private val SUGGESTIONS = 44.dp
private val KEY_SHAPE = RoundedCornerShape(8.dp)

/** Height of the drawn keyboard above the home indicator's strip. */
internal val KEYBOARD_HEIGHT = SUGGESTIONS + KEY_HEIGHT * 4 + ROW_GAP * 3 + 8.dp + 50.dp

/**
 * A drawing of the system's light keyboard, for the page behind the sheet. It is not a keyboard:
 * it takes no input, and the block never opens the real one.
 */
@Composable
internal fun Keyboard(width: Dp, bottomInset: Dp, modifier: Modifier = Modifier) {
    val key = (width - SIDE * 2 - GAP * 9) / 10
    Column(
        modifier
            .width(width)
            .background(BED)
            .padding(bottom = maxOf(bottomInset - 20.dp, 0.dp)),
    ) {
        Row(Modifier.fillMaxWidth().height(SUGGESTIONS)) {
            WORDS.forEachIndexed { index, word ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (index > 0) {
                        Box(Modifier.align(Alignment.CenterStart).padding(vertical = 12.dp).width((2f / 3f).dp).fillMaxHeight().background(DIVIDER))
                    }
                    Text(word, color = KEY_INK, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = if (index == 1) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
        Column(Modifier.padding(horizontal = SIDE), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                for (letter in ROWS[0]) Letter(letter, key)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP, Alignment.CenterHorizontally)) {
                for (letter in ROWS[1]) Letter(letter, key)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Key(SPECIAL, Modifier.width(key * 1.3f)) { Icon(Icons.Outlined.ArrowUpward, null, tint = KEY_INK, modifier = Modifier.size(21.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    for (letter in ROWS[2]) Letter(letter, key)
                }
                Key(SPECIAL, Modifier.width(key * 1.3f)) { Icon(Icons.AutoMirrored.Outlined.Backspace, null, tint = KEY_INK, modifier = Modifier.size(21.dp)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                Key(SPECIAL, Modifier.width(key * 1.3f + GAP + key * 0.9f)) { Small("123") }
                Key(KEY, Modifier.weight(1f)) { Small("space") }
                Key(SPECIAL, Modifier.width(key * 2.3f + GAP)) { Small("return") }
            }
        }
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(50.dp).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(Icons.Outlined.EmojiEmotions, null, tint = GLYPH, modifier = Modifier.size(26.dp))
            Icon(Icons.Outlined.Mic, null, tint = GLYPH, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun Letter(letter: Char, width: Dp) {
    Key(KEY, Modifier.width(width)) { Text(letter.toString(), color = KEY_INK, fontSize = 23.sp, lineHeight = 28.sp) }
}

@Composable
private fun Small(text: String) {
    Text(text, color = KEY_INK, fontSize = 16.sp, lineHeight = 20.sp)
}

@Composable
private fun Key(fill: Color, modifier: Modifier, label: @Composable () -> Unit) {
    Box(
        modifier
            .height(KEY_HEIGHT)
            // A hard 1 dp shadow under each key, as on the system keyboard.
            .dropShadow(KEY_SHAPE, Shadow(radius = 0.dp, color = Color.Black, offset = DpOffset(0.dp, 1.dp), alpha = 0.3f))
            .background(fill, KEY_SHAPE),
        contentAlignment = Alignment.Center,
    ) { label() }
}
