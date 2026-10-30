package com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.C
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Page
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Sheet
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Card
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.QR
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Bear
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Gift
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Sticker
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Bubble
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.Frame
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.T
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.mix
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.smooth
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardDriver.Live

/** Canvas-only renderer. Static page/hero, glyph outlines and filtered bitmaps are cached once. */
internal class QrJoyPostcardArt(private val context: Context) {
    private val regular = Typeface.createFromAsset(context.assets, "qrjoypostcard/qrjoypostcard_Inter-Regular.ttf")
    private val semi = Typeface.createFromAsset(context.assets, "qrjoypostcard/qrjoypostcard_Inter-SemiBold.ttf")
    private val bold = Typeface.createFromAsset(context.assets, "qrjoypostcard/qrjoypostcard_Inter-Bold.ttf")
    private val hand = Typeface.createFromAsset(context.assets, "qrjoypostcard/qrjoypostcard_CoveredByYourGrace.ttf")

    private fun image(name: String) = context.assets.open("qrjoypostcard/qrjoypostcard_$name.webp").use {
        requireNotNull(BitmapFactory.decodeStream(it))
    }
    private val bear = image("bear-atlas")
    private val cushion = image("cushion")
    private val tulips = image("tulips")
    private val rawGift = image("gift")
    private lateinit var c: Canvas

    private fun color(v: Int, a: Double = 1.0) = (a.coerceIn(0.0, 1.0)*255).roundToInt().shl(24) or v

    private fun paint(v: Int = 0xffffff, a: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = color(v, a)
    }

    private fun stroke(v: Int, w: Double, a: Double = 1.0) = paint(v, a).apply {
        style = Paint.Style.STROKE
        strokeWidth = w.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun rect(x: Number, y: Number, w: Number, h: Number) = RectF(x.toFloat(), y.toFloat(), (x.toDouble()+w.toDouble()).toFloat(), (y.toDouble()+h.toDouble()).toFloat())

    private fun translate(x: Number, y: Number) {
        c.translate(x.toFloat(), y.toFloat())
    }

    private fun scale(x: Number, y: Number = x) {
        c.scale(x.toFloat(), y.toFloat())
    }

    private fun rotate(degrees: Number) {
        c.rotate(degrees.toFloat())
    }
    private inline fun saved(block: () -> Unit) {
        c.save()
        try {
            block()
        } finally {
            c.restore()
        }
    }
    private inline fun layer(p: Paint? = null, block: () -> Unit) {
        c.saveLayer(null, p)
        try {
            block()
        } finally {
            c.restore()
        }
    }

    private fun rr(x: Number, y: Number, w: Number, h: Number, r: Number, p: Paint) {
        c.drawRoundRect(rect(x, y, w, h), r.toFloat(), r.toFloat(), p)
    }

    private fun circle(x: Number, y: Number, r: Number, p: Paint) {
        c.drawCircle(x.toFloat(), y.toFloat(), max(0f, r.toFloat()), p)
    }

    private fun line(x1: Number, y1: Number, x2: Number, y2: Number, p: Paint) {
        c.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), p)
    }

    private fun linear(x1: Number, y1: Number, x2: Number, y2: Number, colors: IntArray, stops: FloatArray? = null) = paint().apply {
        shader = LinearGradient(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), colors, stops, Shader.TileMode.CLAMP)
    }

    private fun radial(x: Number, y: Number, r: Number, colors: IntArray, stops: FloatArray? = null) = paint().apply {
        shader = RadialGradient(x.toFloat(), y.toFloat(), max(0.01f, r.toFloat()), colors, stops, Shader.TileMode.CLAMP)
    }

    private fun blended(a: Int, b: Int, p: Double, alpha: Double = 1.0): Paint {

        fun ch(shift: Int) = mix(((a shr shift) and 255).toDouble(), ((b shr shift) and 255).toDouble(), p).roundToInt().coerceIn(0, 255)
        return paint((ch(16) shl 16) or (ch(8) shl 8) or ch(0), alpha)
    }

    private fun drawImage(image: Bitmap, x: Number, y: Number, w: Number, h: Number, p: Paint? = null, src: Rect? = null) {
        c.drawBitmap(image, src, rect(x, y, w, h), p?: paint())
    }

    private fun clipCard() {
        c.clipPath(cardPath)
    }
    private val cardPath = Path().apply {
        addRoundRect(rect(0, 0, Card.w, Card.h), Card.r.toFloat(), Card.r.toFloat(), Path.Direction.CW)
    }
    private val star = requireNotNull(PathParser.createPathFromPathData("M0 -10 Q1.5 -1.5 10 0 Q1.5 1.5 0 10 Q-1.5 1.5 -10 0 Q-1.5 -1.5 0 -10 Z"))
    private val heart = requireNotNull(PathParser.createPathFromPathData("M0 7.4 C-1.1 6.4 -10 0.6 -10 -3.6 C-10 -7.2 -6.9 -8.8 -4.9 -8.8 C-2.8 -8.8 -1.2 -7.6 0 -5.6 C1.2 -7.6 2.8 -8.8 4.9 -8.8 C6.9 -8.8 10 -7.2 10 -3.6 C10 0.6 1.1 6.4 0 7.4 Z"))
    private val tick = requireNotNull(PathParser.createPathFromPathData("M-4.6 0.4 L-1.4 3.6 L4.8 -3.4"))
    private val squares = Path().apply {
        for (m in QrJoyPostcardTheme.modules) addRect(rect(QR.x+m.c*QR.m-0.02, QR.y+m.r*QR.m-0.02, QR.m+0.04, QR.m+0.04), Path.Direction.CW)
    }
    private val dots = Path().apply {
        for (m in QrJoyPostcardTheme.modules) if (!m.heart) addCircle((QR.x+(m.c+0.5)*QR.m).toFloat(), (QR.y+(m.r+0.5)*QR.m).toFloat(), (QR.m*0.42).toFloat(), Path.Direction.CW)
    }
    private val dotPaint = linear(QR.x, QR.y, QR.x+QR.size, QR.y+QR.size, intArrayOf(color(C.dotA), color(C.dotB)))
    private val postPaint = linear(0, 0, Card.w*0.5, Card.h, intArrayOf(color(C.postTop), color(C.postBottom)))
    private val bubblePaint = paint().apply {
        typeface = hand
        textSize = 22f
        isSubpixelText = true
        hinting = Paint.HINTING_OFF
        fontFeatureSettings = "'kern' 0, 'liga' 0"
    }
    private val bubbleText = Path().apply {
        bubblePaint.getTextPath("for you", 0, 7, 0f, 0f, this)
    }
    private val bubbleTextW = bubblePaint.measureText("for you").toDouble()
    private val labels = mutableMapOf<String, Paint>()

    private fun label(text: String, x: Number, cy: Number, size: Double, face: Typeface = regular, ax: Double = 0.0, p: Paint = paint(C.ink)) {
        val key = "$text:$size:${face.hashCode()}"
        val font = labels.getOrPut(key) {
            paint().apply {
                typeface = face
                textSize = size.toFloat()
                isSubpixelText = true
                hinting = Paint.HINTING_OFF
                fontFeatureSettings = "'kern' 0, 'liga' 0"
            }
        }
        font.color = p.color
        font.maskFilter = p.maskFilter
        c.drawText(text, (x.toDouble()-font.measureText(text)*ax).toFloat(), (cy.toDouble()+size*0.36).toFloat(), font)
    }

    private fun bake(w: Double, h: Double, draw: () -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(ceil(w*3).toInt(), ceil(h*3).toInt(), Bitmap.Config.ARGB_8888)
        val previous = if (::c.isInitialized) c else null
        c = Canvas(bitmap)
        scale(3)
        draw()
        if (previous != null) c = previous
        return bitmap
    }
    private val gift = QrJoyPostcardRaster.blur(bake(Gift.w+40, Gift.h+40) {
        drawImage(rawGift, 20, 20, Gift.w, Gift.h)
    }, Gift.blur*3)
    private val stickerBase = bake(Sticker.w+Sticker.border*4, Sticker.h+Sticker.border*4) {
        drawImage(tulips, Sticker.border*2, Sticker.border*2, Sticker.w, Sticker.h)
    }
    private val sticker = QrJoyPostcardRaster.silhouette(stickerBase, (Sticker.border*3).roundToInt(), 0xffffff).also {
        Canvas(it).drawBitmap(stickerBase, 0f, 0f, paint())
    }
    private val stickerShadow = QrJoyPostcardRaster.blur(QrJoyPostcardRaster.silhouette(stickerBase, (Sticker.border*3).roundToInt(), 0x5b0a55), 9.0)
    private val page = bake(402.0, 874.0) {
        drawPage()
    }
    private val hero = bake(402.0, Sheet.heroBottom-Sheet.top+40) {
        translate(0, -Sheet.top)
        drawHero()
    }
    // BlurMaskFilter is ignored by a hardware Canvas. RenderNode gives actual Gaussian blur on
    // API 31+, and older devices rasterize the small affected region rather than losing the blur.
    private val blurNodes = mutableListOf<RenderNode>()
    private var blurIndex = 0
    private inline fun blurred(sigma: Double, bounds: RectF, block: () -> Unit) {
        if (sigma <= 0.01) {
            block()
            return
        }
        val previous = c
        if (Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated) {
            val index = blurIndex++
            val node = if (index<blurNodes.size) blurNodes[index] else RenderNode("QrJoyPostcard blur").also {
                blurNodes.add(it)
            }
            val pad = ceil(sigma*3).toInt()
            val left = floor(bounds.left).toInt()-pad
            val top = floor(bounds.top).toInt()-pad
            node.setPosition(left, top, ceil(bounds.right).toInt()+pad, ceil(bounds.bottom).toInt()+pad)
            node.setClipToBounds(false)
            node.setRenderEffect(RenderEffect.createBlurEffect(sigma.toFloat(), sigma.toFloat(), Shader.TileMode.DECAL))
            c = node.beginRecording()
            translate(-left, -top)
            try {
                block()
            } finally {
                node.endRecording()
                c = previous
            }
            c.drawRenderNode(node)
        } else {
            val pad = ceil(sigma*3).toInt()
            val left = floor(bounds.left).toInt()-pad
            val top = floor(bounds.top).toInt()-pad
            val bitmap = Bitmap.createBitmap((ceil(bounds.width()).toInt()+pad*2).coerceAtLeast(1), (ceil(bounds.height()).toInt()+pad*2).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            c = Canvas(bitmap)
            translate(-left, -top)
            try {
                block()
            } finally {
                c = previous
            }
            c.drawBitmap(QrJoyPostcardRaster.blur(bitmap, sigma), left.toFloat(), top.toFloat(), paint())
        }
    }

    private fun drawPage() {
        c.drawColor(color(C.page))
        rr(0, -40, 402, Page.headerY+40, 26, paint(C.card))
        c.drawPath(Path().apply {
            moveTo((Page.backX+4.5).toFloat(), (Page.backY-9).toFloat())
            lineTo((Page.backX-4.5).toFloat(), Page.backY.toFloat())
            lineTo((Page.backX+4.5).toFloat(), (Page.backY+9).toFloat())
        }, stroke(0x8f2be0, 2.4))
        label("Orders", 201, Page.ordersY, 17.4, semi, 0.5)
        val row = Page.row
        rr(row.x, row.y, row.w, row.h, row.r, paint(C.card))
        val bx = Page.bagX
        val by = Page.bagY
        val bag = Path().apply {
            addRoundRect(rect(bx-9.5, by-3.5, 19, 15), 3.2f, 3.2f, Path.Direction.CW)
            moveTo((bx-4.6).toFloat(), (by-3.5).toFloat())
            cubicTo((bx-4.6).toFloat(), (by-12).toFloat(), (bx+4.6).toFloat(), (by-12).toFloat(), (bx+4.6).toFloat(), (by-3.5).toFloat())
        }
        c.drawPath(bag, stroke(C.ink, 2.0))
        label("Purchases", Page.purchasesX, by+1, 17.4, semi)
        chevron(Page.chevronX, Page.chevronY, 3.0, 6.5, stroke(C.ink, 2.2))
        val promo = Page.promo
        rr(promo.x, promo.y, promo.w, promo.h, promo.r, paint(C.promo))
        val tb = Page.tulips
        drawImage(tulips, tb.x, tb.y, tb.w, tb.h)
        label("Decorate QR code", Page.promoX, Page.promoY, 17.4, semi, 0.0, paint(C.violet))
        chevron(Page.promoChevronX, Page.promoY, 2.4, 5.0, stroke(C.violet, 2.0))
        val od = Page.order
        rr(od.x, od.y, od.w, 874, od.r, paint(C.card))
        label("Pick up", 16.4, Page.pick1Y, 23.4, bold)
        label("by Mar 17, Tue", 16.4, Page.pick2Y, 23.4, bold)
        val qb = Page.qrButton
        rr(qb.x, qb.y, qb.w, qb.h, qb.r, paint(C.chip))
        val gx = qb.x+qb.w/2
        val gy = qb.y+qb.h/2
        for ((dx, dy) in arrayOf(-1 to -1, 1 to -1, -1 to 1)) rr(gx+dx*5.6-4.2, gy+dy*5.6-4.2, 8.4, 8.4, 2.6, stroke(C.violet, 2.3))
        for ((dx, dy) in arrayOf(2.6 to 2.6, 8.4 to 2.6, 2.6 to 8.4, 8.4 to 8.4)) rr(gx+dx-1.6, gy+dy-1.6, 3.2, 3.2, 0.9, paint(C.violet))
        label("Pickup point · 3A Quiet Lane, Riverside", 16.4, Page.pointY, 16.4, regular, 0.0, paint(C.grey))
    }

    private fun chevron(x: Double, y: Double, dx: Double, dy: Double, p: Paint) {
        c.drawPath(Path().apply {
            moveTo((x-dx).toFloat(), (y-dy).toFloat())
            lineTo((x+dx).toFloat(), y.toFloat())
            lineTo((x-dx).toFloat(), (y+dy).toFloat())
        }, p)
    }

    private fun drawHero() {
        val top = Sheet.top
        val bottom = Sheet.heroBottom
        rr(0, top, 402, bottom-top+40, Sheet.r, linear(0, top, 402*0.3, bottom, intArrayOf(color(C.heroTop), color(C.heroMid))))
        saved {
            c.clipPath(Path().apply {
                addRoundRect(rect(0, top, 402, bottom-top+40), Sheet.r.toFloat(), Sheet.r.toFloat(), Path.Direction.CW)
            })
            for (pool in arrayOf(doubleArrayOf(12.0, 455.0, 95.0, 0.9), doubleArrayOf(392.0, 528.0, 120.0, 0.9), doubleArrayOf(210.0, 525.0, 90.0, 0.45))) circle(pool[0], pool[1], pool[2], radial(pool[0], pool[1], pool[2], intArrayOf(color(0xffe4fc, pool[3]), color(0xffe4fc, 0.0))))
            val shaft = Path().apply {
                moveTo(40f, (top-10).toFloat())
                lineTo(95f, (top-10).toFloat())
                lineTo(180f, bottom.toFloat())
                lineTo(110f, bottom.toFloat())
                close()
            }
            // This bake is already software
            // A mask blur follows Skia's sigma-to-radius mapping.
            val p = linear(20, top, 120, top+60, intArrayOf(color(0xffffff, 0.0), color(0xffffff, 0.32), color(0xffffff, 0.0)), floatArrayOf(0f, 0.5f, 1f))
            p.maskFilter = BlurMaskFilter(((10-0.5)/0.57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
            c.drawPath(shaft, p)
        }
    }

    private fun drawStar(x: Double, y: Double, half: Double, rot: Double, a: Double) {
        if (a <= 0.01 || half <= 0.2) return
        saved {
            translate(x, y)
            rotate(rot)
            scale(half/10)
            blurred(2.2, rect(-10, -10, 20, 20)) {
                c.drawPath(star, paint(0xffffff, a*0.55))
            }
            c.drawPath(star, paint(0xffffff, a))
        }
    }

    private fun drawSparkles(live: Live, boost: Double) {
        for ((i, s) in QrJoyPostcardTheme.sparkles.withIndex()) {
            val cycles = if (i%2 == 0) 2 else 3
            val w = 0.5+0.5*sin(2*PI*(cycles*live.amb/QrJoyPostcardMotion.LOOP+s[3]))
            val k = (0.72+0.28*w)*(1+0.25*boost)
            drawStar(s[0]-live.tiltX*3, s[1]-live.tiltY*2, s[2]*k, 6*sin(2*PI*(live.amb/QrJoyPostcardMotion.LOOP+s[3])), 0.75+0.25*w)
        }
    }

    private fun drawGift(live: Live) {
        val bob = 2.2*sin(2*PI*live.amb/QrJoyPostcardMotion.LOOP)
        val rot = Gift.rot+1.2*sin(2*PI*live.amb/QrJoyPostcardMotion.LOOP+1.1)
        saved {
            translate(Gift.x-live.tiltX*6, Gift.y+bob-live.tiltY*3)
            rotate(rot)
            drawImage(gift, -(Gift.w+40)/2, -(Gift.h+40)/2, Gift.w+40, Gift.h+40)
        }
    }

    private fun enterCard(f: Frame, live: Live) {
        val angle = mix(Card.angle, QrJoyPostcardMotion.TURN_LIFTED, f.lift.coerceIn(0.0, 1.0))+0.045*live.jiggle
        translate(Card.cx, Card.cy-QrJoyPostcardMotion.LIFT*f.lift)
        rotate(angle*180/PI)
        if (abs(live.tiltX)>0.001 || abs(live.tiltY)>0.001) {
            // Project Rx then Ry with the same 620-pt perspective as processTransform3d.
            val rx = -live.tiltY*0.18
            val ry = live.tiltX*0.24
            val m = Matrix().apply {
                setValues(floatArrayOf(cos(ry).toFloat(), (sin(ry)*sin(rx)).toFloat(), 0f, 0f, cos(rx).toFloat(), 0f, (sin(ry)/620).toFloat(), (-cos(ry)*sin(rx)/620).toFloat(), 1f))
            }
            c.concat(m)
        }
        val k = 1+0.012*f.landSquash
        scale(1/k, k)
        translate(-Card.w/2, -Card.h/2)
    }

    private fun drawCardFace(f: Frame) {
        blurred(9.0, rect(4, 8+8*f.lift, Card.w-8, Card.h)) {
            rr(4, 8+8*f.lift, Card.w-8, Card.h, Card.r, paint(0x8c0d80, 0.16+0.08*min(1.0, f.lift)))
        }
        c.drawPath(cardPath, paint())
        if (f.face <= 0.001) return
        val mx = QR.x+QR.size/2
        val my = QR.y+QR.size/2
        val radius = 12+f.face*190
        saved {
            clipCard()
            if (f.face<0.999) {
                layer {
                    c.drawRect(rect(0, 0, Card.w, Card.h), postPaint)
                    val mask = radial(mx, my, radius, intArrayOf(color(0), color(0), color(0, 0.0)), floatArrayOf(0f, max(0.0, (radius-16)/radius).toFloat(), 1f))
                    mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    c.drawRect(rect(0, 0, Card.w, Card.h), mask)
                }
                blurred(3.0, rect(mx-radius, my-radius, radius*2, radius*2)) {
                    circle(mx, my, radius-8, stroke(0xff6ed9, 5.0, 0.35*sin(PI*f.face)))
                }
            } else c.drawRect(rect(0, 0, Card.w, Card.h), postPaint)
            val dash = stroke(0xffffff, 1.3, 0.95*smooth((f.face-0.6)/0.4)).apply {
                pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
            }
            rr(7, 7, Card.w-14, Card.h-14, Card.r-6, dash)
        }
    }

    private fun drawFinders(f: Frame) {
        val m = QR.m
        val k = f.finders
        for ((i, fd) in QrJoyPostcardTheme.finders.withIndex()) {
            val x = QR.x+fd.second*m
            val y = QR.y+fd.first*m
            saved {
                c.rotate((10*sin(PI*k)*(if (i == 1) -1 else 1)).toFloat(), (x+3.5*m).toFloat(), (y+3.5*m).toFloat())
                val ring = Path().apply {
                    addRoundRect(rect(x, y, 7*m, 7*m), (2.2*m*k).toFloat(), (2.2*m*k).toFloat(), Path.Direction.CW)
                    addRoundRect(rect(x+m, y+m, 5*m, 5*m), (1.5*m*k).toFloat(), (1.5*m*k).toFloat(), Path.Direction.CW)
                    fillType = Path.FillType.EVEN_ODD
                }
                c.drawPath(ring, blended(C.qrInk, C.finder, k))
                rr(x+2*m, y+2*m, 3*m, 3*m, 1.5*m*k, blended(C.qrInk, C.finderCore, k))
            }
        }
    }

    private fun drawModules(t: Double, flying: Boolean) {
        val m = QR.m
        val ink = paint(C.qrInk)
        val calm = t<T.swirlAt || (t >= QrJoyPostcardMotion.SWIRL_DONE && t<T.backSwirlAt) || t >= QrJoyPostcardMotion.BACK_DONE
        if (calm) {
            if (flying) return
            if (t<T.swirlAt || t >= QrJoyPostcardMotion.BACK_DONE) c.drawPath(squares, ink) else c.drawPath(dots, dotPaint)
            return
        }
        for (md in QrJoyPostcardTheme.modules) {
            val u = QrJoyPostcardMotion.moduleU(md, t)
            val settled = u <= 0.0005 || u >= 0.9995
            if (settled == flying) continue
            val g = (md.r+md.c)/(2.0*QR.n)
            if (u <= 0.0005) {
                c.drawRect(rect(QR.x+md.c*m-0.02, QR.y+md.r*m-0.02, m+0.04, m+0.04), ink)
                continue
            }
            if (u >= 0.9995) {
                if (!md.heart) circle(QR.x+(md.c+0.5)*m, QR.y+(md.r+0.5)*m, m*0.42, dotPaint)
                continue
            }
            val pose = QrJoyPostcardMotion.modulePose(md, u)
            val round = smooth((u-0.12)/0.5)
            val size = m*mix(1.0, 0.84, smooth(u))*(1+0.55*pose.z)
            val alpha = if (md.heart) 1-smooth((u-0.55)/0.4) else 1.0
            if (pose.z>0.3) {
                val back = QrJoyPostcardMotion.modulePose(md, (if (t<T.back) u-0.07 else u+0.07).coerceIn(0.0, 1.0))
                circle(back.x, back.y, size*0.42, blended(C.qrInk, C.petal, min(1.0, u*2), 0.22*pose.z*alpha))
            }
            val dot = blended(C.dotA, C.dotB, g).color and 0xffffff
            val p = if (u<0.5) blended(C.qrInk, C.petal, u*2, alpha) else blended(C.petal, dot, (u-0.5)*2, alpha)
            saved {
                translate(pose.x, pose.y)
                rotate(pose.spin*180/PI)
                val pw = size*(1+0.75*pose.z)
                val ph = size*(1-0.32*pose.z)
                rr(-pw/2, -ph/2, pw, ph, min(pw, ph)/2*round, p)
            }
        }
    }

    private fun drawBear(f: Frame, live: Live) {
        val frame = floor((if (live.wave != 0.0) Bear.rest+live.wave else f.wave)+0.5).toInt().coerceIn(0, Bear.frames-1)
        val col = frame%Bear.cols
        val row = frame/Bear.cols
        val breathe = 0.012*sin(2*PI*3*live.amb/QrJoyPostcardMotion.LOOP)
        val squash = f.squash+live.squash
        val hop = f.hop+live.hop
        saved {
            translate(Bear.x, Bear.y)
            scale(1+max(0.0, squash)*0.4, 1-max(0.0, squash)*0.3)
            drawImage(cushion, -Bear.w/2, -Bear.h, Bear.w, Bear.h)
        }
        val bounds = rect(Bear.x-34+hop*0.6, Bear.y-26, 68-hop*1.2, 12)
        blurred(3.0, bounds) {
            c.drawOval(bounds, paint(0x1f004d, 0.28*(1-min(1.0, hop/18))))
        }
        saved {
            translate(Bear.x, Bear.y-16-hop)
            rotate(-live.lean*7)
            scale(1+squash, (1-squash)*(1+breathe))
            translate(0, 16)
            drawImage(bear, -Bear.w/2, -Bear.h, Bear.w, Bear.h, src = Rect(col*Bear.fw, row*Bear.fh, (col+1)*Bear.fw, (row+1)*Bear.fh))
        }
    }

    private fun drawHeart(f: Frame) {
        if (f.heart <= 0.01) return
        val m = QR.m
        val k = max(0.0, f.heart)
        val a = min(1.0, k*2.5)
        saved {
            translate(QR.x+QR.size/2, QR.y+QR.size/2)
            scale(0.55+0.45*k)
            blurred(3.0, rect(-3.3*m, -3.1*m, 6.6*m, 6.6*m)) {
                rr(-3.3*m, -3.1*m, 6.6*m, 6.6*m, 1.8*m, paint(0x990d73, 0.18*a))
            }
            rr(-3.3*m, -3.3*m, 6.6*m, 6.6*m, 1.8*m, paint(0xffffff, a))
            scale(0.95)
            translate(0, 0.8)
            c.drawPath(heart, paint(0xf0219e, a))
        }
    }

    private fun drawSticker(f: Frame, live: Live) {
        if (f.t<3.15 || f.stickerPeel >= 0.999) return
        val e = smooth(f.sticker)
        val drop = 1-e*e
        val sq = f.stickerSquash
        val peel = f.stickerPeel
        val k = 1+0.38*drop+0.12*peel
        val rot = Sticker.rot+22*drop+26*peel
        val x = Sticker.x+18*drop+70*peel+live.tiltX*3
        val y = Sticker.y-46*drop-95*peel+live.tiltY*2
        val a = min(1.0, f.sticker*5)*(1-peel)
        val sw = Sticker.w+Sticker.border*4
        val sh = Sticker.h+Sticker.border*4
        saved {
            translate(x+2+10*drop, y+3+14*drop)
            rotate(rot)
            scale(k)
            drawImage(stickerShadow, -sw/2, -sh/2, sw, sh, paint(0xffffff, a*(0.32-0.16*drop)))
        }
        saved {
            translate(x, y)
            rotate(rot)
            scale(k*(1+0.07*sq), k*(1-0.07*sq))
            drawImage(sticker, -sw/2, -sh/2, sw, sh, paint(0xffffff, a))
        }
    }

    private fun drawSheen(f: Frame) {
        if (f.sheen<0) return
        val x = mix(-90.0, Card.w+90, f.sheen)
        val p = linear(x-40, 0, x+40, 30, intArrayOf(color(0xffffff, 0.0), color(0xffffff, 0.42), color(0xffffff, 0.0)), floatArrayOf(0f, 0.5f, 1f)).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
        saved {
            clipCard()
            c.drawRect(rect(0, 0, Card.w, Card.h), p)
        }
    }

    private fun drawBurst(f: Frame) {
        if (f.burst<0) return
        for (i in 0 until 9) {
            val a = 2*PI*i/9+0.35
            val h = 0.5+0.5*sin(i*7.31)
            val d = (52+46*h)*(1-(1-f.burst).pow(3))
            val life = sin(PI*min(1.0, f.burst*1.15))
            drawStar(QR.x+QR.size/2+cos(a)*d, QR.y+QR.size/2+sin(a)*d*0.9, (4+5*h)*life, 40*f.burst+i*20, life)
        }
    }

    private fun drawBubble(f: Frame) {
        if (f.bubble <= 0.01) return
        val k = max(0.0, f.bubble)
        val a = min(1.0, k*2)
        val bx = Bubble.x
        val by = Bubble.y
        saved {
            translate(Bubble.tailX, Bubble.tailY)
            scale(0.5+0.5*k)
            translate(-Bubble.tailX, -Bubble.tailY)
            val path = Path().apply {
                addRoundRect(rect(bx-Bubble.w/2, by-Bubble.h/2, Bubble.w, Bubble.h), Bubble.r.toFloat(), Bubble.r.toFloat(), Path.Direction.CW)
                moveTo((bx-22).toFloat(), (by+10).toFloat())
                quadTo((bx-26).toFloat(), (by+22).toFloat(), Bubble.tailX.toFloat(), Bubble.tailY.toFloat())
                quadTo((bx-10).toFloat(), (by+20).toFloat(), (bx-8).toFloat(), (by+14).toFloat())
                close()
            }
            saved {
                translate(0, 3)
                blurred(3.0, rect(bx-Bubble.w/2, by-Bubble.h/2, Bubble.w, Bubble.h+30)) {
                    c.drawPath(path, paint(0x9e0080, 0.16*a))
                }
            }
            c.drawPath(path, paint(0xffffff, a))
            val heartW = 15
            val total = bubbleTextW+4+heartW
            val x0 = bx-total/2
            val yb = by+22*0.3
            if (f.write>0) layer {
                saved {
                    translate(x0, yb)
                    c.drawPath(bubbleText, paint(0x7a1fa8))
                }
                val reveal = min(1.0, f.write/0.82)*(bubbleTextW+10)
                val pen = linear(x0+reveal-8, 0, x0+reveal, 0, intArrayOf(color(0), color(0, 0.0))).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                }
                c.drawRect(rect(x0-4, by-Bubble.h/2, total+8, Bubble.h), pen)
            }
            val hp = smooth((f.write-0.8)/0.2)
            if (hp>0) saved {
                translate(x0+bubbleTextW+4+heartW/2.0, by-1)
                scale(0.62*(0.6+0.4*hp)*(1+0.15*sin(PI*hp)))
                c.drawPath(heart, paint(0xf0219e, a*hp))
            }
        }
    }

    private fun drawClose() {
        circle(Sheet.closeX, Sheet.closeY, Sheet.closeR, paint())
        val p = stroke(0xffadf2, 1.9)
        val d = 4.4
        line(Sheet.closeX-d, Sheet.closeY-d, Sheet.closeX+d, Sheet.closeY+d, p)
        line(Sheet.closeX+d, Sheet.closeY-d, Sheet.closeX-d, Sheet.closeY+d, p)
    }

    private fun drawText(f: Frame, live: Live) {
        val ink = paint(0x252527)
        label("Share the joy", Sheet.titleX, Sheet.titleY, 23.4, bold, p = ink)
        label("Turn any order into a reason to smile!", Sheet.titleX, Sheet.subY, 16.2, p = ink)
        for (s in 0..1) {
            for (i in 0..2) label(QrJoyPostcardTheme.steps[s][i], Sheet.stepTextX, Sheet.linesY[s][i], 16.2, p = ink)
            val cy = Sheet.chipY[s]
            val k = if (s == 0) f.check else 0.0
            circle(Sheet.chipX, cy, Sheet.chipR*(1+0.12*sin(PI*k)), blended(C.stepChip, C.cta, k))
            if (k<0.999) saved {
                translate(Sheet.chipX, cy)
                scale(1-0.4*k)
                label("${s+1}", 0, 0, 15.0, semi, 0.5, paint(0x252527, 1-k))
            }
            if (k>0.001) {
                val trimmed = Path()
                val measure = PathMeasure(tick, false)
                measure.getSegment(0f, (measure.length*min(1.0, k*1.15)).toFloat(), trimmed, true)
                saved {
                    translate(Sheet.chipX, cy)
                    c.drawPath(trimmed, stroke(0xffffff, 2.3))
                }
            }
        }
        val b = Sheet.cta
        val press = max(f.press, live.cta)
        val bs = 1-0.04*press
        val k = f.cta
        saved {
            translate(b.x+b.w/2, b.y+b.h/2)
            scale(bs)
            rr(-b.w/2, -b.h/2, b.w, b.h, b.r, blended(C.cta, C.ctaPress, press))
            val blur = if (k>0.001 && k<0.999) 2.2*sin(PI*k) else 0.0
            val bounds = rect(-b.w/2, -b.h/2, b.w, b.h)
            if (k<0.999) blurred(blur, bounds) {
                label("Decorate QR code", 0, -9*k, 16.8, semi, 0.5, paint(0xffffff, 1-k))
            }
            if (k>0.001) blurred(blur, bounds) {
                label("Send postcard", 0, 9*(1-k), 16.8, semi, 0.5, paint(0xffffff, k))
            }
        }
    }

    fun drawScene(canvas: Canvas, width: Float, height: Float, f: Frame, live: Live) {
        c = canvas
        blurIndex = 0
        val s = width/402.0
        val dy = height-874*s
        c.drawColor(color(0xf6f6fa))
        saved {
            scale(s)
            drawImage(page, 0, 0, 402, 874)
        }
        c.drawRect(rect(0, 0, width, height), paint(0, 0.5))
        saved {
            scale(s)
            val b = Page.grabber
            rr(b.x, b.y, b.w, b.h, b.h/2, paint(0xffffff, 0.62))
        }
        saved {
            translate(0, dy)
            scale(s)
            drawImage(hero, 0, Sheet.top, 402, Sheet.heroBottom-Sheet.top+40)
            saved {
                c.clipRect(rect(0, Sheet.top, 402, Sheet.heroBottom-Sheet.top))
                drawGift(live)
                drawSparkles(live, if (f.burst >= 0) sin(PI*f.burst) else 0.0)
            }
            saved {
                enterCard(f, live)
                drawCardFace(f)
                drawFinders(f)
                drawModules(f.t, false)
                drawHeart(f)
                drawSheen(f)
                drawBear(f, live)
                drawModules(f.t, true)
                drawSticker(f, live)
                drawBurst(f)
            }
            c.drawRect(rect(0, Sheet.heroBottom, 402, 874-Sheet.heroBottom+200), paint())
            drawBubble(f)
            drawClose()
            drawText(f, live)
        }
    }
}
