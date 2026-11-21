package com.ngocdevv.nativecook.blocks.media.shapeshiftviewfinder
import kotlin.math.*

internal data class ShapeShiftViewfinderPoint(var x:Double,var y:Double)
internal object ShapeShiftViewfinderGeometry {
    val names=listOf("clover","heart","flower","cookie","squircle","circle")
    private val heart=doubleArrayOf(
  0.6704, 0.6864, 0.7255, 0.7687, 0.8061, 0.8391, 0.8685, 0.8946, 0.9175, 0.9377, 0.9555, 0.9708,
  0.9837, 0.9945, 1.0032, 1.0099, 1.0148, 1.0180, 1.0195, 1.0192, 1.0171, 1.0133, 1.0077, 1.0001,
  0.9906, 0.9790, 0.9651, 0.9489, 0.9301, 0.9085, 0.8839, 0.8561, 0.8254, 0.7920, 0.7561, 0.7185,
  0.6800, 0.6417, 0.6045, 0.5688, 0.5357, 0.5058, 0.4784, 0.4533, 0.4308, 0.4105, 0.3922, 0.3758,
  0.3610, 0.3477, 0.3359, 0.3254, 0.3157, 0.3069, 0.2990, 0.2917, 0.2849, 0.2787, 0.2734, 0.2687,
  0.2642, 0.2601, 0.2566, 0.2536, 0.2509, 0.2485, 0.2465, 0.2448, 0.2432, 0.2419, 0.2411, 0.2406,
  0.2404, 0.2405, 0.2409, 0.2417, 0.2427, 0.2439, 0.2457, 0.2480, 0.2504, 0.2531, 0.2563, 0.2599,
  0.2641, 0.2689, 0.2743, 0.2805, 0.2873, 0.2950, 0.3040, 0.3142, 0.3261, 0.3387, 0.3486, 0.3540,
  0.3556, 0.3540, 0.3487, 0.3392, 0.3271, 0.3152, 0.3047, 0.2956, 0.2877, 0.2807, 0.2746, 0.2693,
  0.2647, 0.2606, 0.2568, 0.2536, 0.2509, 0.2485, 0.2464, 0.2446, 0.2433, 0.2423, 0.2414, 0.2408,
  0.2406, 0.2409, 0.2416, 0.2424, 0.2435, 0.2452, 0.2471, 0.2491, 0.2514, 0.2540, 0.2572, 0.2608,
  0.2647, 0.2692, 0.2741, 0.2794, 0.2853, 0.2919, 0.2992, 0.3072, 0.3162, 0.3260, 0.3366, 0.3484,
  0.3616, 0.3764, 0.3928, 0.4111, 0.4313, 0.4537, 0.4788, 0.5062, 0.5363, 0.5696, 0.6052, 0.6424,
  0.6809, 0.7196, 0.7571, 0.7927, 0.8261, 0.8568, 0.8845, 0.9092, 0.9308, 0.9495, 0.9657, 0.9796,
  0.9913, 1.0007, 1.0081, 1.0138, 1.0175, 1.0193, 1.0196, 1.0183, 1.0151, 1.0100, 1.0032, 0.9945,
  0.9837, 0.9708, 0.9555, 0.9377, 0.9173, 0.8940, 0.8678, 0.8385, 0.8054, 0.7678, 0.7245, 0.6857,
)
    fun rest(shape:Int)=DoubleArray(432){if(it%6==shape)1.0 else 0.0}
    private val bands:Array<DoubleArray> by lazy {
        val n=192;val tau=2*PI;val ranges=arrayOf(0..0,1..1,2..3,4..7,8..15,16..96)
        val bank=ArrayList<DoubleArray>()
        for(shape in 0..5){
            val r=DoubleArray(n){j->val t=j.toDouble()/n*tau;val sq=.86/(abs(sin(t)).pow(5)+abs(cos(t)).pow(5)).pow(.2)
                when(shape){0->.74+.26*abs(cos(2*(t-PI/4))).pow(.55);1->heart[j];2->.8+.2*abs(cos(3*t)).pow(.6);3->sq*(1+.035*cos(12*t));4->sq;else->1.0}}
            val a=DoubleArray(97);val b=DoubleArray(97)
            for(k in 0..96){for(j in 0 until n){val p=tau*k*j/n;a[k]+=r[j]*cos(p);b[k]+=r[j]*sin(p)};val norm=if(k==0||k==96)1/192.0 else 2/192.0;a[k]*=norm;b[k]*=norm}
            for(range in ranges){val out=DoubleArray(n);for(k in range)for(j in 0 until n){val p=tau*k*j/n;out[j]+=a[k]*cos(p)+b[k]*sin(p)};bank+=out}
        };bank.toTypedArray()
    }
    private fun radiiOne(w:DoubleArray,offset:Int):DoubleArray {
        val out=DoubleArray(192);var deviation=0.0
        for(b in 0..5){var squares=0.0;for(s in 0..5){val weight=w[offset+b*6+s];squares+=weight*weight;if(abs(weight)<1e-5)continue;val bank=bands[s*6+b];for(j in 0..191)out[j]+=weight*bank[j]};deviation=max(deviation,abs(1-squares))}
        val strength=min(1.0,.625*deviation);if(strength<1e-8)return out
        return DoubleArray(192){j->out[j]+strength*((out[(j+191)%192]+2*out[j]+out[(j+1)%192])/4-out[j])}
    }
    fun radii(f:ShapeShiftViewfinderFrame):DoubleArray {
        var same=true
        loop@for(q in 1..11)for(i in 0..35)if(abs(f.w[q*36+i]-f.w[(q-1)*36+i])>2e-5){same=false;break@loop}
        val rq=Array(if(same)1 else 12){radiiOne(f.w,it*36)}
        return DoubleArray(192){j->val a=j/192.0*2*PI;val d=atan2(sin(a-f.tapA),cos(a-f.tapA));val q=abs(d)/PI*11;val i=min(q.toInt(),10);val k=q-i
            var r=if(same)rq[0][j] else rq[i][j]+(rq[i+1][j]-rq[i][j])*k
            if(f.dent>1e-4)r-=f.dent/(150*max(f.scale,.05))*exp(-d*d/(2*(26*PI/180).pow(2)))
            r}
    }
    private fun wall(p:ShapeShiftViewfinderPoint,h:Double):Pair<ShapeShiftViewfinderPoint,Double> {
        val dx=p.x-195;val dy=p.y-h/2;val ax=abs(dx)-(195-4-40);val ay=abs(dy)-(h/2-4-40)
        var nx=0.0;var ny=0.0;val sd:Double
        if(ax>0&&ay>0){val l=hypot(ax,ay);sd=l-40;nx=(if(dx<0)-1 else 1)*ax/l;ny=(if(dy<0)-1 else 1)*ay/l}
        else if(ax>ay){sd=ax-40;nx=if(dx<0)-1.0 else 1.0}else{sd=ay-40;ny=if(dy<0)-1.0 else 1.0}
        val soft=if(sd<=-10)sd else -10*exp(-(sd+10)/10)
        return ShapeShiftViewfinderPoint(p.x+nx*(soft-sd),p.y+ny*(soft-sd)) to sd
    }
    fun points(f:ShapeShiftViewfinderFrame,height:Double):Array<ShapeShiftViewfinderPoint> {
        val radii=radii(f);val angle=(f.rot+f.lean)*PI/180;val ca=cos(angle);val sa=sin(angle);val cx=195+f.x;val cy=height/2+f.y;val scale=f.scale
        val es=f.jx*f.dx+f.jy*f.dy;val stretch=if(abs(es)<.002)0.0 else es.coerceIn(-.16,.16);val dir=atan2(f.dx,-f.dy)
        val points=Array(192){j->val ph=j/192.0*2*PI+f.twist*PI/180;val a=ph+angle-dir;val r=radii[j]*150*f.iris*(1+stretch*cos(2*a)-.5*stretch*cos(a));val x=r*sin(ph);val y=-r*cos(ph);ShapeShiftViewfinderPoint(cx+scale*(x*ca-y*sa),cy+scale*(x*sa+y*ca))}
        var deepest=Double.NEGATIVE_INFINITY;var nx=0.0;var ny=0.0
        for(p in points){val(q,sd)=wall(p,height);if(sd>deepest){deepest=sd;val x=p.x-q.x;val y=p.y-q.y;val m=max(hypot(x,y),1e-10);nx=x/m;ny=y/m}}
        if(deepest>40)for(p in points){p.x-=nx*(deepest-40);p.y-=ny*(deepest-40)}
        var near=0;var hits=0;var pushX=0.0;var pushY=0.0
        val out=Array(192){j->val p=points[j];val(q,sd)=wall(p,height);if(sd> -10)near++;if(sd>0){hits++;pushX+=q.x-p.x;pushY+=q.y-p.y};q}
        if(near==0)return points
        var area0=0.0;var area1=0.0
        for(j in 0..191){val i=(j+1)%192;area0+=points[j].x*points[i].y-points[i].x*points[j].y;area1+=out[j].x*out[i].y-out[i].x*out[j].y}
        area0=abs(area0)/2;area1=abs(area1)/2;val loss=area0-area1;val pm=hypot(pushX,pushY)
        if(hits>0&&loss>.5&&pm>1e-6){val bulge=1+.6*loss/max(area0,1.0);val nx=pushX/pm;val ny=pushY/pm;val tx=-ny;val ty=nx
            for(j in 0..191){val u=(out[j].x-cx)*tx+(out[j].y-cy)*ty;val v=(out[j].x-cx)*nx+(out[j].y-cy)*ny;out[j]=wall(ShapeShiftViewfinderPoint(cx+tx*u*bulge+nx*v,cy+ty*u*bulge+ny*v),height).first}}
        return out
    }
}
