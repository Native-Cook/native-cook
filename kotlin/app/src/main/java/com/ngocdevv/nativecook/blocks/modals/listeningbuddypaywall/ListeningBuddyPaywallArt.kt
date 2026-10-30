package com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall

import android.content.Context
import android.graphics.*
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.BEAT
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.bump
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.clamp01
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.easeOut
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.FACES
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.Frame
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.LOOP
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.mix
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.smooth
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallMotion.spring
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.C
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.COPY
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.DY
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.PIN_W
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.PLANS
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.S
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.UI
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.REF_W

/** Every path is in the reference pin's 736-pixel space. */
internal class ListeningBuddyPaywallArt(context: Context) {
    private lateinit var c: ListeningBuddyPaywallCanvas
    private data class Geo(val w: Double, val h: Double, val s: Double)
    private val faces = listOf("Manrope_400Regular.ttf", "Manrope_500Medium.ttf", "Manrope_600SemiBold.ttf", "Manrope_700Bold.ttf", "Nunito-ExtraBold.ttf").map {
        Typeface.createFromAsset(context.assets, "listeningbuddypaywall/listeningbuddypaywall_$it")
    }
    private val fonts = mapOf("title" to (4 to 46), "sub" to (1 to 26), "name" to (3 to 22), "price" to (4 to 44), "per" to (1 to 19), "popular" to (3 to 19), "line" to (1 to 22), "link" to (1 to 22), "cta" to (3 to 26)).mapValues {
        (_, value) ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = faces[value.first]
            textSize = value.second.toFloat()
        }
    }
    // A software-rasterized blur retains the close-button shadow on every hardware API.
    private val closeShadow = Bitmap.createBitmap(152, 152, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawCircle(76f, 76f, 44f, paint(0xB9A68F, .18).apply {
            maskFilter = BlurMaskFilter((8f - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL)
        })
    }
    private val TAU = PI * 2
    private fun color(value: Int, alpha: Double = 1.0) = ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or (value and 0xffffff)
    private fun paint(value: Int, alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(value, alpha)
    }
    private fun stroke(value: Int, width: Number, alpha: Double = 1.0) = paint(value, alpha).apply {
        style = Paint.Style.STROKE
        strokeWidth = width.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private fun mixCol(a: Int, b: Int, amount: Double): Int {
        fun channel(shift: Int) = mix((a shr shift) and 255, (b shr shift) and 255, amount).roundToInt().coerceIn(0,255)
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    private fun rect(x: Number, y: Number, w: Number, h: Number) = RectF(x.toFloat(),y.toFloat(),x.toFloat()+w.toFloat(),y.toFloat()+h.toFloat())
    private fun rrect(x: Number, y: Number, w: Number, h: Number, radius: Number) = Pair(rect(x,y,w,h),radius.toFloat())
    private fun text(value: String, x: Number, cy: Number, font: Paint, ink: Paint, ax: Double = 0.0, track: Number = 0): Double {
        val p = Paint(font).apply {
            color = ink.color
        }
        val spacing = track.toDouble()
        val widths = FloatArray(value.length)
        p.getTextWidths(value, widths)
        val width = if (spacing == 0.0) p.measureText(value).toDouble() else widths.sum() + spacing * (value.length - 1)
        var px = x.toDouble() - width * ax
        val baseline = cy.toDouble() + p.textSize * .36
        if (spacing == 0.0) c.drawText(value, px, baseline, p)
        else for (i in value.indices) {
            c.drawText(value[i].toString(), px, baseline, p)
            px += widths[i] + spacing
        }
        return width
    }
    private fun note(x: Double, y: Double, s: Double, color: Int, a: Double, double: Boolean) {
        c.save()
        c.translate(x, y)
        c.scale(s, s)
        c.rotate(-18, 0, 0)
        c.drawOval(rect(-11, -7, 22, 15), paint(color, a))
        c.restore()
        c.save()
        c.translate(x, y)
        c.scale(s, s)
        c.drawLine(9, -2, 9, -40, stroke(color, 3.4, a))
        if (double) {
            c.drawOval(rect(18, -12, 22, 15), paint(color, a))
            c.drawLine(38, -6, 38, -44, stroke(color, 3.4, a))
            c.drawLine(9, -40, 38, -44, stroke(color, 6, a))
        } else {
            val fl = ListeningBuddyPaywallPath()
            fl.moveTo(9, -40)
            fl.cubicTo(22, -34, 24, -24, 18, -16)
            c.drawPath(fl, stroke(color, 3.4, a))
        }
        c.restore()
    }
    // ------------------------------------------------------------------ the scene
    private fun drawSky(f: Frame, top: Double) {
        c.drawRect(rect(-10, top, PIN_W + 20, UI.white - top), paint(C.sky))
        val drift = 26 * sin((TAU * f.amb) / LOOP)
        fun cloud(x: Double, y: Double, s: Double) {
            val p = ListeningBuddyPaywallPath()
            p.moveTo(x - 140 * s, y)
            p.cubicTo(x - 140 * s, y - 40 * s, x - 90 * s, y - 50 * s, x - 70 * s, y - 40 * s)
            p.cubicTo(x - 60 * s, y - 95 * s, x + 30 * s, y - 100 * s, x + 40 * s, y - 50 * s)
            p.cubicTo(x + 80 * s, y - 70 * s, x + 130 * s, y - 40 * s, x + 130 * s, y)
            p.close()
            c.drawPath(p, paint(C.cloud))
        }
        cloud(140 + drift, 155.0, 1.05)
        cloud(640 - drift * 0.6, 238.0, 0.9)
        // the hills behind the buddy
        val h1 = ListeningBuddyPaywallPath()
        h1.moveTo(-10, 470)
        h1.cubicTo(140, 380, 300, 420, 420, 480)
        h1.cubicTo(520, 530, 600, 420, 746, 440)
        h1.lineTo(746, UI.white + 2)
        h1.lineTo(-10, UI.white + 2)
        h1.close()
        c.drawPath(h1, paint(C.hillB))
        val h2 = ListeningBuddyPaywallPath()
        h2.moveTo(330, UI.white + 2)
        h2.cubicTo(420, 560, 470, 520, 540, 500)
        h2.cubicTo(620, 470, 690, 440, 746, 470)
        h2.lineTo(746, UI.white + 2)
        h2.close()
        c.drawPath(h2, paint(C.hillA))
    }
    private fun drawBuddy(f: Frame) {
        // the face blends from the last plan's to this one's on a spring
        val k = spring(f.selAt, 0.45, 0.62)
        val A = FACES[f.from]
        val B = FACES[f.plan]
        val eyes = mix(A.eyes, B.eyes, k)
        val smile = mix(A.smile, B.smile, k)
        val open = mix(A.open, B.open, k)
        val blush = mix(A.blush, B.blush, k)
        // the beat: a sway and a nod a happy hop on Continue or a poke
        val beat = (f.amb % BEAT) / BEAT
        val nod = sin(PI * beat).pow(2)
        val sway = 2.2 * sin((TAU * f.amb) / (BEAT * 2))
        val hop = max(if (f.go >= 0) 34 * bump(f.go, 0.0, 0.5) + 16 * bump(f.go, 0.5, 0.35) else 0.0, if (f.poke >= 0) 26 * bump(f.poke, 0.0, 0.42) else 0.0)
        val squeeze = if (f.poke >= 0) bump(f.poke, 0.0, 0.3) else 0.0
        val U = UI.buddy
        c.save()
        c.translate(U.x, U.y + 6 * nod - hop)
        c.rotate(-6 + sway, 0, 0)
        c.scale(1 + 0.04 * squeeze, 1 - 0.05 * squeeze)
        // the sprout, wiggling on the beat
        for (i in 0 until 2) {
            c.save()
            c.translate(-12 + i * 22, -238)
            c.rotate((if (i == 0) -28 else 26) + 8 * sin(TAU * beat + i), 0, 0)
            val lf = ListeningBuddyPaywallPath()
            lf.moveTo(0, 0)
            lf.cubicTo(-24, -14, -22, -50, 0, -66)
            lf.cubicTo(22, -50, 24, -14, 0, 0)
            lf.close()
            c.drawPath(lf, paint(if (i == 0) C.leaf else C.leafDark))
            c.drawLine(0, -4, 0, -56, stroke(0xFFFFFF, 2.4, 0.35))
            c.restore()
        }
        c.drawLine(-2, -232, 2, -246, stroke(C.leafDark, 6))
        // the body: a tall pebble
        val body = ListeningBuddyPaywallPath()
        body.moveTo(-150, 60)
        body.cubicTo(-158, -60, -150, -170, -100, -215)
        body.cubicTo(-50, -255, 70, -250, 115, -195)
        body.cubicTo(155, -145, 150, -40, 145, 60)
        body.close()
        c.drawPath(body, paint(C.body))
        c.save()
        c.clipPath(body)
        c.drawOval(rect(-120, -235, 150, 110), paint(C.bodyLight, 0.55))
        c.drawOval(rect(60, -160, 140, 260), paint(C.bodyShade, 0.4))
        c.restore()
        // cheeks
        c.drawCircle(-92, -78, 26, paint(C.cheek, 0.55 * blush + 0.2))
        c.drawCircle(98, -72, 24, paint(C.cheek, 0.55 * blush + 0.2))
        // eyes: closed happy arcs, open dots, or wide with a shine
        val ink = C.face
        for (e in 0 until 2) {
            val ex = if (e == 0) -34 else 40
            val ey = -128
            val closed = clamp01(1 - eyes)
            val wide = clamp01(eyes - 1)
            if (closed > 0.01) {
                val arc = ListeningBuddyPaywallPath()
                arc.moveTo(ex - 16, ey)
                arc.quadTo(ex, ey + 14 * closed, ex + 16, ey)
                c.drawPath(arc, stroke(ink, 6, closed))
            }
            if (closed < 0.99) {
                val r = mix(7, 13, wide)
                // blinks now and then
                val blink = max(0.0, 1 - abs(((f.amb + 0.4) % 3) - 0.08) / 0.08)
                // they look down toward the chosen plan
                val lookX = mix((f.from - 1) * 4.0, (f.plan - 1) * 4.0, k)
                c.save()
                c.translate(ex + lookX, ey + 2.5)
                c.scale(1, max(0.12, (1 - blink) * (1 - closed)))
                c.drawCircle(0, 0, r, paint(ink))
                if (wide > 0.05) c.drawCircle(-r * 0.35, -r * 0.35, r * 0.32, paint(0xFFFFFF, wide))
                c.restore()
            }
        }
        // the mouth: a smile that widens, or an open "o"
        val mw = mix(18, 46, smile) * (1 - 0.6 * open)
        val mp = ListeningBuddyPaywallPath()
        mp.moveTo(4 - mw, -86)
        mp.quadTo(4, -86 + mix(10, 34, smile), 4 + mw, -86)
        if (open < 0.05) c.drawPath(mp, stroke(ink, 6))
        else {
            c.drawPath(mp, stroke(ink, 6, 1 - open))
            c.drawOval(rect(4 - 12 * open, -92, 24 * open, 26 * open), paint(ink, open))
        }
        // headphones: the band over the top, the cups on either side
        val band = ListeningBuddyPaywallPath()
        band.moveTo(-150, -110)
        band.cubicTo(-160, -290, 150, -300, 140, -100)
        c.drawPath(band, stroke(C.phones, 15))
        c.drawPath(band, stroke(C.phonesLight, 4, 0.6))
        for (side in intArrayOf(-1, 1)) {
            c.save()
            c.translate(if (side < 0) -150 else 142, -100)
            c.drawRRect(rrect(-26, -44, 52, 88, 24), paint(C.phones))
            c.drawRRect(rrect(-14, -34, 18, 68, 9), paint(C.phonesLight, 0.6))
            c.restore()
        }
        c.restore()
        // the beat rings out of the right cup
        for (k2 in 0 until 2) {
            val q = (beat + k2 * 0.5) % 1
            val rx = U.x + 142 + 30 + 44 * q
            val a = 0.45 * (1 - q)
            val arc = ListeningBuddyPaywallPath()
            // small arcs beside the cup, clear of the pager dots below
            arc.addArc(rect(rx - 30, U.y - 150 - 14 * q, 60, 64 + 28 * q), -50, 100)
            c.drawPath(arc, stroke(C.phones, 4, a))
        }
    }
    private fun drawBushes(f: Frame) {
        val bob = 3 * sin((TAU * f.amb) / LOOP)
        fun bush(x: Double, y: Double, s: Double, color: Int) {
            val p = ListeningBuddyPaywallPath()
            p.moveTo(x - 75 * s, y)
            p.cubicTo(x - 80 * s, y - 40 * s, x - 40 * s, y - 60 * s, x - 15 * s, y - 45 * s)
            p.cubicTo(x, y - 85 * s, x + 50 * s, y - 80 * s, x + 50 * s, y - 40 * s)
            p.cubicTo(x + 80 * s, y - 45 * s, x + 85 * s, y - 10 * s, x + 80 * s, y)
            p.close()
            c.drawPath(p, paint(color))
        }
        bush(640.0, UI.white + 12 + bob, 1.0, C.bushB)
        bush(540.0, UI.white + 14 - bob, 1.05, C.bushA)
    }
    private fun drawNotes(f: Frame) {
        // a note drifts up from the cup every 1.5 s
        val U = UI.buddy
        for (i in 0 until 6) {
            val q = (f.amb / 1.5 + i) % 6
            if (q > 1.6) continue
            val u = q / 1.6
            // they stay in the sky between the buddy and the hills, clear of the text above
            val x = U.x + 210 + 24 * sin(u * 5 + i) + 50 * u
            val y = U.y - 150 - 130 * u
            note(x, y, 0.7 + 0.2 * (i % 2), if (i % 2 != 0) C.phones else C.bodyShade, 0.55 * sin(PI * u), i % 3 == 0)
        }
        // Continue: a burst of notes from the bar and the buddy
        if (f.go >= 0 && f.go < 2.4) {
            for (i in 0 until 9) {
                val t0 = i * 0.06
                val u = clamp01((f.go - t0) / 1.8)
                if (u <= 0 || u >= 1) continue
                val fromBar = i % 3 != 0
                val x0 = if (fromBar) 120.0 + i * 60 else U.x + 140
                val y0 = if (fromBar) UI.bar.y else U.y - 160
                val x = x0 + 50 * sin(u * 6 + i) * u
                val y = y0 - 520 * easeOut(u)
                note(x, y, 0.9 + 0.3 * sin(i.toDouble()), if (i % 2 != 0) C.border else C.title, 1 - smooth((u - 0.5) / 0.5), i % 2 == 0)
            }
        }
    }
    private fun drawPlans(f: Frame) {
        val K = UI.cards
        val Fr = UI.frame
        val k = spring(f.selAt, 0.42, 0.78)
        // the orange frame slides from the old card to the new one
        val fx = mix(K.x[f.from], K.x[f.plan], k)
        // while it travels the frame stretches along its way and thins a little, like a drop
        val travel = if (f.from != f.plan) sin(PI * clamp01(k)) else 0.0
        val fw = (K.w + Fr.pad * 2) * (1 + 0.22 * travel)
        val fh = (K.h + Fr.pad * 2) * (1 - 0.05 * travel)
        val fcx = fx + K.w / 2
        val fcy = K.y + K.h / 2
        c.drawRRect(rrect(fcx - fw / 2, fcy - fh / 2, fw, fh, Fr.r), stroke(C.border, Fr.w))
        for (i in 0 until 3) {
            val on = if (i == f.plan) k else if (i == f.from) 1 - k else 0.0
            val swell = if (i == f.plan) 0.035 * bump(f.selAt, 0.0, 0.45) else 0.0
            val cx = K.x[i] + K.w / 2
            val cy = K.y + K.h / 2
            c.save()
            c.translate(cx, cy)
            c.scale(1 + swell, 1 + swell)
            c.translate(-cx, -cy)
            c.drawRRect(rrect(K.x[i], K.y, K.w, K.h, K.r), paint(C.card))
            if (i == 1) {
                c.save()
                c.clipRRect(rrect(K.x[i], K.y, K.w, K.h, K.r))
                c.drawRect(rect(K.x[i], K.y, K.w, UI.header.h), paint(C.header))
                c.restore()
                text(COPY.popular[0], cx, K.y + 20, fonts.getValue("popular"), paint(C.white), 0.5, 4)
                text(COPY.popular[1], cx, K.y + 44, fonts.getValue("popular"), paint(C.white), 0.5, 4)
            }
            val ink = Paint(Paint.ANTI_ALIAS_FLAG)
            ink.setColor(mixCol(C.label, 0x3F4560, on))
            val pl = PLANS[i]
            text(pl.name, cx, K.y + 106, fonts.getValue("name"), ink, 0.5, 5)
            // the price, fitted to the card
            val pw = fonts.getValue("price").measureText(pl.price).toDouble()
            val sk = min(1.0, (K.w - 16) / pw)
            c.save()
            c.translate(cx, K.y + 163)
            c.scale(sk, sk)
            text(pl.price, 0, 0, fonts.getValue("price"), ink, 0.5)
            c.restore()
            text(pl.per, cx, K.y + 216, fonts.getValue("per"), paint(C.per), 0.5)
            c.restore()
        }
        // the note under the plans: the old lines leave, the new ones arrive
        val lk = smooth((f.selAt - 0.05) / 0.3)
        for (l in 0 until 2) {
            val y = UI.lines[l]
            val prev = PLANS[f.from].lines[l]
            val next = PLANS[f.plan].lines[l]
            if (prev == next || lk >= 1) {
                text(next, PIN_W / 2, y, fonts.getValue("line"), paint(C.note), 0.5)
                continue
            }
            text(prev, PIN_W / 2, y - 12 * lk, fonts.getValue("line"), paint(C.note, 1 - lk), 0.5)
            text(next, PIN_W / 2, y + 12 * (1 - lk), fonts.getValue("line"), paint(C.note, lk), 0.5)
        }
        val lw = fonts.getValue("link").measureText(COPY.links[0]).toDouble()
        val gap = 48
        val rw = fonts.getValue("link").measureText(COPY.links[1]).toDouble()
        val x0 = PIN_W / 2 - (lw + gap + rw) / 2
        text(COPY.links[0], x0, UI.links, fonts.getValue("link"), paint(C.fine))
        c.drawCircle(x0 + lw + gap / 2, UI.links, 2.5, paint(C.fine))
        text(COPY.links[1], x0 + lw + gap, UI.links, fonts.getValue("link"), paint(C.fine))
    }
    fun drawScene(native: Canvas, width: Double, height: Double, f: Frame) {
        c = ListeningBuddyPaywallCanvas(native)
        val g = Geo(width, height, width / REF_W)
        c.save()
        c.scale(g.s, g.s)
        c.translate(0, DY)
        c.scale(S, S)
        val top = -DY / S - 10
        val bottom = (g.h / g.s - DY) / S + 10
        c.drawRect(rect(-10, top, PIN_W + 20, bottom - top), paint(C.white))
        drawSky(f, top)
        // close
        val X = UI.close
        val cs = 1 - 0.04 * f.closeK
        c.save()
        c.translate(X.x, X.y)
        c.scale(cs, cs)
        c.drawBitmap(closeShadow, -76f, -72f, null)
        c.drawCircle(0, 0, X.r, paint(C.white))
        c.drawLine(-15, -15, 15, 15, stroke(C.close, 4))
        c.drawLine(15, -15, -15, 15, stroke(C.close, 4))
        c.restore()
        text(COPY.title, PIN_W / 2, UI.title.y, fonts.getValue("title"), paint(C.title), 0.5)
        for (l in 0 until 2) text(COPY.sub[l], PIN_W / 2, UI.sub[l], fonts.getValue("sub"), paint(C.sub), 0.5)
        for (i in 0 until 3) c.drawCircle(UI.dots.x[i], UI.dots.y, 7, paint(if (i == 0) C.dot else C.dotOff))
        // the buddy stands in the sky, cut off by the white sheet below
        c.save()
        c.clipRect(rect(-10, top, PIN_W + 20, UI.white - top))
        drawBuddy(f)
        c.restore()
        drawBushes(f)
        drawPlans(f)
        // the bar, full width to the bottom edge
        val B = UI.bar
        val bp = Paint(Paint.ANTI_ALIAS_FLAG)
        bp.setColor(mixCol(C.bar, 0x5B6077, f.press))
        c.drawRect(rect(-10, B.y, PIN_W + 20, bottom - B.y), bp)
        val ts = 1 - 0.04 * f.press
        c.save()
        c.translate(PIN_W / 2, B.y + B.h / 2)
        c.scale(ts, ts)
        text(COPY.cta, 0, 0, fonts.getValue("cta"), paint(C.white), 0.5, 6)
        c.restore()
        drawNotes(f)
        c.restore()
    }
}
