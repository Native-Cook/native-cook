package com.ngocdevv.nativecook.blocks.cards.popupquestcard

import kotlin.math.*

internal class PopUpQuestCardDriver {
    var pose = PopUpQuestCardMotion.Pose()
    var active = 0
    var now = 0.0
    private var lastTouch = -100.0
    private var v = 0.0
    private var yawV = 0.0
    private var target = 0.0
    private var loopT = 0.0
    private var scripted = false
    var drag = false
    private var horizontal = false
    private var o0 = 0.0
    val settled get() = !drag && abs(pose.o-target)<0.00001 && abs(v)<0.0001 && abs(pose.yaw)<0.00001 && abs(yawV)<0.0001
    fun step(wall: Double, auto: Boolean, reduced: Boolean) {
        now += wall; val dt = min(wall,0.05)
        if(auto && !reduced && !drag && now-lastTouch>3.5 && (scripted || (pose.o<0.001 && target==0.0))) {
            if(!scripted) { scripted=true; loopT=7.0 }
            loopT=(loopT+wall)%PopUpQuestCardMotion.LOOP; pose=PopUpQuestCardMotion.at(loopT)
            if(active!=0 && pose.o==0.0)active=0
            v=0.0; yawV=0.0; target=if(pose.o>0.5)1.0 else 0.0
        } else {
            scripted=false
            if(!drag) {
                val k=if(reduced)500.0 else 120.0; val c=2*0.86*sqrt(k)
                v+=(k*(target-pose.o)-c*v)*dt
                var o=pose.o+v*dt
                if(o<0) { o=0.0; v=0.0 }
                yawV+=(-160*pose.yaw-18*yawV)*dt
                pose=PopUpQuestCardMotion.Pose(o,pose.yaw+yawV*dt)
            }
        }
    }
    fun touched() { scripted=false; lastTouch=now }
    fun open(i: Int) { if(pose.o>0.02 && active!=i)return; active=i; target=1.0; touched() }
    fun close() { target=0.0; touched() }
    fun begin(x: Double,y: Double) { drag=true; horizontal=abs(x)>abs(y); o0=pose.o; v=0.0; touched() }
    fun move(x: Double,y: Double,heroH: Double) {
        pose=if(horizontal)pose.copy(yaw=(x*0.0022).coerceIn(-0.45,0.45)) else pose.copy(o=PopUpQuestCardMotion.clamp(o0-max(0.0,y)/(heroH*0.9)))
        lastTouch=now
    }
    fun end(vx: Double,vy: Double,heroH: Double): Boolean {
        if(!drag)return false; drag=false
        var closing=false
        if(horizontal)yawV=vx*0.0015 else { closing=pose.o<0.75 || vy>700; target=if(closing)0.0 else 1.0; v=-vy/(heroH*0.9) }
        lastTouch=now; return closing
    }
}
