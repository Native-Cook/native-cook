package com.ngocdevv.nativecook.blocks.progress.setupchecklistallset

import android.content.Context
import android.graphics.RectF
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetTheme.C
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetTheme.COPY
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetTheme.UI
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetMotion.D
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetMotion.Live
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetDrawing.Surface
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetDrawing.Path
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetDrawing.Paint
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetDrawing.Point
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetDrawing.Font
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetDrawing.Gradient
import com.ngocdevv.nativecook.blocks.progress.setupchecklistallset.SetupChecklistAllSetDrawing.Rounded

internal class SetupChecklistAllSetArt(context: Context) {
    private val N = 6
    private val NEVER = -100.0
    private val REF_H = 874.0
    private val TAU = 2*PI
    private val ITEMS = SetupChecklistAllSetTheme.items
    class Res(context: Context) {
        class Fonts(context: Context) {
            val handle = Font(context,"ExtraBold",33.0)
            val url = Font(context,"Regular",16.5)
            val chip = Font(context,"Medium",15.0)
            val pro = Font(context,"SemiBold",15.0)
            val title = Font(context,"Medium",15.5)
            val badge = Font(context,"SemiBold",13.5)
            val row = Font(context,"Medium",14.8)
            val desc = Font(context,"Regular",14.6)
            val action = Font(context,"Medium",14.4)
            val allSet = Font(context,"Bold",21.0)
            val body = Font(context,"Regular",15.5)
            val cta = Font(context,"SemiBold",16.0)
            val tab = Font(context,"Medium",10.5)
            val small = Font(context,"Medium",12.5)
            val ring = Font(context,"SemiBold",12.0)
        }
        val f = Fonts(context)
    }
    private val resources = Res(context)
    fun draw(canvas: android.graphics.Canvas, scale: Double, live: Live) { drawScene(Surface(canvas),resources,scale,live) }
    private fun col(hex: String, alpha: Double = 1.0) = SetupChecklistAllSetDrawing.color(hex,alpha)
    private fun mixCol(first: String, second: String, amount: Double, alpha: Double = 1.0): Int {
        val a = android.graphics.Color.parseColor(first)
        val b = android.graphics.Color.parseColor(second)
        return android.graphics.Color.argb((alpha.coerceIn(0.0,1.0)*255).roundToInt(),
            mix(android.graphics.Color.red(a),android.graphics.Color.red(b),amount).roundToInt(),
            mix(android.graphics.Color.green(a),android.graphics.Color.green(b),amount).roundToInt(),
            mix(android.graphics.Color.blue(a),android.graphics.Color.blue(b),amount).roundToInt())
    }
    private fun paint(hex: String, alpha: Double = 1.0) = Paint().apply { setColor(col(hex,alpha)) }
    private fun stroke(hex: String,width: Number,alpha: Double = 1.0) = paint(hex,alpha).apply {
        native.style = android.graphics.Paint.Style.STROKE
        native.strokeWidth = width.toFloat()
        native.strokeCap = android.graphics.Paint.Cap.ROUND
        native.strokeJoin = android.graphics.Paint.Join.ROUND
    }
    private fun rect(x: Number,y: Number,w: Number,h: Number) = RectF(x.toFloat(),y.toFloat(),(x.toDouble()+w.toDouble()).toFloat(),(y.toDouble()+h.toDouble()).toFloat())
    private fun rrect(x: Number,y: Number,w: Number,h: Number,resources: Number) = Rounded(rect(x,y,w,h),resources.toFloat())
    private fun text(canvas: Surface,string: String,x: Number,cy: Number,font: Font,p: Paint,anchor: Double = 0.0): Double {
        val width = font.width(string)
        font.native.color = p.native.color
        canvas.native.drawText(string,(x.toDouble()-width*anchor).toFloat(),(cy.toDouble()+font.size*0.36).toFloat(),font.native)
        return width
    }
    private fun textFit(canvas: Surface,string: String,x: Number,cy: Number,font: Font,p: Paint,maxWidth: Number,anchor: Double = 0.0): Double {
        val width = font.width(string)
        val scale = min(1.0,maxWidth.toDouble()/width)
        canvas.save(); canvas.translate(x,cy); canvas.scale(scale,scale); text(canvas,string,0,0,font,p,anchor); canvas.restore()
        return width*scale
    }
    private fun numberString(number: Double) = number.toInt().toString()
    private fun remainder(x: Double,y: Number) = x%y.toDouble()
    private fun clamp01(x: Double) = SetupChecklistAllSetMotion.clamp01(x)
    private fun mix(a: Number,b: Number,p: Number) = SetupChecklistAllSetMotion.mix(a.toDouble(),b.toDouble(),p.toDouble())
    private fun smooth(x: Double) = SetupChecklistAllSetMotion.smooth(x)
    private fun easeOut(x: Double) = SetupChecklistAllSetMotion.easeOut(x)
    private fun easeInOut(x: Double) = SetupChecklistAllSetMotion.easeInOut(x)
    private fun spring(t: Double,response: Double,damping: Double) = SetupChecklistAllSetMotion.spring(t,response,damping)
    private fun countRolled(s: SetupChecklistAllSetMotion.State,now: Double) = SetupChecklistAllSetMotion.countRolled(s,now)
    private fun openness(s: SetupChecklistAllSetMotion.State,i: Int,now: Double) = SetupChecklistAllSetMotion.openness(s,i,now)
    private fun doneness(s: SetupChecklistAllSetMotion.State,i: Int,now: Double) = SetupChecklistAllSetMotion.doneness(s,i,now)

    // ------------------------------------------------------------------ the page behind

    private fun drawPage(canvas: Surface, resources: Res) {
        canvas.drawColor(col(C.page))
        // avatar
        canvas.drawCircle(UI.page.avatar.x, UI.page.avatar.y, UI.page.avatar.r, paint("#C7C7C3"))
        canvas.save()
        canvas.clipRRect(rrect(UI.page.avatar.x - UI.page.avatar.r, UI.page.avatar.y - UI.page.avatar.r, UI.page.avatar.r * 2, UI.page.avatar.r * 2, UI.page.avatar.r))
        canvas.drawCircle(UI.page.avatar.x, UI.page.avatar.y - 4, 8, paint("#EDEDEA"))
        canvas.drawOval(rect(UI.page.avatar.x - 15, UI.page.avatar.y + 8, 30, 22), paint("#EDEDEA"))
        canvas.restore()
        val pr = UI.page.pro
        canvas.drawRRect(rrect(pr.x, pr.y, pr.w, pr.h, 12), stroke(C.pro, 1.5))
        text(canvas, COPY.pro, pr.x + pr.w / 2, pr.y + pr.h / 2, resources.f.pro, paint(C.pro), 0.5)
        // share and bell
        val sh = stroke(C.ink, 1.8)
        canvas.drawLine(UI.page.share.x, UI.page.share.y - 12, UI.page.share.x, UI.page.share.y + 3, sh)
        canvas.drawLine(UI.page.share.x - 5, UI.page.share.y - 7, UI.page.share.x, UI.page.share.y - 12, sh)
        canvas.drawLine(UI.page.share.x + 5, UI.page.share.y - 7, UI.page.share.x, UI.page.share.y - 12, sh)
        val box = Path()
        box.moveTo(UI.page.share.x - 5, UI.page.share.y - 4)
        box.lineTo(UI.page.share.x - 10, UI.page.share.y - 4)
        box.lineTo(UI.page.share.x - 10, UI.page.share.y + 12)
        box.lineTo(UI.page.share.x + 10, UI.page.share.y + 12)
        box.lineTo(UI.page.share.x + 10, UI.page.share.y - 4)
        box.lineTo(UI.page.share.x + 5, UI.page.share.y - 4)
        canvas.drawPath(box, sh)
        val bell = Path()
        bell.moveTo(UI.page.bell.x - 10, UI.page.bell.y + 7)
        bell.lineTo(UI.page.bell.x - 8, UI.page.bell.y + 3)
        bell.cubicTo(UI.page.bell.x - 8, UI.page.bell.y - 13, UI.page.bell.x + 8, UI.page.bell.y - 13, UI.page.bell.x + 8, UI.page.bell.y + 3)
        bell.lineTo(UI.page.bell.x + 10, UI.page.bell.y + 7)
        bell.close()
        canvas.drawPath(bell, sh)
        canvas.drawLine(UI.page.bell.x - 3, UI.page.bell.y + 11, UI.page.bell.x + 3, UI.page.bell.y + 11, sh)
        text(canvas, COPY.handle, UI.page.handle.x, UI.page.handle.y, resources.f.handle, paint(C.ink))
        val uw = text(canvas, COPY.url, UI.page.url.x, UI.page.url.y, resources.f.url, paint(C.ink2))
        canvas.drawRRect(rrect(UI.page.url.x + uw + 6, UI.page.url.y - 5, 8, 9, 1.5), stroke(C.ink2, 1.2))
        canvas.drawRRect(rrect(UI.page.url.x + uw + 9, UI.page.url.y - 8, 8, 9, 1.5), stroke(C.ink2, 1.2))
        for (i in 0 until 3) {
            val x = UI.page.chips.x[i]
            val w = UI.page.chips.w[i]
            canvas.drawRRect(rrect(x, UI.page.chips.y - UI.page.chips.h / 2, w, UI.page.chips.h, UI.page.chips.h / 2), paint(C.chip))
            val ic = stroke(C.ink, 1.4)
            val ix = x + 22
            val iy = UI.page.chips.y
            val g = Path()
            if (i == 0) {
                // a T-shirt
                g.moveTo(ix - 4, iy - 7)
                g.quadTo(ix, iy - 4, ix + 4, iy - 7)
                g.lineTo(ix + 9, iy - 4)
                g.lineTo(ix + 7, iy)
                g.lineTo(ix + 5, iy - 1)
                g.lineTo(ix + 5, iy + 7)
                g.lineTo(ix - 5, iy + 7)
                g.lineTo(ix - 5, iy - 1)
                g.lineTo(ix - 7, iy)
                g.lineTo(ix - 9, iy - 4)
                g.close()
            } else if (i == 1) {
                // a paint roller
                g.addRRect(rrect(ix - 8, iy - 7, 16, 7, 2))
                g.moveTo(ix + 8, iy - 4)
                g.lineTo(ix + 10, iy - 4)
                g.lineTo(ix + 10, iy + 1)
                g.lineTo(ix, iy + 2)
                g.lineTo(ix, iy + 8)
            } else {
                // a gear
                for (k in 0 until 8) {
                    val a0 = (k * TAU) / 8
                    g.moveTo(ix + cos(a0) * 5.5, iy + sin(a0) * 5.5)
                    g.lineTo(ix + cos(a0) * 8, iy + sin(a0) * 8)
                }
                g.addCircle(ix, iy, 5.5)
                g.addCircle(ix, iy, 2.2)
            }
            canvas.drawPath(g, ic)
            text(canvas, COPY.chips[i], x + 37, UI.page.chips.y, resources.f.chip, paint(C.ink))
        }
        // the cards, mostly under the sheet
        val cy = UI.page.cards.y
        canvas.drawRRect(rrect(17, cy, 169, UI.page.cards.h, 14), paint("#E8E8E5"))
        canvas.drawRRect(rrect(17, cy, 169, UI.page.cards.h, 14), stroke("#D2D2CE", 1))
        canvas.drawCircle(101, cy + 64, 20, paint("#B9B9B5"))
        text(canvas, "@" + COPY.handle, 101, cy + 104, resources.f.small, paint(C.ink), 0.5)
        canvas.drawRRect(rrect(54, cy + 146, 94, 22, 11), paint("#FFFFFF"))
        canvas.drawRRect(rrect(196, cy, 190, UI.page.cards.h, 14), paint("#E1E1DE"))
        canvas.drawCircle(291, cy + 72, 34, paint("#C48BF0"))
        canvas.drawCircle(291, cy + 72, 18, paint("#FFFFFF", 0.85))
        text(canvas, "Set up your Shop", 291, cy + 132, resources.f.chip, paint(C.ink), 0.5)
        text(canvas, "Links", 17, cy + UI.page.cards.h + 18, resources.f.chip, paint(C.ink))
        text(canvas, "Shop", 196, cy + UI.page.cards.h + 18, resources.f.chip, paint(C.ink))
        canvas.drawRRect(rrect(330, cy + UI.page.cards.h + 6, 56, 26, 13), paint(C.chip))
        text(canvas, "Set up", 358, cy + UI.page.cards.h + 19, resources.f.small, paint(C.ink), 0.5)
        // the setup ring
        val rg = UI.page.ring
        canvas.drawCircle(rg.x, rg.y, rg.r + 9, paint("#FFFFFF"))
        canvas.drawCircle(rg.x, rg.y, rg.r + 9, stroke("#C9B6F7", 2))
        canvas.drawCircle(rg.x, rg.y, rg.r, stroke("#EDE7FB", 5))
        val arc = Path()
        arc.addArc(rect(rg.x - rg.r, rg.y - rg.r, rg.r * 2, rg.r * 2), -90, 360 / 6)
        canvas.drawPath(arc, stroke(C.barFrom, 5))
        text(canvas, COPY.ring[0], rg.x, rg.y - 7, resources.f.ring, paint(C.ink), 0.5)
        text(canvas, COPY.ring[1], rg.x, rg.y + 8, resources.f.ring, paint(C.ink), 0.5)
        // tab bar
        canvas.drawRect(rect(0, 784, 402, 90), paint("#FFFFFF"))
        canvas.drawLine(0, 784, 402, 784, stroke("#E3E3E0", 1))
        val tabs = listOf("My Tapline", "Earn", "Insights", "Audience", "More")
        for (i in 0 until 5) {
            val x = 40 + i * 80.5
            val on = i == 0
            canvas.drawRRect(rrect(x - 10, 798, 20, 18, 5), stroke(if (on) C.badge else C.grey, 1.6))
            text(canvas, tabs[i], x, 832, resources.f.tab, paint(if (on) C.badge else C.grey), 0.5)
        }
    }

    // ------------------------------------------------------------------ the checklist

    private fun drawCheck(canvas: Surface, x: Double, y: Double, rad: Double, p: Double) {
        // the empty ring
        canvas.drawCircle(x, y, rad, stroke(C.ring, 1.6, 1 - smooth(p * 3)))
        if (p <= 0) return
        // a soft green ring pulses out as it is ticked
        if (p > 0.15 && p < 0.9) {
            val q = (p - 0.15) / 0.75
            canvas.drawCircle(x, y, rad * (1 + 0.9 * easeOut(q)), stroke(C.green, 2 * (1 - q), 0.5 * (1 - q)))
        }
        val fill = spring(p * D.tick, 0.32, 0.55)
        val s = mix(0.4, 1, fill)
        canvas.drawCircle(x, y, rad * s, paint(C.green, min(1.0, p * 4)))
        // the tick, drawn
        val k = clamp01((p - 0.25) / 0.45)
        if (k > 0) {
            val a = Point(x - rad * 0.42, y + rad * 0.02)
            val b = Point(x - rad * 0.1, y + rad * 0.34)
            val e = Point(x + rad * 0.46, y - rad * 0.3)
            val l1 = hypot(b.x - a.x, b.y - a.y)
            val l2 = hypot(e.x - b.x, e.y - b.y)
            val d = easeOut(k) * (l1 + l2)
            val tk = Path()
            tk.moveTo(a.x, a.y)
            if (d <= l1) tk.lineTo(mix(a.x, b.x, d / l1), mix(a.y, b.y, d / l1))
            else {
                tk.lineTo(b.x, b.y)
                val q = (d - l1) / l2
                tk.lineTo(mix(b.x, e.x, q), mix(b.y, e.y, q))
            }
            canvas.drawPath(tk, stroke("#FFFFFF", 2.3))
        }
    }

    private fun chevron(canvas: Surface, x: Double, y: Double, up: Double, a: Double) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(180 * up)
        val p = Path()
        p.moveTo(-6.5, -3)
        p.lineTo(0, 3.5)
        p.lineTo(6.5, -3)
        canvas.drawPath(p, stroke(C.ink, 1.5, a))
        canvas.restore()
    }

    private fun drawChecklist(canvas: Surface, resources: Res, live: Live, fade: Double) {
        val s = live.s
        val now = live.now
        val a = 1 - fade
        val count = countRolled(s, now)
        // header
        val tw = text(canvas, COPY.title, UI.title.x, UI.title.y, resources.f.title, paint(C.ink, a))
        val badge = UI.badge
        val bx = UI.title.x + tw + 8
        // the badge pops as its number changes
        var changed = NEVER
        for (i in 0 until N) if (s.doneAt[i] > changed) changed = s.doneAt[i]
        val pq = clamp01((now - changed - 0.06) / 0.32)
        val pop = if (changed > NEVER && pq > 0 && pq < 1) sin(PI * pq) * (1 - pq * 0.4) else 0.0
        canvas.save()
        canvas.translate(bx + badge.w / 2, badge.y + badge.h / 2)
        canvas.scale(1 + 0.15 * pop, 1 + 0.15 * pop)
        canvas.translate(-(bx + badge.w / 2), -(badge.y + badge.h / 2))
        canvas.drawRRect(rrect(bx, badge.y, badge.w, badge.h, badge.h / 2), paint(C.badge, a))
        // the count rolls
        val n0 = floor(count + 1e-4)
        val k = smooth(count - n0)
        canvas.save()
        canvas.clipRect(rect(bx, badge.y, badge.w, badge.h))
        val wp = paint("#FFFFFF", a)
        val slash = "/6"
        val sw = resources.f.badge.width(slash)
        val dw = resources.f.badge.width(numberString(n0))
        val x0 = bx + badge.w / 2 - (dw + sw) / 2
        text(canvas, numberString(n0), x0, badge.y + badge.h / 2 - k * 14, resources.f.badge, wp)
        if (k > 0.001) text(canvas, numberString(min(N.toDouble(), n0 + 1)), x0, badge.y + badge.h / 2 + (1 - k) * 14, resources.f.badge, wp)
        text(canvas, slash, x0 + dw, badge.y + badge.h / 2, resources.f.badge, wp)
        canvas.restore()
        canvas.restore()
        // close
        val close = UI.close
        canvas.drawCircle(close.x, close.y, close.r, paint(C.close, a))
        canvas.drawLine(close.x - 5, close.y - 5, close.x + 5, close.y + 5, stroke(C.ink, 1.7, a))
        canvas.drawLine(close.x + 5, close.y - 5, close.x - 5, close.y + 5, stroke(C.ink, 1.7, a))
        // the bar: its fill follows the count on a spring; a gloss runs along it after each change
        val bar = UI.bar
        canvas.drawRRect(rrect(bar.x, bar.y, bar.w, bar.h, bar.h / 2), paint(C.track, a))
        var last = NEVER
        for (i in 0 until N) if (s.doneAt[i] > last) last = s.doneAt[i]
        val fw = max(bar.h, (bar.w * count) / N)
        val bp = Paint()
        bp.setAntiAlias(true)
        bp.setAlphaf(a)
        bp.gradient = Gradient(bar.x, bar.x + fw, listOf(C.barFrom, C.barTo), listOf(1.0, 1.0))
        canvas.drawRRect(rrect(bar.x, bar.y, fw, bar.h, bar.h / 2), bp)
        val gl = now - last
        if (last > NEVER && gl > 0.1 && gl < 0.9) {
            val gx = bar.x + fw * easeInOut((gl - 0.1) / 0.8)
            canvas.save()
            canvas.clipRRect(rrect(bar.x, bar.y, fw, bar.h, bar.h / 2))
            val gp = Paint()
            gp.gradient = Gradient(gx - 40, gx + 10, listOf("#FFFFFF", "#FFFFFF", "#FFFFFF"), listOf(0.0, 0.55 * a, 0.0))
            canvas.drawRect(rect(gx - 40, bar.y, 50, bar.h), gp)
            canvas.restore()
        }
        // rows
        val rows = UI.rows
        var y = rows.y0
        for (i in 0 until N) {
            val e = openness(s, i, now)
            val h = rows.h + rows.open * e
            val cy = y + rows.h / 2
            val dn = doneness(s, i, now)
            drawCheck(canvas, UI.rows.check.x, cy, UI.rows.check.r, dn)
            val ink = Paint()
            ink.setAntiAlias(true)
            val strikeK = clamp01((dn - 0.45) / 0.55)
            ink.setColor(mixCol(C.ink, C.mute, smooth(dn * 1.4), a))
            val tw2 = text(canvas, ITEMS[i].title, rows.textX, cy, resources.f.row, ink)
            if (strikeK > 0) canvas.drawLine(rows.textX, cy + 1, rows.textX + tw2 * easeOut(strikeK), cy + 1, stroke(C.mute, 1.4, a))
            // done rows lose their chevron
            chevron(canvas, rows.chevronX, cy, e, a * (1 - smooth(dn * 2)))
            if (e > 0.005) {
                canvas.save()
                canvas.clipRect(rect(0, y + rows.h - 6, 402, h - rows.h + 6))
                val ca = a * smooth((e - 0.15) / 0.85)
                val lift = (1 - e) * -12
                for (l in 0 until 2) textFit(canvas, ITEMS[i].body[l], rows.textX, cy + UI.desc.dy[l] + lift, resources.f.desc, paint(C.ink2, ca), 332)
                val layout = UI.action
                val aw = resources.f.action.width(ITEMS[i].action) + layout.padX * 2
                val press = if (s.pressRow == i) max(0.0, 1 - abs(now - s.pressAt - 0.08) / 0.12) else 0.0
                val ps = 1 - 0.04 * press
                canvas.save()
                canvas.translate(rows.textX + aw / 2, cy + layout.dy + lift)
                canvas.scale(ps, ps)
                canvas.drawRRect(rrect(-aw / 2, -layout.h / 2, aw, layout.h, 12), paint(C.purple, ca))
                text(canvas, ITEMS[i].action, 0, 0, resources.f.action, paint("#FFFFFF", ca), 0.5)
                canvas.restore()
                text(canvas, COPY.skip, rows.textX + aw + UI.skipDx, cy + layout.dy + lift, resources.f.action, paint(C.ink, ca))
                canvas.restore()
            }
            if (i < N - 1) canvas.drawLine(16.5, y + h, 385.5, y + h, stroke(C.divider, 1, a))
            y += h
        }
    }

    // ------------------------------------------------------------------ all set

    private fun drawPopper(canvas: Surface, x: Double, y: Double, k: Double, amb: Double) {
        if (k <= 0) return
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(k, k)
        // the cone, pointing down-left
        val cone = Path()
        cone.moveTo(-15, 15)
        cone.lineTo(-3, -11)
        cone.lineTo(11, 3)
        cone.close()
        canvas.drawPath(cone, paint(C.popper))
        canvas.drawLine(-9, 2, 1, 12, stroke("#FFFFFF", 1.6, 0.8))
        // sparkles above it, twinkling
        val tw = { i: Double -> 1 + 0.18 * sin((TAU * 2 * amb) / 9 + i * 2) }
        fun plus(px: Double, py: Double, rr: Double) {
            canvas.drawLine(px - rr, py, px + rr, py, stroke(C.popper, 1.8))
            canvas.drawLine(px, py - rr, px, py + rr, stroke(C.popper, 1.8))
        }
        plus(1.0, -17.0, 3.6 * tw(0.0))
        plus(15.0, -13.0, 4.4 * tw(1.0))
        plus(18.0, 4.0, 3.4 * tw(2.0))
        canvas.drawCircle(9, -3, 1.8, paint(C.popper))
        canvas.drawCircle(-6, -20, 1.5, paint(C.popper))
        canvas.restore()
    }

    /** Confetti from the popper: a fixed scatter, `tau` seconds after it fired. */
    private fun drawConfetti(canvas: Surface, x: Double, y: Double, tau: Double, seed: Double) {
        if (tau < 0 || tau > 2.2) return
        for (i in 0 until 46) {
            val h1 = sin((i + 1) * 12.9898 + seed * 78.233) * 43758.5453
            val r1 = h1 - floor(h1)
            val h2 = sin((i + 1) * 39.3468 + seed * 11.135) * 24634.6345
            val r2 = h2 - floor(h2)
            val h3 = sin((i + 1) * 73.156 + seed * 52.235) * 12345.6789
            val r3 = h3 - floor(h3)
            val ang = -PI / 2 + (r1 - 0.45) * 2.6
            val v = 260 + 260 * r2
            val drag = 1 - exp(-tau * 2.6)
            val px = x + (cos(ang) * v * drag) / 2.6
            val py = y + (sin(ang) * v * drag) / 2.6 + 140 * tau * tau
            val fade = 1 - smooth((tau - 1.2) / 1)
            val cc = C.confetti[i % C.confetti.size]
            canvas.save()
            canvas.translate(px, py)
            canvas.rotate(remainder(r3 * 360 + tau * 520 * (r1 - 0.5), 360))
            val flutter = abs(cos(tau * (6 + 8 * r3) + r2 * 6))
            if (i % 3 == 0) canvas.drawCircle(0, 0, 2.6, paint(cc, fade))
            else canvas.drawRect(rect(-3.5, -1.8 * flutter - 0.4, 7, 3.6 * flutter + 0.8), paint(cc, fade))
            canvas.restore()
        }
    }

    private fun drawAllSet(canvas: Surface, resources: Res, live: Live, a: Double) {
        val layout = UI.allSet
        val icon = UI.allSet.icon
        val now = live.now
        val s = live.s
        val m0 = D.morph
        val m1 = D.morph + D.bloom
        val tx = { i: Double -> smooth((a - m0 - 0.3 - i * 0.07) / 0.35) }
        // the white sheet grows out of the bubble
        val g = if (a < m0) 0.0 else spring(a - m0, 0.5, 0.78)
        val top = mix(icon.y - icon.r, layout.y, g)
        val left = mix(icon.x - icon.r, 0, g)
        val right = mix(icon.x + icon.r, 402, g)
        val bottom = mix(icon.y + icon.r, REF_H + 60, g)
        val rad = mix(icon.r, 34, clamp01(g))
        if (a >= m0) canvas.drawRRect(rrect(left, top, right - left, bottom - top, rad), paint(C.sheet))
        // the bubble: during the morph it is the shrinking sheet itself (see drawScene); after, the icon
        if (a >= m0) {
            val settle = spring(a - m0, 0.38, 0.55)
            canvas.drawCircle(icon.x, icon.y, icon.r * mix(1.08, 1, settle), paint(C.lilac))
            drawPopper(canvas, icon.x, icon.y, spring(a - m0 - 0.12, 0.42, 0.5), live.amb)
        }
        drawConfetti(canvas, icon.x + 10, icon.y - 14, a - m0 - 0.2, 1.0)
        if (s.ctaAt > NEVER) drawConfetti(canvas, 201.0, UI.allSet.cta.y, now - s.ctaAt, 2.0)
        if (a < m1 - 0.3) return
        val t0 = tx(0.0)
        text(canvas, COPY.allSet, 201, layout.title + 8 * (1 - t0), resources.f.allSet, paint(C.ink, t0), 0.5)
        for (l in 0 until 3) {
            val k = tx(1 + l * 0.4)
            text(canvas, COPY.allSetBody[l], 201, layout.body[l] + 8 * (1 - k), resources.f.body, paint(C.grey, k), 0.5)
        }
        val kc = tx(3.0)
        val cp = if (s.ctaAt > NEVER) max(0.0, 1 - abs(now - s.ctaAt - 0.08) / 0.14) else 0.0
        val cs = 1 - 0.04 * cp
        val cta = UI.allSet.cta
        canvas.save()
        canvas.translate(cta.x + cta.w / 2, cta.y + cta.h / 2 + 10 * (1 - kc))
        canvas.scale(cs, cs)
        canvas.drawRRect(rrect(-cta.w / 2, -cta.h / 2, cta.w, cta.h, cta.h / 2), paint(C.cta, kc))
        text(canvas, COPY.cta, 0, 0, resources.f.cta, paint("#FFFFFF", kc), 0.5)
        canvas.restore()
        val kl = tx(3.6)
        val lp = if (s.laterAt > NEVER) max(0.0, 1 - abs(now - s.laterAt - 0.1) / 0.18) else 0.0
        if (lp > 0) canvas.drawRRect(rrect(150, layout.later - 18, 102, 36, 18), paint("#000000", 0.06 * lp))
        text(canvas, COPY.later, 201, layout.later + 8 * (1 - kl), resources.f.cta, paint(C.ink, kl), 0.5)
    }

    fun drawScene(canvas: Surface, resources: Res, scale: Double, live: Live) {
        val s = live.s
        val now = live.now
        canvas.save()
        canvas.scale(scale, scale)
        drawPage(canvas, resources)
        // how far the sheet is down (dismissed) and which sheet it is
        var down = 0.0
        if (s.leaveAt > NEVER && now >= s.leaveAt) {
            if (s.backAt > NEVER && now >= s.backAt) down = 1 - easeOut((now - s.backAt) / D.enter)
            else down = easeInOut((now - s.leaveAt) / D.leave)
        }
        val allSet = s.allSetAt > NEVER && now >= s.allSetAt && !(s.backAt > NEVER && now >= s.backAt)
        canvas.drawRect(rect(0, 0, 402, REF_H), paint(C.dim, 0.5 * (1 - down)))
        val off = down * (REF_H - UI.allSet.y + 40)
        canvas.save()
        canvas.translate(0, off)
        if (!allSet) {
            canvas.drawRRect(rrect(0, UI.sheet.y, 402, REF_H - UI.sheet.y + 80, UI.sheet.r), paint(C.sheet))
            drawChecklist(canvas, resources, live, 0.0)
        } else {
            val a = now - s.allSetAt
            if (a < D.morph) {
                // the list fades and shrinks first; the sheet folds into a bubble at the icon
                val icon = UI.allSet.icon
                val fade = smooth(a / 0.22)
                val m = easeInOut(a / D.morph)
                val top = mix(UI.sheet.y, icon.y - icon.r, m)
                val left = mix(0, icon.x - icon.r, m)
                val right = mix(402, icon.x + icon.r, m)
                val bottom = mix(REF_H + 80, icon.y + icon.r, m)
                val rad = mix(UI.sheet.r, icon.r, m)
                val sc = Paint()
                sc.setAntiAlias(true)
                sc.setColor(mixCol(C.sheet, C.lilac, smooth((m - 0.4) / 0.6)))
                canvas.drawRRect(rrect(left, top, right - left, bottom - top, rad), sc)
                if (fade < 1) {
                    canvas.save()
                    canvas.clipRRect(rrect(left, top, right - left, bottom - top, rad))
                    canvas.translate(icon.x, icon.y)
                    canvas.scale(1 - 0.08 * fade, 1 - 0.08 * fade)
                    canvas.translate(-icon.x, -icon.y)
                    drawChecklist(canvas, resources, live, fade)
                    canvas.restore()
                }
            } else drawAllSet(canvas, resources, live, a)
        }
        canvas.restore()
        canvas.restore()
    }

}
