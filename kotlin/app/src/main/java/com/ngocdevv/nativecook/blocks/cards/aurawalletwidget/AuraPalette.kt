package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import kotlin.math.floor
import kotlin.math.min

// Every measured size and color of the violet skin, on a 362-point drawing.
internal object AuraPalette {
    const val design = 362.0
    const val maximum = 362.0
    const val margin = 20.0
    const val bleed = 64.0
    object Lines {
        const val owner = 16.0
        const val chip = 14.0
        const val balance = 42.0
        const val paneLabel = 14.0
        const val title = 18.0
        const val note = 14.0
        const val amount = 20.0
        const val action = 20.0
    }

    object Page {
        const val lift = 48.0
        const val hintGap = 24.0
        const val hintSize = 13.0
        const val hintLine = 18.0
    }

    object Touch {
        const val minimum = 44.0
        const val retention = 20.0
    }

    object Details {
        const val chipPad = 10.0
        const val chipStroke = 1.0
        const val actionOpticalX = 1.0
        const val ownerOpacity = 0.85
        const val viewAllOpacity = 0.9
    }

    const val radius = 44.0
    const val inset = 12.0
    const val tight = -0.25
    const val faceZoom = 1.24
    const val faceLift = 0.04
    object Hero {
        const val x = 12.0
        const val y = 12.0
        const val w = 338.0
        const val h = 156.0
        const val radiusTop = 32.0
        const val radiusBottom = 40.0
    }

    object Lip {
        const val cy = 164.0
        const val avatar = 36.0
        const val ring = 3.0
        const val pitch = 50.0
        const val count = 5
    }

    object Owner {
        const val cx = 40.0
        const val cy = 37.0
        const val avatar = 22.0
        const val nameX = 56.0
        const val nameSize = 12.0
    }

    object Info {
        const val cx = 326.0
        const val cy = 37.0
        const val size = 22.0
    }

    object Chip {
        const val cx = 181.0
        const val cy = 58.0
        const val w = 90.0
        const val h = 22.0
        const val textSize = 10.0
        const val tracking = 0.2
    }

    object Balance {
        const val cy = 98.0
        const val size = 34.0
        const val tracking = -0.6
    }

    object Pane {
        const val x = 12.0
        const val y = 201.0
        const val w = 338.0
        const val h = 80.0
        const val radius = 24.0
    }

    object PaneLabel {
        const val x = 26.0
        const val cy = 219.0
        const val size = 11.0
        const val right = 336.0
    }

    object PaneMark {
        const val cx = 46.0
        const val cy = 252.0
        const val size = 36.0
    }

    object PaneTitle {
        const val x = 72.0
        const val cy = 245.0
        const val size = 14.0
    }

    object PaneNote {
        const val x = 72.0
        const val cy = 262.0
        const val size = 11.0
    }

    object PaneAmount {
        const val right = 334.0
        const val cy = 259.0
        const val size = 16.0
    }

    object Actions {
        const val y = 298.0
        const val h = 52.0
        const val gap = 6.0
        const val settingsWidth = 52.0
        const val swapWidth = 52.0
        const val receiveWidth = 120.0
        const val sendWidth = 96.0
        const val labelSize = 15.0
        const val icon = 18.0
        const val stroke = 1.5
        const val arrow = 14.0
        const val arrowGap = 5.0
    }


    object Motion {
        const val pressIn = 90.0
        const val pressScale = 0.96
        const val releaseDuration = 300.0
        const val releaseDampingRatio = 0.7
    }

    object Tone {
        const val page = "#040116"
        const val lampLeftCore = "#D9A8DC"
        const val lampLeftEdge = "#A25CD8"
        const val lampLowCore = "#5A12B8"
        const val lampLowEdge = "#2D065F"
        const val lampHigh = "#1B075C"
        const val shell = "#17142A"
        const val shellClear = 0.3
        const val heroCore = "#B98FF5"
        const val heroMid = "#8A4FD8"
        const val heroLeft = "#6928CA"
        const val heroEdge = "#5411C1"
        const val pillowTop = "#3E3C4A"
        const val pillowMid = "#2B273E"
        const val pillowLow = "#211D30"
        const val pillowShine = "#7B7986"
        const val accentTop = "#8655CB"
        const val accentMid = "#6330B9"
        const val accentLow = "#5829A9"
        const val accentShine = "#EBD9FF"
        const val ink = "#FFFFFF"
        const val sub = "rgba(255,255,255,0.62)"
        const val onAccent = "#FFFFFF"
        const val chipFill = "rgba(255,255,255,0.16)"
        const val chipLine = "rgba(255,255,255,0.18)"
        const val paneFill = "rgba(255,255,255,0.045)"
        const val paneLine = "rgba(255,255,255,0.16)"
        const val dotFill = "rgba(38,6,112,0.42)"
        const val dotInk = "rgba(255,255,255,0.72)"
        const val pearlHigh = "#FFFFFF"
        const val pearlLow = "#CFC1E4"
        const val hint = "rgba(255,255,255,0.6)"
    }
}

internal fun auraWidth(screen: Double) = floor(min(screen - AuraPalette.margin * 2, AuraPalette.maximum) / 2) * 2
