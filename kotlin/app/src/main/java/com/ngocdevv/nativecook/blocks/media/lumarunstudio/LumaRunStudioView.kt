package com.ngocdevv.nativecook.blocks.media.lumarunstudio

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.*

/** Native Canvas stays isolated from Compose recomposition; the clock runs only for visible motion. */
internal class LumaRunStudioView(context:Context,onBack:()->Unit):View(context),Choreographer.FrameCallback {
    private val resources=LumaRunStudioResources(context)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path();private val clip=Path()
    private val sans=Typeface.create("sans-serif",Typeface.NORMAL)
    private val medium=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    private val bold=Typeface.create("sans-serif",Typeface.BOLD)
    private var callback=false;private var pointer= -1;private val ignored=HashSet<Int>()
    private var focused= -1
    private var scale=1.0;private var bottom=0.0
    val driver=LumaRunStudioDriver(resources,::wake,{sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)},{performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)},onBack)
    init {isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;setBackgroundColor(Color.rgb(23,25,24))}
    fun props(autoPlay:Boolean,previewTime:Double?,reduced:Boolean){driver.props(autoPlay,previewTime,reduced);wake()}
    private fun wake(){postInvalidateOnAnimation();if(!callback&&driver.ticks()&&isAttachedToWindow){callback=true;Choreographer.getInstance().postFrameCallback(this)}}
    override fun doFrame(frameTimeNanos:Long){callback=false;invalidate();if(driver.ticks())wake()}
    override fun onAttachedToWindow(){super.onAttachedToWindow();driver.active(true);wake()}
    override fun onWindowFocusChanged(hasWindowFocus:Boolean){super.onWindowFocusChanged(hasWindowFocus);driver.active(hasWindowFocus);wake()}
    override fun onDetachedFromWindow(){Choreographer.getInstance().removeFrameCallback(this);callback=false;pointer= -1;ignored.clear();driver.active(false);super.onDetachedFromWindow()}
    fun release() { driver.close() }
    private fun fill(c:Canvas,color:Int,alpha:Double=1.0){paint.shader=null;paint.xfermode=null;paint.style=Paint.Style.FILL;paint.color=color;paint.alpha=(alpha*255).roundToInt().coerceIn(0,255)}
    private fun stroke(color:Int,width:Double,alpha:Double=1.0){paint.shader=null;paint.xfermode=null;paint.style=Paint.Style.STROKE;paint.color=color;paint.alpha=(alpha*255).roundToInt().coerceIn(0,255);paint.strokeWidth=width.toFloat();paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND}
    private fun rgb(hex:Int)=hex or -0x1000000
    private fun image(c:Canvas,b:Bitmap,rect:RectF,alpha:Double=1.0){paint.shader=null;paint.xfermode=null;paint.style=Paint.Style.FILL;paint.alpha=(alpha*255).roundToInt().coerceIn(0,255);c.drawBitmap(b,null,rect,paint)}
    private fun text(c:Canvas,value:String,x:Double,y:Double,size:Double,color:Int,weight:Int=0,align:Paint.Align=Paint.Align.LEFT){textPaint.typeface=when(weight){1->medium;2->bold;else->sans};textPaint.textSize=size.toFloat();textPaint.color=rgb(color);textPaint.alpha=255;textPaint.textAlign=align;val fm=textPaint.fontMetrics;c.drawText(value,x.toFloat(),(y-(fm.ascent+fm.descent)/2).toFloat(),textPaint)}
    private fun line(c:Canvas,x:Double,y:Double,xx:Double,yy:Double,color:Int,width:Double=1.0,alpha:Double=1.0){stroke(rgb(color),width,alpha);c.drawLine(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),paint)}
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);if(width==0)return;scale=width/393.0;val h=max(852.0,height/scale);bottom=max(0.0,(ViewCompat.getRootWindowInsets(this)?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom?:0)/scale-34)
        val scene=driver.drawScene();val p=scene.params;val a=scene.appearance;val c=canvas;c.save();c.scale(scale.toFloat(),scale.toFloat())
        c.drawColor(rgb(0x171918));image(c,resources.frame,RectF(0f,105f,393f,567f))
        val art=RectF(29f,136f,364f,520f);clip.reset();clip.addRoundRect(art,10f,10f,Path.Direction.CW);c.save();c.clipPath(clip);image(c,resources.canonical,art)
        if(p.q>0){val roi=RectF((59+p.roiX).toFloat(),(178+p.roiY).toFloat(),(334+p.roiX).toFloat(),(478+p.roiY).toFloat());c.save();c.clipRect(roi);val layer=c.saveLayer(roi,null)
            image(c,scene.pair.a.image,art);image(c,scene.pair.b.image,art,scene.pair.mix)
            paint.alpha=255;paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN);c.drawBitmap(resources.mask,null,roi,paint);paint.xfermode=null;c.restoreToCount(layer);c.restore();image(c,resources.canonical,art,scene.compare)}
        c.restore()
        stroke(rgb(0xD8E68B),1.0,a.bracketAlpha)
        for((x,y,sx,sy)in arrayOf(doubleArrayOf(59.0,178.0,1.0,1.0),doubleArrayOf(334.0,178.0,-1.0,1.0),doubleArrayOf(59.0,478.0,1.0,-1.0),doubleArrayOf(334.0,478.0,-1.0,-1.0))){path.reset();val xx=x+p.roiX;val yy=y+p.roiY;path.moveTo((xx+sx*6).toFloat(),yy.toFloat());path.lineTo((xx+sx*3).toFloat(),yy.toFloat());path.quadTo(xx.toFloat(),yy.toFloat(),xx.toFloat(),(yy+sy*3).toFloat());path.lineTo(xx.toFloat(),(yy+sy*6).toFloat());c.drawPath(path,paint)}
        val low=24+345*p.low-a.anticipation+a.lowOffset;val high=24+345*p.high+a.anticipation+a.highOffset
        fill(c,rgb(0x484E43));c.drawRoundRect(24f,629f,369f,633f,2f,2f,paint);fill(c,rgb(0xCDD979));c.drawRoundRect(low.toFloat(),629f,high.toFloat(),633f,2f,2f,paint)
        val radius=(10*a.handleScale).toFloat()
        for(x in listOf(low,high)){fill(c,Color.BLACK,.3);c.drawCircle(x.toFloat(),632.5f,radius,paint);fill(c,rgb(0xD8E68B));c.drawCircle(x.toFloat(),631f,radius,paint);stroke(Color.WHITE,1.0,.16);c.drawCircle(x.toFloat(),631f,radius-.5f,paint);line(c,x,628.0,x,634.0,0x374022,1.2)}
        fill(c,rgb(0x242724));c.drawRoundRect(24f,670f,369f,730f,12f,12f,paint);stroke(rgb(0x484E43),1.0);c.drawCircle(329f,700f,21f,paint)
        for(i in 0..7){val theta=i*PI/4;line(c,329+cos(theta)*17,700+sin(theta)*17,329+cos(theta)*19,700+sin(theta)*19,0xFFFFFF,1.0,.2)}
        val angle=p.angle*PI/180;val dx=cos(angle);val dy=sin(angle);path.reset();path.moveTo((329-11*dx).toFloat(),(700-11*dy).toFloat());path.lineTo((329+11*dx).toFloat(),(700+11*dy).toFloat());path.moveTo((329+7*dx-3*dy).toFloat(),(700+7*dy+3*dx).toFloat());path.lineTo((329+11*dx).toFloat(),(700+11*dy).toFloat());path.lineTo((329+7*dx+3*dy).toFloat(),(700+7*dy-3*dx).toFloat());stroke(rgb(0xD8E68B),1.4);c.drawPath(path,paint)
        text(c,"Luma Run",196.5,86.0,18.0,0xF0EEDC,1,Paint.Align.CENTER)
        line(c,46.0,78.0,38.0,86.0,0xF0EEDC,1.8);line(c,38.0,86.0,46.0,94.0,0xF0EEDC,1.8)
        if(driver.kept){line(c,344.0,86.0,349.0,91.0,0xF0EEDC,1.6);line(c,349.0,91.0,358.0,80.0,0xF0EEDC,1.6)}else{stroke(rgb(0xF0EEDC),1.4);c.drawRoundRect(343f,82f,359f,94f,3f,3f,paint);line(c,351.0,76.0,351.0,89.0,0xF0EEDC,1.4);line(c,347.0,85.0,351.0,89.0,0xF0EEDC,1.4);line(c,355.0,85.0,351.0,89.0,0xF0EEDC,1.4)}
        text(c,"Pixel sorting",24.0,554.0,15.0,0xF0EEDC,1)
        text(c,if(driver.ready)"Bright runs · preview ready"else"Preparing local preview…",24.0,574.5,11.0,0x9EA394)
        fill(c,rgb(0x242724));c.drawRoundRect(280f,537f,369f,571f,17f,17f,paint);stroke(rgb(0x484E43),.8);c.drawRoundRect(280f,537f,369f,571f,17f,17f,paint);text(c,"Original",324.5,554.0,11.0,0xF0EEDC,1,Paint.Align.CENTER)
        text(c,"Luma window",24.0,601.0,13.0,0xF0EEDC,1);text(c,"${(p.low*100).roundToInt()}–${(p.high*100).roundToInt()}%",369.0,601.0,12.0,0x9EA394,align=Paint.Align.RIGHT)
        text(c,"Direction",40.0,691.5,12.0,0x9EA394,1);text(c,"${p.angle.roundToInt()}°",40.0,713.5,19.0,0xF0EEDC)
        fill(c,rgb(0xD8E68B));c.drawRoundRect(24f,(737-bottom).toFloat(),369f,(793-bottom).toFloat(),15f,15f,paint)
        text(c,if(driver.kept)"Edit kept"else"Keep this edit",if(driver.kept)203.5 else 196.5,765-bottom,15.0,0x20251A,2,Paint.Align.CENTER)
        if(driver.kept){line(c,158.0,764-bottom,162.0,768-bottom,0x20251A,1.4);line(c,162.0,768-bottom,169.0,761-bottom,0x20251A,1.4)}
        text(c,"All changes stay on this device",196.5,809.5-bottom,10.0,0x9EA394,align=Paint.Align.CENTER)
        c.restore()
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        val index=e.actionIndex;val id=e.getPointerId(index);val x=e.getX(index)/scale;var y=e.getY(index)/scale
        // The footer is raised only when the device has an unusually deep safe inset.
        if(y in (737-bottom)..(793-bottom))y+=bottom
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{if(driver.down(id,x,y)){pointer=id;parent?.requestDisallowInterceptTouchEvent(true);wake();return true};return false}
            MotionEvent.ACTION_POINTER_DOWN->{ignored+=id;return pointer>=0}
            MotionEvent.ACTION_MOVE->{val i=e.findPointerIndex(pointer);if(i>=0){driver.move(pointer,e.getX(i)/scale,e.getY(i)/scale);wake()};return pointer>=0}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP->{if(id==pointer){driver.up(id,x,y);pointer= -1;parent?.requestDisallowInterceptTouchEvent(false);wake()};ignored-=id;return true}
            MotionEvent.ACTION_CANCEL->{if(pointer>=0)driver.up(pointer,x,y,true);pointer= -1;ignored.clear();parent?.requestDisallowInterceptTouchEvent(false);wake();return true}}
        return true
    }
    private data class Node(val label:String,val value:String,val rect:RectF,val owner:LumaRunStudioOwner,val button:Boolean=false)
    private fun nodes():List<Node>{val s=driver.snapshot();val p=s.params;val a=s.appearance
        fun r(x:Double,y:Double,w:Double,h:Double)=RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat())
        return listOf(Node("Back","",r(20.0,64.0,44.0,44.0),LumaRunStudioOwner.Back,true),Node(if(driver.kept)"Edit kept"else"Keep edit","",r(329.0,64.0,44.0,44.0),LumaRunStudioOwner.Keep,true),Node("Canyon preview","Strength ${(p.q*100).roundToInt()} percent",r(29.0,136.0,335.0,384.0),LumaRunStudioOwner.Image),Node("Sorting region","Horizontal ${p.roiX.toInt()}, vertical ${p.roiY.toInt()}",r(312+p.roiX,456+p.roiY,44.0,44.0),LumaRunStudioOwner.Region),Node("Original","Hold to compare",r(280.0,532.0,89.0,44.0),LumaRunStudioOwner.Compare,true),Node("Low luma threshold","${(p.low*100).roundToInt()} percent",r(24+345*p.low-a.anticipation+a.lowOffset-22,609.0,44.0,44.0),LumaRunStudioOwner.Low),Node("High luma threshold","${(p.high*100).roundToInt()} percent",r(24+345*p.high+a.anticipation+a.highOffset-22,609.0,44.0,44.0),LumaRunStudioOwner.High),Node("Sort direction","${p.angle.roundToInt()} degrees",r(307.0,678.0,44.0,44.0),LumaRunStudioOwner.Dial),Node(if(driver.kept)"Edit kept"else"Keep this edit","",r(24.0,737-bottom,345.0,56.0),LumaRunStudioOwner.Keep,true),Node(if(driver.ready)"Local preview ready"else"Preparing local preview","",r(24.0,566.0,230.0,17.0),LumaRunStudioOwner.Image,true))}
    private val provider=object:AccessibilityNodeProvider(){
        override fun createAccessibilityNodeInfo(virtualViewId:Int):AccessibilityNodeInfo? {
            if(virtualViewId==HOST_VIEW_ID){val info=AccessibilityNodeInfo.obtain(this@LumaRunStudioView);onInitializeAccessibilityNodeInfo(info);nodes().indices.forEach {info.addChild(this@LumaRunStudioView,it)};return info}
            val n=nodes().getOrNull(virtualViewId)?:return null;val info=AccessibilityNodeInfo.obtain();info.setSource(this@LumaRunStudioView,virtualViewId);info.setParent(this@LumaRunStudioView);info.packageName=context.packageName;info.className=if(n.button)"android.widget.Button"else"android.widget.SeekBar";info.contentDescription=n.label+(if(n.value.isEmpty())""else", "+n.value);info.isVisibleToUser=true;info.isEnabled=driver.ready||n.owner==LumaRunStudioOwner.Back;info.isFocusable=true;info.isAccessibilityFocused=focused==virtualViewId
            val rect=Rect((n.rect.left*scale).toInt(),(n.rect.top*scale).toInt(),(n.rect.right*scale).toInt(),(n.rect.bottom*scale).toInt());info.setBoundsInParent(rect);val origin=IntArray(2);getLocationOnScreen(origin);rect.offset(origin[0],origin[1]);info.setBoundsInScreen(rect);info.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);info.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            if(n.button)info.addAction(AccessibilityNodeInfo.ACTION_CLICK)else{info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)};return info
        }
        override fun performAction(virtualViewId:Int,action:Int,arguments:Bundle?):Boolean {val n=nodes().getOrNull(virtualViewId)?:return false
            when(action){AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS->{focused=virtualViewId;sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);return true};AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS->{focused= -1;return true};AccessibilityNodeInfo.ACTION_CLICK->{when(n.owner){LumaRunStudioOwner.Back->{driver.down(-2,n.rect.centerX().toDouble(),n.rect.centerY().toDouble());driver.up(-2,n.rect.centerX().toDouble(),n.rect.centerY().toDouble())};LumaRunStudioOwner.Keep->driver.save();LumaRunStudioOwner.Compare->driver.toggleCompare();else->return false};wake();return true};AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD->{val sign=if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)1 else -1;val delta=when(n.owner){LumaRunStudioOwner.Low,LumaRunStudioOwner.High->.01;LumaRunStudioOwner.Dial->15.0;LumaRunStudioOwner.Region->5.0;else->.1};driver.adjust(n.owner,sign*delta);wake();return true}}
            return false}
    }
    override fun getAccessibilityNodeProvider():AccessibilityNodeProvider=provider
}
