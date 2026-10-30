package com.ngocdevv.nativecook.blocks.sheets.bookfansignin

import android.content.Context
import android.graphics.*
import androidx.core.graphics.PathParser
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.LOOP
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.Frame
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.cellAt
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.coverOf
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.flying
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.mix
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.slot
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.smooth
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.wallShift

/** Native Canvas mirrors Skia's reference geometry; the home layer and its Gaussian blur are baked once. */
internal class BookFanSigninArt(context: Context) {
    private val covers = context.assets.open("bookfansignin/bookfansignin_covers.webp").use {
        requireNotNull(BitmapFactory.decodeStream(it))
    }
    private val faces = listOf("Regular","Medium","SemiBold","Bold").associateWith {
        Typeface.createFromAsset(context.assets,"bookfansignin/bookfansignin_Inter-$it.ttf")
    }
    private val ink = 0x0e0e10
    private val grey = 0x8a8a8e
    private val greyLight = 0x9a9a9f
    private val pink = 0xe0457b
    private val logo = path("M-15 14 L-15 -6 C-15 -15 -5 -17 -1 -10 C2 -4 -4 2 3 6 C9 9.5 15 4 14 -4 C13 -11 6 -12 4 -7")
    private val mail = Path().apply {
        addRoundRect(RectF(-10f, -7.5f, 10f, 7.5f), 2.5f, 2.5f, Path.Direction.CW)
        moveTo(-9f, -6f)
        lineTo(0f, 1.2f)
        lineTo(9f, -6f)
    }
    private val labels = mutableMapOf<String, Paint>()
    private val softShapes = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 64
    }
    private val sigmas = java.util.WeakHashMap<MaskFilter, Double>()
    private val over = Picture().apply {
        val canvas = beginRecording(402, 1074)
        homeOver(canvas)
        endRecording()
    }
    private val blurred: Bitmap = run {
        val page = Bitmap.createBitmap(804, 1748, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.scale(2f, 2f)
        canvas.drawColor(Color.WHITE)
        wall(canvas, wallShift(0.0, 0.0), DoubleArray(6))
        canvas.drawPicture(over)
        BookFanSigninBlur.gaussian(page, 26.0)
    }
    private fun color(rgb: Int, opacity: Double = 1.0) = ((opacity.coerceIn(0.0, 1.0)*255).roundToInt() shl 24) or rgb
    private fun paint(rgb: Int = 0xffffff, opacity: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = color(rgb, opacity)
    }
    private fun stroke(rgb: Int, strokeThickness: Double, opacity: Double = 1.0) = paint(rgb, opacity).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeThickness.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private fun rect(x: Double, y: Double, coverWidth: Double, coverHeight: Double) = RectF(x.toFloat(), y.toFloat(), (x+coverWidth).toFloat(), (y+coverHeight).toFloat())
    // GPU Canvas ignores MaskFilter on shapes; reuse small software-rasterized shadows.
    private fun round(canvas: Canvas, x: Double, y: Double, coverWidth: Double, coverHeight: Double, radius: Double, brush: Paint) {
        val sigma = sigmas[brush.maskFilter]
        if (sigma!=null) {
            val pad = ceil(sigma*3).toInt()
            val key = "$coverWidth:$coverHeight:$radius:$sigma:${brush.color and 0xffffff}"
            val bitmap = softShapes.getOrPut(key) {
                Bitmap.createBitmap(ceil(coverWidth).toInt()+pad*2, ceil(coverHeight).toInt()+pad*2, Bitmap.Config.ARGB_8888).also {
                    image->
                    Canvas(image).drawRoundRect(rect(pad.toDouble(), pad.toDouble(), coverWidth, coverHeight), radius.toFloat(), radius.toFloat(), Paint(brush).apply {
                        alpha = 255
                    }
                    )
                }
            }
            canvas.drawBitmap(bitmap, (x-pad).toFloat(), (y-pad).toFloat(), paint(opacity = brush.alpha/255.0))
        }
        else canvas.drawRoundRect(rect(x, y, coverWidth, coverHeight), radius.toFloat(), radius.toFloat(), brush)
    }
    private fun path(data: String) = requireNotNull(PathParser.createPathFromPathData(data))
    private fun shadow(rgb: Int, opacity: Double, sigma: Double) = paint(rgb, opacity).apply {
        maskFilter = BlurMaskFilter(((sigma-0.5)/0.57735).coerceAtLeast(0.001).toFloat(), BlurMaskFilter.Blur.NORMAL).also {
            sigmas[it] = sigma
        }
    }
    private fun font(face: String, size: Double) = labels.getOrPut("$face:$size") {
        paint().apply {
            typeface = faces.getValue(face)
            textSize = size.toFloat()
            isSubpixelText = true
        }
    }
    private fun width(text: String, face: String, size: Double, track: Double = 0.0): Double {
        val widths = FloatArray(text.length)
        font(face, size).getTextWidths(text, widths)
        return widths.sum().toDouble()+max(0, text.length-1)*track*size
    }
    private fun label(canvas: Canvas,
         text: String,
         x: Double,
         cy: Double,
         size: Double,
         face: String = "Regular",
         rgb: Int = ink,
         opacity: Double = 1.0,
         ax: Double = 0.0,
         track: Double = 0.0,
         blur: Double = 0.0,
         shader: Shader? = null) {
        val brush = Paint(font(face, size))
        brush.color = color(rgb, opacity)
        brush.shader = shader
        if (blur>0.1)brush.maskFilter = BlurMaskFilter(((blur-0.5)/0.57735).coerceAtLeast(0.001).toFloat(), BlurMaskFilter.Blur.NORMAL)
        val widths = FloatArray(text.length)
        brush.getTextWidths(text, widths)
        val textWidth = width(text, face, size, track)
        var at = x-textWidth*ax
        // Only the short title focus transition needs a tiny software text mask.
        if (blur>0.1&&canvas.isHardwareAccelerated) {
            val pad = ceil(blur*3).toInt()+2
            val image = Bitmap.createBitmap(ceil(textWidth).toInt()+pad*2, ceil(size*2).toInt()+pad*2, Bitmap.Config.ARGB_8888)
            val bitmapCanvas = Canvas(image)
            var dx = pad.toDouble()
            for (i in text.indices) {
                bitmapCanvas.drawText(text, i, i+1, dx.toFloat(), (pad+size+size*0.36).toFloat(), brush)
                dx+=widths[i]+track*size
            }
            canvas.drawBitmap(image, (at-pad).toFloat(), (cy-pad-size).toFloat(), null)
        }
        else for (i in text.indices) {
            canvas.drawText(text, i, i+1, at.toFloat(), (cy+size*0.36).toFloat(), brush)
            at+=widths[i]+track*size
        }
    }
    private fun source(coverIndex: Int): Rect {
        val index = ((coverIndex%10)+10)%10
        return Rect(index%5*300, index/5*450, index%5*300+300, index/5*450+450)
    }
    private fun cover(canvas: Canvas, coverIndex: Int, x: Double, y: Double, coverWidth: Double, deg: Double, shadowAmount: Double, opacity: Double = 1.0, lift: Double = 0.0) {
        val coverHeight = coverWidth*1.5
        canvas.save()
        canvas.translate(x.toFloat(), (y - 3 * lift).toFloat())
        canvas.rotate(deg.toFloat())
        canvas.scale((1 + 0.05 * lift).toFloat(), (1 + 0.05 * lift).toFloat())
        if (shadowAmount>0.01)round(canvas,
             -coverWidth/2+2,
             -coverHeight/2+4+8*shadowAmount,
             coverWidth,
             coverHeight,
             3.0,
             shadow(0x1a1426,
             0.22*shadowAmount*opacity,
             4+8*shadowAmount))
        canvas.save()
        val clip = Path().apply {
            addRoundRect(rect(-coverWidth/2, -coverHeight/2, coverWidth, coverHeight), 2.5f, 2.5f, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        canvas.drawBitmap(covers, source(coverIndex), rect(-coverWidth/2, -coverHeight/2, coverWidth, coverHeight), paint(opacity = opacity))
        if (lift > 0.02) {
            val gloss = paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                shader = LinearGradient((-coverWidth / 2).toFloat(), (-coverHeight / 2).toFloat(), (coverWidth / 2).toFloat(), (coverHeight / 2).toFloat(), intArrayOf(color(0xffffff, 0.0), color(0xffffff, 0.22 * lift * opacity), color(0xffffff, 0.0)), floatArrayOf(0.2f, 0.5f, 0.8f), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(rect(-coverWidth / 2, -coverHeight / 2, coverWidth, coverHeight), gloss)
        }
        canvas.restore()
        canvas.restore()
    }
    private fun wall(canvas: Canvas, shift: Double, taken: DoubleArray, amb: Double = -1.0) {
        for (i in 0..6)for (j in -1..6) {
            if (flying.indices.any {
                flying[it].first==i&&flying[it].second==j&&taken[it]>0.001
            }
            )continue
            val brush = cellAt(i, j, shift)
            if (brush.second< -60||brush.second>330)continue
            val front = -180 + ((amb % (LOOP / 2)) / (LOOP / 2)) * 900
            val d = brush.first + brush.second * 0.6 - front
            val lift = if (amb < 0) 0.0 else exp(-(d * d) / 2600)
            cover(canvas, coverOf(i, j), brush.first, brush.second, 60.0, -27.0, 0.25 + 0.6 * lift, 0.9, lift)
        }
        val fade = paint().apply {
            shader = RadialGradient(214f,
                 176f,
                 150f,
                 intArrayOf(color(0xffffff,
                 0.08),
                 color(0xffffff,
                 0.62),
                 Color.WHITE),
                 floatArrayOf(46f/150,
                 0.74f,
                 1f),
                 Shader.TileMode.CLAMP)
        }
        canvas.drawRect(rect(0.0, -60.0, 402.0, 420.0), fade)
        canvas.drawRect(rect(0.0, 228.0, 402.0, 140.0), paint().apply {
            shader = LinearGradient(0f, 228f, 0f, 292f, intArrayOf(0x00ffffff, Color.WHITE), null, Shader.TileMode.CLAMP)
        }
        )
    }
    private fun homeOver(canvas: Canvas) {
        label(canvas,"retold", 22.0, 90.0, 30.0,"Bold", track = -0.045)
        label(canvas,"Read less, remember more", 22.0, 309.0, 15.2,"Medium", pink)
        listOf("Which book is","on your mind?").forEachIndexed {
            i, text->label(canvas, text, 22.0, 344.0+39*i, 35.0,"Bold", track = -0.02)
        }
        listOf("Turn the best ideas from great books into lessons","you can actually use.").forEachIndexed {
            i, text->label(canvas, text, 22.0, 420.0+23*i, 15.0, rgb = grey)
        }
        round(canvas, 22.0, 476.0, 356.0, 42.0, 21.0, paint(0xf5f5f6))
        canvas.drawCircle(48f, 496f, 6.5f, stroke(greyLight, 1.6))
        canvas.drawLine(53f, 501f, 56.5f, 504.5f, stroke(greyLight, 1.6))
        label(canvas,"Search books or authors", 64.0, 497.0, 16.0, rgb = greyLight)
        val xs = doubleArrayOf(22.0, 115.0, 240.0, 348.0)
        val ws = doubleArrayOf(80.0, 113.0, 91.0, 104.0)
        listOf("For you","Most popular","Top picks","Best sellers").forEachIndexed {
            i, text->
            round(canvas, xs[i], 538.0, ws[i], 37.0, 18.5, paint(if (i==0)0x121212 else 0xf3f3f4))
            val ix = xs[i]+16
            val iy = 556.5
            canvas.save()
            canvas.translate(ix.toFloat(), iy.toFloat())
            when (i) {
                0->canvas.drawPath(path("M0 -6.5 Q0.9 -0.9 6.5 0 Q0.9 0.9 0 6.5 Q-0.9 0.9 -6.5 0 Q-0.9 -0.9 0 -6.5 Z"), paint())
                1->canvas.drawPath(path("M0 -6 C3 -2.5 4.6 0 4.2 2.4 C3.8 5 2 6.3 0 6.3 C-2.2 6.3 -4 4.9 -4.2 2.6 C-4.4 0.4 -2.8 -1 -2.2 -2.8 C-1.4 -1.3 -0.6 -0.8 -0.2 -0.9 C-0.6 -2.6 -0.6 -4.4 0 -6 Z"),
                     paint(0x6e6e73))
                2->{
                    for (k in 0..2)canvas.drawLine(-5f, (-4+k*4).toFloat(), (1-k*1.5).toFloat(), (-4+k*4).toFloat(), stroke(0x6e6e73, 1.5))
                    canvas.drawCircle(4.5f, 4f, 1.8f, paint(0x6e6e73))
                }
                3->round(canvas, -5.5, -5.0, 11.0, 10.0, 2.0, stroke(0x6e6e73, 1.5))
            }
            canvas.restore()
            label(canvas, text, xs[i]+30, iy, 13.6,"Medium", if (i==0)0xffffff else 0x6e6e73)
        }
        fun chevron(y: Double) {
            canvas.drawPath(Path().apply {
                moveTo(369f, (y-5).toFloat())
                lineTo(374f, y.toFloat())
                lineTo(369f, (y+5).toFloat())
            }
            , stroke(greyLight, 1.8))
        }
        label(canvas,"Recommended for you", 22.0, 615.0, 15.0,"Medium", grey)
        chevron(615.0)
        val bx = doubleArrayOf(31.0, 159.0, 285.0)
        val ns = intArrayOf(3, 7, 0)
        val titles = listOf(listOf("Small Steps,","Long Roads"), listOf("The Lighthouse","Habit"), listOf("Before the Sun","Gets Up"))
        val authors = listOf("Mira Okafor","Jonas Feld","Ines Varga")
        for (i in 0..2) {
            round(canvas, bx[i]+2, 641.0, 87.0, 116.0, 0.0, shadow(0, 0.16, 5.0))
            canvas.drawBitmap(covers, source(ns[i]), rect(bx[i], 636.0, 87.0, 116.0), paint())
            val tx = bx[i]-4+if (i==2)-10 else 0
            titles[i].forEachIndexed {
                k, text->label(canvas, text, tx, 770.0+k*18, 14.5,"SemiBold")
            }
            label(canvas, authors[i], tx, 806.0, 14.5, rgb = grey)
        }
        label(canvas,"Top picks", 22.0, 842.0, 15.0,"Medium", grey)
        chevron(842.0)
        for (i in 0..2)canvas.drawBitmap(covers, source((i*4+5)%10), rect(bx[i], 871.0, 87.0, 116.0), paint())
    }
    // The strip is clipped to the sheet; its incoming covers fade into the settled copies.
    private fun sheet(canvas: Canvas, frame: Frame, driver: BookFanSigninDriver, oy: Double) {
        canvas.save()
        canvas.translate(0f, oy.toFloat())
        round(canvas, 12.0, 424.0, 378.0, 442.0, 42.0, shadow(0, 0.18*frame.sheet, 18.0))
        round(canvas, 12.0, 418.0, 378.0, 442.0, 42.0, paint())
        canvas.save()
        canvas.clipPath(Path().apply {
            addRoundRect(rect(12.0, 418.0, 378.0, 442.0), 42f, 42f, Path.Direction.CW)
        }
        )
        val drift = 4*sin(2*PI*driver.amb/9)
        for (k in 0..5) {
            val u = frame.flights[k]
            if (u<0.86)continue
            val sl = slot(k)
            cover(canvas, coverOf(flying[k].first, flying[k].second), sl.first+drift, sl.second, 62.0, sl.third, 0.4, smooth((u-0.86)/0.14))
        }
        canvas.drawRect(rect(12.0, 448.0, 378.0, 53.0), paint().apply {
            shader = LinearGradient(0f, 448f, 0f, 500f, intArrayOf(0x00ffffff, Color.WHITE), null, Shader.TileMode.CLAMP)
        }
        )
        canvas.restore()
        val icon = max(0.0, frame.icon)
        if (icon>0.01) {
            canvas.save()
            canvas.translate(201f, 483f)
            val scale = (0.7+0.3*icon).toFloat()
            canvas.scale(scale, scale)
            round(canvas, -31.0, -31.0, 62.0, 62.0, 15.0, paint(0x121212, min(1.0, icon*2)))
            if (frame.logo>0.01) {
                val mark = if (frame.logo<0.999)Path().also {
                    PathMeasure(logo, false).getSegment(0f, (PathMeasure(logo, false).length*frame.logo).toFloat(), it, true)
                }
                else logo
                canvas.drawPath(mark, stroke(0xffffff, 5.2))
            }
            canvas.restore()
        }
        if (frame.tag>0.01) {
            canvas.save()
            canvas.translate(0f, (10*(1-frame.tag)).toFloat())
            round(canvas, 113.0, 557.0, 176.0, 24.0, 12.0, paint(0xfce7ef, frame.tag))
            label(canvas,"Your Books, Put Into Practice", 201.0, 569.0, 14.0,"Medium", pink, frame.tag, 0.5)
            canvas.restore()
        }
        val titles = listOf("Books Don't Change You,","Practice Does.")
        for (i in 0..1) {
            val opacity = if (i==0)frame.title else frame.title2
            if (opacity<=0.01)continue
            label(canvas, titles[i], 201.0, 601.0+35*i+12*(1-opacity), 30.0,"Bold", if (i==0)ink else pink, opacity, 0.5, -0.02, if (opacity<0.98)6*(1-opacity) else 0.0)
            if (i==1&&frame.shimmer>=0) {
                val titleWidth = width(titles[i], "Bold", 30.0, -0.02)
                val shimmerX = mix(201 - titleWidth / 2 - 60, 201 + titleWidth / 2 + 60, frame.shimmer)
                val shimmerShader = LinearGradient(
                    (shimmerX - 40).toFloat(), 0f,
                    (shimmerX + 40).toFloat(), 0f,
                    intArrayOf(0x00ffffff, color(0xffd1e2, 0.95), 0x00ffffff),
                    null, Shader.TileMode.CLAMP,
                )
                canvas.save()
                canvas.clipRect(rect(shimmerX - 40, 614.0, 80.0, 44.0))
                label(
                    canvas, titles[i], 201.0, 636.0, 30.0, "Bold",
                    ax = 0.5, track = -0.02, shader = shimmerShader,
                )
                canvas.restore()
            }
        }
        if (frame.sub>0.01)listOf("Turn any non-fiction book into interactive exercises",
            "you actually do. Stop reading. Start applying.",
            "Make knowledge stick forever.").forEachIndexed {
            i, text->label(canvas, text, 201.0, doubleArrayOf(672.0, 691.0, 709.0)[i]+8*(1-frame.sub)*(1+i*0.4), 14.2, rgb = grey, opacity = frame.sub, ax = 0.5)
        }
        if (frame.cta>0.01) {
            canvas.save()
            canvas.translate(201f, (783.5+22*(1-frame.cta)).toFloat())
            val scale = (1-0.03*driver.ctaP).toFloat()
            canvas.scale(scale, scale)
            round(canvas, -165.0, -28.5, 330.0, 57.0, 28.5, paint(0x121212, frame.cta))
            val coverWidth = width("Continue with Email","SemiBold", 19.0)+32
            label(canvas,"Continue with Email", -coverWidth/2+32, 0.0, 19.0,"SemiBold", 0xffffff, frame.cta)
            canvas.save()
            canvas.translate((-coverWidth/2+10).toFloat(), 0f)
            canvas.drawPath(mail, stroke(0xffffff, 1.8, frame.cta))
            canvas.restore()
            canvas.restore()
        }
        canvas.restore()
    }
    fun draw(canvas: Canvas, viewWidth: Float, viewHeight: Float, frame: Frame, driver: BookFanSigninDriver) {
        canvas.drawColor(Color.WHITE)
        canvas.save()
        val scale = viewWidth/402f
        canvas.scale(scale, scale)
        val shift = wallShift(driver.amb, driver.scroll)
        wall(canvas, shift, frame.flights, driver.amb)
        val ps = (1-0.04*max(frame.press, driver.pressP)).toFloat()
        canvas.save()
        canvas.translate(333.5f, 90f)
        canvas.scale(ps, ps)
        round(canvas, -43.5, -15.0, 87.0, 36.0, 18.0, shadow(0, 0.08, 8.0))
        round(canvas, -43.5, -18.0, 87.0, 36.0, 18.0, paint())
        label(canvas,"Sign in", 0.0, 0.0, 16.0,"Medium", ax = 0.5)
        canvas.restore()
        canvas.drawPicture(over)
        if (frame.blur>0.001) {
            val edge = mix(-160.0, 914.0, frame.blur)
            canvas.saveLayer(0f, 0f, 402f, 874f, null)
            canvas.drawBitmap(blurred, null, rect(0.0, 0.0, 402.0, 874.0), paint())
            canvas.drawRect(rect(0.0, 0.0, 402.0, 874.0), paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                shader = LinearGradient(0f, edge.toFloat(), 0f, (edge+160).toFloat(), intArrayOf(Color.BLACK, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            }
            )
            canvas.restore()
        }
        if (frame.dim>0.001)canvas.drawRect(rect(0.0, -50.0, 402.0, 974.0), paint(0, 0.42*frame.dim))
        val oy = (1-frame.sheet)*496+max(0.0, driver.drag)*min(1.0, frame.sheet)+min(0.0, driver.drag)*0.25+(viewHeight-874*scale)/scale
        if (frame.sheet>0.001)sheet(canvas, frame, driver, oy)
        for (k in 0..5) {
            val u = frame.flights[k]
            if (u<=0.001||u>=0.999)continue
            val src = cellAt(flying[k].first, flying[k].second, shift)
            val sl = slot(k)
            val ex = sl.first
            val ey = sl.second+oy
            val cx = mix(src.first, ex, 0.5)+(k-2.5)*22
            val cy = mix(src.second, ey, 0.45)-36
            val q = 1-u
            val lift = sin(PI*u)
            cover(canvas,
                 coverOf(flying[k].first,
                 flying[k].second),
                 q*q*src.first+2*q*u*cx+u*u*ex,
                 q*q*src.second+2*q*u*cy+u*u*ey,
                 mix(60.0,
                 62.0,
                 u)*(1+0.22*lift),
                 mix(-27.0,
                 sl.third,
                 u)+14*lift*if (k%2==0)1 else -1,
                 lift,
                 if (u>0.86)1-smooth((u-0.86)/0.14) else 1.0)
        }
        canvas.restore()
    }
}
