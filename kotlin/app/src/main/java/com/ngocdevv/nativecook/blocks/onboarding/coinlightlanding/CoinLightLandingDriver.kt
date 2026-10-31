package com.ngocdevv.nativecook.blocks.onboarding.coinlightlanding

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.coinlightlanding.CoinLightLandingMotion as M

internal class CoinLightLandingDriver {
    var f=M.Frame()
    var now=0.016; var t=0.0; var last=-10.0
    var vx=0.0; var vy=0.0; var vyaw=0.0
    var held=false; var mode=0; var at=0.0; var gx=0.0; var gy=0.0
    var payAt=-10.0; var payFrom=M.BASE
    fun step(elapsed: Double,w: Double,h: Double,top: Double,auto: Boolean,reduced: Boolean): M.Frame {
        now+=elapsed; val dt=min(elapsed,0.05)
        if(f.x==0.0 && f.y==0.0) { f.x=w/2; f.y=h*0.74 }
        if(auto && !reduced && !held && mode==0 && now-last>=2) {
            t=(t+elapsed)%M.LOOP; f=M.at(t,w,h,top)
        } else {
            val rx=w/2; val ry=h*0.74
            if(mode==1) {
                val k=M.easeOut((now-at)/0.3)
                f.x=M.mix(f.x,104.0,0.35); f.y=M.mix(f.y,top+354,0.35); f.s=M.mix(1.0,0.3,k); f.a=1-k
                if(now-at>1.3) { mode=2; at=now; f.x=rx; f.y=h+140; f.a=1.0; f.s=1.0 }
            } else if(mode==2) {
                f.y=M.mix(h+140,ry,M.easeOutBack((now-at)/0.8)); if(now-at>=0.8) mode=0
            } else if(!held) {
                val k=if(reduced)300.0 else 90.0; val z=2*sqrt(k)*0.62
                vx+=(k*(rx-f.x)-z*vx)*dt; vy+=(k*(ry-f.y)-z*vy)*dt; f.x+=vx*dt; f.y+=vy*dt
            }
            vyaw*=exp(-dt*1.6); f.yaw+=vyaw*dt
            if(abs(vyaw)<0.6) { val target=round(f.yaw/(2*PI))*2*PI; f.yaw+=(target-f.yaw)*(1-exp(-dt*4)) }
            f.tx+=(vx.times(0.0012).coerceIn(-0.5,0.5)-f.tx)*(1-exp(-dt*10))
            f.pay=if(payAt>0) M.mix(payFrom,payFrom+48,M.easeInOut(now-payAt-0.1)) else f.pay
            f.button=if(payAt>0) exp(-max(0.0,now-payAt-0.1)*1.6) else 0.0; f.t=t
        }
        return f.copy()
    }
    fun begin(x: Double,y: Double): Boolean {
        last=now; t=0.0
        if(mode!=0 || hypot(x-f.x,y-f.y)>100) return false
        held=true; gx=f.x-x; gy=f.y-y; return true
    }
    fun move(x: Double,y: Double) {
        if(!held)return
        last=now; val nx=x+gx; val ny=y+gy
        vx=vx*0.5+(nx-f.x)*30; f.x=nx; f.y=ny
    }
    fun drop() { mode=1; at=now; payFrom=f.pay; payAt=now }
    fun become(): Boolean { last=now; t=0.0; if(mode!=0)return false; drop(); return true }
    fun end(vx: Double,vy: Double,top: Double,reduced: Boolean): String? {
        if(!held)return null
        last=now; held=false
        if(abs(f.x-104)<92 && abs(f.y-(top+354))<50) { drop(); return "snap" }
        this.vx=vx; this.vy=vy
        if(!reduced && abs(vx)>500) { vyaw=vx*0.012; return "spin" }
        return null
    }
    fun moving(auto: Boolean,reduced: Boolean) = (auto && !reduced) || held || mode!=0 || now-last<2.1 || abs(vx)+abs(vy)+abs(vyaw)>0.01 || f.button>0.0001
}
