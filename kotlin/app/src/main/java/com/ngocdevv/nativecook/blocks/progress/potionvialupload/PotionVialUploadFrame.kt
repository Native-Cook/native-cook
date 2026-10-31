package com.ngocdevv.nativecook.blocks.progress.potionvialupload

import kotlin.math.*

internal class PotionVialUploadFrame(t:Double,d:PotionVialUploadDriver,now:Double,g:PotionVialUploadGeo,still:Boolean) {
    private val m=PotionVialUploadMotion
    val t=t; val p=m.progress(t); val front=m.mix(18.6,313.0,p)
    val dp=(m.progress(t+0.05)-m.progress(t-0.05))*10; val bulge=5+min(dp*9,4.0)
    val glow=if(t>=2.73)0.12+0.88*m.smooth(m.seg(p,0.35,1.0)) else 0.0
    val liquidOn=t>=2.73; val flow=p*120
    var ghostX=106.0; var ghostY=g.cardY+34; var ghostScale=1.0; var ghostAlpha=0.0; var ghostRot=0.0; var slotEmpty=0.0; var hot=0.0; var mist=0.0
    val gemX=315.5+d.gem.x; val gemY=14+d.gem.y; val gemRot=d.gem.rot
    val gemVis=m.mix(-atan2(d.gem.x,d.gem.y)*0.9,-0.35,if(d.gem.corked) { if(d.gem.homeAt<0)1.0 else m.clamp((now-d.gem.homeAt)/d.gem.homeDur) } else 0.0)
    var spX=0.0; var spY=0.0; var spHead=1.75; var spWalk=0.0; var spPhase=0.0; val spIdle=t; var spHop=0.0
    var restA=if(still)0.0 else 1-m.smooth(m.seg(t,0.9,1.0))
    var readyA=if(still)0.0 else m.smooth(m.seg(t,0.9,1.0))*(1-m.smooth(m.seg(t,1.6,1.7)))
    val counterA=if(still)1.0 else m.smooth(m.seg(t,2.07,2.2)); val doneA=if(still)1.0 else m.smooth(m.seg(t,8.4,8.5))
    val cardState=if(t>=8.4)2 else if(t>=1.75)1 else 0; val cardDoneA=if(cardState==2)m.smooth(m.seg(t,8.4,8.7)) else 0.0
    val sinceHome=now-d.gem.lastHome-d.gem.homeDur
    val shake=if(sinceHome>=0 && sinceHome<0.4)sin(sinceHome*60)*exp(-sinceHome*11)*1.2 else 0.0
    val burst=if(t>=8.4 && t<9.8)t-8.4 else -1.0
    val cloud=if(t>=2.03 && t<3.13)m.seg(t,2.03,2.73) else -1.0
    val cloudA=1-m.smooth(m.seg(t,2.68,3.13))
    val fade=if(still)0.0 else if(t<0.35)1-m.smooth(m.seg(t,0.0,0.35)) else m.smooth(m.seg(t,11.15,11.6))
    init {
        val finger=d.mode==1 && !d.fileX.isNaN()
        if(finger) {
            ghostX=d.fileX; ghostY=d.fileY; ghostAlpha=1.0; slotEmpty=1.0
            val dist=hypot(ghostX-339,ghostY); hot=1-m.smooth((dist-88*0.55)/(88*0.6)); mist=if(!d.gem.corked)0.35+0.65*hot else 0.0; ghostRot=-0.12
        } else if(d.fileBack<1) {
            val k=m.back(d.fileBack); ghostX=m.mix(d.fileFromX,106.0,k); ghostY=m.mix(d.fileFromY,g.cardY+34,k); ghostAlpha=1.0; slotEmpty=1-m.smooth(m.seg(d.fileBack,0.75,1.0))
        } else if(t>=0.6 && t<1.89) {
            val k=m.inOut(m.seg(t,0.6,1.15)); val u=1-k; val cardY=g.cardY+34; val hover=m.seg(t,1.15,1.75)
            ghostX=u*u*106+2*u*k*372+k*k*357+sin(hover*PI)*6
            ghostY=u*u*cardY+2*u*k*(cardY-110)+k*k*(-50)-sin(hover*PI*2)*3
            ghostAlpha=1.0; ghostRot=-0.14*m.smooth(m.seg(t,0.6,0.9)); slotEmpty=m.smooth(m.seg(t,0.6,0.75))
            hot=m.smooth(m.seg(t,0.8,1.0)); mist=m.smooth(m.seg(t,1.0,1.2))
            if(t>=1.75) {
                val drop=m.inside(m.seg(t,1.75,1.88)); val sx=if(!d.dropX.isNaN())d.dropX else ghostX; val sy=if(!d.dropY.isNaN())d.dropY else ghostY
                ghostX=m.mix(sx,347.0,drop); ghostY=m.mix(sy,-4.0,drop); ghostScale=m.mix(1.0,0.45,drop); ghostAlpha=1-m.smooth(m.seg(t,1.82,1.88))
            }
        }
        if(!finger && !d.dropX.isNaN() && t>=1.75 && t<1.89) {
            val k=m.inside(m.seg(t,1.75,1.88)); ghostX=m.mix(d.dropX,347.0,k); ghostY=m.mix(d.dropY,-4.0,k); ghostScale=m.mix(1.0,0.45,k); ghostAlpha=1-m.smooth(m.seg(t,1.82,1.88)); ghostRot=-0.12; slotEmpty=1.0
        }
        if(!finger)mist*=1-m.smooth(m.seg(t,1.88,1.97))
        if(t>=8.5 && t<9.7)mist=max(mist,0.4*(1-m.smooth(m.seg(t,8.6,9.6))))
        if(t>=1.85 && !finger && d.fileBack>=1)slotEmpty=if(t<8.4)1.0 else 1-m.smooth(m.seg(t,8.4,8.7))
        var sp=m.spider(t); var sp2=m.spider(max(0.0,t-0.06)); var head=1.75; var walk=0.0
        if(t>=9.3 && !d.gem.corked) {
            val bx=gemX+sin(gemRot)*16; val by=gemY-cos(gemRot)*16; val links=m.links(315.5,14.0,bx,by,64.0); val k=m.inOut(m.seg(t,9.3,10.0)); val n=links.size
            if(t<10 && n>1) {
                val fi=k*(n-1); val a=links[floor(fi).toInt()]; val b=links[min(n-1,floor(fi).toInt()+1)]; val e=fi-floor(fi); val k0=m.smooth(m.seg(t,9.3,9.42))
                sp=PotionVialUploadMotion.Point(m.mix(296.0,m.mix(a.x,b.x,e),k0),m.mix(-18.0,m.mix(a.y,b.y,e),k0)); head=atan2(b.y-a.y,b.x-a.x); walk=1.0
            } else { sp=PotionVialUploadMotion.Point(bx-sin(gemRot)*4,by+cos(gemRot)*4); head=PI/2+gemRot }; sp2=sp
        } else {
            val vx=(sp.x-sp2.x)/0.06; val vy=(sp.y-sp2.y)/0.06; walk=m.smooth((hypot(vx,vy)-4)/22)
            if(t>=2.73 && t<7.6)head=0.05 else if(t>=7.6)head=m.mix(0.05,PI*0.75,m.smooth(m.seg(t,8.0,8.3)))
            var dh=atan2(vy,vx)-head; dh-=floor(dh/(PI*2)+0.5)*PI*2; head+=dh*walk
        }
        val ka=now-d.kickAt; val kick=if(ka>=0 && ka<1.2)sin(min(ka/0.35,1.0)*PI*0.5)*exp(-max(0.0,ka-0.35)*4) else 0.0
        spX=sp.x+kick*22*d.kickDir; spY=sp.y; spHead=head; spWalk=max(walk,if(kick>0.02 && ka<0.5)1.0 else 0.0); spPhase=(sp.x+sp.y*0.6)/9+kick*4.5
        spHop=if(ka>=0 && ka<0.4)sin(ka/0.4*PI) else 0.0
        if(finger)readyA=m.smooth((hot-0.4)/0.4)
        val dropA=if(still)0.0 else m.smooth(m.seg(t,1.6,1.7))*(1-m.smooth(m.seg(t,2.07,2.2)))
        restA=max(restA,dropA)
    }
}
