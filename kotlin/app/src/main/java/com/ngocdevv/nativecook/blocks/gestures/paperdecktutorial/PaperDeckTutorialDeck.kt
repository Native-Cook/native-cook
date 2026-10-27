package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The card deck: the `width` by `height` box of the React Native file, holding the six paper sheets and
 * the illustrated cover. The box takes the pan and describes the current card to a screen reader (with
 * "Next card" and "Previous card" actions); everything in it is drawn in one pass from the controller's
 * [PaperDeckTutorialController.pose], which is read while drawing so a frame redraws only this box. The
 * drawing is not clipped, so a thrown sheet flies on past the box.
 */
@Composable
internal fun PaperDeckTutorialDeck(
    controller: PaperDeckTutorialController,
    assets: PaperDeckTutorialAssets,
    geometry: PaperDeckTutorialGeometry,
    reduced: Boolean,
    touchEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val cards = remember(assets) { PaperDeckTutorialCardPainter(assets) }
    val cover = remember(assets) { PaperDeckTutorialCoverPainter(assets) }
    val step by remember(controller) { derivedStateOf { controller.pose().step } }
    Box(
        modifier
            .size(geometry.width.dp, geometry.height.dp)
            .semantics {
                val current = step
                contentDescription = if (current < 6) {
                    "Card ${current + 1} of 6. ${PaperDeckTutorialScript.STEPS[current].title.replace('\n', ' ')}"
                } else {
                    "This or that. Burger or apple?"
                }
                customActions = listOf(
                    CustomAccessibilityAction("Next card") {
                        controller.accessibilityAdvance(back = false)
                        true
                    },
                    CustomAccessibilityAction("Previous card") {
                        controller.accessibilityAdvance(back = true)
                        true
                    },
                )
            }
            .paperDeckTutorialPan(touchEnabled, controller)
            .drawBehind {
                val pose = controller.pose()
                val grab = controller.grab
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    // The painters work in dp.
                    native.scale(density, density)
                    cards.draw(native, pose, grab, geometry, reduced, density, fontScale)
                    cover.draw(native, pose, geometry, reduced)
                    native.restore()
                }
            },
    )
}
