package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import androidx.compose.ui.graphics.Color

/** The five operations on the keys (`Operation` in `keyGeometry.ts`). */
internal enum class TactileCalculatorWelcomeOperation {
    Add,
    Subtract,
    Multiply,
    Divide,
    Equals,
}

/** `CREAM`, `INK` and `OUTLINE` of the React Native file. */
internal object TactileCalculatorWelcomePalette {
    val Cream = Color(0xFFEEE8DC)
    val Ink = Color(0xFF151412)
    val Outline = Color(0xFF1D1B18)
}

/**
 * One entry of `KEYS`: the colours of the cap's face, of the lit wall and of the symbol's lit walls,
 * the name TalkBack reads, and where the key sits (`centerX` and `top` are fractions of the scene).
 */
internal class TactileCalculatorWelcomeKeySpec(
    val operation: TactileCalculatorWelcomeOperation,
    val face: Color,
    val wall: Color,
    val glyphWall: Color,
    val label: String,
    val centerX: Float,
    val top: Float,
)

/** `KEYS`, in drawing order: each key is drawn over the ones before it. */
internal val TACTILE_CALCULATOR_WELCOME_KEYS = listOf(
    TactileCalculatorWelcomeKeySpec(
        operation = TactileCalculatorWelcomeOperation.Subtract,
        face = Color(0xFFF7EED9),
        wall = Color(0xFFE8D2A4),
        glyphWall = Color(0xFFE8D2A4),
        label = "Subtract",
        centerX = 0.5f,
        top = -0.024f,
    ),
    TactileCalculatorWelcomeKeySpec(
        operation = TactileCalculatorWelcomeOperation.Divide,
        face = Color(0xFF6F9CF2),
        wall = Color(0xFFF2C46F),
        glyphWall = Color(0xFFF2C46F),
        label = "Divide",
        centerX = 0.928f,
        top = 0.092f,
    ),
    TactileCalculatorWelcomeKeySpec(
        operation = TactileCalculatorWelcomeOperation.Add,
        face = Color(0xFFFFC694),
        wall = Color(0xFFFA9441),
        glyphWall = Color(0xFFF89A4B),
        label = "Add",
        centerX = 0.5f,
        top = 0.2099f,
    ),
    TactileCalculatorWelcomeKeySpec(
        operation = TactileCalculatorWelcomeOperation.Multiply,
        face = Color(0xFFF45A45),
        wall = Color(0xFFF2C46F),
        glyphWall = Color(0xFFC13A28),
        label = "Multiply",
        centerX = 0.074f,
        top = 0.321f,
    ),
    TactileCalculatorWelcomeKeySpec(
        operation = TactileCalculatorWelcomeOperation.Equals,
        face = Color(0xFFFFF6DA),
        wall = Color(0xFFF2C46F),
        glyphWall = Color(0xFFF2C46F),
        label = "Equals",
        centerX = 0.505f,
        top = 0.4403f,
    ),
)
