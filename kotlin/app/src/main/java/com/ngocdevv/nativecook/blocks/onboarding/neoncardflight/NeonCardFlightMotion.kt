package com.ngocdevv.nativecook.blocks.onboarding.neoncardflight

import android.graphics.PointF
import kotlin.math.*

/** Measured source-frame geometry copied from the Swift reference. */
internal object NeonCardFlightGeometry {
    data class Piece(val name: String, val x: Double, val y: Double, val w: Double, val h: Double, val roll: Double, val dx: Double, val dy: Double, val turn: Double, val start: Double, val end: Double, val foreground: Boolean)
    data class Card(val points: List<PointF>, val visible: Boolean = true, val behindForeground: Boolean = false, val trailX: Double = 0.0, val trailY: Double = 0.0, val intro: Boolean = false)
    data class Pose(val x: Double, val y: Double, val scale: Double, val roll: Double, val alpha: Double, val a: Double, val b: Double, val c: Double, val d: Double, val tx: Double, val ty: Double)
    val pieces = listOf(
        Piece("orange",286.0,520.0,150.0,160.0,-5.0,-5.0,-30.0,30.0,2.267,3.12,false),
        Piece("cap",253.0,331.0,120.0,110.0,-3.0,42.0,8.0,19.0,2.267,3.1,false),
        Piece("coin",355.0,383.0,52.0,52.0,-25.0,0.0,-5.0,-80.0,2.3,3.12,false),
        Piece("coin",341.0,413.0,30.0,30.0,-15.0,0.0,-5.0,70.0,2.35,3.18,false),
        Piece("cup",134.0,370.0,174.0,180.0,-6.0,0.0,-10.0,25.0,2.233,2.95,false),
        Piece("star",51.0,396.0,110.0,110.0,-8.0,-8.0,4.0,-48.0,3.017,3.2,false),
        Piece("links",217.0,480.0,230.0,310.0,0.0,-12.0,34.0,-8.0,2.25,3.2,true),
        Piece("headphones",92.0,520.0,140.0,150.0,-20.0,-22.0,12.0,32.0,2.267,3.12,true),
        Piece("locks",295.0,535.0,125.0,155.0,0.0,0.0,-30.0,-28.0,2.267,3.18,true),
        Piece("shield",179.0,577.0,140.0,155.0,-3.0,5.0,-20.0,32.0,2.3,3.18,true),
    )
    val rush = arrayOf(
        doubleArrayOf(0.0,110.0,414.0,284.0,414.0,374.0,566.0,49.0,578.0),
        doubleArrayOf(0.1667,113.0,411.0,301.0,414.0,423.0,632.0,32.0,619.0),
        doubleArrayOf(0.3333,105.0,414.0,314.0,414.0,451.0,667.0,20.0,667.0),
        doubleArrayOf(0.5,108.0,414.0,327.0,414.0,475.0,692.0,6.0,690.0),
        doubleArrayOf(0.6667,102.0,420.0,333.0,414.0,500.0,718.0,-22.0,710.0),
        doubleArrayOf(0.8333,102.0,417.0,350.0,414.0,535.0,745.0,-40.0,731.0),
        doubleArrayOf(1.0,93.0,417.0,338.0,414.0,575.0,783.0,-55.0,772.0),
        doubleArrayOf(1.1667,84.0,417.0,334.0,414.0,625.0,840.0,-70.0,833.0),
        doubleArrayOf(1.3333,70.0,417.0,340.0,414.0,658.0,882.0,-88.0,880.0),
        doubleArrayOf(1.5,47.0,414.0,350.0,415.0,690.0,920.0,-110.0,915.0),
        doubleArrayOf(1.6667,25.0,409.0,357.0,409.0,725.0,960.0,-135.0,955.0),
        doubleArrayOf(1.8833,2.0,391.0,339.0,391.0,765.0,1015.0,-180.0,1015.0)
    )
    val left = arrayOf(
        doubleArrayOf(1.9,359.0,376.0,497.0,413.0,419.0,564.0,282.0,509.0),
        doubleArrayOf(1.9167,359.0,376.0,497.0,413.0,419.0,564.0,282.0,509.0),
        doubleArrayOf(1.9333,359.0,376.0,497.0,413.0,419.0,564.0,282.0,509.0),
        doubleArrayOf(1.95,285.0,347.0,423.0,394.0,354.0,544.0,218.0,480.0),
        doubleArrayOf(1.9833,246.0,338.0,383.0,373.0,325.0,524.0,189.0,477.0),
        doubleArrayOf(2.0167,244.0,338.0,380.0,374.0,320.0,524.0,189.0,477.0),
        doubleArrayOf(2.05,178.0,327.0,298.0,350.0,242.0,498.0,131.0,461.0),
        doubleArrayOf(2.0833,99.0,312.0,194.0,328.0,150.0,465.0,52.0,439.0),
        doubleArrayOf(2.1167,17.0,300.0,89.0,309.0,42.0,439.0,-24.0,429.0),
        doubleArrayOf(2.167,-180.0,280.0,-58.0,290.0,-102.0,421.0,-218.0,410.0)
    )
    val right = arrayOf(
        doubleArrayOf(2.266666667,-100.0,312.0,39.0,389.0,-70.0,459.0,-209.0,382.0),
        doubleArrayOf(2.283333333,-100.0,312.0,39.0,389.0,-70.0,459.0,-209.0,382.0),
        doubleArrayOf(2.3,-70.0,320.0,66.0,397.0,-20.0,499.0,-156.0,422.0),
        doubleArrayOf(2.3333,-70.0,320.0,66.0,397.0,-20.0,499.0,-156.0,422.0),
        doubleArrayOf(2.4167,26.0,376.0,127.0,420.0,83.0,508.0,-20.0,464.0),
        doubleArrayOf(2.5,112.0,411.0,204.0,443.0,169.0,536.0,77.0,504.0),
        doubleArrayOf(2.5833,223.0,435.0,322.0,447.0,310.0,552.0,210.0,537.0),
        doubleArrayOf(2.6667,272.0,444.0,369.0,449.0,366.0,544.0,273.0,540.0),
        doubleArrayOf(2.75,296.0,449.0,421.0,446.0,424.0,544.0,300.0,546.0),
        doubleArrayOf(2.867,435.0,449.0,563.0,446.0,566.0,544.0,439.0,546.0)
    )
    val returning = arrayOf(
        doubleArrayOf(3.45,455.0,444.0,190.0,126.0,8.0,12.0),
        doubleArrayOf(3.5,430.0,444.0,190.0,126.0,7.0,10.0),
        doubleArrayOf(3.6,393.0,444.0,190.0,125.0,6.0,8.0),
        doubleArrayOf(3.7,365.0,444.0,190.0,123.0,5.0,6.0),
        doubleArrayOf(3.8,333.0,444.0,190.0,121.0,4.0,4.0),
        doubleArrayOf(3.9,294.0,444.0,190.0,120.0,3.0,2.0),
        doubleArrayOf(4.0,277.0,443.0,190.0,119.0,2.6,1.0),
        doubleArrayOf(4.1,257.0,442.0,188.0,118.0,2.0,0.0),
        doubleArrayOf(4.2,248.0,442.0,187.0,117.0,1.8,-1.0),
        doubleArrayOf(4.3,230.0,442.0,184.0,116.0,1.4,-2.0),
        doubleArrayOf(4.4,223.0,442.0,181.0,115.0,1.0,-3.0),
        doubleArrayOf(4.5,215.0,442.0,178.0,114.0,0.8,-4.0),
        doubleArrayOf(4.6,211.0,443.0,176.0,113.0,0.6,-4.0),
        doubleArrayOf(4.7,207.0,444.0,175.0,112.0,0.5,-4.0),
        doubleArrayOf(4.9,207.0,444.0,175.0,113.0,0.5,-4.0),
        doubleArrayOf(5.1,207.0,445.0,176.0,114.0,0.5,-4.0),
        doubleArrayOf(5.3,208.0,447.0,182.0,117.0,0.5,-4.0),
        doubleArrayOf(5.5,208.0,449.0,189.0,121.0,0.5,-4.0),
        doubleArrayOf(5.583,208.0,449.0,189.0,121.0,0.5,-4.0)
    )
    val accent = arrayOf(
        doubleArrayOf(2.4,-20.0,620.0,-6.0),
        doubleArrayOf(2.8,105.0,615.0,-12.0),
        doubleArrayOf(3.1,180.0,600.0,-20.0),
        doubleArrayOf(3.45,270.0,540.0,-30.0),
        doubleArrayOf(3.7,330.0,472.0,-45.0),
        doubleArrayOf(4.1,354.0,400.0,-52.0),
        doubleArrayOf(4.7,377.0,340.0,-40.0),
        doubleArrayOf(5.4,385.0,340.0,-35.0)
    )

    fun seg(t: Double,a: Double,b: Double)=((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun ease(p: Double)=p*p*(3-2*p)
    fun out(p: Double)=1-(1-p).pow(3)
    fun track(t: Double,keys: Array<DoubleArray>,column: Int): Double {
        if(t<=keys.first()[0])return keys.first()[column]
        if(t>=keys.last()[0])return keys.last()[column]
        var i=0;while(i+1<keys.size&&keys[i+1][0]<t)i++
        val a=keys[i];val b=keys[i+1];val left=keys[max(0,i-1)];val right=keys[min(keys.size-1,i+2)]
        val dt=b[0]-a[0];val u=(t-a[0])/dt;val m0=(b[column]-left[column])/(b[0]-left[0])*dt;val m1=(right[column]-a[column])/(right[0]-a[0])*dt
        return (2*u*u*u-3*u*u+1)*a[column]+(u*u*u-2*u*u+u)*m0+(-2*u*u*u+3*u*u)*b[column]+(u*u*u-u*u)*m1
    }
    fun points(s: Double,keys: Array<DoubleArray>)=(0..3).map{PointF(track(s,keys,it*2+1).toFloat(),track(s,keys,it*2+2).toFloat())}
    fun quad(cx: Double,cy: Double,width: Double,height: Double,roll: Double,yaw: Double): List<PointF> {
        val a=roll*PI/180;val b=yaw*PI/180
        return listOf(-0.5 to -0.5,0.5 to -0.5,0.5 to 0.5,-0.5 to 0.5).map{(x,y)->
            val lx=x*width;val perspective=620/(620+lx*sin(b));val px=lx*cos(b)*perspective;val py=y*height*perspective
            PointF((cx+px*cos(a)-py*sin(a)).toFloat(),(cy+px*sin(a)+py*cos(a)).toFloat())
        }
    }
    fun card(t: Double): Card {
        val s=max(t-0.65,0.0)
        if(s<1.9-1e-9)return Card(points(s,rush),intro=true)
        val keys: Array<DoubleArray>;val behind: Boolean
        if(s<=2.167){keys=left;behind=false}
        else if(s>=2.266666667-1e-9&&s<2.867-1e-9){keys=right;behind=true}
        else if(s<3.45-1e-9)return Card(emptyList(),false)
        else return Card(quad(track(s,returning,1),track(s,returning,2),track(s,returning,3),track(s,returning,4),track(s,returning,5),track(s,returning,6)))
        val p=points(s,keys);val previous=points(max(keys.first()[0],s-0.006),keys)
        var dx=0.0;var dy=0.0
        for(i in 0..3){dx+=(p[i].x-previous[i].x)/4;dy+=(p[i].y-previous[i].y)/4}
        val length=hypot(dx,dy);if(length>5){dx*=5/length;dy*=5/length}
        return Card(p,true,behind,dx,dy)
    }
    fun pose(piece: Piece,t: Double): Pose {
        val s=max(t-0.65,0.0);val p=out(seg(s,piece.start,piece.end))
        var v=ease(seg(s,2.42,3.18));var sx=1.0;var sy=1.0;var shear=0.0;var axis=0.0;var pivotY=0.0
        when(piece.name){
            "links"->{v=ease(seg(s,2.45,3.15));sx=0.28+0.72*v;sy=1.14-0.14*v;axis=-55.0;pivotY=-piece.h*0.18;shear=-0.12*(1-v)}
            "headphones"->{sx=0.4+0.6*v;sy=0.85+0.15*v;shear=0.18*(1-v)}
            "locks"->{sx=1.15-0.15*v;sy=0.22+0.78*v;shear=0.35*(1-v);pivotY=-piece.h*0.1}
            "shield"->{sx=0.26+0.74*v;sy=1.06-0.06*v;shear=0.22*(1-v)}
            "cup"->{sx=0.88+0.12*v;sy=0.8+0.2*v;shear=-0.2*(1-v);pivotY=-piece.h*0.1}
            "cap"->{sy=0.28+0.72*v;shear=0.25*(1-v)}
            "star"->{v=ease(seg(s,3.05,3.18));sx=0.14+0.86*v;shear=0.1*(1-v)}
            "orange"->{sy=0.65+0.35*v;shear=-0.1*(1-v)}
            "coin"->{val angle=if(s>3.18)(s-3.18)*2*PI/1.45+piece.y/35 else 1.4*(1-v);sx=0.2+0.8*abs(cos(angle))}
        }
        val angle=axis*PI/180;val co=cos(angle);val si=sin(angle)
        val a=sx*co*co+sy*si*si;val b=(sx-sy)*si*co;val c=b+shear;val d=sx*si*si+sy*co*co
        val life=if(s>3.2)sin((s-3.2)*2*PI/2.4+piece.x/80) else 0.0
        val drift=if(piece.name=="cup")track(s,arrayOf(doubleArrayOf(3.2,0.0),doubleArrayOf(4.7,15.0),doubleArrayOf(4.9,15.0),doubleArrayOf(5.5,6.0)),1)*p else 0.0
        return Pose(piece.x+piece.dx*(1-p),piece.y+piece.dy*(1-p)+drift,0.001+0.999*p.pow(1.55),piece.roll+piece.turn*(1-p)+life*0.6,if(p>0)1.0 else 0.0,a,b,c,d,-c*pivotY,(1-d)*pivotY)
    }
}
