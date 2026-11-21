package com.ngocdevv.nativecook.blocks.charts.focusinhometiles
import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class FocusInHomeTilesRenderer(context:Context){
    private val m=FocusInHomeTilesMotion
    private val r=FocusInHomeTilesResources
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val regular=Typeface.createFromAsset(context.assets,"focusinhometiles/focusinhometiles_Figtree_Regular.ttf")
    private val medium=Typeface.createFromAsset(context.assets,"focusinhometiles/focusinhometiles_Figtree_Medium.ttf")
    private val grain=context.assets.open("focusinhometiles/focusinhometiles_paper_grain.png").use{BitmapFactory.decodeStream(it)}
    private val gpu=if(Build.VERSION.SDK_INT>=33)GPU(grain)else null
    private val textBlur=if(Build.VERSION.SDK_INT>=31)TextBlur()else null
    private data class Run(val paths:List<Path>,val xs:List<Double>,val width:Double)
    private val runs=mutableMapOf<String,Run>()
    private fun run(text:String,size:Double,bold:Boolean,track:Double):Run=runs.getOrPut("$text/$size/$bold/$track"){
        paint.typeface=if(bold)medium else regular;paint.textSize=size.toFloat();val ws=FloatArray(text.length);paint.getTextRunAdvances(text.toCharArray(),0,text.length,0,text.length,false,ws,0);var pen=0.0;val xs=ws.map{val x=pen;pen+=it+track*size;x};val paths=text.indices.map{i->Path().also{paint.getTextPath(text,i,i+1,0f,0f,it)}};Run(paths,xs,pen)
    }
    private fun rgb(h:Int)=doubleArrayOf((h shr 16 and 255)/255.0,(h shr 8 and 255)/255.0,(h and 255)/255.0)
    private fun color(h:Int,alpha:Double=1.0)=(h and 0xFFFFFF)or((alpha.coerceIn(0.0,1.0)*255).roundToInt() shl 24)
    private fun color(v:DoubleArray)=Color.rgb((v[0]*255).roundToInt().coerceIn(0,255),(v[1]*255).roundToInt().coerceIn(0,255),(v[2]*255).roundToInt().coerceIn(0,255))
    private fun text(c:Canvas,value:String,x:Double,base:Double,size:Double,bold:Boolean,tint:Int,track:Double=0.0,width:Double=0.0){val run=run(value,size,bold,track);val xx=x+if(width>0)(width-run.width)/2 else 0.0;paint.shader=null;paint.color=tint;paint.style=Paint.Style.FILL;for(i in run.paths.indices){val save=c.save();c.translate((xx+run.xs[i]).toFloat(),base.toFloat());c.drawPath(run.paths[i],paint);c.restoreToCount(save)}}
    fun draw(c:Canvas,f:FocusInHomeTilesMotion.Frame,height:Double,pixels:Double,time:Double){
        val rects=mutableListOf<Double>();val pars=mutableListOf<Double>();val cols=mutableListOf<Double>()
        for(shape in r.shapes){val g=shape.group;val q=m.groupGeo(g.toDouble(),f.s,f.fp);val focus=m.groupFocus(g.toDouble(),f);var y=shape.rect[1];var h=shape.rect[3];if(shape.bar>=0){h=m.barHeight(shape.bar.toDouble(),f.grow[shape.bar]);y=697-h}
            val x=q.cx+(shape.rect[0]-q.cx)*q.scale;val yy=q.cy+(y-q.cy)*q.scale+q.dy;val w=shape.rect[2]*q.scale;val hh=h*q.scale;val visible=yy+hh > -60&&yy<height+60
            rects.addAll(listOf(x,yy,w,hh));pars.addAll(listOf(shape.radius*q.scale,focus.r,if(visible)focus.a else 0.0,shape.kind));cols.addAll(rgb(shape.color).toList()+if(m.GROUPS[g].plane<f.fp)-1.0 else 1.0)
        }
        if(Build.VERSION.SDK_INT>=33&&gpu!=null)gpu.scene(c,height,pixels,rects,pars,cols,paint)else fallbackScene(c,height,rects,pars,cols)
        for(g in 0..7){val group=m.GROUPS[g];val q=m.groupGeo(g.toDouble(),f.s,f.fp);val focus=m.groupFocus(g.toDouble(),f);val top=group.rect[1]+q.dy;if(top>940||top+group.rect[3] < -60)continue
            val save=c.saveLayerAlpha(null,(focus.a*(if(g==7).6+.4*f.alert else 1.0)*255).roundToInt().coerceIn(0,255));c.translate(q.cx.toFloat(),(q.cy+q.dy).toFloat());c.scale(q.scale.toFloat(),q.scale.toFloat());c.translate(-q.cx.toFloat(),-q.cy.toFloat())
            val picture=Picture();val recording=picture.beginRecording(402,780);recording.clipRect((group.rect[0]-44).toFloat(),(group.rect[1]-44).toFloat(),(group.rect[0]+group.rect[2]+44).toFloat(),(group.rect[1]+group.rect[3]+44).toFloat());contents(recording,g,f);picture.endRecording()
            if(Build.VERSION.SDK_INT>=31&&c.isHardwareAccelerated&&focus.r>.02)textBlur?.draw(c,g,picture,focus.r*.5)else c.drawPicture(picture);c.restoreToCount(save)
        }
        if(Build.VERSION.SDK_INT>=33&&gpu!=null){gpu.front(c,f,height,time,paint);gpu.orange(c,f,pixels,time,paint)}else fallbackOrange(c,f)
        if(f.glyph>.001){val save=c.save();c.translate((f.ox-9).toFloat(),(f.oy+f.oh/2-32).toFloat());c.scale(.75f,.75f);stroke(c,trim(r.paths.getValue("power"),f.glyph),color(0x1E2D23),2.4);c.restoreToCount(save)}
    }
    private fun stroke(c:Canvas,path:Path,tint:Int,width:Double){paint.shader=null;paint.color=tint;paint.style=Paint.Style.STROKE;paint.strokeWidth=width.toFloat();paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;c.drawPath(path,paint);paint.style=Paint.Style.FILL}
    private fun icon(c:Canvas,name:String,x:Double,y:Double,size:Double,tint:Int,width:Double=2.0){val save=c.save();c.translate((x-size/2).toFloat(),(y-size/2).toFloat());c.scale((size/24).toFloat(),(size/24).toFloat());stroke(c,r.paths.getValue(name),tint,width);c.restoreToCount(save)}
    private fun figure(c:Canvas,digit:Int,x:Double,y:Double){val save=c.save();c.translate(x.toFloat(),y.toFloat());c.scale(.85f,.85f);stroke(c,r.paths.getValue("digit$digit"),color(0x1F2A22),3.4);c.restoreToCount(save)}
    private fun figures(c:Canvas,value:Int,right:Double,base:Double){val deg=right-22.8;val y=base-55.25;figure(c,value/10%10,deg-85.6,y);figure(c,value%10,deg-42.8,y);figure(c,10,deg,y)}
    private fun contents(c:Canvas,g:Int,f:FocusInHomeTilesMotion.Frame){
        for(t in r.texts)if(t.group==g)text(c,t.value,t.x,t.base,t.size,t.medium,color(t.color),t.tracking,t.width)
        for(q in r.icons)if(q.group==g)icon(c,q.name,q.x,q.y,q.size,color(q.color))
        if(g==1)figures(c,24,366.0,500.0)
        if(g==3){val deg=340.2;val y=100.75;for(pos in 0..1){val a=if(pos==0)floor(f.degA/10).toInt()%10 else floor(f.degA+.5).toInt()%10;val b=if(pos==0)floor(f.degB/10).toInt()%10 else floor(f.degB+.5).toInt()%10;val x=if(pos==0)deg-85.6 else deg-42.8
            if(a==b||f.degK<=0||f.degK>=1)figure(c,if(f.degK>=1)b else a,x,y)else{var save=c.saveLayerAlpha(null,((1-f.degK)*255).roundToInt());paint.maskFilter=BlurMaskFilter((4*f.degK).toFloat().coerceAtLeast(.01f),BlurMaskFilter.Blur.NORMAL);figure(c,a,x,y+6*f.degK);paint.maskFilter=null;c.restoreToCount(save);save=c.saveLayerAlpha(null,(f.degK*255).roundToInt());paint.maskFilter=BlurMaskFilter((4*(1-f.degK)).toFloat().coerceAtLeast(.01f),BlurMaskFilter.Blur.NORMAL);figure(c,b,x,y-8*(1-f.degK));paint.maskFilter=null;c.restoreToCount(save)}
        };figure(c,10,deg,y)}
        if(g==4){paint.color=color(0xA9AB9B);for(x in 47..310 step 6)c.drawCircle(x.toFloat(),(424+f.dip).toFloat(),1f,paint)}
        if(g==6){val picker=c.save();val path=Path().apply{addRoundRect(16f,218f,386f,440f,40f,40f,Path.Direction.CW)};c.clipPath(path)
            for(i in 0..4){val u=i-f.pick;val sy=cos(min(abs(u),1.6)*.62);val op=m.clamp01(1.6-abs(u));val ink=m.clamp01(1-abs(u));if(op<=0)continue;val save=c.saveLayerAlpha(null,(op*255).roundToInt());c.translate(0f,(329+u*44.5).toFloat());c.scale(1f,sy.toFloat());c.translate(0f,-329f);c.clipRect(16f,303f,386f,355f)
                val a=rgb(0xA9AB9B);val b=rgb(0x1F2A22);text(c,listOf("Entrance","Kitchen","Living room","Bedroom","Hallway")[i],37.0,336.5,22.0,false,color(DoubleArray(3){m.mix(a[it],b[it],ink)}),width=180.0)
                val blur=3*m.clamp01(abs(f.pick-floor(f.pick+.5))*4);if(blur>.01)paint.maskFilter=BlurMaskFilter(blur.toFloat(),BlurMaskFilter.Blur.NORMAL)
                icon(c,listOf("timer","hourglassSmall","hourglassSmall","boltSmall","timer")[i],237.0,329.0,16.0,color(0x1F2A22),2.4);text(c,listOf("2 HR","12 MIN","28 MIN","1.1 KWH","4 HR")[i],258.0,334.7,16.0,true,color(0x1F2A22),.04);paint.maskFilter=null;c.restoreToCount(save)
            };c.restoreToCount(picker)
        }
    }
    private fun trim(path:Path,fraction:Double):Path{val lengths=mutableListOf<Float>();val scan=PathMeasure(path,false);do{lengths.add(scan.length)}while(scan.nextContour());var remaining=(lengths.sum()*fraction.coerceIn(0.0,1.0)).toFloat();val out=Path();val measure=PathMeasure(path,false);for(length in lengths){if(remaining<=0)break;measure.getSegment(0f,min(length,remaining),out,true);remaining-=length;measure.nextContour()};return out}
    private fun fallbackScene(c:Canvas,height:Double,rects:List<Double>,pars:List<Double>,cols:List<Double>){c.drawColor(color(0xE4E3D3));for(i in r.shapes.indices){if(pars[i*4+2]<=0)continue;val x=rects[i*4];val y=rects[i*4+1];paint.color=color(doubleArrayOf(cols[i*4],cols[i*4+1],cols[i*4+2]));paint.alpha=(pars[i*4+2]*255).roundToInt();paint.style=if(pars[i*4+3]>1.5)Paint.Style.STROKE else Paint.Style.FILL;paint.strokeWidth=1.5f;c.drawRoundRect(x.toFloat(),y.toFloat(),(x+rects[i*4+2]).toFloat(),(y+rects[i*4+3]).toFloat(),pars[i*4].toFloat(),pars[i*4].toFloat(),paint)};paint.alpha=255;paint.style=Paint.Style.FILL}
    private fun fallbackOrange(c:Canvas,f:FocusInHomeTilesMotion.Frame){paint.shader=LinearGradient(0f,(f.oy-f.oh/2).toFloat(),0f,(f.oy+f.oh/2).toFloat(),color(if(f.oth<.5)0xF25A05 else 0xF7FAE8),color(if(f.oth<.5)0xE2E1CF else 0xF25A05),Shader.TileMode.CLAMP);paint.color=Color.WHITE;c.drawRoundRect((f.ox-f.ow/2).toFloat(),(f.oy-f.oh/2).toFloat(),(f.ox+f.ow/2).toFloat(),(f.oy+f.oh/2).toFloat(),f.or.toFloat(),f.or.toFloat(),paint);paint.shader=null}
    @RequiresApi(31)private class TextBlur{private val nodes=List(8){i->RenderNode("Focus tile text $i").apply{setPosition(0,0,402,780);setClipToBounds(false)}}
        fun draw(c:Canvas,i:Int,picture:Picture,radius:Double){val node=nodes[i];node.beginRecording().drawPicture(picture);node.endRecording();node.setRenderEffect(RenderEffect.createBlurEffect(radius.toFloat().coerceAtLeast(.01f),radius.toFloat().coerceAtLeast(.01f),Shader.TileMode.DECAL));c.drawRenderNode(node)}
    }
    @RequiresApi(33)private class GPU(grain:Bitmap){
        private val scene=RuntimeShader(FocusInHomeTilesShaders.SCENE);private val front=RuntimeShader(FocusInHomeTilesShaders.FRONT);private val orange=RuntimeShader(FocusInHomeTilesShaders.ORANGE)
        init{scene.setInputShader("grain",BitmapShader(grain,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT))}
        private fun floats(v:List<Double>)=v.map(Double::toFloat).toFloatArray()
        private fun rgb(h:Int)=floatArrayOf((h shr 16 and 255)/255f,(h shr 8 and 255)/255f,(h and 255)/255f)
        fun scene(c:Canvas,height:Double,pixels:Double,rects:List<Double>,pars:List<Double>,cols:List<Double>,p:Paint){scene.setFloatUniform("uP3",0f);scene.setFloatUniform("uPx",(1/pixels).toFloat());scene.setFloatUniform("uGrainK",pixels.toFloat());scene.setFloatUniform("uPaper",rgb(0xE4E3D3));scene.setFloatUniform("uRect",floats(rects));scene.setFloatUniform("uPar",floats(pars));scene.setFloatUniform("uCol",floats(cols));p.color=Color.WHITE;p.alpha=255;p.shader=scene;c.drawRect(0f,0f,402f,height.toFloat(),p);p.shader=null}
        fun front(c:Canvas,f:FocusInHomeTilesMotion.Frame,height:Double,time:Double,p:Paint){if(f.front>=1.9)return;front.setFloatUniform("uP3",0f);front.setFloatUniform("uFront",f.front.toFloat());front.setFloatUniform("uDir",f.dir.toFloat());front.setFloatUniform("uT",time.toFloat());front.setFloatUniform("uForest",rgb(0x1E2D23));p.color=Color.WHITE;p.shader=front;c.drawRect(0f,0f,402f,height.toFloat(),p);p.shader=null}
        fun orange(c:Canvas,f:FocusInHomeTilesMotion.Frame,pixels:Double,time:Double,p:Paint){orange.setFloatUniform("uP3",0f);orange.setFloatUniform("uPx",(1/pixels).toFloat());orange.setFloatUniform("uBox",floatArrayOf(f.ox.toFloat(),f.oy.toFloat(),max(f.ow,.5).toFloat(),max(f.oh,.5).toFloat()));orange.setFloatUniform("uRad",f.or.toFloat());orange.setFloatUniform("uTheta",f.oth.toFloat());orange.setFloatUniform("uFlood",f.flood.toFloat());orange.setFloatUniform("uLamp",f.olamp.toFloat());orange.setFloatUniform("uBreath",(3*sin(2*PI*.3*time)).toFloat());orange.setFloatUniform("uBlink",(.5+.5*cos(2*PI*time)).toFloat());for((name,col)in listOf("uOr" to 0xF25A05,"uOrL" to 0xF8A35E,"uGrey" to 0xE2E1CF,"uTile" to 0xF7FAE8))orange.setFloatUniform(name,rgb(col));p.color=Color.WHITE;p.shader=orange;c.drawRect((f.ox-f.ow/2-60).toFloat(),(f.oy-f.oh/2-60).toFloat(),(f.ox+f.ow/2+60).toFloat(),(f.oy+f.oh/2+60).toFloat(),p);p.shader=null}
    }
}
