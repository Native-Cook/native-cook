package com.ngocdevv.nativecook.blocks.pickers.appiconatelier

import kotlin.math.*

internal class AppIconAtelierDriver(val auto:Boolean,val reduced:Boolean,val buzz:(Int)->Unit) {
    private val m=AppIconAtelierMotion
    var frame=AppIconAtelierMotion.Frame()
    var now=0.0
    private var t=0.0
    private var manual=!auto||reduced
    private var lastTouch=-100.0
    private var resume=false
    var dragging=false
    var holding=false
    private var frontFrom=1.45
    private var pending=-1
    private var updatedUntil=-100.0
    val updated get()=now<updatedUntil
    private data class Anim(val from:Double,val to:Double,val start:Double,val duration:Double,val curve:Int,val damping:Double=1.0,var v:Double=0.0,val omega:Double=0.0,val energy:Double=0.0,val done:(()->Unit)?=null)
    private val animations=mutableMapOf<String,Anim>()
    val moving get()=(auto&&!reduced)||animations.isNotEmpty()||holding||dragging||updated
    private fun value(key:String)=when(key){"front"->frame.front;"press"->frame.press;"peek"->frame.peek;"lean"->frame.lean;"bob"->frame.bob;"land"->frame.land;"finger"->frame.finger;"pop"->frame.pop;else->0.0}
    private fun set(key:String,v:Double){when(key){"front"->frame.front=v;"press"->frame.press=v;"peek"->frame.peek=v;"lean"->frame.lean=v;"bob"->frame.bob=v;"land"->frame.land=v;"finger"->frame.finger=v;"pop"->frame.pop=v}}
    private fun timing(key:String,to:Double,duration:Double,curve:Int=0,done:(()->Unit)?=null){animations[key]=Anim(value(key),to,now,duration,curve,done=done)}
    private fun spring(key:String,to:Double,duration:Double,z:Double,velocity:Double=0.0,done:(()->Unit)?=null){
        val x=value(key)-to
        var v=(animations[key]?.v?:0.0)+velocity
        if((to>value(key)&&v<0)||(to<value(key)&&v>0))v=0.0
        val settling=duration*1.5
        fun energy(k:Double):Double{val w=sqrt(k/4)*z;val e=exp(-w*settling);val xt=(x+(v+x*w)*settling)*e;val vt=-xt*w+(v+x*w)*e;val e0=.5*k*x*x+2*v*v;return if(e0==0.0)0.0 else (.5*k*xt*xt+2*vt*vt)/e0}
        var lo=2.22e-16;var hi=8000.0
        repeat(100){val mid=(lo+hi)/2;if(energy(mid)>6e-9)lo=mid else hi=mid}
        val k=(lo+hi)/2
        animations[key]=Anim(value(key),to,now,duration,3,z,v,sqrt(k/4),.5*k*x*x+2*velocity*velocity,done)
    }
    private fun bezier(x:Double,a:Double,b:Double,c:Double,d:Double):Double{
        fun at(t:Double,p:Double,q:Double)=3*(1-t)*(1-t)*t*p+3*(1-t)*t*t*q+t*t*t
        var lo=0.0;var hi=1.0
        repeat(24){val t=(lo+hi)/2;if(at(t,a,c)<x)lo=t else hi=t};return at((lo+hi)/2,b,d)
    }
    fun step(dt:Double):AppIconAtelierMotion.Frame {
        now+=dt
        if(auto&&!reduced&&!manual){t=(t+dt)%m.LOOP;val applied=frame.applied;frame=m.at(t);frame.applied=applied;return frame}
        val was=frame.peek>.96
        for(key in animations.keys.toList()){
            val a=animations[key]?:continue
            var done=false
            if(a.curve==3){
                val h=min(dt,.064);val x=value(key)-a.to;val w=a.omega;val z=a.damping;val e=exp(-z*w*h)
                val position:Double;val velocity:Double
                if(z<1){val wd=w*sqrt(1-z*z);val s=sin(wd*h);val c=cos(wd*h);val frag=e*(s*(a.v+z*w*x)/wd+x*c);position=a.to+frag;velocity=-z*w*frag+e*(c*(a.v+z*w*x)-wd*x*s)}
                else {val q=x+(a.v+w*x)*h;position=a.to+e*q;velocity=e*(-w*q+a.v+w*x)}
                set(key,position);a.v=velocity;done=a.energy==0.0||(2*w*w*(position-a.to).pow(2)+2*velocity*velocity)/a.energy<=6e-9
            }else{
                val p=m.clamp((now-a.start)/a.duration)
                val k=when(a.curve){1->bezier(p,.23,1.0,.32,1.0);2->bezier(p,.77,0.0,.175,1.0);4->p;else->if(p<.5)2*p*p else 1-(-2*p+2).pow(2)/2}
                set(key,a.from+(a.to-a.from)*k);done=p>=1
            }
            if(done){set(key,a.to);animations.remove(key);a.done?.invoke()}
        }
        if(!reduced)frame.phase=(frame.phase+m.PHASE*dt)%m.TAU
        if(!was&&frame.peek>.96){if(!reduced)timing("land",.07,.09,1){spring("land",0.0,.42,.5)};buzz(1)}
        if(auto&&!reduced&&!holding&&!dragging&&now-lastTouch>=2){
            if(!resume){resume=true;if(frame.peek>.001)spring("peek",0.0,.6,.8);if(frame.a!=0||pending>=0)goTo(0)}
            else {val k=m.progress(frame.front,frame.dir);if(frame.a==0&&(k<.0005||k>.9995)&&abs(frame.peek)<.002&&abs(frame.lean)<.003&&abs(frame.bob)<.001){resume=false;t=0.0;manual=false;animations.clear()}}
        }
        return frame
    }
    fun takeOver(){
        lastTouch=now;resume=false
        if(manual)return
        manual=true;timing("finger",0.0,.15);frame.fingerPress=0.0;timing("bob",0.0,.5,1);spring("lean",0.0,.5,.85);timing("press",0.0,.15);timing("land",0.0,.15)
        if(frame.peek>.001&&!holding)spring("peek",0.0,.6,.75)
        val k=m.progress(frame.front,frame.dir)
        if(k>.001&&k<.999){val d=frame.dir;timing("front",-d*m.END,.26+.5*(1-k),1){frame.a=frame.b;frame.front=d*m.END}}
    }
    private fun startSweep(i:Int){
        if(i==frame.a||i !in 0..4)return
        val d=if(i>frame.a)1.0 else -1.0;frame.dir=d;frame.b=i;frame.front=d*m.END
        timing("front",-d*m.END,if(reduced).22 else .9,if(reduced)4 else 2){frame.a=i;frame.front=d*m.END;buzz(0)}
    }
    private fun goTo(i:Int){
        val k=m.progress(frame.front,frame.dir)
        if(k>.001&&k<.999){pending=i;val d=frame.dir;timing("front",-d*m.END,.16,1){frame.a=frame.b;frame.front=d*m.END;val n=pending;pending=-1;if(n>=0)startSweep(n)}}else startSweep(i)
    }
    fun pick(i:Int){takeOver();goTo(i)}
    fun apply(){takeOver();val i=frame.shown;if(i==frame.applied)return;frame.applied=i;updatedUntil=now+1.8;if(!reduced)timing("pop",1.0,.12,1){spring("pop",0.0,.65,.45)};buzz(2)}
    fun down(){takeOver();if(!reduced)timing("press",1.0,.3,1)}
    fun hold(){holding=true;timing("press",0.0,.2);if(reduced)timing("peek",1.0,.2)else spring("peek",1.0,.65,.7)}
    fun startDrag(dx:Double,size:Double){dragging=true;val k=m.progress(frame.front,frame.dir);if(k<=.001||k>=.999){if(k>=.999)frame.a=frame.b;frame.dir=if(dx<0)1.0 else -1.0;frame.b=(frame.a+frame.dir.toInt()).let{if(it in 0..4)it else frame.a};frame.front=frame.dir*m.END};animations.remove("front");pending=-1;frontFrom=frame.front-dx/size}
    fun drag(dx:Double,vx:Double,size:Double){lastTouch=now;val start=frame.dir*m.END;var f=frontFrom+dx/size;if(frame.a==frame.b){val over=(start-f)*frame.dir;f=start-frame.dir*(if(over>0)over/(1+over/.35)else 0.0)};frame.front=f.coerceIn(-m.END,m.END);if(!reduced)frame.lean=frame.lean*.8+.2*(vx/(size*22)).coerceIn(-.22,.22)}
    fun up(vx:Double,size:Double,cancel:Boolean=false){
        lastTouch=now
        if(dragging&&!cancel){val d=frame.dir;val k=m.progress(frame.front,d);val v=vx/size;val along=-v*d;val commit=frame.b!=frame.a&&along> -1.2&&(k>.45||along>1.2);val target=if(commit)-d*m.END else d*m.END;val done={if(commit){frame.a=frame.b;frame.front=d*m.END;buzz(0)}};if(reduced)timing("front",target,.22,0,done)else spring("front",target,.62,.92,v,done)}
        dragging=false;timing("press",0.0,.16,1);spring("lean",0.0,.65,.6)
        if(holding){holding=false;if(reduced)timing("peek",0.0,.2)else spring("peek",0.0,.7,.72)}
    }
}
