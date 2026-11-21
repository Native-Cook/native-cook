package com.ngocdevv.nativecook.blocks.media.shapeshiftviewfinder
import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class ShapeShiftViewfinderArt(context:Context) {
    private val assets=context.assets
    private fun image(name:String)=assets.open("shapeshiftviewfinder/shapeshiftviewfinder_$name").use{BitmapFactory.decodeStream(it)!!}
    private val plate=image("plate.jpg");private val front=image("plate_front.jpg");private val shade=image("shade.png")
    private val backShader=BitmapShader(plate,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
    private val frontShader=BitmapShader(front,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
    private val shadeShader=BitmapShader(shade,Shader.TileMode.MIRROR,Shader.TileMode.MIRROR)
    private val semi=Typeface.createFromAsset(assets,"shapeshiftviewfinder/shapeshiftviewfinder_Inter_SemiBold.ttf")
    private val medium=Typeface.createFromAsset(assets,"shapeshiftviewfinder/shapeshiftviewfinder_Inter_Medium.ttf")
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG);private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.ROUND}
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{isSubpixelText=true;isLinearText=true}
    private val clear=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeJoin=Paint.Join.ROUND;xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)}
    private val picture=Paint(Paint.ANTI_ALIAS_FLAG).apply{isFilterBitmap=true}
    private val clip=Path();private val path=Path();private val matrix=Matrix();private val rect=RectF()
    val colors=intArrayOf(0xF02FA6,0xB9836B,0x315FEA,0xF4E59B,0xC5AFF3,0x171820,0xF4F1E7,0x43B8E5,0x7548CF,0xC61CCB,0xFFC58F,0x9EDFE4)
    val dynamic=intArrayOf(0x118169,0x2E6E54,0x4E4A5A,0x555274,0x5B4A55,0x3F5A4E,0xA9C9B4,0x4A6B38)
    private fun rgb(c:Int,a:Double=1.0)=(c and 0xFFFFFF) or ((a.coerceIn(0.0,1.0)*255).roundToInt() shl 24)
    private fun outline(points:Array<ShapeShiftViewfinderPoint>,out:Path=path):Path {
        out.rewind();if(points.isEmpty())return out;out.moveTo(points[0].x.toFloat(),points[0].y.toFloat())
        for(j in 0..191){val a=points[(j+191)%192];val b=points[j];val c=points[(j+1)%192];val d=points[(j+2)%192];out.cubicTo((b.x+(c.x-a.x)/6).toFloat(),(b.y+(c.y-a.y)/6).toFloat(),(c.x-(d.x-b.x)/6).toFloat(),(c.y-(d.y-b.y)/6).toFloat(),c.x.toFloat(),c.y.toFloat())};out.close();return out
    }
    fun draw(c:Canvas,f:ShapeShiftViewfinderFrame,engine:ShapeShiftViewfinderEngine,w:Double,h:Double,top:Double,bottom:Double) {
        val stageTop=max(top,20.0)+3;val s=min(w/402,max(1.0,h-stageTop-max(bottom,12.0))/590);val left=(w-402*s)/2;val ctrlY=h-max(bottom,12.0)-80*s;val stageH=(ctrlY-60*s-stageTop)/s;val sheetTop=h-max(bottom,12.0)+5*s-210*s
        c.drawColor(Color.BLACK)
        outline(ShapeShiftViewfinderGeometry.points(f,stageH))
        c.save();c.translate((left+6*s).toFloat(),stageTop.toFloat());c.scale(s.toFloat(),s.toFloat())
        clip.rewind();clip.addRoundRect(RectF(0f,0f,390f,stageH.toFloat()),44f,44f,Path.Direction.CW);c.clipPath(clip)
        fill.color=rgb(f.base);c.drawRect(0f,0f,390f,stageH.toFloat(),fill)
        if(f.f1u>=0)flood(c,path,f.f1u,f.f1c,stageH)
        if(f.f2u>=0)flood(c,path,f.f2u,f.f2c,stageH)
        for(pass in 0..1){val offset=if(pass==0)2.0 else 14+7*f.lift;val blur=if(pass==0)5.0 else 30+5*f.lift;val alpha=if(pass==0).24 else .16+.03*f.lift
            fill.color=rgb(f.dot,.35);fill.setShadowLayer(blur.toFloat(),0f,offset.toFloat(),rgb(f.dot,alpha));c.drawPath(path,fill);fill.clearShadowLayer()}
        val previous=engine.previousFrame
        if(engine.reduced&&previous!=null&&engine.crossfade<1){val p=outline(ShapeShiftViewfinderGeometry.points(previous,stageH),Path());drawPicture(c,previous,p,stageH,1-engine.crossfade)}
        drawPicture(c,f,path,stageH,if(engine.reduced)engine.crossfade else 1.0)
        val cx=(195+f.x).toFloat();val cy=(stageH/2+f.y).toFloat()
        c.save();c.clipPath(path);line.strokeWidth=28f;line.shader=LinearGradient((cx-112.5*f.scale).toFloat(),(cy-112.5*f.scale).toFloat(),(cx+112.5*f.scale).toFloat(),(cy+112.5*f.scale).toFloat(),intArrayOf(0x4DFFFFFF,0x00FFFFFF,0x29000000),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP);c.drawPath(path,line);line.shader=null;c.restore()
        line.strokeWidth=1.5f;line.shader=LinearGradient((cx-135*f.scale).toFloat(),(cy-135*f.scale).toFloat(),(cx+135*f.scale).toFloat(),(cy+135*f.scale).toFloat(),intArrayOf(0xBFFFFFFF.toInt(),0x00FFFFFF,0x00FFFFFF),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP);c.drawPath(path,line);line.shader=null
        if(f.blinkA>0)echo(c,path,f.blinkD,2.0,Color.WHITE,f.blinkA,stageH)
        val radii=ShapeShiftViewfinderGeometry.radii(f)
        for(i in 0..1)if(f.rings[i*3]>.001){val a=f.rings[i*3+1];val j=((((a%(2*PI))+2*PI)%(2*PI))/(2*PI)*192).roundToInt()%192;val r=radii[j]*150*max(f.iris,.001);val x=r*sin(a);val y=-r*cos(a);val angle=(f.rot+f.lean)*PI/180;val px=cx+f.scale*(x*cos(angle)-y*sin(angle));val py=cy+f.scale*(x*sin(angle)+y*cos(angle));val radius=(13*f.rings[i*3+2]).toFloat();fill.color=rgb(0xFFFFFF,f.rings[i*3]*.45);line.color=rgb(0xFFFFFF,f.rings[i*3]);line.strokeWidth=2f;c.drawCircle(px.toFloat(),py.toFloat(),radius,fill);c.drawCircle(px.toFloat(),py.toFloat(),radius,line)}
        fill.color=rgb(0x000000,.34);rect.set(162f,(stageH-62).toFloat(),228f,(stageH-24).toFloat());c.drawRoundRect(rect,19f,19f,fill)
        label(c,if(abs(f.k-1)<.05)"1×" else String.format(java.util.Locale.US,"%.1f×",f.k),195.0,stageH-43,17.0,Color.WHITE,false)
        c.restore()
        val hints=arrayOf("Tap to change shape","Pinch to resize","Drag to move")
        for(i in 0..2)if(f.hint[i]>.001){text.typeface=semi;text.textSize=(17*s).toFloat();val tw=text.measureText(hints[i]);val x=(w-tw-30*s)/2;val y=stageTop+(stageH/2-175)*s
            c.save();c.translate(x.toFloat(),(y-11*s).toFloat());c.scale(s.toFloat(),s.toFloat());drawHand(c,i,f.hint[i]);c.restore();text.color=rgb(0xFAFAF8,f.hint[i]);text.textAlign=Paint.Align.LEFT;c.drawText(hints[i],(x+30*s).toFloat(),(y-(text.ascent()+text.descent())/2).toFloat(),text)}
        c.save();c.scale(s.toFloat(),s.toFloat());c.translate((left/s).toFloat(),0f);drawControls(c,f,ctrlY/s);c.restore()
        if(f.sheet>.001)drawSheet(c,f,engine,left,s,sheetTop,h)
    }
    private fun drawPicture(c:Canvas,f:ShapeShiftViewfinderFrame,path:Path,sh:Double,alpha:Double) {
        c.save();c.clipPath(path)
        val isFront=f.flip>=.5;val image=if(isFront)front else plate;val shader=if(isFront)frontShader else backShader;val fr=if(isFront)doubleArrayOf(.5,.56,440.0,0.0)else f.fr;val size=fr[2]*1.04
        matrix.reset();matrix.postScale((size/image.width).toFloat(),(size/image.width).toFloat());matrix.postTranslate((-fr[0]*size).toFloat(),(-fr[1]*size).toFloat());matrix.postScale(max(abs(cos(PI*f.flip)),.002).toFloat(),1f);matrix.postRotate((fr[3]+f.sr).toFloat());matrix.postTranslate((f.sx+f.lx).toFloat(),(f.sy+f.ly).toFloat());matrix.postScale(f.scale.toFloat(),f.scale.toFloat());matrix.postRotate((f.rot+f.lean).toFloat());matrix.postTranslate((195+f.x).toFloat(),(sh/2+f.y).toFloat());shader.setLocalMatrix(matrix)
        picture.shader=shader;picture.alpha=(255*alpha).roundToInt();c.drawPath(path,picture)
        matrix.reset();matrix.postScale(640f/shade.width,640f/shade.width);matrix.postTranslate(-320f,-320f);matrix.postRotate(f.shr.toFloat());matrix.postTranslate(f.shx.toFloat(),f.shy.toFloat());matrix.postScale(f.scale.toFloat(),f.scale.toFloat());matrix.postRotate((f.rot+f.lean).toFloat());matrix.postTranslate((195+f.x).toFloat(),(sh/2+f.y).toFloat());shadeShader.setLocalMatrix(matrix)
        picture.shader=shadeShader
        picture.alpha=(255*.55*alpha).roundToInt()
        if (Build.VERSION.SDK_INT >= 29) picture.blendMode = BlendMode.MULTIPLY
        else picture.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        c.drawPath(path,picture)
        picture.shader=null
        if (Build.VERSION.SDK_INT >= 29) picture.blendMode = null
        else picture.xfermode = null
        picture.alpha=255
        if(f.dim<1){fill.color=rgb(0x000000,1-f.dim);c.drawPath(path,fill)};if(f.flash>0){fill.color=rgb(0xFFFFFF,f.flash);c.drawPath(path,fill)};c.restore()
    }
    private fun echo(c:Canvas,p:Path,d:Double,w:Double,color:Int,alpha:Double,sh:Double){if(d<=.5||alpha<=.01)return;val checkpoint=c.saveLayerAlpha(RectF(0f,0f,390f,sh.toFloat()),(255*alpha).roundToInt());line.color=color;line.strokeWidth=(2*d+w).toFloat();c.drawPath(p,line);clear.strokeWidth=max(2*d-w,0.0).toFloat();c.drawPath(p,clear);clear.style=Paint.Style.FILL;c.drawPath(p,clear);clear.style=Paint.Style.STROKE;c.restoreToCount(checkpoint)}
    private fun flood(c:Canvas,p:Path,u:Double,color:Int,sh:Double){val m=ShapeShiftViewfinderMath;val front=560*(1-(1-u.coerceIn(0.0,1.0)).pow(1.6));val width=max(front-52*(1-m.seg(front,120.0,260.0)),0.0);val fade=1-m.seg(front,190.0,260.0);fill.color=rgb(color);line.color=rgb(color);line.strokeWidth=(2*width).toFloat();c.drawPath(p,fill);c.drawPath(p,line);val light=rgb(m.color(color,0xFFFFFF,.25));for(v in arrayOf(doubleArrayOf(52.0,2.0,.2),doubleArrayOf(26.0,2.5,.4),doubleArrayOf(0.0,3.0,.7)))echo(c,p,front-v[0],v[1],light,v[2]*fade,sh)}
    private fun label(c:Canvas,value:String,cx:Double,cy:Double,size:Double,color:Int,isMedium:Boolean){text.typeface=if(isMedium)medium else semi;text.textSize=size.toFloat();text.color=color;text.textAlign=Paint.Align.CENTER;c.drawText(value,cx.toFloat(),(cy-(text.ascent()+text.descent())/2).toFloat(),text)}
    private fun drawControls(c:Canvas,f:ShapeShiftViewfinderFrame,y:Double){val alpha=1-ShapeShiftViewfinderMath.seg(f.sheet,0.0,.6);for(x in doubleArrayOf(80.0,322.0)){fill.color=rgb(0x1A1A1C,alpha);c.drawCircle(x.toFloat(),y.toFloat(),22f,fill)};fill.color=rgb(f.dot,alpha);c.drawCircle(80f,y.toFloat(),13f,fill);line.color=rgb(0x1C1C1E,alpha);line.strokeWidth=6f;c.drawCircle(201f,y.toFloat(),37f,line);fill.color=rgb(0xFAFAF8,alpha);c.drawCircle(201f,y.toFloat(),(32*f.disc).toFloat(),fill);c.save();c.translate(311f,(y-11).toFloat());drawFlip(c,alpha);c.restore()}
    private fun drawFlip(c:Canvas,a:Double){line.color=rgb(0xFFFFFF,a);line.strokeWidth=2f;c.drawArc(3.65f,3.65f,18.35f,18.35f,204f,148f,false,line);c.drawArc(3.65f,3.65f,18.35f,18.35f,24f,148f,false,line);c.drawLine(3.85f,3.8f,3.85f,6.5f,line);c.drawLine(3.85f,6.5f,6.55f,6.5f,line);c.drawLine(18.15f,18.2f,18.15f,15.5f,line);c.drawLine(18.15f,15.5f,15.45f,15.5f,line)}
    private fun drawHand(c:Canvas,i:Int,alpha:Double){line.color=rgb(0xFFFFFF,alpha);line.strokeWidth=2f;clip.rewind();clip.moveTo(8.8f,12.4f);clip.lineTo(8.8f,5.5f);clip.cubicTo(8.8f,3.4f,11.8f,3.4f,11.8f,5.5f);clip.lineTo(11.8f,10.8f);clip.cubicTo(17f,7.5f,19.2f,12f,17.6f,17f);clip.cubicTo(15.7f,22f,9f,21f,6f,16.5f);clip.cubicTo(3.5f,13.3f,5.5f,11.5f,8.8f,15.5f);c.drawPath(clip,line);if(i==0){c.drawLine(4f,6f,2f,4f,line);c.drawLine(17f,6f,19f,4f,line);c.drawLine(10.3f,1f,10.3f,0f,line)}else if(i==2){c.drawLine(15f,3f,21f,3f,line);c.drawLine(19f,1f,21f,3f,line);c.drawLine(19f,5f,21f,3f,line)}}
    private fun drawSheet(c:Canvas,f:ShapeShiftViewfinderFrame,engine:ShapeShiftViewfinderEngine,left:Double,s:Double,top:Double,h:Double){val y=top+(1-f.sheet)*(h-top+12);c.save();c.translate((left+14*s).toFloat(),y.toFloat());c.scale(s.toFloat(),s.toFloat());clip.rewind();clip.addRoundRect(RectF(0f,0f,374f,210f),28f,28f,Path.Direction.CW);c.clipPath(clip);fill.color=rgb(f.dot);c.drawRect(0f,0f,374f,210f,fill);fill.color=rgb(0x000000,.2);c.drawRect(0f,0f,374f,210f,fill);fill.color=rgb(0xFFFFFF,.3);c.drawRoundRect(RectF(169f,5f,205f,10f),2.5f,2.5f,fill);label(c,"Background Color",187.0,35.0,24.0,Color.WHITE,false);fill.color=rgb(0x000000,.45);c.drawRoundRect(RectF(19f,72f,355f,108f),18f,18f,fill);fill.color=rgb(0x2B2B2E);c.drawRoundRect(RectF((19+168*f.segment).toFloat(),72f,(187+168*f.segment).toFloat(),108f),18f,18f,fill);label(c,"Dynamic",103.0,90.0,15.0,Color.WHITE,true);label(c,"Solid",271.0,90.0,15.0,Color.WHITE,true)
        c.save();c.clipRect(19f,123f,355f,185f);for((i,tone)in (if(engine.solidTarget)colors else dynamic).withIndex()){val x=(24+i*50-engine.rowScroll).toFloat();rect.set(x,128f,x+42,180f);fill.color=rgb(tone);c.drawRoundRect(rect,14f,14f,fill);if(i==engine.selectedSwatch){line.color=Color.WHITE;line.strokeWidth=2.5f;c.drawRoundRect(RectF(x-5,123f,x+47,185f),19f,19f,line)}};c.restore();c.restore()}
}
