package com.ngocdevv.nativecook.blocks.lists.luckytaskhub

import android.content.Context
import android.graphics.*
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.C
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.COPY
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.UI
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.BALANCE
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.FLIPS
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.STEPS
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.DY
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.S
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.fillAt
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.Frame
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.LOOP
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.BILLS
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.BILL_GAP
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.BILL_FLIGHT
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.mix as motionMix
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.smooth
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.easeOut
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubCanvas.Shape

/** Atlas cells and bundled typefaces match the Skia source; all geometry stays in pin pixels. */
internal class LuckyTaskHubArt(context: Context) {
    private val atlas = context.assets.open("luckytaskhub/luckytaskhub_atlas.webp").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    private fun font(context: Context, name: String, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply{
        typeface = Typeface.createFromAsset(context.assets, "luckytaskhub/luckytaskhub_$name.ttf")
        textSize = size
        // Skia drawSimpleText uses direct glyph advances without shaping features.
        fontFeatureSettings = "'kern' 0, 'liga' 0"
    }
    private val fonts = mapOf(
        "header" to font(context, "Nunito-Black", 46f), "title" to font(context, "Nunito-Black", 40f),
        "go" to font(context, "Nunito-Black", 27f), "small" to font(context, "Inter-SemiBold", 20f),
        "timer" to font(context, "Inter-SemiBold", 21f), "label" to font(context, "Inter-Bold", 19f),
        "h2" to font(context, "Inter-Bold", 31f), "count" to font(context, "Nunito-Black", 62f),
        "countSmall" to font(context, "Nunito-Black", 38f), "body" to font(context, "Inter-Medium", 23f),
        "reward" to font(context, "Inter-Bold", 27f), "balance" to font(context, "Inter-Bold", 29f),
        "dollar" to font(context, "Nunito-Black", 30f))
    private object A {
        val trophy = listOf(0, 1, 2)
        const val cash = 3
        const val peace = 4
        const val back = 6
        const val chest = 7
        val games = listOf(8, 9, 10)
        const val bill = 11
    }
    private fun mix(a: Number, b: Number, p: Double) = motionMix(a.toDouble(), b.toDouble(), p)
    private fun maximum(a: Number, b: Number) = max(a.toDouble(), b.toDouble())
    private fun minimum(a: Number, b: Number) = min(a.toDouble(), b.toDouble())
    private fun power(a: Number, b: Number) = a.toDouble().pow(b.toDouble())
    private val TAU = PI * 2
    private fun col(h: String, a: Double = 1.0) = (Color.parseColor(h) and 0xffffff) or ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24)
    private fun paint(h: String, a: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = col(h, a) }
    private fun stroke(h: String, w: Number, a: Double = 1.0) = paint(h, a).apply {
        style = Paint.Style.STROKE
        strokeWidth = w.toFloat()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private fun rect(x: Number, y: Number, w: Number, h: Number) = RectF(x.toFloat(), y.toFloat(), (x.toDouble() + w.toDouble()).toFloat(), (y.toDouble() + h.toDouble()).toFloat())
    private fun rrect(x: Number, y: Number, w: Number, h: Number, r: Number) = LuckyTaskHubCanvas.RoundRect(rect(x, y, w, h), r.toFloat())
    private fun gradient(x1: Number, y1: Number, x2: Number, y2: Number, colors: IntArray, stops: FloatArray? = null) = LinearGradient(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), colors, stops, Shader.TileMode.CLAMP)
    private fun sprite(c: LuckyTaskHubCanvas, cell: Int, cx: Number, cy: Number, size: Number, alpha: Double = 1.0) {
        val source = Rect(cell % 4 * 256, cell / 4 * 256, cell % 4 * 256 + 256, cell / 4 * 256 + 256)
        c.native.drawBitmap(atlas, source, rect(cx.toDouble() - size.toDouble() / 2, cy.toDouble() - size.toDouble() / 2, size, size), paint("#FFFFFF", alpha))
    }
    private fun inkWidth(font: Paint, value: String): Double {
        val bounds = Rect()
        font.getTextBounds(value, 0, value.length, bounds)
        return bounds.width().toDouble()
    }
    private fun text(c: LuckyTaskHubCanvas, s: String, x: Number, cy: Number, font: Paint, p: Paint, ax: Number = 0.0): Double {
        val ink = Paint(p).apply {
            typeface = font.typeface
            textSize = font.textSize
            fontFeatureSettings = font.fontFeatureSettings
        }
        val width = inkWidth(font, s)
        c.native.drawText(s, (x.toDouble() - width * ax.toDouble()).toFloat(), (cy.toDouble() + font.textSize * 0.36).toFloat(), ink)
        return width
    }
    private fun title(c: LuckyTaskHubCanvas, s: String, x: Number, cy: Number, font: Paint, ink: String) {
        c.save()
        c.translate(x, cy)
        c.skew(-0.16, 0)
        text(c, s, 0, 0, font, stroke("#FFFFFF", 7))
        text(c, s, 0, 0, font, paint(ink))
        c.restore()
    }
    private fun withCommas(n: Double): String {
        val s = n.toLong().toString()
        return s.mapIndexed { i, ch ->
            ch.toString() + if (s.length - 1 - i > 0 && (s.length - 1 - i) % 3 == 0) "," else ""
        }.joinToString("")
    }
    private fun rolling(c: LuckyTaskHubCanvas, v: Double, x: Number, cy: Number, font: Paint, p: Paint, ax: Number, commas: Boolean): Double {
        val n0 = floor(v + 1e-6)
        val k = (v - n0).coerceIn(0.0, 1.0)
        val a = if (commas) withCommas(n0) else n0.toLong().toString()
        val b = if (commas) withCommas(n0 + 1) else (n0 + 1).toLong().toString()
        if (k < 0.001 || a.length != b.length) return text(c, if (k > 0.5) b else a, x, cy, font, p, ax)
        val width = inkWidth(font, a)
        val height = font.textSize * 1.05
        val e = smooth(k)
        var px = x.toDouble() - width * ax.toDouble()
        for (i in a.indices) {
            val ca = a[i].toString()
            val cb = b[i].toString()
            val cw = inkWidth(font, ca)
            if (ca == cb) {
                text(c, ca, px, cy, font, p)
            } else {
                c.save()
                c.clipRect(rect(px - 2, cy.toDouble() - height * 0.55, cw + 4, height * 1.1))
                text(c, ca, px, cy.toDouble() - e * height, font, p)
                text(c, cb, px, cy.toDouble() + (1 - e) * height, font, p)
                c.restore()
            }
            px += cw
        }
        return width
    }
    private fun goButton(c: LuckyTaskHubCanvas, gx: Double, gy: Double, gw: Double, gh: Double, press: Double, amb: Double) {
        c.save()
        val scale = 1 - 0.04 * press
        c.translate(gx + gw / 2, gy + gh / 2)
        c.scale(scale, scale)
        c.drawRRect(rrect(-gw / 2, -gh / 2, gw, gh, gh / 2), paint(C.black))
        c.save()
        c.translate(-gw / 2 + 14, 0)
        c.skew(-0.16, 0)
        text(c, COPY.go, 0, -1, fonts.getValue("go"), paint(C.white))
        c.restore()
        val nudge = 4 * max(0.0, sin(TAU * 3 * amb / LOOP)).pow(6)
        val path = Shape()
        val ax = gw / 2 - 34 + nudge
        path.moveTo(ax, 5)
        path.lineTo(ax + 18, 5)
        path.lineTo(ax + 10, -4)
        path.close()
        c.drawPath(path, paint(C.white))
        c.restore()
    }
    private fun star4(c: LuckyTaskHubCanvas, x: Number, y: Number, rr: Number, p: Paint) {
        val xx = x.toDouble()
        val yy = y.toDouble()
        val rad = rr.toDouble()
        val path = Shape()
        path.moveTo(xx, yy - rad)
        path.quadTo(xx, yy, xx + rad, yy)
        path.quadTo(xx, yy, xx, yy + rad)
        path.quadTo(xx, yy, xx - rad, yy)
        path.quadTo(xx, yy, xx, yy - rad)
        path.close()
        c.drawPath(path, p)
    }

    private fun drawRanking(c: LuckyTaskHubCanvas, f: Frame) {
      val R = UI.ranking
      // the red card behind, tilted
      c.save()
      c.translate(R.back.x + R.back.w / 2, R.back.y + R.back.h / 2)
      c.rotate(R.back.rot, 0, 0)
      val bp = Paint(Paint.ANTI_ALIAS_FLAG)
      bp.isAntiAlias = true
      bp.setShader(gradient(0, -R.back.h / 2, 0, R.back.h / 2, intArrayOf(col(C.orange), col(C.redCard)), null))
      c.drawRRect(rrect(-R.back.w / 2, -R.back.h / 2, R.back.w, R.back.h, R.back.r), bp)
      c.restore()
      val k = R.card
      val yp = Paint(Paint.ANTI_ALIAS_FLAG)
      yp.isAntiAlias = true
      yp.setShader(gradient(k.x, k.y, k.x, k.y + k.h, intArrayOf(col("#FFE13A"), col(C.yellow)), null));
      c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), yp)
      // its faint square rings, as in the pin
      c.save()
      c.clipRRect(rrect(k.x, k.y, k.w, k.h, k.r))
      for (i in 1..3) {
        val d = 70 * i
        c.save()
        c.translate(k.x + k.w * 0.55, k.y + k.h * 0.55)
        c.rotate(12, 0, 0)
        c.drawRRect(rrect(-d, -d, d * 2, d * 2, 22), stroke("#FFEA70", 14, 0.7));
        c.restore()
      }
      c.restore()
      title(c, COPY.ranking, R.title.x, R.title.y, fonts.getValue("title"), C.ink2);
      // timer pill
      val tm = R.timer
      c.drawRRect(rrect(tm.x, tm.y, tm.w, tm.h, tm.h / 2), paint("#FFF6D2"));
      c.drawCircle(tm.x + 17, tm.y + tm.h / 2, 8, stroke(C.ink, 2.2))
      c.drawLine(tm.x + 17, tm.y + tm.h / 2, tm.x + 17, tm.y + tm.h / 2 - 5, stroke(C.ink, 2))
      c.drawLine(tm.x + 17, tm.y + tm.h / 2, tm.x + 21, tm.y + tm.h / 2 + 2, stroke(C.ink, 2))
      text(c, COPY.timer, tm.x + 32, tm.y + tm.h / 2, fonts.getValue("timer"), paint(C.ink));
      // the panel
      val pn = R.panel
      val pp = Paint(Paint.ANTI_ALIAS_FLAG)
      pp.isAntiAlias = true
      pp.setShader(gradient(0, pn.y, 0, pn.y + pn.h, intArrayOf(col("#FFFBE6"), col("#FFEFA0")), null));
      c.drawRRect(rrect(pn.x, pn.y, pn.w, pn.h, pn.r), pp)
      c.drawRRect(rrect(pn.x, pn.y, pn.w, pn.h, pn.r), stroke("#FFFFFF", 5));
      c.save()
      c.clipRRect(rrect(pn.x, pn.y, pn.w, pn.h, pn.r))
      // podium
      val pd = R.podium
      c.drawOval(rect(pd.x - pd.rx, pd.y - pd.ry + 10, pd.rx * 2, pd.ry * 2), paint("#F4C21C"));
      c.drawOval(rect(pd.x - pd.rx, pd.y - pd.ry, pd.rx * 2, pd.ry * 2), paint(C.podium))
      c.drawOval(rect(pd.x - pd.rx * 0.8, pd.y - pd.ry * 0.7, pd.rx * 1.6, pd.ry * 1.4), stroke("#FFF2A6", 3, 0.8));
      c.restore()
      // the trophy: a hop with a squash on take-off and landing, the tier swapping at the top
      val tr = R.trophy
      var ty = 0.0
      var sx = 1.0
      var sy = 1.0
      var tilt = 0.0
      if (f.hop >= 0) {
        val h = f.hop
        val air = maximum(0.0, minimum(1.0, (h - 0.08) / 0.62))
        ty = -58 * sin(PI * air)
        val crouch = maximum(0.0, sin(PI * minimum(1.0, h / 0.12))) * ((if (h < 0.12) 1.0 else 0.0))
        val land = (if (h > 0.7) exp(-(h - 0.7) * 7) * sin((h - 0.7) * 22) else 0.0)
        sy = 1 - 0.12 * crouch - 0.1 * land + 0.06 * sin(PI * air)
        sx = 1 + 0.1 * crouch + 0.08 * land - 0.03 * sin(PI * air)
        tilt = 10 * sin(TAU * air) * (1 - air)
      }
      // shadow on the podium
      val shK = 1 + ty / 120
      c.drawOval(rect(pd.x - 44 * shK, pd.y - 8 * shK, 88 * shK, 16 * shK), paint("#C99A00", 0.35 * shK));
      c.save()
      c.translate(tr.x, pd.y + 2 + ty)
      c.rotate(tilt, 0, 0)
      c.scale(sx, sy)
      val base = -tr.size * 0.47
      if (f.tierK < 1) sprite(c, A.trophy[f.tierFrom], 0, base, tr.size, 1.0)
      if (f.tierK > 0) {
        // the new tier arrives behind a flash
        sprite(c, A.trophy[f.tierTo], 0, base, tr.size, f.tierK)
      }
      c.restore()
      // sparkles burst from the trophy at the top of the hop
      if (f.hop >= 0.3 && f.hop < 1.2) {
        val q = (f.hop - 0.3) / 0.9
        for (i in 0 until 8) {
          val a = (i.toDouble() / 8) * TAU + 0.4
          val d = 30 + 90 * easeOut(q)
          val sp = paint((if (i % 2 == 0) "#FFFFFF" else "#FFF07A"), 1 - q);
          star4(c, tr.x + cos(a) * d, pd.y - 80 - 58 + 40 * q + sin(a) * d * 0.8, 9 * (1 - q * 0.6), sp)
        }
      }
      // the tier's label, swapped with a crossfade
      val lb = R.label
      c.drawRRect(rrect(lb.x - lb.w / 2, lb.y - lb.h / 2, lb.w, lb.h, lb.h / 2), paint("#FFFFFF"));
      c.drawRRect(rrect(lb.x - lb.w / 2, lb.y - lb.h / 2, lb.w, lb.h, lb.h / 2), stroke("#F4C21C", 3));
      for (i in 0 until 2) {
        val tier = (if (i == 0) f.tierFrom else f.tierTo)
        val same = f.tierTo == f.tierFrom
        val a = if (i == 0) (if (same) 1.0 else 1-f.tierK) else f.tierK
        if (a < 0.01 || (i == 1 && same)) continue
        c.save()
        c.translate(lb.x, lb.y + ((if (same) 0.0 else (if (i == 0) -8 * f.tierK else 8 * (1 - f.tierK)))))
        c.skew(-0.16, 0)
        text(c, COPY.tiers[tier], 0, 0, fonts.getValue("label"), paint(C.ink, a), 0.5);
        c.restore()
      }
      // the mascot: two big eyes with stars, a red nose and a grin; it blinks and watches the trophy
      val m = R.mascot
      val blink = maximum(0.0, 1 - abs(f.amb - 1.9) / 0.08) + maximum(0.0, 1 - abs(f.amb - 6.2) / 0.08)
      val look = (if (f.hop >= 0) -sin(PI * minimum(1.0, f.hop / 0.8)) * 5 else 0.0)
      for (e in 0 until 2) {
        val ex = m.x - 16 + e * 32
        val ey = m.y - 18
        c.save()
        c.translate(ex, ey)
        c.rotate((if (e == 0) -14 else 12), 0, 0)
        c.scale(1, maximum(0.08, 1 - blink))
        c.drawOval(rect(-17, -21, 34, 42), paint(C.black))
        star4(c, 2, -4 + look, 11, paint("#FFE84A"));
        c.restore()
      }
      c.drawCircle(m.x, m.y + 4, 11, paint("#FF3B2F"));
      c.drawCircle(m.x - 3, m.y, 3.5, paint("#FFFFFF", 0.8));
      val mouth = Shape()
      mouth.moveTo(m.x - 48, m.y + 22)
      mouth.quadTo(m.x - 26, m.y + 40, m.x - 4, m.y + 26)
      c.drawPath(mouth, stroke(C.black, 4))
      // the up arrow: pops on a promotion, otherwise breathes
      val ar = R.arrow
      val pop = (if (f.hop >= 0) exp(-maximum(0.0, f.hop - 0.4) * 5) * sin(minimum(PI, maximum(0.0, f.hop - 0.4) * 9)) else 0.0)
      c.save()
      c.translate(ar.x, ar.y - 10 * pop)
      c.scale(1 + 0.2 * pop, 1 + 0.2 * pop)
      val arc = Shape()
      arc.moveTo(-26, 30)
      arc.quadTo(4, 34, 8, -24)
      c.drawPath(arc, stroke(C.black, 9))
      val head = Shape()
      head.moveTo(8, -46)
      head.lineTo(26, -18)
      head.lineTo(-10, -20)
      head.close()
      c.drawPath(head, paint(C.black))
      c.restore()
      goButton(c, R.go.x, R.go.y, R.go.w, R.go.h, (if (f.press == 0) f.pressK else 0.0), f.amb)
    }

    private fun drawFlip(c: LuckyTaskHubCanvas, f: Frame) {
      val F = UI.flip
      val k = F.card
      val pp = Paint(Paint.ANTI_ALIAS_FLAG)
      pp.isAntiAlias = true
      pp.setShader(gradient(k.x, k.y, k.x + k.w, k.y + k.h, intArrayOf(col("#F59BE0"), col(C.pink), col(C.pinkDeep)), floatArrayOf(0.0f, 0.5f, 1.0f)));
      c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), pp)
      c.save()
      c.clipRRect(rrect(k.x, k.y, k.w, k.h, k.r))
      // soft swirls in the background, as in the pin
      val sw = stroke("#FFB4EC", 22, 0.35);
      val p1 = Shape()
      p1.moveTo(k.x + 120, k.y + 200)
      p1.cubicTo(k.x + 160, k.y + 60, k.x + 280, k.y + 150, k.x + 330, k.y + 20)
      c.drawPath(p1, sw)
      c.drawCircle(k.x + 70, k.y + 10, 46, sw)
      // the back card of the deck
      val bk = F.back
      c.save()
      c.translate(bk.x, bk.y)
      c.rotate(bk.rot, 0, 0)
      sprite(c, A.back, 0, 0, bk.size * 1.2)
      c.restore()
      // the front card turns over (a full turn: its back shows half way), lifting as it turns
      val fr = F.front
      val ang = f.flipAng
      val turn = (ang * PI) / 180
      val lift = sin(turn / 2)
      val face = (if (ang < 90) A.peace + f.flipFrom else (if (ang > 270) A.peace + f.flipTo else A.back))
      c.save()
      c.translate(fr.x - 6 * lift, fr.y - 16 * lift)
      c.rotate(fr.rot - 8 * lift, 0, 0)
      val sx = maximum(0.02, abs(cos(turn)))
      c.scale(sx * (1 + 0.12 * lift), 1 + 0.12 * lift)
      if (lift > 0.02) c.drawRRect(rrect(-fr.size * 0.42, -fr.size * 0.4 + 14 * lift, fr.size * 0.84, fr.size * 0.86, 16), paint("#7A1F66", 0.25 * lift));
      sprite(c, face, 0, 0, fr.size * 1.3)
      c.restore()
      c.restore()
      title(c, COPY.flip, F.title.x, F.title.y, fonts.getValue("title"), C.ink2);
      val rp = paint(C.white, 0.92)
      val w0 = text(c, "Remaining:", F.remaining.x, F.remaining.y, fonts.getValue("small"), rp) + 6;
      val w1 = rolling(c, f.remaining, F.remaining.x + w0, F.remaining.y, fonts.getValue("small"), rp, 0, false);
      text(c, "/$FLIPS", F.remaining.x + w0 + w1, F.remaining.y, fonts.getValue("small"), rp);
      goButton(c, F.go.x, F.go.y, F.go.w, F.go.h, (if (f.press == 1) f.pressK else 0.0), f.amb)
      // the fire badge in the corner, flickering
      val fi = F.fire
      c.drawCircle(fi.x, fi.y, fi.r, paint("#FFFFFF"));
      val fl = 1 + 0.07 * sin((TAU * 6 * f.amb) / LOOP) + 0.04 * sin((TAU * 13 * f.amb) / LOOP)
      c.save()
      c.translate(fi.x, fi.y + 9)
      c.scale(1, fl)
      val fp = Shape()
      fp.moveTo(0, 0)
      fp.cubicTo(-14, 0, -15, -14, -8, -22)
      fp.cubicTo(-7, -14, -2, -14, -2, -18)
      fp.cubicTo(-2, -24, 2, -28, 5, -30)
      fp.cubicTo(4, -22, 14, -18, 13, -8)
      fp.cubicTo(13, -3, 8, 0, 0, 0)
      fp.close()
      c.drawPath(fp, paint("#FF3D2E"));
      c.drawCircle(1, -7, 4, paint("#FFFFFF", 0.85));
      c.restore()
    }

    private fun drawWheel(c: LuckyTaskHubCanvas, x: Double, y: Double, rad: Double, ang: Double, blur: Double) {
      c.save()
      c.translate(x, y)
      c.rotate(ang, 0, 0)
      val a = Color.parseColor(C.wheelA)
      val b = Color.parseColor(C.wheelB)
      for (i in 0 until 8) {
        val cc = if (i % 2 == 0) a else b
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun channel(shift: Int): Int {
          val ca = (a shr shift) and 255
          val cb = (b shr shift) and 255
          return mix(((cc shr shift) and 255).toDouble(), (ca+cb)/2.0, blur).roundToInt()
        }
        p.color = Color.rgb(channel(16), channel(8), channel(0))
        val sp = Shape()
        sp.moveTo(0, 0)
        sp.arcToOval(rect(-rad, -rad, rad * 2, rad * 2), i * 45, 45, false)
        sp.close()
        c.drawPath(sp, p)
      }
      for (i in 0 until 8) {
        val t = (i * 45 * PI) / 180
        c.drawLine(0, 0, cos(t) * rad, sin(t) * rad, stroke("#FFFFFF", 3, 0.7 * (1 - blur)));
      }
      c.restore()
      c.drawCircle(x, y, rad, stroke("#FFFFFF", 7));
      c.drawCircle(x, y, rad + 4, stroke(C.greenDeep, 3))
      c.drawCircle(x, y, rad * 0.32, paint("#FFFFFF"));
      c.drawCircle(x, y, rad * 0.32, stroke(C.wheelB, 6))
      c.drawCircle(x, y, rad * 0.12, paint(C.wheelB))
      // the pointer at the top: it flicks back each time a slice's edge passes under it
      val phase = ((ang % 45.0) + 45.0) % 45.0 / 45.0
      val flick = 16 * (1 - phase).pow(3) * min(1.0, blur * 3)
      c.save()
      c.rotate(-flick, x, y - rad - 8)
      val pt = Shape()
      pt.moveTo(x - 9, y - rad - 8)
      pt.lineTo(x + 9, y - rad - 8)
      pt.lineTo(x, y - rad + 10)
      pt.close()
      c.drawPath(pt, paint("#FF3B2F"));
      c.drawPath(pt, stroke(C.black, 2.5))
      c.restore()
    }

    private fun drawSpin(c: LuckyTaskHubCanvas, f: Frame) {
      val P = UI.spin
      val k = P.card
      val gp = Paint(Paint.ANTI_ALIAS_FLAG)
      gp.isAntiAlias = true
      gp.setShader(gradient(k.x, k.y, k.x, k.y + k.h, intArrayOf(col("#8EE33A"), col(C.green), col(C.greenDeep)), floatArrayOf(0.0f, 0.6f, 1.0f)));
      c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), gp)
      c.save()
      c.clipRRect(rrect(k.x, k.y, k.w, k.h, k.r))
      c.drawCircle(k.x + 60, k.y + k.h + 10, 90, stroke("#A6F05A", 20, 0.4));
      val wh = P.wheel
      drawWheel(c, wh.x, wh.y, wh.r, f.wheel, minimum(1.0, f.wheelSpeed * 1.3))
      // the flash when it lands
      if (f.pop > 0.01) c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), paint("#FFFFFF", 0.35 * f.pop));
      c.restore()
      title(c, COPY.spin, P.title.x, P.title.y, fonts.getValue("title"), C.ink2);
      val rp = paint(C.white, 0.92)
      text(c, "Remaining: 0/1", P.remaining.x, P.remaining.y, fonts.getValue("small"), rp);
      goButton(c, P.go.x, P.go.y, P.go.w, P.go.h, (if (f.press == 2) f.pressK else 0.0), f.amb)
      // the $ $ eyes above the wheel: they widen while it spins and pop when it lands
      val ey = P.eyes
      val wide = 1 + 0.25 * f.wheelSpeed + 0.25 * f.pop
      for (e in 0 until 2) {
        val ex = ey.x - 16 + e * 32
        c.save()
        c.translate(ex, ey.y)
        c.scale(wide, wide)
        c.drawOval(rect(-15, -19, 30, 38), paint("#FFFFFF"));
        c.drawOval(rect(-15, -19, 30, 38), stroke(C.black, 3.5))
        c.save()
        c.rotate((if (e == 0) -8 else 8), 0, 0)
        // the $ pupils are dragged round with the wheel while it spins
        text(c, "$", 4 * f.wheelSpeed, 0, fonts.getValue("dollar"), paint(C.black), 0.5);
        c.restore()
        c.restore()
      }
      c.drawCircle(ey.x, ey.y + 18, 9, paint("#FF3B2F"));
      c.drawCircle(ey.x - 2.5, ey.y + 15, 3, paint("#FFFFFF", 0.8));
    }

    private fun drawProgress(c: LuckyTaskHubCanvas, f: Frame) {
      val N = UI.newbie
      text(c, COPY.newbie, N.titleX, N.titleY, fonts.getValue("h2"), paint(C.ink));
      val tm = N.timer
      c.drawRRect(rrect(tm.x, tm.y, tm.w, tm.h, tm.h / 2), paint(C.timerBg))
      val bolt = Shape()
      val bx = tm.x + 6
      val by = tm.y - 4
      bolt.moveTo(bx + 16, by)
      bolt.lineTo(bx + 4, by + 22)
      bolt.lineTo(bx + 14, by + 22)
      bolt.lineTo(bx + 8, by + 42)
      bolt.lineTo(bx + 26, by + 16)
      bolt.lineTo(bx + 16, by + 16)
      bolt.lineTo(bx + 22, by)
      bolt.close()
      c.drawPath(bolt, paint(C.bolt))
      text(c, COPY.newbieTimer, tm.x + tm.w - 12, tm.y + tm.h / 2, fonts.getValue("label"), paint(C.ink), 1.0);
      val P = UI.progress
      val k = P.card
      c.drawRRect(rrect(k.x, k.y + 6, k.w, k.h, k.r), paint(C.cardEdge))
      c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), paint(C.cardBg))
      c.drawRRect(rrect(k.x, k.y, k.w, k.h, k.r), stroke(C.cardEdge, 3))
      // 3/14, its first number rolling
      c.save()
      c.translate(P.count.x, P.count.y)
      c.skew(-0.14, 0)
      val w0 = rolling(c, f.prog, 0, 0, fonts.getValue("count"), paint(C.ink), 0, false);
      text(c, "/$STEPS", w0 + 2, 6, fonts.getValue("countSmall"), paint(C.ink));
      c.restore()
      // the bar: a track, a striped fill that marches, and a star riding its end
      val B = P.bar
      c.drawRRect(rrect(B.x, B.y, B.w, B.h, B.h / 2), paint(C.line))
      val fw = maximum(B.h, fillAt(f.prog) * B.w)
      c.save()
      c.clipRRect(rrect(B.x, B.y, fw, B.h, B.h / 2))
      c.drawRect(rect(B.x, B.y, fw, B.h), paint("#FFC21F"));
      val march = ((f.amb / LOOP) * 12 * 36) % 36
      for (i in -2 until ceil(fw / 18 + 2).toInt()) {
        val x = B.x + i * 36 + march
        val sp = Shape()
        sp.moveTo(x, B.y + B.h)
        sp.lineTo(x + 14, B.y)
        sp.lineTo(x + 28, B.y)
        sp.lineTo(x + 14, B.y + B.h)
        sp.close()
        c.drawPath(sp, paint("#FF4A2E"));
      }
      c.restore()
      val sx = B.x + fw - 4
      val spark = 1 + 0.12 * sin((TAU * 4 * f.amb) / LOOP)
      star4(c, sx, B.y + B.h / 2, 21 * spark, paint("#FF3B2F"));
      star4(c, sx, B.y + B.h / 2, 21 * spark, stroke("#FFFFFF", 2.5));
      // a step done: a ring of sparks at the star
      val stepK = f.prog - floor(f.prog)
      if (stepK > 0.6 && stepK < 0.999) {
        val q = (stepK - 0.6) / 0.4
        for (i in 0 until 6) {
          val a = (i.toDouble() / 6) * TAU
          star4(c, sx + cos(a) * (14 + 30 * q), B.y + B.h / 2 + sin(a) * (14 + 30 * q), 7 * (1 - q), paint("#FFB000", 1 - q));
        }
      }
      // the first reward: a coin with a gift
      val nd = P.node
      c.drawCircle(nd.x, nd.y, nd.r + 3, paint(C.cardEdge))
      c.drawCircle(nd.x, nd.y, nd.r, paint("#FFFFFF"));
      c.drawRRect(rrect(nd.x - 12, nd.y - 6, 24, 18, 3), paint("#20B15A"));
      c.drawRRect(rrect(nd.x - 14, nd.y - 11, 28, 7, 2), paint("#20B15A"));
      c.drawRect(rect(nd.x - 2, nd.y - 11, 4, 23), paint("#FFFFFF"));
      text(c, COPY.node, nd.x, P.nodeLabel.y, fonts.getValue("label"), paint(C.ink), 0.5);
      sprite(c, A.cash, P.goal.x, P.goal.y, 72)
      text(c, COPY.goal, P.goal.x, P.nodeLabel.y, fonts.getValue("label"), paint(C.ink), 0.5);
      val ar = P.arrow
      c.drawRRect(rrect(ar.x, ar.y, ar.w + 30, ar.h, ar.h / 2), paint(C.black))
      val ch = Shape()
      ch.moveTo(ar.x + 18, ar.y + ar.h / 2)
      ch.lineTo(ar.x + 34, ar.y + ar.h / 2)
      ch.moveTo(ar.x + 28, ar.y + ar.h / 2 - 7)
      ch.lineTo(ar.x + 35, ar.y + ar.h / 2)
      ch.lineTo(ar.x + 28, ar.y + ar.h / 2 + 7)
      c.drawPath(ch, stroke("#FFFFFF", 3.5));
    }

    private fun drawOffers(c: LuckyTaskHubCanvas) {
      val O = UI.offers
      text(c, COPY.offers, 32, O.titleY, fonts.getValue("h2"), paint(C.ink));
      text(c, COPY.seeAll, 688, O.titleY, fonts.getValue("body"), paint(C.ink), 1.0);
      for (i in 0 until 4) {
        val x = O.cards[i]
        c.drawRRect(rrect(x, O.y + 6, O.w, O.h, 30), paint(C.cardEdge))
        c.drawRRect(rrect(x, O.y, O.w, O.h, 30), paint(C.cardBg))
        c.drawRRect(rrect(x, O.y, O.w, O.h, 30), stroke(C.cardEdge, 3))
        if (i == 3) continue
        val g = COPY.games[i]
        val ix = x + O.w / 2
        val iy = O.y + 32
        c.drawRRect(rrect(ix - O.icon / 2 - 5, iy - O.icon / 2 - 5, O.icon + 10, O.icon + 10, 24), paint("#FFFFFF"));
        c.save()
        c.clipRRect(rrect(ix - O.icon / 2, iy - O.icon / 2, O.icon, O.icon, 20))
        sprite(c, A.games[i], ix, iy, O.icon)
        c.restore()
        text(c, g.name, ix, O.y + 108, fonts.getValue("body"), paint(C.ink2), 0.5);
        c.drawRRect(rrect(x + 14, O.y + 132, O.w - 28, 46, 23), paint(C.pill))
        sprite(c, A.cash, x + 42, O.y + 155, 46)
        text(c, g.reward, x + O.w / 2 + 18, O.y + 155, fonts.getValue("reward"), paint(C.ink), 0.5);
      }
    }

    private fun drawInProgress(c: LuckyTaskHubCanvas, f: Frame) {
      val P = UI.inProgress
      val b = P.bubble
      c.drawRRect(rrect(b.x, b.y, b.w, b.h, b.h / 2), paint("#FFFFFF"));
      c.drawRRect(rrect(b.x, b.y, b.w, b.h, b.h / 2), stroke(C.cardEdge, 3))
      val tp = paint(C.ink2)
      val w0 = text(c, COPY.progress[0], b.x + 26, b.y + b.h / 2, fonts.getValue("body"), tp);
      val w1 = text(c, COPY.progress[1], b.x + 26 + w0 + 6, b.y + b.h / 2, fonts.getValue("reward"), paint(C.ink));
      text(c, COPY.progress[2], b.x + 26 + w0 + w1 + 6, b.y + b.h / 2, fonts.getValue("body"), tp);
      val av = P.avatar
      c.drawCircle(av.x, av.y, av.r + 4, paint(C.cardEdge))
      c.drawCircle(av.x, av.y, av.r, paint("#FFFFFF"));
      drawWheel(c, av.x, av.y, av.r * 0.62, -f.wheel * 0.5, 0.0)
      // the chest, wiggling now and then
      val ch = P.chest
      val cp = Paint(Paint.ANTI_ALIAS_FLAG)
      cp.isAntiAlias = true
      cp.setShader(gradient(0, ch.y, 0, ch.y + ch.h, intArrayOf(col("#FFE46A"), col("#FFC21F")), null));
      c.drawRRect(rrect(ch.x, ch.y, ch.w + 30, ch.h, 24), cp)
      c.drawRRect(rrect(ch.x, ch.y, ch.w + 30, ch.h, 24), stroke("#F2A500", 3));
      val wig = (if (f.chest >= 0) sin(f.chest * 30) * exp(-f.chest * 4) * 12 else 0.0)
      val hopY = (if (f.chest >= 0) -10 * sin(PI * minimum(1.0, f.chest / 0.35)) else 0.0)
      c.save()
      c.translate(ch.x + ch.w / 2, ch.y + 34 + hopY)
      c.rotate(wig, 0, 0)
      sprite(c, A.chest, 0, 0, 74)
      c.restore()
      text(c, COPY.chest, ch.x + ch.w / 2, ch.y + ch.h - 18, fonts.getValue("label"), paint(C.ink), 0.5);
    }

    private fun drawCasual(c: LuckyTaskHubCanvas) {
      val P = UI.casual
      text(c, COPY.casual, 32, P.titleY, fonts.getValue("h2"), paint(C.ink));
      val w = P.row
      c.drawRRect(rrect(w.x, w.y + 6, w.w, w.h, 30), paint(C.cardEdge))
      c.drawRRect(rrect(w.x, w.y, w.w, w.h, 30), paint(C.cardBg))
      c.save()
      c.clipRRect(rrect(w.x + 22, w.y + 20, 80, 80, 18))
      sprite(c, A.games[2], w.x + 62, w.y + 60, 80)
      c.restore()
      text(c, COPY.row, w.x + 124, w.y + 46, fonts.getValue("body"), paint(C.ink2));
      c.drawRRect(rrect(w.x + 124, w.y + 70, 120, 34, 17), paint(C.pill))
      sprite(c, A.cash, w.x + 146, w.y + 87, 34)
      text(c, COPY.rowReward, w.x + 168, w.y + 87, fonts.getValue("label"), paint(C.ink));
    }

    /** The bills flying from the wheel to the balance, each along its own arc. */
    private fun drawBills(c: LuckyTaskHubCanvas, f: Frame) {
      if (f.billsT < 0) return
      val W = UI.spin.wheel
      val toX = UI.header.balance.x + 34
      val toY = UI.header.balance.y + UI.header.balance.h / 2
      for (i in BILLS - 1 downTo 0) {
        val q = (f.billsT - i * BILL_GAP) / BILL_FLIGHT
        if (q <= 0 || q >= 1) continue
        val e = 1 - power(1 - q, 2.2)
        val cx = W.x + 120 + i * 18
        val cy = W.y - 260 - i * 30
        val x = (1 - e) * (1 - e) * W.x + 2 * (1 - e) * e * cx + e * e * toX
        val y = (1 - e) * (1 - e) * (W.y - 20) + 2 * (1 - e) * e * cy + e * e * toY
        val size = mix(88, 44, e) * (1 + 0.25 * sin(PI * minimum(1.0, q * 3)))
        // a short trail of light behind each bill
        for (k in 1..3) {
          val qe = maximum(0.0, e - k * 0.05)
          val tx = (1 - qe) * (1 - qe) * W.x + 2 * (1 - qe) * qe * cx + qe * qe * toX
          val ty = (1 - qe) * (1 - qe) * (W.y - 20) + 2 * (1 - qe) * qe * cy + qe * qe * toY
          c.drawCircle(tx, ty, 9 - k * 2, paint("#FFFFFF", 0.5 - k * 0.12));
        }
        c.save()
        c.translate(x, y)
        c.rotate(-25 + 340 * e * ((if (i % 2 == 0) 1 else -1)) * 0.35, 0, 0)
        sprite(c, A.bill, 0, 0, size)
        c.restore()
      }
    }

    private fun drawHeader(c: LuckyTaskHubCanvas, f: Frame) {
      val H = UI.header
      text(c, COPY.header, 32, H.titleY, fonts.getValue("header"), paint(C.ink));
      val b = H.balance
      val s = 1 + 0.08 * f.bump
      c.save()
      c.translate(b.x + b.w / 2, b.y + b.h / 2)
      c.scale(s, s)
      c.drawRRect(rrect(-b.w / 2, -b.h / 2 + 4, b.w, b.h, b.h / 2), paint(C.cardEdge))
      c.drawRRect(rrect(-b.w / 2, -b.h / 2, b.w, b.h, b.h / 2), paint("#FFFFFF"));
      if (f.bump > 0.01) c.drawRRect(rrect(-b.w / 2, -b.h / 2, b.w, b.h, b.h / 2), stroke(C.cash, 4, f.bump))
      sprite(c, A.cash, -b.w / 2 + 34, 0, 56)
      val bp = paint(C.ink)
      if (f.balFade > 0.001 && f.balFade < 0.999) {
        // the loop's way back: the start value fades in over the won one
        val a = Paint(Paint.ANTI_ALIAS_FLAG)
        a.alpha = ((1 - f.balFade) * 255).toInt()
        c.saveLayer(a)
        rolling(c, f.balance, b.w / 2 - 20, 0, fonts.getValue("balance"), bp, 1, true);
        c.restore()
        val z = Paint(Paint.ANTI_ALIAS_FLAG)
        z.alpha = ((f.balFade) * 255).toInt()
        c.saveLayer(z)
        text(c, withCommas(BALANCE), b.w / 2 - 20, 0, fonts.getValue("balance"), bp, 1);
        c.restore()
      } else if (f.balFade >= 0.999) text(c, withCommas(BALANCE), b.w / 2 - 20, 0, fonts.getValue("balance"), bp, 1);
      else rolling(c, f.balance, b.w / 2 - 20, 0, fonts.getValue("balance"), bp, 1, true);
      c.restore()
    }

    fun drawScene(canvas: Canvas, width: Float, f: Frame) {
      val c = LuckyTaskHubCanvas(canvas)
      c.drawColor(col(C.page))
      c.save()
      c.scale(width / 402.0, width / 402.0)
      c.translate(0, DY)
      c.scale(S, S)
      val bg = Paint(Paint.ANTI_ALIAS_FLAG)
      bg.setShader(gradient(0, -DY / S, 0, 520, intArrayOf(col(C.bgTop), col(C.bgMid), col(C.page)), floatArrayOf(0.0f, 0.6f, 1.0f)))
      c.drawRect(rect(0, -DY / S, 720, 520 + DY / S), bg)
      drawHeader(c, f)
      drawRanking(c, f)
      drawFlip(c, f)
      drawSpin(c, f)
      drawProgress(c, f)
      drawOffers(c)
      drawInProgress(c, f)
      drawCasual(c)
      drawBills(c, f)
      c.restore()
    }
}
