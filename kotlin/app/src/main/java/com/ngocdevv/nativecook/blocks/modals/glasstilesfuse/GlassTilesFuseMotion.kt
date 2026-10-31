package com.ngocdevv.nativecook.blocks.modals.glasstilesfuse

import kotlin.math.*

// Direct transcription of gtf-motion.ts, in points (Compose dp).
internal object GlassTilesFuseMotion {
    const val LOOP=9.0
    const val SCALE=1.35
    const val GAP=86.0
    const val FUSED_W=70.0
    const val FUSED_G=30.0
    const val SNAP_DIST=185.0
    const val TENSION=0.15
    const val PULL_X=(SNAP_DIST-FUSED_W-FUSED_G)/(1-TENSION)+4
    object T {
        const val PRESS=1.0; const val CONNECT0=1.2; const val FUSED=2.8; const val GRAB=3.1
        const val SNAP=4.2; const val SETTLED=5.0; const val DONE=6.5; const val BACK0=6.75; const val BACK1=8.2
    }
    fun clamp01(v:Double)=v.coerceIn(0.0,1.0)
    fun seg(t:Double,a:Double,b:Double)=clamp01((t-a)/(b-a))
    fun easeInOut(x:Double)=if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x:Double)=1-(1-x).pow(3)
    fun mix(a:Double,b:Double,t:Double)=a+(b-a)*t
    fun springTo(t:Double,response:Double,zeta:Double):Double {
        if(t<=0) return 0.0
        val w=2*PI/response; val wd=w*sqrt(1-zeta*zeta)
        return 1-exp(-zeta*w*t)*(cos(wd*t)+zeta*w/wd*sin(wd*t))
    }
    data class Layout(val w:Double,val h:Double,val top:Double,val bottom:Double) {
        val titleTop=top+24; val rowsTop=top+126; val rowH=63.0
        val buttonTop=h-max(bottom,16.0)-12-52
        val textTop=buttonTop-24-76
        val tilesY=textTop-40-0.5*145
        val sheetTop=max(top+200,tilesY-0.5*145-56)
        val stageH=textTop-12-sheetTop
        val white=floor(107*SCALE+0.5); val glass=floor(102*SCALE+0.5); val side=32.0
    }
    data class Poses(val wx:Double,val wy:Double,val wa:Double,val gx:Double,val gy:Double,val ga:Double,
                     val size:Double=1.0,val fuse:Double=0.0,val seam:Double=-1.0,val check:Double=0.0)
    fun rest(l:Layout,t:Double):Poses {
        val bob=sin(t/LOOP*PI*4); val sway=sin(t/LOOP*PI*2)
        return Poses(l.w/2-22*SCALE,l.tilesY+12*SCALE,0.14,l.w/2+36*SCALE+sway*4,l.tilesY-16*SCALE+bob*5,-0.12+sway*0.03)
    }
    fun linked(l:Layout,r:Poses,k:Double):Poses {
        val a=easeInOut(seg(k,0.0,0.3)); val fuseIn=easeInOut(seg(k,0.18,0.42))
        val part=easeInOut(seg(k,0.56,0.9)); val fuseOut=easeInOut(seg(k,0.56,0.84))
        val cx=l.w/2; val cy=l.tilesY; val wobble=sin(seg(k,0.82,1.0)*PI*3)*(1-seg(k,0.82,1.0))*6
        return Poses(mix(mix(r.wx,cx-FUSED_W,a),cx-GAP-wobble,part),mix(mix(r.wy,cy,a),cy,part),mix(r.wa,0.0,a),
            mix(mix(r.gx,cx+FUSED_G,a),cx+GAP+wobble,part),mix(mix(r.gy,cy,a),cy,part),mix(r.ga,0.0,a),
            1-0.1*part,fuseIn*(1-fuseOut),-1+2*seg(k,0.36,0.58),easeOut(seg(k,0.78,1.0)))
    }
    fun linkAt(t:Double):Double {
        if(t<T.CONNECT0) return 0.0
        if(t<T.SNAP) return 0.5*easeInOut(seg(t,T.CONNECT0,T.FUSED))
        if(t<T.BACK0) return 0.5+0.5*easeOut(seg(t,T.SNAP,T.SETTLED))
        return 1-easeInOut(seg(t,T.BACK0,T.BACK1))
    }
    fun pullAt(t:Double):Double {
        if(t<T.GRAB) return 0.0
        if(t<T.SNAP) return PULL_X*easeInOut(seg(t,T.GRAB,T.SNAP))
        return PULL_X*(1-easeOut(seg(t,T.SNAP,T.SETTLED)))
    }
    fun stickAt(t:Double):Double {
        if(t<T.SNAP) return 1.0
        if(t<T.BACK0) return 1-easeOut(seg(t,T.SNAP,T.SNAP+0.14))
        return 1.0
    }
    fun recoilAt(t:Double):Double {
        if(t<T.GRAB) return 0.0
        if(t<T.SNAP) return TENSION*pullAt(t)
        val x=(t-T.SNAP)/0.6
        return if(x>=1) 0.0 else TENSION*PULL_X*exp(-4*x)*cos(9*x)*(1-x)
    }
    fun pressAt(t:Double,at:Double)=easeOut(seg(t,at,at+0.1))*(1-easeOut(seg(t,at+0.16,at+0.36)))
    fun fingerAt(t:Double):Pair<Double,Int> {
        fun near(a:Double,b:Double)=easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.25)))
        val onButton=near(T.PRESS,T.PRESS+0.25)+near(T.DONE,T.DONE+0.25); val onGlass=near(T.GRAB,T.SNAP)
        return clamp01(onButton+onGlass) to if(onGlass>0.001) 1 else 0
    }
}
