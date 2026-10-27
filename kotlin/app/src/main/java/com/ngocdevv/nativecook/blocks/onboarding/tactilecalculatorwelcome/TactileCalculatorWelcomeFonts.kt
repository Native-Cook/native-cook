package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

/**
 * The two faces of the screen, `TactileInter-Regular` and `TactileInter-Bold` in the React Native file:
 * Inter, bundled in the app's assets. The reference sets its welcome in a wide grotesk and Inter
 * matches its proportions. Loading is synchronous, so the copy is there from the first frame.
 */
internal class TactileCalculatorWelcomeFonts private constructor(
    val regular: FontFamily,
    val bold: FontFamily,
) {
    companion object {
        private const val PREFIX = "tactilecalculatorwelcome/tactilecalculatorwelcome_"

        fun load(context: Context): TactileCalculatorWelcomeFonts = TactileCalculatorWelcomeFonts(
            regular = FontFamily(Typeface.createFromAsset(context.assets, PREFIX + "Inter-Regular.ttf")),
            bold = FontFamily(Typeface.createFromAsset(context.assets, PREFIX + "Inter-Bold.ttf")),
        )
    }
}
