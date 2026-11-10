package com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal

import kotlin.math.*

internal object PasskeyGlassSealPrint {
    data class Pt(val x:Double,val y:Double)
    data class Ridge(val pts:List<Pt>,val k:Int)
    private fun rand(n:Double):Double{val s=sin(n*91.3458)*47453.5453;return s-floor(s)}
    val ridges:List<Ridge> by lazy {
        val out=mutableListOf<Ridge>()
        for(k in 0..7){
            val a=5.5+6.9*k;val b=7+7.5*k;val cy=-2.0
            val legL=54-cy-(if(k>5)(k-5)*14 else 0)-3*sin(k*1.7)
            val pts=mutableListOf<Pt>()
            for(i in 0..24){val y=cy+legL*(1-i/24.0);val d=y-cy;pts.add(Pt(-(a+0.05*d-0.0016*d*d)+0.0012*d*d,y))}
            for(i in 1..59){val th=PI*(1-i/60.0);pts.add(Pt(a*cos(th)*(1+0.04*sin(th*2)),cy-b*sin(th)))}
            val legR=legL*(0.82+0.12*rand(k+3.0))
            for(i in 0..24){val y=cy+legR*i/24;val d=y-cy;pts.add(Pt(a+0.05*d-0.0016*d*d+0.0012*d*d,y))}
            val pieces=mutableListOf<List<Pt>>(pts)
            repeat(if(k<2)0 else if(k<5)1 else 2){c->
                val longest=pieces.maxBy{it.size};val at=floor(longest.size*(0.25+0.5*rand(k*7.0+c))).toInt()
                val index=pieces.indexOf(longest);pieces.removeAt(index)
                pieces.add(index,longest.drop(at+3));pieces.add(index,longest.take(at))
            }
            pieces.filter{it.size>2}.forEach{out.add(Ridge(it,k))}
        }
        out.add(0,Ridge(List(16){Pt(0.0,-2+it*1.6)},0));out
    }
    private fun keyLines():List<List<Pt>>{
        fun ring(r:Double)=List(81){val t=it/80.0*PI*2;Pt(-50+r*cos(t),r*sin(t))}
        val teeth=listOf(Pt(66.0,6.5),Pt(62.0,6.5),Pt(62.0,16.0),Pt(54.0,16.0),Pt(54.0,6.5),Pt(48.0,6.5),Pt(48.0,13.0),Pt(40.0,13.0),Pt(40.0,6.5),Pt(34.0,6.5),Pt(34.0,18.0),Pt(27.0,18.0),Pt(27.0,6.5),Pt(-26.0,6.5))
        val under=mutableListOf<Pt>()
        for(i in 0 until teeth.size-1){val a=teeth[i];val b=teeth[i+1];val n=max(2,floor(hypot(b.x-a.x,b.y-a.y)/2+0.5).toInt());repeat(n){j->under.add(Pt(a.x+(b.x-a.x)*j/n,a.y+(b.y-a.y)*j/n))}}
        return listOf(ring(24.0),ring(11.0),List(47){Pt(-26+it*2.0,-6.5)},List(8){Pt(66.0,-6.5+13*it/7.0)},under)
    }
    private fun sample(lines:List<List<Pt>>):Array<FloatArray>{
        data class Segment(val a:Pt,val b:Pt,val len:Double,val line:Int)
        val segs=lines.flatMapIndexed{li,pts->pts.zipWithNext{a,b->Segment(a,b,hypot(b.x-a.x,b.y-a.y),li)}}
        val total=segs.sumOf{it.len};var si=0;var acc=0.0
        return Array(6144){i->
            val s=(i+0.5)/6144*total
            while(si<segs.size-1&&acc+segs[si].len<s){acc+=segs[si].len;si++}
            val g=segs[si];val f=if(g.len>0)(s-acc)/g.len else 0.0
            floatArrayOf((g.a.x+(g.b.x-g.a.x)*f).toFloat(),(g.a.y+(g.b.y-g.a.y)*f).toFloat(),g.line.toFloat())
        }
    }
    // Every sixth source particle; the same arc-length positions and deterministic seeds.
    val points:Array<DoubleArray> by lazy {
        val a=sample(ridges.map{it.pts});val b=sample(keyLines())
        Array(1024){j->val i=j*6;doubleArrayOf(a[i][0].toDouble(),a[i][1].toDouble(),b[i][0].toDouble(),b[i][1].toDouble(),ridges[a[i][2].toInt()].k/7.0,min(1.0,hypot(a[i][0].toDouble(),a[i][1].toDouble()+2)/62),rand(i+11.0))}
    }
}
