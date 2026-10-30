package com.ngocdevv.nativecook.blocks.sheets.bookfansignin

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.LOOP
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.RESUME
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.PEAK
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.T
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.inSheet
internal class BookFanSigninDriver {
    var t = 0.0
    var mode = 0
    var stop = 0.0
    var rate = 1.0
    var last = -10.0
    var amb = 0.0
    var scroll = 0.0
    var scrollV = 0.0
    var drag = 0.0
    var dragV = 0.0
    var dragging = 0
    var ctaAim = 0.0
    var ctaP = 0.0
    var pressAim = 0.0
    var pressP = 0.0
    fun step(dt: Double, now: Double, auto: Boolean): Double {
        if (mode==1) {
            t+=dt*rate
            if (t>=stop) {
                t = if (stop>=LOOP)0.0 else stop
                mode = 2
                rate = 1.0
                last = now
            }
        }
        else if (mode==2) {
            if (auto&&dragging==0&&now-last>=RESUME)mode = 0
        }
        else if (auto)t = (t+dt)%LOOP
        amb = (amb+dt)%LOOP
        if (dragging==0) {
            scroll+=scrollV*dt
            scrollV*=exp(-dt*2.4)
        }
        if (dragging==0&&drag!=0.0) {
            val k = 260.0
            val c = 2*0.9*sqrt(k)
            dragV+=(-k*drag-c*dragV)*dt
            drag+=dragV*dt
            if (abs(drag)<0.05&&abs(dragV)<1) {
                drag = 0.0
                dragV = 0.0
            }
        }
        ctaP+=(ctaAim-ctaP)*(1-exp(-dt*if (ctaAim>0)40 else 16))
        pressP+=(pressAim-pressP)*(1-exp(-dt*if (pressAim>0)40 else 16))
        return t
    }
    fun touched(now: Double) {
        last = now
        if (mode!=0)return
        rate = 1.0
        if (t>=T.press[0]&&t<PEAK) {
            mode = 1
            stop = PEAK
        }
        else if (t>=T.close&&t<T.closeEnd) {
            mode = 1
            stop = LOOP
        }
        else mode = 2
    }
    fun open() {
        t = T.open
        mode = 1
        rate = 1.0
        stop = PEAK
    }
    fun close(): Boolean {
        if (!inSheet(t)&&!(t>=PEAK&&t<T.close))return false
        t = T.close
        mode = 1
        rate = 1.4
        stop = LOOP
        return true
    }
}
