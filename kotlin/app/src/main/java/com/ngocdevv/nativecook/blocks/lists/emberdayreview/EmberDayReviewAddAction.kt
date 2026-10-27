package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow

/**
 * The Add Action pill of the Today footer (`styles.addAction`): a 54 dp white capsule with a 2 dp peach border and a
 * soft orange glow, a grey plus, a centred text field ("Add Action") and a 20 dp spacer that keeps the text centred.
 * Return adds the action and keeps the keyboard up. While the field has focus the demo never takes the screen back.
 */
@Composable
internal fun EmberDayReviewAddAction(controller: EmberDayReviewController, enabled: Boolean) {
    val shape = RoundedCornerShape(27.dp)
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (focused) {
                controller.editing = false
            }
        }
    }
    val factor = emberDayReviewTextFactor(1.6f)
    val style = TextStyle(
        color = EmberDayReviewPalette.AddInput,
        fontSize = (15f * factor).sp,
        lineHeight = (20f * factor).sp,
        textAlign = TextAlign.Center,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
            .cssBoxShadow(EmberDayReviewPalette.AddGlow, offsetY = 0.dp, blur = 14.dp, radius = 27.dp)
            .heightIn(min = 54.dp)
            .background(EmberDayReviewPalette.Screen, shape)
            .border(2.dp, EmberDayReviewPalette.AddBorder, shape)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmberDayReviewIcon(EmberDayReviewGlyph.Add, 20.dp, EmberDayReviewPalette.AddIcon)
        BasicTextField(
            value = controller.draft,
            onValueChange = { controller.draft = it },
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .semantics { contentDescription = "Add an action for today" }
                .onFocusChanged { state ->
                    if (state.isFocused != focused) {
                        focused = state.isFocused
                        controller.focusChanged(state.isFocused)
                    }
                },
            enabled = enabled,
            textStyle = style,
            singleLine = true,
            cursorBrush = SolidColor(EmberDayReviewPalette.AddInput),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { controller.addTask() }),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                    if (controller.draft.isEmpty()) {
                        EmberDayReviewText(
                            text = "Add Action",
                            size = 15f,
                            color = EmberDayReviewPalette.AddIcon,
                            line = 20f,
                            cap = 1.6f,
                            align = TextAlign.Center,
                        )
                    }
                    inner()
                }
            },
        )
        Box(Modifier.width(20.dp))
    }
}
