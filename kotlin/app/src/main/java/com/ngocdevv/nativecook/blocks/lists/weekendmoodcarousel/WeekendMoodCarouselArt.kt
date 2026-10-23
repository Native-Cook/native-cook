package com.ngocdevv.nativecook.blocks.lists.weekendmoodcarousel

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.RectF
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** The measured character silhouettes and faces. Coordinates are the RN reference's logical dp. */
internal object WeekendMoodCarouselArt {
    private const val EDGE_SOFTNESS = 0.22f
    private const val LOBE_SOFTNESS = 2.5f
    private const val BAG_SOFTNESS = 0.55f

    private val shapeData = mapOf(
        "sleepy" to "M-137.7 188.0 L-152.0 203.7 L-163.3 219.0 L-173.0 235.7 L-182.0 255.7 L-188.0 274.7 L-192.0 294.3 L-193.7 323.0 L-192.3 343.3 L-190.0 358.0 L-182.0 385.3 L-175.7 400.0 L-165.3 418.7 L-156.7 431.3 L-148.0 442.0 L-130.7 459.0 L-130.0 481.0 L122.3 481.0 L143.0 439.3 L158.0 418.7 L170.3 396.0 L177.7 376.3 L179.0 376.7 L183.7 392.0 L184.7 392.3 L186.0 391.0 L192.7 367.3 L193.0 359.7 L194.7 356.3 L195.0 344.0 L196.7 340.0 L196.7 302.0 L195.0 297.0 L194.7 283.7 L193.0 280.0 L192.7 272.7 L184.0 244.3 L178.3 231.0 L175.0 226.3 L173.0 227.3 L168.7 233.3 L168.0 239.3 L151.7 212.7 L133.7 191.0 L116.7 175.7 L98.3 162.3 L84.0 154.3 L81.3 154.0 L79.3 155.3 L79.7 157.3 L83.0 157.7 L89.0 160.3 L89.3 163.0 L87.7 165.0 L82.0 166.0 L78.0 164.3 L68.3 164.0 L65.3 161.3 L65.3 159.3 L72.0 152.0 L72.7 150.0 L71.7 148.7 L61.3 144.3 L40.7 138.3 L18.3 134.3 L-25.3 134.3 L-55.3 140.3 L-87.3 152.3 L-115.7 169.3 Z",
        "foodie" to "M-153.7 225.0 L-159.3 232.7 L-160.0 237.0 L-154.0 249.0 L-147.7 266.7 L-143.7 282.3 L-143.3 289.3 L-141.7 293.3 L-141.3 306.0 L-139.7 311.7 L-139.7 330.3 L-141.3 334.3 L-141.7 347.7 L-143.3 352.7 L-143.7 358.0 L-145.3 361.7 L-145.7 366.7 L-149.3 378.3 L-137.0 416.0 L-131.3 428.0 L-125.0 437.7 L-117.3 446.7 L-104.0 457.7 L-101.7 481.0 L108.0 481.0 L125.0 445.0 L133.3 434.7 L142.0 418.7 L144.3 412.7 L145.3 406.0 L153.3 414.0 L155.0 414.0 L155.0 398.0 L144.7 381.3 L136.0 360.7 L135.7 355.7 L134.0 352.3 L131.7 341.3 L131.7 318.3 L136.0 303.0 L140.7 293.7 L160.0 266.0 L164.0 257.0 L168.3 241.7 L168.7 215.0 L167.7 213.0 L166.3 212.7 L165.0 214.0 L161.0 224.7 L160.7 230.0 L159.3 231.0 L158.3 223.7 L149.0 214.3 L52.0 145.0 L39.3 138.7 L26.3 134.3 L13.3 132.0 L-6.0 132.0 L-27.0 136.3 L-46.3 145.3 L-140.0 212.0 Z",
        "binge" to "M-90.0 164.7 L-106.3 181.0 L-121.3 203.0 L-132.3 228.7 L-136.3 247.7 L-137.0 257.0 L-122.0 279.7 L-113.3 298.3 L-109.3 313.3 L-109.0 333.0 L-107.7 336.3 L-109.0 343.7 L-109.3 358.0 L-111.0 361.7 L-111.3 368.0 L-115.7 382.3 L-125.3 402.0 L-134.0 414.7 L-138.0 418.7 L-137.0 440.7 L-133.3 481.0 L152.3 481.0 L152.7 455.3 L155.3 450.7 L157.0 450.7 L161.3 454.3 L163.7 454.7 L164.3 439.3 L154.3 424.3 L150.0 409.0 L150.0 193.3 L144.0 183.7 L142.7 183.3 L140.3 192.3 L140.3 198.3 L138.3 199.7 L131.3 189.0 L121.0 176.3 L109.7 165.3 L91.7 152.3 L74.7 143.7 L60.3 138.3 L43.3 134.3 L24.0 132.0 L-12.7 132.7 L-27.0 134.7 L-42.7 138.7 L-64.0 147.3 L-80.0 157.0 Z",
        "shopping" to "M-113.0 156.3 L-123.3 167.0 L-129.0 177.0 L-131.7 185.3 L-133.3 201.7 L-133.3 419.0 L-129.3 433.0 L-124.7 441.7 L-119.3 448.0 L-111.7 454.3 L-104.7 458.3 L-103.0 481.0 L112.3 481.0 L113.7 458.3 L120.3 454.7 L125.7 450.3 L133.7 441.0 L138.0 433.0 L143.0 416.0 L150.3 425.3 L151.3 425.7 L152.3 424.3 L152.3 234.0 L151.0 232.7 L146.7 239.3 L144.3 241.7 L142.7 241.3 L142.0 190.7 L138.3 177.7 L132.0 166.3 L124.3 158.0 L117.3 153.0 L109.0 149.0 L100.7 146.7 L57.3 146.0 L51.7 147.3 L53.0 144.3 L53.0 137.7 L51.0 136.0 L49.3 136.3 L48.3 138.0 L48.7 144.3 L50.3 146.7 L42.3 146.0 L39.7 148.3 L38.7 146.3 L38.3 137.3 L36.7 135.7 L34.3 136.0 L33.3 137.3 L33.3 146.0 L32.0 149.0 L29.7 146.0 L-19.7 146.7 L-19.3 137.7 L-20.0 136.3 L-23.0 135.7 L-25.0 138.3 L-25.0 144.3 L-23.0 147.0 L-31.0 146.0 L-32.7 147.3 L-33.0 163.0 L-34.3 161.7 L-34.7 137.3 L-36.3 135.7 L-38.3 135.7 L-40.0 137.3 L-40.0 145.7 L-41.0 148.3 L-43.3 146.0 L-87.0 146.0 L-99.7 149.0 Z",
        "scroll" to "M28.0 132.3 L15.7 130.7 L2.0 134.0 L-8.7 140.7 L-28.7 159.0 L-41.7 165.7 L-55.3 166.7 L-80.0 165.0 L-89.0 166.7 L-100.7 172.7 L-108.0 180.3 L-112.7 187.7 L-116.7 199.3 L-121.7 223.0 L-126.0 233.7 L-133.0 244.0 L-133.0 413.3 L-131.7 417.3 L-124.3 427.0 L-115.0 432.7 L-104.3 435.3 L-86.3 437.7 L-69.3 442.3 L-51.7 452.0 L-42.0 481.0 L63.7 481.0 L64.0 459.3 L74.0 450.0 L88.7 442.0 L106.7 437.7 L124.7 436.7 L144.7 432.7 L154.0 427.0 L161.0 417.7 L169.3 424.3 L170.3 423.0 L170.3 408.3 L160.3 390.3 L156.3 378.7 L156.0 362.0 L160.7 343.7 L188.7 275.0 L189.0 249.3 L187.7 248.7 L186.3 250.0 L183.0 259.0 L180.0 261.7 L177.3 256.7 L159.0 241.3 L150.7 226.3 L142.0 190.7 L137.3 182.0 L129.3 173.0 L118.0 166.7 L108.3 164.7 L95.7 166.0 L78.0 166.0 L70.7 164.0 L62.7 159.7 L39.0 138.0 Z",
        "social" to "M-113.3 155.3 L-118.7 162.7 L-123.0 171.3 L-152.3 247.0 L-163.7 272.7 L-164.3 276.3 L-171.7 292.7 L-182.7 322.7 L-186.0 328.7 L-192.3 348.0 L-192.7 354.3 L-194.3 360.0 L-194.3 377.0 L-192.3 388.7 L-185.3 407.3 L-179.7 416.3 L-169.0 427.3 L-155.0 436.7 L-144.3 441.0 L-136.3 443.0 L-127.3 443.7 L142.3 443.7 L151.3 443.0 L169.3 437.0 L183.3 428.0 L191.7 419.7 L197.3 411.7 L199.3 407.0 L220.7 406.3 L220.7 331.0 L199.3 330.3 L198.7 325.0 L175.0 268.3 L137.7 172.7 L133.7 164.3 L127.3 154.7 L118.0 145.3 L102.3 136.3 L85.3 132.7 L-70.0 132.7 L-81.3 134.3 L-96.0 140.3 L-101.7 144.0 Z",
        "recovery" to "M-112.7 194.7 L-117.3 207.0 L-121.3 238.7 L-129.7 282.0 L-129.3 307.7 L-125.3 329.7 L-115.7 356.7 L-104.7 375.3 L-90.0 393.3 L-71.0 410.0 L-51.7 422.3 L-38.7 428.7 L-21.0 435.3 L-0.7 440.7 L14.3 443.3 L47.0 446.7 L66.7 445.7 L71.0 447.3 L73.7 445.3 L79.7 443.3 L88.3 442.3 L114.3 434.7 L143.3 421.7 L161.0 409.3 L166.0 408.7 L174.3 396.0 L175.0 397.7 L172.7 402.0 L161.3 418.0 L161.0 424.0 L162.7 425.3 L170.0 420.0 L176.0 418.3 L182.3 408.7 L190.3 392.0 L194.7 377.0 L195.0 371.3 L196.7 368.0 L197.0 355.3 L198.7 350.0 L198.3 315.7 L197.0 312.3 L196.7 303.3 L195.0 300.3 L192.7 288.3 L184.0 270.3 L171.0 251.0 L165.3 237.3 L163.3 240.3 L161.3 252.7 L161.3 257.3 L164.0 262.3 L163.7 263.3 L161.0 260.0 L156.7 251.0 L154.7 243.7 L154.0 230.3 L152.3 226.3 L152.3 220.7 L150.0 213.3 L143.3 200.7 L131.3 188.3 L120.7 182.3 L95.0 176.0 L86.0 171.7 L76.3 163.7 L61.3 147.7 L46.7 137.0 L30.0 130.3 L13.0 129.0 L-1.7 130.7 L-8.3 132.7 L-18.0 137.3 L-32.0 147.0 L-51.7 164.7 L-61.3 171.3 L-74.7 177.3 L-97.0 183.7 L-106.3 189.0 Z",
        "recoveryLobe" to "M-116.3 198.3 L-122.3 207.3 L-127.0 217.7 L-129.0 224.0 L-129.3 230.7 L-131.3 236.0 L-131.7 240.7 L-136.0 252.7 L-157.3 284.0 L-161.7 293.0 L-166.0 305.3 L-166.7 319.7 L-168.0 324.7 L-168.0 334.7 L-166.7 340.3 L-166.0 354.3 L-164.0 358.7 L-164.0 362.0 L-159.3 378.3 L-155.3 387.0 L-142.7 408.0 L-130.7 421.0 L-121.3 429.0 L-106.3 439.0 L-90.0 447.3 L-71.3 454.7 L-53.7 459.3 L-52.0 481.0 L84.0 481.0 L111.3 451.3 L131.3 441.3 L145.7 431.3 L159.7 418.3 L161.0 420.0 L161.7 432.0 L163.3 431.7 L167.7 427.0 L168.7 424.3 L171.7 422.3 L172.7 420.3 L172.7 406.0 L171.0 403.7 L171.0 400.3 L152.0 399.7 L133.0 412.3 L102.3 425.7 L78.0 433.0 L70.0 433.7 L63.7 436.0 L39.0 435.3 L10.7 431.3 L-12.3 425.3 L-33.7 417.0 L-46.0 410.7 L-63.7 399.0 L-85.3 378.3 L-94.3 367.7 L-109.3 341.0 L-115.7 320.0 L-120.0 292.3 L-111.7 248.7 L-107.7 216.0 L-106.0 212.0 L-106.3 192.7 L-108.0 191.7 L-111.0 192.7 Z",
        "shoppingHandles" to "M-33.3 106.3 L-38.0 115.3 L-40.0 123.7 L-40.0 160.3 L-39.0 161.3 L-35.3 161.3 L-34.3 160.3 L-34.3 124.3 L-32.3 116.7 L-28.0 108.7 L-24.7 105.0 L-17.7 100.0 L-14.0 98.3 L-12.7 98.3 L-12.3 99.7 L-19.7 108.3 L-24.0 118.3 L-25.0 124.3 L-24.7 145.7 L-19.7 146.0 L-18.7 145.0 L-18.7 124.0 L-18.0 119.3 L-15.3 113.3 L-12.0 108.3 L-5.3 102.0 L5.3 97.0 L11.7 97.7 L19.7 102.0 L25.0 106.7 L30.7 115.7 L33.0 123.3 L33.3 161.0 L37.7 161.3 L38.7 160.3 L38.3 120.3 L33.7 108.7 L26.7 100.3 L27.0 98.7 L31.7 100.3 L36.0 103.3 L43.0 111.0 L46.3 117.7 L47.7 123.0 L48.3 145.3 L49.3 146.0 L53.0 146.0 L54.0 145.0 L54.3 126.3 L53.0 122.7 L52.7 116.3 L48.7 108.3 L45.0 103.3 L38.0 97.0 L31.0 93.0 L18.0 90.0 L8.7 90.7 L1.3 90.0 L-7.3 90.3 L-15.3 92.7 L-22.0 96.0 L-28.0 100.3 Z",
        "scrollPhone" to "M-24.0 300.3 L-31.7 300.0 L-34.7 304.7 L-37.7 307.0 L-43.3 309.7 L-59.0 314.7 L-60.0 315.7 L-57.3 316.0 L-43.0 313.3 L-36.7 313.0 L-33.3 314.3 L-30.3 319.0 L-31.3 324.3 L-35.3 328.0 L-41.0 330.7 L-56.0 334.0 L-56.7 334.7 L-56.3 335.7 L-28.3 341.3 L-23.7 341.0 L-19.0 337.7 L-17.7 333.3 L-18.3 326.3 L-20.3 319.0 L-22.7 303.7 Z",
        "scrollHand" to "M-85.7 283.3 L-87.0 284.7 L-87.7 290.7 L-90.3 295.3 L-93.0 302.7 L-94.3 310.0 L-94.3 319.3 L-91.0 331.0 L-82.0 342.7 L-74.7 347.3 L-68.7 348.7 L-61.0 348.7 L-54.7 347.3 L-50.7 345.0 L-45.0 340.0 L-44.7 338.7 L-47.7 337.7 L-49.7 336.0 L-51.0 331.3 L-54.3 328.0 L-55.0 324.3 L-58.7 323.3 L-60.7 321.7 L-61.7 317.7 L-60.3 313.7 L-65.0 313.3 L-68.3 311.7 L-74.7 304.7 L-78.0 299.0 L-79.3 294.0 L-79.0 291.7 L-77.3 288.7 L-78.7 284.3 L-80.3 283.3 Z",
    )

    private val shapeColors = mapOf(
        "sleepy" to "#FAB501",
        "foodie" to "#50B345",
        "recovery" to "#FE7766",
        "recoveryLobe" to "#FC5944",
        "binge" to "#CB7EF4",
        "shopping" to "#FF8CE0",
        "shoppingHandles" to "#F34D44",
        "scroll" to "#FE4F48",
        "scrollPhone" to "#2A2A2E",
        "scrollHand" to "#E9483F",
        "social" to "#FB9703",
    )

    private val panelColors = intArrayOf(
        Color.parseColor("#F0E1B0"), Color.parseColor("#BEDB98"), Color.parseColor("#FDBFB6"),
        Color.parseColor("#F6D0FB"), Color.parseColor("#FDD7FC"), Color.parseColor("#FDA4AB"),
        Color.parseColor("#FDD2BA"),
    )
    private val tickTop = intArrayOf(
        Color.parseColor("#C2B4D8"), Color.parseColor("#6FAFDB"), Color.parseColor("#CFB2E6"),
        Color.parseColor("#DE6573"), Color.parseColor("#DAAEEF"), Color.parseColor("#DAAEEF"),
        Color.parseColor("#CEB0E8"),
    )
    private val tickBottom = intArrayOf(
        Color.parseColor("#F4D27E"), Color.parseColor("#82CC86"), Color.parseColor("#F0D978"),
        Color.parseColor("#D6AFEA"), Color.parseColor("#E4A3CB"), Color.parseColor("#D66476"),
        Color.parseColor("#6FB1E6"),
    )

    private val layers = arrayOf(
        arrayOf("sleepy"), arrayOf("foodie"), arrayOf("recoveryLobe", "recovery"),
        arrayOf("binge"), arrayOf("shoppingHandles", "shopping"),
        arrayOf("scroll", "scrollHand", "scrollPhone"), arrayOf("social"),
    )
    private val positions = floatArrayOf(0f, 326f, 614f, 910f, 1182f, 1456f, 1794f)
    private val kinds = arrayOf("sleepy", "foodie", "recovery", "binge", "shopping", "scroll", "social")

    internal fun parseShapes(): Map<String, Path> = shapeData.mapValues { (key, value) ->
        PathParser().parsePathString(value).toPath().asAndroidPath().apply {
            if (key == "shoppingHandles") fillType = Path.FillType.EVEN_ODD
        }
    }

    internal fun drawPanel(
        canvas: Canvas,
        width: Float,
        panelHeight: Float,
        charPos: Float,
        selectedChar: Int,
        previousChar: Int,
        fill: Float,
        age: Float,
        phase: Float,
        reduced: Boolean,
        shapes: Map<String, Path>,
        opacity: Float = 1f,
    ) {
        val mid = width / 2f
        val bg = interpolateStops(charPos, panelColors)
        canvas.save()
        canvas.clipPath(panelPath(width, panelHeight, 13.5f))
        canvas.drawColor(withAlpha(bg, opacity))
        canvas.save()
        canvas.clipRect(0f, 0f, width, panelHeight)
        for (i in kinds.indices) {
            val cx = mid + positions[i] - interpolateStopsFloat(charPos, positions)
            if (abs(cx - mid) > width / 2f + 200f) continue
            canvas.save()
            canvas.translate(cx, 0f)
            for (layer in layers[i]) {
                val path = shapes[layer] ?: continue
                if (layer == "scrollHand") {
                    val p = fillPaint("#FC4F48", opacity)
                    p.shader = LinearGradient(-44f, 283f, -94f, 349f, Color.parseColor("#FC4F48"), Color.parseColor("#DC3A2F"), Shader.TileMode.CLAMP)
                    canvas.drawPath(path, p)
                } else {
                    val p = fillPaint(shapeColors[layer].orEmpty(), opacity)
                    val sigma = when (layer) {
                        "recoveryLobe" -> LOBE_SOFTNESS
                        "shopping" -> BAG_SOFTNESS
                        else -> EDGE_SOFTNESS
                    }
                    if (sigma > 0f) p.maskFilter = BlurMaskFilter(sigma, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawPath(path, p)
                }
            }
            canvas.restore()
            var act = 0f
            var faceAge = 9999f
            if (i == selectedChar) {
                act = if (kinds[i] == "scroll") fill else if (age >= 0f) 1f else 0f
                faceAge = age
            } else if (i == previousChar && abs(charPos - i) < 1.4f && kinds[i] != "scroll") {
                act = 1f
            }
            canvas.save()
            canvas.translate(cx, 0f)
            drawFace(canvas, kinds[i], act, faceAge, phase, reduced, opacity)
            canvas.restore()
        }
        canvas.restore()
        canvas.restore()
    }

    internal fun drawRuler(canvas: Canvas, width: Float, chipX: Float, anchor: Float, charPos: Float, opacity: Float = 1f) {
        val mid = width / 2f
        val pitch = 12.25f
        val off = ((chipX - anchor) % pitch + pitch) % pitch
        val base = 87f
        val heights = floatArrayOf(82f, 77.3f, 67.7f, 59.7f, 51.7f, 44.7f, 38.3f, 33.3f, 28.3f, 22.7f, 19f, 16f, 11.7f, 7.3f, 3f, 1f, 0f)
        val alphas = floatArrayOf(.3f, .24f, .23f, .2f, .19f, .17f, .15f, .13f, .11f, .1f, .08f, .07f, .06f, .04f, .03f, .01f, .01f)
        val count = min(40, ceil(mid / pitch).toInt() + 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (k in -count..count) {
            val x = mid + k * pitch - off
            val d = min(abs(x - mid) / pitch, 16f)
            val h = interpolateStopsFloat(d, heights)
            val a = interpolateStopsFloat(d, alphas) * opacity
            paint.shader = LinearGradient(0f, base - h, 0f, base, Color.argb((a * 255).toInt(), 255, 255, 255), Color.argb((.02f * opacity * 255).toInt(), 255, 255, 255), Shader.TileMode.CLAMP)
            paint.style = Paint.Style.FILL
            paint.maskFilter = BlurMaskFilter(.9f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(x - 1f, base - h, x + 1f, base, 1f, 1f, paint)
        }
        val top = interpolateStops(charPos, tickTop)
        val bottom = interpolateStops(charPos, tickBottom)
        paint.shader = LinearGradient(0f, 6.7f, 0f, 95.7f, withAlpha(top, opacity), withAlpha(bottom, opacity), Shader.TileMode.CLAMP)
        paint.maskFilter = null
        canvas.drawRoundRect(mid - 1.25f, 6.7f, mid + 1.25f, 95.7f, 1.25f, 1.25f, paint)
    }

    private fun drawFace(canvas: Canvas, kind: String, act: Float, age: Float, phase: Float, reduced: Boolean, opacity: Float) {
        val sleepyInk = "#29254E"; val sleepyEyes = "#1E1D24"; val sleepyMouth = "#532802"; val sleepyTongue = "#DA4C3C"
        val foodieInk = "#2B1703"; val foodieMouth = "#331700"; val foodieTongue = "#D03B34"
        val ink = "#1A1615"; val white = "#FBFBF8"
        when (kind) {
            "sleepy" -> {
                curve(canvas, -58.5f, 196f, -42.8f, 211.5f, -27.1f, 196f, 5.6f, sleepyEyes, opacity)
                curve(canvas, 18.5f, 196f, 33.8f, 211.5f, 49.1f, 196f, 5.6f, sleepyEyes, opacity)
                val mouth = RectF(-25.3f, 218.7f, 16.3f, 249f)
                canvas.drawOval(mouth, fillPaint(sleepyMouth, opacity))
                canvas.save(); canvas.clipPath(Path().apply { addOval(mouth, Path.Direction.CW) })
                canvas.drawOval(RectF(-20.8f, 241.4f, 12.2f, 258.6f), fillPaint(sleepyTongue, opacity)); canvas.restore()
                val on = if (act > 0f) 1f else 0f
                val a = if (reduced) 9999f else age
                zed(canvas, 44.7f, 161.3f, 57f, 176.3f, 3.2f, on * pop(a / 220f), sleepyInk, opacity)
                zed(canvas, 67f, 138f, 90.3f, 164f, 4.6f, on * pop((a - 110f) / 220f), sleepyInk, opacity)
                zed(canvas, 96f, 110f, 126f, 139.3f, 5.6f, on * pop((a - 220f) / 220f), sleepyInk, opacity)
            }
            "foodie" -> {
                curve(canvas, -47f, 215.5f, -30.4f, 197.5f, -13.7f, 215.5f, 5.4f, foodieInk, opacity)
                curve(canvas, 21.3f, 214.5f, 37.8f, 196.5f, 54.3f, 214.5f, 5.4f, foodieInk, opacity)
                val open = max(.08f, if (reduced) act else ((age / 200f).coerceIn(0f, 1f) * act))
                canvas.save(); canvas.translate(0f, 230f); canvas.scale(1f, open); canvas.translate(0f, -230f)
                val m = Path().apply { moveTo(-18.7f, 233f); quadTo(7.8f, 225f, 34.3f, 233f); cubicTo(34.3f, 262f, -18.7f, 262f, -18.7f, 233f); close() }
                canvas.drawPath(m, fillPaint(foodieMouth, opacity)); canvas.save(); canvas.clipPath(m)
                canvas.drawOval(RectF(-11f, 250.5f, 28f, 269.5f), fillPaint(foodieTongue, opacity)); canvas.restore(); canvas.restore()
                val drool = if (reduced) act else (((age - 100f) / 200f).coerceIn(0f, 1f) * act)
                if (drool > .01f) {
                    val len = 17f * drool; val b = Path().apply { moveTo(18.5f, 261.3f); lineTo(24.5f, 261.3f); cubicTo(27f, 261.3f + len * .55f, 27f, 261.3f + len, 21.5f, 261.3f + len); cubicTo(16f, 261.3f + len, 16f, 261.3f + len * .55f, 18.5f, 261.3f); close() }
                    canvas.drawPath(b, fillPaint("#44C2F8", opacity))
                }
            }
            "recovery" -> {
                curve(canvas, -47f, 219f, -28.2f, 192f, -9.4f, 219f, 7.6f, ink, opacity)
                curve(canvas, 44.4f, 219f, 63.5f, 192f, 82.6f, 219f, 7.6f, ink, opacity)
                canvas.drawOval(RectF(-45.1f, 228f, .7f, 278.8f), fillPaint(white, opacity)); canvas.drawOval(RectF(30f, 227.3f, 79.8f, 279.7f), fillPaint(white, opacity))
                curve(canvas, -39f, 252.6f, -22.9f, 269.5f, -6.8f, 252.6f, 5.6f, ink, opacity)
                curve(canvas, 38.4f, 252.3f, 54.5f, 269.2f, 70.6f, 252.3f, 5.6f, ink, opacity)
                curve(canvas, -16.3f, 301f, 18.2f, 323f, 52.7f, 301f, 6.4f, ink, opacity)
                val cross = interpolateStops(act, intArrayOf(Color.parseColor("#FBCFC4"), Color.parseColor("#F92105")))
                canvas.drawRoundRect(-2f, 184.4f, 31.7f, 195.2f, 1.5f, 1.5f, fillPaint(cross, opacity)); canvas.drawRoundRect(9.5f, 173.3f, 20.3f, 206.3f, 1.5f, 1.5f, fillPaint(cross, opacity))
            }
            "binge" -> {
                canvas.drawRoundRect(-76f, 236f, 98f, 258f, 9f, 9f, fillPaint("#50146E", .28f * opacity))
                canvas.drawRoundRect(-77.7f, 197.7f, 98.7f, 249f, 5f, 5f, fillPaint("#D9D7D3", opacity))
                canvas.drawRoundRect(-77.7f, 197.7f, 98.7f, 244.2f, 5f, 5f, fillPaint("#F6F5F3", opacity))
                canvas.drawOval(RectF(-2.5f, 237f, 24.5f, 267f), fillPaint("#CB7EF4", opacity))
                val flick = (phase / 130f).toInt(); val swap = act > .5f && !reduced && (((flick + 1) * 7919) % 5) % 2 == 1
                lens(canvas, -61.3f, -.3f, if (swap) "#2A4BC2" else "#FA4250", if (swap) "#0640A6" else "#C91F2A", 1f, opacity)
                lens(canvas, 22f, 83f, if (swap) "#FA4250" else "#2A4BC2", if (swap) "#C91F2A" else "#0640A6", -1f, opacity)
                val m = Path().apply { moveTo(-35f, 263f); lineTo(55f, 263f); cubicTo(55f, 324f, -35f, 324f, -35f, 263f); close() }
                canvas.drawPath(m, fillPaint("#3D1F36", opacity)); canvas.save(); canvas.clipPath(m)
                val teeth = Path().apply { moveTo(-36f, 259f); lineTo(56f, 259f); lineTo(56f, 274f); quadTo(10f, 281f, -36f, 274f); close() }; canvas.drawPath(teeth, fillPaint("#FFFFFE", opacity)); canvas.drawOval(RectF(-21f, 293f, 41f, 329f), fillPaint("#CF242B", opacity)); canvas.restore()
            }
            "shopping" -> {
                curve(canvas, -42.6f, 220f, -29.2f, 200f, -15.8f, 220f, 5.6f, ink, opacity)
                curve(canvas, 20.8f, 220f, 34.2f, 200f, 47.6f, 220f, 5.6f, ink, opacity)
                curve(canvas, -28.4f, 239.4f, 2.2f, 267f, 32.8f, 239.4f, 6.2f, ink, opacity)
                val beat = if (reduced) 1f else 1f + .12f * act * max(0f, kotlin.math.sin(phase / 1150f * Math.PI * 2).toFloat())
                heart(canvas, 3.85f, 312.4f, 49.7f * beat, 42.7f * beat, opacity)
            }
            "scroll" -> {
                curve(canvas, -32f, 210f, -16.8f, 189f, -1.6f, 210f, 6.6f, "#181514", opacity); curve(canvas, 41f, 210f, 56.7f, 189f, 72.4f, 210f, 6.6f, "#181514", opacity)
                canvas.drawOval(RectF(-30.7f, 222.7f, 6.3f, 257.7f), fillPaint(white, opacity)); canvas.drawOval(RectF(29f, 222.3f, 69.6f, 258.3f), fillPaint(white, opacity))
                canvas.drawOval(RectF(-21.3f, 229.3f, -3.7f, 249.1f), fillPaint("#181514", opacity)); canvas.drawOval(RectF(35f, 229.7f, 53f, 249.1f), fillPaint("#181514", opacity))
                val lid = .45f + (.04f - .45f) * act
                val eye1 = Path().apply { addOval(RectF(-30.7f, 222.7f, 6.3f, 258.7f), Path.Direction.CW) }; canvas.save(); canvas.clipPath(eye1); canvas.drawRect(-31.7f, 221.7f, 7.3f, 222.7f + 36f * lid, fillPaint("#E9393E", opacity)); canvas.restore()
                val eye2 = Path().apply { addOval(RectF(29f, 222.3f, 69.6f, 258.3f), Path.Direction.CW) }; canvas.save(); canvas.clipPath(eye2); canvas.drawRect(28f, 221.3f, 70.6f, 222.3f + 36f * lid, fillPaint("#E9393E", opacity)); canvas.restore()
                curve(canvas, -6.5f, 276.5f, 22f, 295f, 47f, 276.5f, 6.2f, "#181514", opacity); canvas.drawOval(RectF(-80.7f, 243.9f, -69.7f, 254.9f), fillPaint("#181514", opacity))
            }
            else -> {
                curve(canvas, -41.5f, 212f, -36f, 194f, -21.5f, 192.5f, 6.4f, "#1B1B1B", opacity); curve(canvas, 38.5f, 190f, 60f, 188f, 70.5f, 212f, 6.4f, "#1B1B1B", opacity)
                for (i in 0..1) {
                    val ex = if (i == 0) -21f else 41f; val ey = if (i == 0) 238.5f else 237.8f
                    canvas.drawOval(RectF(ex - 14f, ey + 17f, ex + 16f, ey + 25f), fillPaint("#9A9AA2", opacity)); canvas.drawOval(RectF(ex - 23.7f, ey - 22.2f, ex + 23.7f, ey + 22.2f), fillPaint("#1B1B1B", opacity))
                    canvas.save(); canvas.translate(ex - 5.6f, ey - 8.4f); canvas.rotate(-25f); canvas.drawOval(RectF(-13.5f, -10.6f, 13.5f, 10.6f), fillPaint(white, opacity)); canvas.restore(); canvas.drawOval(RectF(ex + .2f, ey + 3.7f, ex + 10.4f, ey + 12.9f), fillPaint(white, opacity))
                }
                curve(canvas, -14f, 283.5f, 9.2f, 299.5f, 32.5f, 283.5f, 5.6f, "#1B1B1B", opacity)
                val flapA = if (!reduced && act > 0f) .45f + .55f * abs(cos(max(0f, age) / 165f * Math.PI).toFloat()) else 1f
                butterfly(canvas, -80f, 201f, 0f, flapA, opacity); butterfly(canvas, 86.5f, 299.5f, -43f, flapA, opacity)
            }
        }
    }

    private fun curve(c: Canvas, x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, width: Float, color: String, alpha: Float) {
        val p = Path().apply { moveTo(x0, y0); quadTo(cx, cy, x1, y1) }
        c.drawPath(p, strokePaint(color, width, alpha))
    }

    private fun zed(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, scale: Float, color: String, alpha: Float) {
        if (scale <= .001f) return
        val cx = (x0 + x1) / 2f; val cy = (y0 + y1) / 2f; val hw = (x1 - x0) / 2f - width / 2f; val hh = (y1 - y0) / 2f - width / 2f
        val p = Path().apply { moveTo(-hw * .82f, -hh); lineTo(hw * .82f, -hh); lineTo(-hw * .82f, hh); lineTo(hw * .82f, hh) }
        c.save(); c.translate(cx, cy); c.scale(scale, scale); c.rotate(-12f); c.drawPath(p, strokePaint(color, width, alpha, Paint.Cap.SQUARE, Paint.Join.MITER)); c.restore()
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, alpha: Float) {
        val s = w / 2f; val top = cy - h / 2f; val bot = cy + h / 2f
        val p = Path().apply { moveTo(cx, bot); cubicTo(cx - s * .3f, bot - h * .22f, cx - s, cy + h * .02f, cx - s, top + h * .3f); cubicTo(cx - s, top - h * .02f, cx - s * .15f, top - h * .06f, cx, top + h * .2f); cubicTo(cx + s * .15f, top - h * .06f, cx + s, top - h * .02f, cx + s, top + h * .3f); cubicTo(cx + s, cy + h * .02f, cx + s * .3f, bot - h * .22f, cx, bot); close() }
        c.drawPath(p, fillPaint("#FB5754", alpha))
    }

    private fun lens(c: Canvas, x0: Float, x1: Float, top: String, bottom: String, inner: Float, alpha: Float) {
        val p = Path().apply { moveTo(x0, 207f); lineTo(x1, 207f); lineTo(x1 - if (inner > 0) 4f else 0f, 242f); lineTo(x0 + if (inner < 0) 4f else 0f, 242f); close() }
        val paint = fillPaint(top, alpha)
        paint.shader = LinearGradient(x0, 207f, x1, 242f, Color.parseColor(top), Color.parseColor(bottom), Shader.TileMode.CLAMP)
        paint.pathEffect = CornerPathEffect(3f)
        c.drawPath(p, paint)
    }

    private val upper = floatArrayOf(-3f, -10f, -36f, -12f, -38f, -5f, -31f, 3f, -4f, 2f)
    private val lower = floatArrayOf(-4f, 4f, -30f, 7f, -27f, 16f, -19f, 19f, -6f, 13f)
    private fun butterfly(c: Canvas, x: Float, y: Float, angle: Float, flap: Float, alpha: Float) {
        c.save(); c.translate(x, y); c.rotate(angle)
        repeat(2) { side ->
            c.save(); c.scale(flap, 1f)
            val up = polygon(if (side == 1) mirror(upper) else upper); val lo = polygon(if (side == 1) mirror(lower) else lower)
            val wing = fillPaint("#E8FAA4", alpha)
            val outline = strokePaint("#14296B", 2.6f, alpha)
            val corner = CornerPathEffect(4f)
            wing.pathEffect = corner
            outline.pathEffect = corner
            c.drawPath(lo, wing); c.save(); c.clipPath(lo); c.drawRect(if (side == 1) 4f else -34f, 6f, if (side == 1) 34f else -4f, 11f, fillPaint("#3CD43C", alpha)); c.restore(); c.drawPath(lo, outline)
            c.drawPath(up, wing); c.drawPath(up, outline)
            val sx = if (side == 1) -1f else 1f; c.drawCircle(-24f * sx, -3f, 3f, fillPaint("#8A8AE6", alpha)); c.drawCircle(-17f * sx, 11f, 3f, fillPaint("#8A8AE6", alpha)); c.drawCircle(-15f * sx, -2f, 1.3f, fillPaint("#14296B", alpha)); c.restore()
        }
        c.drawRoundRect(-2.8f, -11f, 2.8f, 24f, 2.8f, 2.8f, fillPaint("#9DB7F2", alpha)); c.drawRoundRect(-2.8f, -11f, 2.8f, 24f, 2.8f, 2.8f, strokePaint("#14296B", 2.2f, alpha)); c.drawCircle(0f, -14.5f, 4.6f, fillPaint("#9DB7F2", alpha)); c.drawCircle(0f, -14.5f, 4.6f, strokePaint("#14296B", 2.2f, alpha)); curve(c, -1.5f, -18.5f, -2.5f, -23f, -7.5f, -23.5f, 2.2f, "#14296B", alpha); curve(c, 1.5f, -18.5f, 2.5f, -23f, 7.5f, -23.5f, 2.2f, "#14296B", alpha); c.restore()
    }

    private fun polygon(points: FloatArray) = Path().apply { moveTo(points[0], points[1]); var i = 2; while (i < points.size) { lineTo(points[i], points[i + 1]); i += 2 }; close() }
    private fun mirror(points: FloatArray) = FloatArray(points.size) { i -> if (i % 2 == 0) -points[i] else points[i] }
    private fun pop(value: Float): Float { val p = value.coerceIn(0f, 1f); val s = 1.7f; val q = p - 1f; return if (p <= 0f) 0f else 1f + (s + 1f) * q * q * q + s * q * q }

    private fun fillPaint(color: String, alpha: Float) = fillPaint(Color.parseColor(color), alpha)
    private fun fillPaint(color: Int, alpha: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = withAlpha(color, alpha) }
    private fun strokePaint(color: String, width: Float, alpha: Float, cap: Paint.Cap = Paint.Cap.ROUND, join: Paint.Join = Paint.Join.ROUND) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = withAlpha(Color.parseColor(color), alpha); strokeWidth = width; strokeCap = cap; strokeJoin = join }
    private fun panelPath(width: Float, height: Float, radius: Float) = Path().apply { moveTo(0f, 0f); lineTo(width, 0f); lineTo(width, height - radius); quadTo(width, height, width - radius, height); lineTo(radius, height); quadTo(0f, height, 0f, height - radius); close() }
    private fun withAlpha(color: Int, alpha: Float) = Color.argb((Color.alpha(color) * alpha).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    /** Matches Reanimated's default `interpolateColor` gamma correction (2.2). */
    private fun interpolateStops(value: Float, values: IntArray): Int {
        val p = value.coerceIn(0f, (values.size - 1).toFloat())
        val i = p.toInt().coerceAtMost(values.size - 2)
        return gammaColor(values[i], values[i + 1], p - i)
    }

    private fun gammaColor(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun channel(start: Int, end: Int): Int {
            val a = (start / 255f).pow(2.2f)
            val b = (end / 255f).pow(2.2f)
            return ((a + (b - a) * t).pow(1f / 2.2f) * 255f).roundToInt()
        }
        val alpha = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t).roundToInt()
        return Color.argb(
            alpha,
            channel(Color.red(from), Color.red(to)),
            channel(Color.green(from), Color.green(to)),
            channel(Color.blue(from), Color.blue(to)),
        )
    }
    private fun interpolateStopsFloat(value: Float, values: FloatArray): Float { val p = value.coerceIn(0f, (values.size - 1).toFloat()); val i = p.toInt().coerceAtMost(values.size - 2); val t = p - i; return values[i] + (values[i + 1] - values[i]) * t }
}
