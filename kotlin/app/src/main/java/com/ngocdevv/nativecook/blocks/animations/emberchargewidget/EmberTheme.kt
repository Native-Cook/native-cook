package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import kotlin.math.exp
import kotlin.math.pow

// Ember — a 340 dp instrument on a four-dp spacing scale.
// Names follow ember-theme.ts so the two implementations can be read side by side.
internal object EmberTheme {
    // Gaps describe relationships; dimensions describe the things between them.
    object space {
        const val hair = 4f
        const val tight = 12f
        const val column = 16f
        const val near = 20f
        const val edge = 28f
        const val corner = 32f
        const val cornerText = 36f
    }

    const val size = 340f

    object dim {
        const val disc = 56f
        const val glyph = 16f
        const val barH = 56f
        const val countH = 60f

        object heart {
            const val w = 56f
            const val h = 52f
        }

        object line {
            const val title = 32f
            const val subtitle = 20f
            const val time = 16f
            const val value = 28f
            const val unit = 16f
            const val hint = 18f
            const val bpm = 20f
        }
    }

    object header {
        const val top = space.corner
        const val h = dim.disc
    }

    object metrics {
        const val top = header.top + header.h + space.corner
        const val h = dim.line.value + dim.line.unit
    }

    object bar {
        const val top = metrics.top + metrics.h + space.near
        const val h = dim.barH
    }

    object footer {
        const val top = bar.top + bar.h + space.tight
        const val h = dim.countH
    }

    const val contentWidth = size - space.edge * 2
    const val columnWidth = (contentWidth - space.column * 2) / 3
    const val titleTop = header.top + (header.h - (dim.line.title + dim.line.subtitle)) / 2

    object E {
        const val size = EmberTheme.size
        val space = EmberTheme.space

        object bands {
            val header = EmberTheme.header
            val metrics = EmberTheme.metrics
            val bar = EmberTheme.bar
            val footer = EmberTheme.footer
        }

        // The circular radius clips only the tube; the glass uses a cubic squircle.
        const val corner = 96f
        const val radius = 76f
        const val bleed = 60f
        const val canvasSize = size + 2 * bleed
        const val inset = EmberTheme.space.edge
        const val pageMargin = 16f
        val line = dim.line

        object disc {
            const val x = EmberTheme.space.corner
            const val y = header.top
            const val d = dim.disc
            const val cx = x + d / 2
            const val cy = y + d / 2
        }

        object title {
            const val x = EmberTheme.space.corner + dim.disc + EmberTheme.space.near
            const val top = titleTop
            const val sub = titleTop + dim.line.title
        }

        object time {
            const val right = EmberTheme.space.cornerText
            const val top = titleTop + EmberTheme.space.hair
        }

        object metrics {
            val x = floatArrayOf(
                EmberTheme.space.edge,
                EmberTheme.space.edge + columnWidth + EmberTheme.space.column,
                EmberTheme.space.edge + 2 * (columnWidth + EmberTheme.space.column),
            )
            const val w = columnWidth
            const val text = dim.glyph + EmberTheme.space.hair
            const val glyph = dim.glyph
            const val glyphTop = EmberTheme.metrics.top + (dim.line.value - dim.glyph) / 2
            const val valueTop = EmberTheme.metrics.top
            const val unitTop = EmberTheme.metrics.top + dim.line.value
        }

        object bar {
            const val x = EmberTheme.space.edge
            const val y = EmberTheme.bar.top
            const val w = contentWidth
            const val h = EmberTheme.bar.h
            const val r = 14f
            const val bloom = 12f
            const val core = 4f
            const val centerY = y + h / 2

            // Three broad-blur radii plus one tight-blur margin, as in RN's PAD.
            const val pad = 48f
        }

        object count {
            // Optical overhang aligns the count's ink with the straight inset.
            const val x = EmberTheme.space.edge - 2
            const val size = 54f
            const val h = footer.h
            const val top = footer.top
            const val unitGap = EmberTheme.space.tight
            const val unitDrop = 8f
        }

        object heart {
            const val x = size - EmberTheme.space.edge - dim.heart.w
            const val y = footer.top + footer.h - dim.heart.h
            const val w = dim.heart.w
            const val h = dim.heart.h

            // RN's native glyph-core calibration against the heart's area centroid.
            const val rateOffset = 12.72f
            const val cx = w / 2
            const val cy = h / 2
        }

        object embers {
            const val x = 44f
            const val pitch = 36f
            const val below = 20f
        }

        object stripe {
            const val period = 28f
        }

        const val restSteps = 4151
        const val goal = 6400
        const val restBpm = 80
        const val goalBpm = 96
    }

    object countFootprint {
        const val x = E.inset + E.metrics.w
        const val y = E.count.top + E.count.h / 2
        const val rx = E.heart.x - E.inset
        const val ry = E.count.h + space.corner
    }

    /** Smooth copy-band transmission, shared by the glass shader and airborne light. */
    fun countLight(x: Float, y: Float): Float {
        val radius2 = ((x - countFootprint.x) / countFootprint.rx).pow(2) +
                ((y - countFootprint.y) / countFootprint.ry).pow(2)
        return 1 - exp(-radius2 * radius2)
    }

    // ARGB values are shared by native Canvas and Compose; shader ramps remain in AGSL.
    object emberTone {
        // Black page behind the glass and its halo.
        val page = 0xff000000.toInt()

        // Primary copy on the dark face.
        val ink = 0xffffffff.toInt()

        // Secondary copy and metric pictograms on the dark face.
        val sub = 0xff8d8d8d.toInt()

        // White alone clears the count floor's contrast target; size ranks the unit.
        val onEmber = 0xffffffff.toInt()

        // Smoked header disc, lit above its centre.
        val discTop = 0xff454543.toInt()

        // Lower edge of the smoked header disc.
        val discBottom = 0xff222220.toInt()

        // Newly ignited airborne spark.
        val spark = 0xffffd23f.toInt()

        // Cooling airborne spark.
        val sparkCool = 0xffff7a00.toInt()

        // Airborne wave edge and rising embers.
        val wave = 0xffffd98a.toInt()
    }

    object emberHeartTone {
        // Floor contact shadow beneath the heart.
        val contactShadow = 0xff1f0b02.toInt()

        // Vessel light spilling onto the floor beneath the tip.
        val contactCaustic = 0xffffc35d.toInt()

        // Smoked vessel body at the shoulders.
        val bodyTop = 0xff2b1406.toInt()

        // Smoked vessel body at 55 percent of its height.
        val bodyMiddle = 0xff4b2504.toInt()

        // Smoked vessel body at the tip.
        val bodyTip = 0xff6d3803.toInt()

        // Transparent surface of the vessel's liquid light.
        const val poolSurface = 0x00ca6816

        // Warm middle of the vessel's liquid light.
        val poolMiddle = 0xfffa9527.toInt()

        // Bright liquid light at the vessel's tip.
        val poolTip = 0xffffc965.toInt()

        // Inner wall shade along the upper edge.
        const val depthTop = 0x661f0b02

        // Transparent end of the inner wall shade.
        const val depthClear = 0x001f0b02

        // Ember-lit rim on the lower flanks and tip.
        val rimEmber = 0xffffd79a.toInt()

        // Transparent upper end of the ember-lit rim.
        const val rimEmberClear = 0x00ffd79a

        // Faint upper-left key rim.
        val rimKey = 0xfffef1d0.toInt()

        // Transparent end of the upper-left key rim.
        const val rimKeyClear = 0x00fef1d0

        // Crisp crescent reflection on the upper-left lobe.
        val specularCrescent = 0xfffef1d0.toInt()

        // Heart-rate ink on the smoked vessel.
        val ink = 0xffffffff.toInt()
    }

    // Must match KEY_ANGLE in EmberGlassShader; the theme never imports the shader.
    const val emberHeartKeyAngle = -2.356194

    // Type sizes and tracking from the reference's styles (dp, not scalable sp).
    object type {
        const val title = 26f
        const val subtitle = 16f
        const val small = 12f
        const val metric = 24f
        const val bpm = 15f
        const val hint = 13f
        const val titleTracking = -0.8f
        const val subtitleTracking = -0.2f
        const val metricTracking = -0.65f
        const val countTracking = -2.2f
        const val bpmTracking = -0.2f
    }

    // Android ascent offsets, measured against the React Native build's capture at 3 px/dp.
    // They align ink inside the original line boxes without moving the layout bands.
    object optical {
        const val titleBaseline = -3.33f
        const val metricBaseline = -4.33f
        const val subtitleBaseline = -1.33f
        const val countBaseline = 7.33f
    }

    // Retained drawing geometry from ember-heart.tsx and ember-surface.tsx.
    object drawing {
        const val trembleX = 0.6f
        const val trembleY = 0.45f
        const val numericBlur = 1.5f
        const val glyphGrid = 24f
        const val glyphStroke = 1.8f
        const val walkOffset = 15f
        const val walkSize = 29f
        const val discLightOffset = 2f
        const val heartBlur = 5f
        const val contactDrop = 2f
        const val heartClipInset = 7f
        const val heartClipSize = 70f
        const val bodyTop = 3f
        const val bodyBottom = 50f
        const val depthBottom = 38f
        const val rimTop = 22f
        const val depthStroke = 3f
        const val rimStroke = 2f
        const val crescentStroke = 1.5f
        const val poolRest = 37f
        const val poolHeat = 3f
        const val poolBeat = 4f
        const val causticY = 53f
        const val causticRx = 8f
        const val causticRy = 2f

        // The key light's direction (emberHeartKeyAngle) through the heart's centre, as end
        // points rounded to 5 decimals, and the crescent's arc in degrees.
        const val keyX = 3.25126f
        const val keyY = 1.25126f
        const val keyEndX = 52.74874f
        const val keyEndY = 50.74874f
        const val lobeX = 14f
        const val lobeY = 16.75f
        const val crescentRadius = 10.75f
        const val crescentStart = -157.5f
        const val crescentSweep = 45f
        const val touchRadius = 60f
        const val touchGrowth = 90f
        const val emberRadius = 1.6f
        const val emberRise = 190f
        const val emberSway = 7f
        const val waveRadius = 260f
        const val waveStroke = 3f
        const val waveThinning = 2.5f

        // Airborne leading edge sits 2 dp behind the sharp bar head, matching RN.
        const val waveHeadInset = 2f
        const val sparkRadius = 3.5f
        const val sparkSpeed = 120f
        const val sparkSpeedRange = 240f
        const val sparkHalfGravity = 260f
    }
}
