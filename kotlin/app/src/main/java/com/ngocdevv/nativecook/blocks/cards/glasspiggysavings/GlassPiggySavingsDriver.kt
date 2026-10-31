package com.ngocdevv.nativecook.blocks.cards.glasspiggysavings

import com.ngocdevv.nativecook.blocks.cards.glasspiggysavings.GlassPiggySavingsMotion as M
import kotlin.math.*

internal class GlassPiggySavingsDriver {
    val frame=M.Frame()
    var last = -10.0
    val moving get()=frame.held.on || frame.now-frame.held.dropAt<0.3 || frame.now-frame.held.backAt<0.45 || frame.now-frame.bonusAt<2.4 ||
        abs(frame.pig.sq)+abs(frame.pig.sv)+abs(frame.pig.wob)+abs(frame.pig.wv)>0.00001
    fun step(dt:Double,auto:Boolean,land:()->Unit) {
        frame.now+=dt
        val touched=frame.held.on || frame.now-last<2
        if(auto && !touched) {
            val before=frame.t; frame.t+=dt
            if(frame.t>=M.LOOP) frame.t-=M.LOOP
            if(before<2 && frame.t>=2) frame.pig.sv-=0.7
            if(before<2.5 && frame.t>=2.5) { frame.pig.sv-=2.2; frame.plus=20 }
        }
        val hd=frame.held
        if(hd.dropAt>0 && frame.now-hd.dropAt>=0.23 && frame.now-hd.dropAt-dt<0.23) {
            frame.pig.sv-=2.2; frame.bonus+=M.CHIPS[hd.chip]; frame.bonusAt=frame.now; frame.plus=M.CHIPS[hd.chip]; land()
        }
        frame.pig.step(dt)
    }
    fun begin(x:Double,y:Double,l:M.Layout,reduced:Boolean,cue:(String)->Unit,buzz:(Boolean)->Unit) {
        last=frame.now
        for(i in 0..2) if(abs(x-l.chipX(i))<52 && abs(y-l.chipY)<30) {
            frame.held.on=true; frame.held.x=x; frame.held.y=y-30; frame.held.chip=i; buzz(false); return
        }
        if(x>l.pigX && x<l.pigX+340 && y>l.pigY && y<l.pigY+800*M.SCALE) {
            frame.pig.wv+=3.2; if(!reduced) cue("wiggle"); buzz(false)
        }
    }
    fun update(x:Double,y:Double) {
        if(!frame.held.on) return
        last=frame.now; frame.held.x=x; frame.held.y=y-30
    }
    fun end(x:Double,y:Double,l:M.Layout,reduced:Boolean,buzz:(Boolean)->Unit) {
        val hd=frame.held; if(!hd.on) return
        last=frame.now; val cy=y-30
        val over=abs(x-l.slotX)<120 && cy>l.pigY-80 && cy<l.pigY+160
        hd.on=false
        if(over && !reduced) { hd.dropAt=frame.now; hd.fromX=x; hd.fromY=min(cy,l.slotY-30) }
        else if(over) { frame.bonus+=M.CHIPS[hd.chip]; frame.bonusAt=frame.now; frame.plus=M.CHIPS[hd.chip]; buzz(true) }
        else { hd.backAt=frame.now; hd.fromX=x; hd.fromY=cy }
    }
    fun drop(i:Int,l:M.Layout,reduced:Boolean,active:Boolean) {
        last=frame.now
        if(reduced || !active) { frame.bonus+=M.CHIPS[i]; frame.bonusAt=frame.now; frame.plus=M.CHIPS[i] }
        else {
            val hd=frame.held; hd.on=false; hd.chip=i; hd.dropAt=frame.now; hd.fromX=l.slotX; hd.fromY=l.slotY-90
        }
    }
}
