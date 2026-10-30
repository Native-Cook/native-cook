package com.ngocdevv.nativecook.blocks.onboarding.aifeaturetour

internal object AiFeatureTourTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val S = 402.0 / 720.0
    object C {
        const val bgTop = "#D3C9FD"
        const val bgMid = "#EEEBFC"
        const val bg = "#F8F8FA"
        const val glow = "#ECE8F8"
        const val title = "#1D1D22"
        const val rowTitle = "#1A1A1F"
        const val sub = "#8A8A90"
        const val tile = "#FFFFFF"
        const val tileEdge = "#E4E6EA"
        const val tileShade = "#B9BCC6"
        const val calTop = "#DCE2E6"
        const val calRing = "#A9B2BA"
        const val dotBlue = "#4D96F4"
        const val dotGrey = "#D9DDE2"
        const val green = "#43B97A"
        const val greenLine = "#6BCB93"
        const val grid = "#EEF1F3"
        const val check = "#2FC06A"
        const val checkGrey = "#C6CBD1"
        const val line = "#E3E6EA"
        const val runner = "#3D7BEA"
        const val avoSkin = "#3E6B2A"
        const val avoFlesh = "#D9E66A"
        const val avoInner = "#F2F49A"
        const val pit = "#A35A26"
        const val button = "#1F2023"
        const val sparkle = "#C9B8FF"
    }
    const val title = "Halden Intelligence can:"
    const val button = "Continue"
    val rows = listOf(
        Pair("Create check ins", listOf("Reminders that follow up and", "keep you on track.")),
        Pair("Understand your trends", listOf("See what's moving your metrics", "and where they're heading.")),
        Pair("Create workout templates", listOf("Strength templates built around", "your goals and style.")),
        Pair("Log food", listOf("Snap a photo or describe it and", "let Halden handle the rest.")),
        Pair("Create a training plan", listOf("A personalized plan based on", "your goals and activity.")),
        Pair("Explain your records", listOf("Upload records to see your", "biomarkers explained.")),
    )
    object UI {
        object title { const val x = 360.0; const val y = 235.0 }
        object rows {
            const val iconX = 147.0; const val textX = 264.0
            const val y0 = 355.0; const val pitch = 220.0
            val sub = doubleArrayOf(42.0, 84.0)
            const val tile = 114.0
        }
        object button {
            const val x = 45.0; const val y = 1402.0
            const val w = 630.0; const val h = 95.0
        }
    }
}
