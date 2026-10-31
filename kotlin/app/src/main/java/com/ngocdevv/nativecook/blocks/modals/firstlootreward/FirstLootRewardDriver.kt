package com.ngocdevv.nativecook.blocks.modals.firstlootreward

import kotlin.math.*

internal class FirstLootRewardDriver {
    var t=0.0
    var now=0.0
    var last = -10.0
    var pressAt = -1.0
    var openAt = -10.0
    var power=1.0
    private val tiltX=FirstLootRewardMotion.Spring()
    private val tiltY=FirstLootRewardMotion.Spring()
    private val keep=FirstLootRewardMotion.Spring()
    private val view=FirstLootRewardMotion.Spring()
    fun step(dt: Double,auto: Boolean) {
        now+=dt
        val touched=now-last<2 || pressAt>=0
        if(auto && !(touched && FirstLootRewardMotion.resting(t))) { t+=dt; if(t>=4)t-=4 }
    }
    fun begin(target: Int,reduced: Boolean): Boolean {
        if(target==1) {
            if(reduced || !FirstLootRewardMotion.resting(t))return false
            last=now; pressAt=now
        } else if(target==2) { keep.aim(1.0,now,0.16,1.0) } else if(target==3) { view.aim(1.0,now,0.16,1.0) }
        return true
    }
    fun move(dx: Double,dy: Double) {
        if(pressAt<0)return
        last=now; tiltX.set((dx/90).coerceIn(-1.0,1.0)); tiltY.set((dy/90).coerceIn(-1.0,1.0))
    }
    fun end(target: Int,ok: Boolean,reduced: Boolean): Int {
        val m=FirstLootRewardMotion
        if(target==1 && pressAt>=0) {
            power=1+0.6*m.clamp((now-pressAt-0.21)/1.1); pressAt = -1.0; openAt=now; last=now
            tiltX.aim(0.0,now,0.6,0.55); tiltY.aim(0.0,now,0.6,0.55)
            return 2
        }
        if(target==2)keep.aim(0.0,now,0.32,0.7)
        if(target==3)view.aim(0.0,now,0.32,0.7)
        if(!ok || target !in 2..3)return -1
        if(target==2) {
            if(!reduced && m.resting(t)) { t=3.55; last = -10.0 }
            return 0
        }
        if(!reduced && m.resting(t)) { power=1.0; openAt=now; last=now; return 3 }
        return 0
    }
    fun frame(gx: Double=0.0,gy: Double=0.0): FirstLootRewardMotion.Frame {
        val m=FirstLootRewardMotion
        var ch=m.chest(t-0.48)
        val oa=now-openAt
        var charge=0.0; var press=0.0
        if(pressAt>=0) {
            val held=now-pressAt
            ch=m.chest(min(held,0.24)); charge=m.clamp((held-0.24)/1.1); press=min(held/0.12,1.0)
            if(held>0.24)ch=ch.copy(gather=1+sin(held*46)*(0.06+0.08*charge))
        } else if(oa>=0 && oa<1.7)ch=m.chest(0.25+oa)
        return FirstLootRewardMotion.Frame(t,ch,(tiltX.sample(now).first+gx*0.6).coerceIn(-1.0,1.0),(tiltY.sample(now).first+gy*0.6).coerceIn(-1.0,1.0),press,charge,if(pressAt<0 && oa>=0 && oa<1.7)power else 1.0,keep.sample(now).first,view.sample(now).first)
    }
}
