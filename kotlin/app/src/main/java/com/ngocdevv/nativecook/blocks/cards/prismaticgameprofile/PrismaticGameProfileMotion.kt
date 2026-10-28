package com.ngocdevv.nativecook.blocks.cards.prismaticgameprofile

import kotlin.math.*

/** Pure RN-parity geometry. The same poses drive native actors, material and hit testing. */
internal object PrismaticGameProfileMotion {
    const val LOOP = 10.0
    data class Bounds(val x: Float, val y: Float, val width: Float, val height: Float, val radius: Float = 0f) {
        val right get() = x + width
        val bottom get() = y + height
        fun contains(px: Float, py: Float, margin: Float = 0f) = px >= x-margin && px <= right+margin && py >= y-margin && py <= bottom+margin
    }
    data class Formation(val body: Float, val portrait: Float, val name: Float, val handle: Float, val skills: Float, val score: Float, val seal: Float, val curl: Float)
    data class Frame(val rail: Float = 1f, val time: Double = 0.0, val material: Int = -1, val phase: Float = 0f, val power: Float = 0f, val progress: Float = 0f, val content: Float = 0f, val catchStrength: Float = 0f, val tiltX: Float = 0f, val tiltY: Float = 0f, val reduced: Boolean = false, val index: Int = 1)
    data class Source(val fontScale: Float = 1f, val whiteTop: Float = 190f, val nameTop: Float = 186f, val nameHeight: Float = 32f, val handleTop: Float = 222f, val handleHeight: Float = 20f, val balanceTop: Float = 262f, val balanceHeight: Float = 56f, val stackedBalance: Boolean = false, val collectionTop: Float = 344f, val statistics: Bounds = Bounds(251f,334f,118f,44f), val railTop: Float = 386f, val railSize: Float = 164f, val skillsTop: Float = 556f, val skillsHeight: Float = 20f, val scoreTop: Float = 576f, val scoreHeight: Float = 32f, val arrowTop: Float = 560f, val divider: Float = 610f, val lastTop: Float = 627f, val gameTop: Float = 663f, val gameHeight: Float = 64f, val tabsTop: Float = 746f, val tabsHeight: Float = 72f) {
        val railY get() = railTop + railSize/2
    }
    data class Glyph(val width: Float, val height: Float, val x: Float, val y: Float, val scale: Float)
    data class Identity(val avatarY: Float = 298f, val avatarScale: Float = 2.05f, val portraitTop: Float = 163f, val portraitHeight: Float = 270f, val name: Glyph = Glyph(120f,38f,130.5f,469.9f,1.1f), val handle: Glyph = Glyph(99f,18f,117.96f,504.52f,1.08f), val skills: Glyph = Glyph(30f,17f,81.75f,564.775f,1.15f), val score: Glyph = Glyph(49f,26f,113.5f,604.55f,2f), val sealX: Float = 286.5f, val sealScale: Float = 1.05f)
    enum class Kind { Name, Handle, Skills, Score }
    fun clamp(p: Float) = p.coerceIn(0f,1f)
    fun smooth(p: Float): Float { val t=clamp(p); return t*t*(3-2*t) }
    fun step(p: Float, start: Float, end: Float) = smooth((p-start)/(end-start))
    fun formation(progress: Float, reduced: Boolean = false): Formation {
        val p=clamp(progress)
        if(reduced) { val e=if(p>0)1f else 0f; return Formation(e,e,e,e,e,e,e,0f) }
        val r=1-step(p,0f,.8f)
        return Formation(1-r*r*r,step(p,.18f,.82f),step(p,.24f,.88f),step(p,.28f,.9f),step(p,.3f,.94f),step(p,.34f,.96f),step(p,.08f,.97f),step(p,0f,.14f)*(1-step(p,.7f,.97f)))
    }
    fun badgeOrigin(rail: Float, index: Int, source: Source): Bounds {
        val d=min(1f,abs(index-rail)); val size=source.railSize-(source.railSize-96)*d
        return Bounds(196.5f+146*(index-rail)-size/2,source.railY-size/2,size,size,size/2)
    }
    fun surface(p: Float, origin: Bounds, reduced: Boolean): Bounds {
        val b=formation(p,reduced).body
        return Bounds(origin.x+(36.5f-origin.x)*b,origin.y+(85-origin.y)*b,origin.width+(320-origin.width)*b,origin.height+(590-origin.height)*b,origin.width/2+(34-origin.width/2)*b)
    }
    fun sourcePortraitOpacity(p: Float, reduced: Boolean) = if(reduced) if(p>0)0f else 1f else 1-step(p,.37f,.46f)
    fun avatarBounds(p: Float, source: Source, target: Identity, reduced: Boolean): Bounds {
        val e=if(p>0)1f else 0f
        val x=if(reduced)e else step(p,.3f,.56f); val y=if(reduced)e else step(p,.3f,.8f); val grow=if(reduced)e else step(p,.43f,.8f)
        val s=94f/140+(target.avatarScale-94f/140)*grow
        val cx=68+(196.5f-68)*x; val cy=202+(target.avatarY-202)*y
        return Bounds(cx-59*s,cy-61*s,118*s,124*s)
    }
    fun glyph(kind: Kind, p: Float, reduced: Boolean, source: Source, layout: Identity): Pair<Bounds,Float> {
        val f=formation(p,reduced)
        val target=when(kind){Kind.Name->layout.name;Kind.Handle->layout.handle;Kind.Skills->layout.skills;Kind.Score->layout.score}
        val phase=when(kind){Kind.Name,Kind.Handle->f.name;Kind.Skills->f.skills;Kind.Score->f.score}
        val sourceScale=when(kind){Kind.Name->30f/32;Kind.Score->27f/20;else->14f/13}
        val sourceY=when(kind){Kind.Name->source.nameTop+source.nameHeight/2;Kind.Handle->source.handleTop+source.handleHeight/2;Kind.Skills->source.skillsTop+source.skillsHeight/2;Kind.Score->source.scoreTop+source.scoreHeight/2}
        val sourceX=if(kind==Kind.Name||kind==Kind.Handle)128+target.width*sourceScale/2 else 196.5f
        val vertical=if(kind==Kind.Name||kind==Kind.Handle)if(reduced)if(p>0)1f else 0f else step(p,.18f,.43f) else phase
        val footer=step(p,.08f,.2f)
        val horizontal=if(kind==Kind.Name||kind==Kind.Handle)1-(1-vertical)*(1-vertical) else if(reduced)if(p>0)1f else 0f else 1-(1-footer).pow(3)
        val scale=sourceScale+(target.scale-sourceScale)*phase
        val sourceLeft=sourceX-target.width*sourceScale/2; val targetLeft=target.x-target.width*target.scale/2
        val left=sourceLeft+(targetLeft-sourceLeft)*horizontal; val cy=sourceY+(target.y-sourceY)*vertical
        return Bounds(left,cy-target.height*scale/2,target.width*scale,target.height*scale) to scale
    }
    fun island(p: Float, reduced: Boolean, source: Source, layout: Identity): Bounds {
        val avatar=avatarBounds(p,source,layout,reduced); val s=avatar.width/118; val ratio=s/layout.avatarScale
        val cx=avatar.x+59*s; val cy=avatar.y+61*s
        return Bounds(cx+(54.5f-196.5f)*ratio,cy+(layout.portraitTop-layout.avatarY)*ratio,284*ratio,layout.portraitHeight*ratio,28*ratio)
    }
    fun avoidance(p: Float, reduced: Boolean, source: Source, layout: Identity): Bounds {
        val regions=listOf(avatarBounds(p,source,layout,reduced),glyph(Kind.Name,p,reduced,source,layout).first,glyph(Kind.Handle,p,reduced,source,layout).first,island(p,reduced,source,layout))
        val x=regions.minOf { it.x };val y=regions.minOf { it.y }
        return Bounds(x,y,regions.maxOf { it.right }-x,regions.maxOf { it.bottom }-y)
    }
    fun coverage(bounds: Bounds, rect: Bounds): Float {
        val r=min(bounds.radius,min(bounds.width/2,bounds.height/2)); var outside=-100000f
        for(i in 0..1)for(j in 0..1){
            val qx=abs(rect.x+rect.width*i-bounds.x-bounds.width/2)-(bounds.width/2-r)
            val qy=abs(rect.y+rect.height*j-bounds.y-bounds.height/2)-(bounds.height/2-r)
            outside=max(outside,hypot(max(qx,0f),max(qy,0f))+min(max(qx,qy),0f)-r)
        }
        return smooth((-outside-6)/8)
    }
    fun seal(p: Float, reduced: Boolean): Triple<Float,Float,Float> {
        val e=if(p>0)1f else 0f
        if(reduced)return Triple(e,e,e)
        val y=step(p,.08f,.32f);return Triple(step(p,.10f,.30f),y,y)
    }
    fun sealLift(p: Float, reduced: Boolean) = if(reduced)0f else 12*step(p,.1f,.28f)*(1-step(p,.8f,.97f))
    fun bezier(p: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val x=clamp(p);var lo=0f;var hi=1f
        repeat(18){ val t=(lo+hi)/2; val value=3*(1-t).pow(2)*t*x1+3*(1-t)*t*t*x2+t*t*t;if(value<x)lo=t else hi=t }
        val t=(lo+hi)/2;return 3*(1-t).pow(2)*t*y1+3*(1-t)*t*t*y2+t*t*t
    }
    fun contentEase(p: Float)=bezier(p,.23f,1f,.32f,1f)
    fun demo(seconds: Double, reduced: Boolean = false): Frame {
        if(reduced)return Frame(reduced=true)
        val exactTime=seconds.coerceIn(0.0,LOOP)
        fun smoothDouble(value: Double): Double {val v=value.coerceIn(0.0,1.0);return v*v*(3-2*v)}
        val exactRail=when {
            exactTime>=.6&&exactTime<1->1-smoothDouble((exactTime-.6)/.4)
            exactTime>=1&&exactTime<2->0.0
            exactTime>=2&&exactTime<2.4->smoothDouble((exactTime-2)/.4)
            exactTime>=3.4&&exactTime<3.8->1+smoothDouble((exactTime-3.4)/.4)
            exactTime>=3.8&&exactTime<4.9->2.0
            exactTime>=4.9&&exactTime<5.2->2-smoothDouble((exactTime-4.9)/.3)
            else->1.0
        }
        val t=exactTime.toFloat();var rail=1f;var material=-1;var age=-1f;var duration=1f;var moving=-1f;var pulseOrigin=1f;var panel=0f
        when {
            t>=.6f&&t<1f->{rail=1-smooth((t-.6f)/.4f);moving=1f}
            t>=1f&&t<2f->{rail=0f;material=0;age=t-1}
            t>=2f&&t<2.4f->{rail=smooth((t-2)/.4f);moving=0f}
            t>=2.4f&&t<3.4f->{material=1;age=t-2.4f;pulseOrigin=0f}
            t>=3.4f&&t<3.8f->{rail=1+smooth((t-3.4f)/.4f);moving=1f}
            t>=3.8f&&t<4.9f->{rail=2f;material=2;age=t-3.8f;duration=1.1f}
            t>=4.9f&&t<5.2f->rail=2-smooth((t-4.9f)/.3f)
            t>=5.2f&&t<5.8f->panel=bezier((t-5.2f)/.6f,.77f,0f,.175f,1f)
            t>=5.8f&&t<7.6f->panel=1f
            t>=7.6f&&t<8.4f->panel=1-bezier((t-7.6f)/.8f,.77f,0f,.175f,1f)
        }
        rail=exactRail.toFloat()
        var phase=0f;var power=0f
        fun under(origin: Float): Triple<Int,Float,Float>{val nearest=floor(exactRail+.5).toInt().coerceIn(0,2);val offset=(exactRail-nearest).toFloat();val engagement=smooth(abs(rail-origin)/.12f);val focus=1-smooth(abs(offset)/.5f);return Triple(nearest,(.5f+offset*.72f+((rail-origin)*.15f).coerceIn(-.15f,.15f)).coerceIn(.12f,.88f)*engagement,.34f*engagement*focus)}
        if(moving>=0){val light=under(moving);material=light.first;phase=light.second;power=light.third}
        else if(age>=0){val carry=under(pulseOrigin);val q=clamp(age/duration);val handoff=smooth(age/.12f);val strength=smooth(q/.18f)*(1-smooth((q-.57f)/.43f));phase=carry.second*(1-handoff)+q*handoff;power=carry.third*(1-handoff)+strength*handoff}
        val content=contentEase((t-5.8f)/.09f)*(1-contentEase((t-7.51f)/.09f))
        val catchAge=t-5.8f;val catchStrength=if(catchAge<=0||catchAge>=.35f)0f else if(catchAge<=.09f)contentEase(catchAge/.09f)else 1-contentEase((catchAge-.09f)/.26f)
        val envelope=step(t,5.8f,6.1f)*(1-step(t,7.25f,7.6f))
        return Frame(rail,seconds,material,phase,power,panel,if(panel>0)content else 0f,catchStrength,sin((t-5.8f)*3.4f)*4.5f*envelope,sin((t-5.8f)*4.1f)*6*envelope,false,floor(exactRail+.5).toInt().coerceIn(0,2))
    }
    /** The source layout adapts at font/layout boundaries; font sizes are never compressed. */
    fun source(fontScale: Float): Source {
        val fs=fontScale;fun ceil4(n: Float)=ceil(n/4)*4
        var badge=164f;var group=16f
        fun build(): Source {
            val nameTop=186-min(28f,max(0f,32*fs-32));val handle=nameTop+32*fs+4
            val balanceTop=max(246f,handle+20*fs+4)+16;val stacked=73*fs+40>148
            val balanceHeight=max(56f,ceil4((if(stacked)32 else 0)+24*fs+16))
            val heading=balanceTop+balanceHeight+group;val statsHeight=max(44f,ceil4(18*fs+16));val statsWidth=max(118f,ceil4(58*fs+58));val headingHeight=max(44f,max(statsHeight,24*fs))
            val railTop=heading+headingHeight+8;val caption=railTop+badge+4;val captionHeight=max(56f,52*fs+4)
            val skills=caption+(captionHeight-52*fs)/2;val score=skills+20*fs;val divider=caption+captionHeight;val last=divider+17;val game=last+24*fs+12;val gameHeight=max(64f,max(44*fs,20*fs+24));val tabHeight=max(72f,ceil4(36+16*fs+16));val tabs=818-tabHeight
            return Source(fs,190-(186-nameTop),nameTop,32*fs,handle,20*fs,balanceTop,balanceHeight,stacked,heading+(headingHeight-24*fs)/2,Bounds(369-statsWidth,heading+(headingHeight-statsHeight)/2,statsWidth,statsHeight),railTop,badge,skills,20*fs,score,32*fs,caption+(captionHeight-44)/2,divider,last,game,gameHeight,tabs,tabHeight)
        }
        var s=build();var overflow=max(0f,s.gameTop+s.gameHeight+16-s.tabsTop)
        if(overflow>0){badge-=min(68f,ceil4(overflow));s=build();overflow=max(0f,s.gameTop+s.gameHeight+16-s.tabsTop)}
        if(overflow>0){group=8f;s=build()}
        return s
    }
}
