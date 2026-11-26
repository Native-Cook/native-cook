package com.ngocdevv.nativecook.blocks.progress.paperpleatprogress

import android.content.Context
import android.database.ContentObserver
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.*

@Composable
fun PaperPleatProgress(autoPlay: Boolean = false) {
    AndroidView(factory={ PaperPleatProgressView(it) },modifier=Modifier.fillMaxSize(),update={ it.autoPlay=autoPlay })
}

/** Explicit sRGB bytes. Box halvings happen once at load; all seven inputs sample bilinearly without mipmaps. */
internal fun paperPleatProgressImages(context: Context): List<Bitmap> {
    fun image(name: String)=context.assets.open("paperpleatprogress/paperpleatprogress_$name.webp").use {
        BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {
            inScaled=false; inPreferredConfig=Bitmap.Config.ARGB_8888
            inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)
        })!!
    }
    fun half(source: Bitmap): Bitmap {
        val w=source.width/2; val h=source.height/2
        val input=IntArray(source.width*source.height); source.getPixels(input,0,source.width,0,0,source.width,source.height)
        val output=IntArray(w*h)
        for(y in 0 until h) for(x in 0 until w) {
            val i=y*2*source.width+x*2; val a=input[i]; val b=input[i+1]; val c=input[i+source.width]; val d=input[i+source.width+1]
            var pixel=0
            for(shift in intArrayOf(0,8,16,24)) pixel=pixel or (((((a ushr shift) and 255)+((b ushr shift) and 255)+((c ushr shift) and 255)+((d ushr shift) and 255)+2)/4) shl shift)
            output[y*w+x]=pixel
        }
        return Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888).apply { density=Bitmap.DENSITY_NONE }
    }
    fun level(source: Bitmap,n: Int): Bitmap { var out=source; repeat(n) { val next=half(out); if(out!==source) out.recycle(); out=next }; return out }
    val photo=image("pleat"); val plate=image("pleat-plate"); val mask=image("pleat-mask"); val plate5=level(plate,5)
    return listOf(photo,plate,mask,level(photo,4),plate5,half(plate5),level(mask,5))
}

internal class PaperPleatProgressView(context: Context): FrameLayout(context),Choreographer.FrameCallback {
    var autoPlay=false
    val core=PaperPleatProgressMotion()
    private val density=resources.displayMetrics.density
    private var ready=Build.VERSION.SDK_INT>=33
    private val room: View=if(Build.VERSION.SDK_INT>=33) PaperPleatProgressRoom(context) else PaperPleatProgressGlView(context) { ready=true; wake() }
    private val chrome=PaperPleatProgressChrome(context)
    private val progress=PaperPleatProgressRange(context)
    private val sculpture=View(context)
    private var queued=false; private var donesSeen=0; private var held=false
    private val observer=object: ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { readMotion(); wake() } }
    init {
        setBackgroundColor(0xFFE5D9CF.toInt()); addView(room,LayoutParams(-1,-1)); addView(chrome,LayoutParams(-1,-1))
        addView(sculpture); addView(progress)
        sculpture.contentDescription="Paper sculpture"; sculpture.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        sculpture.setTag("paper-pleat-progress-sculpture"); progress.setTag("paper-pleat-progress-export")
        room.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO; chrome.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun readMotion() { core.setStill(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        if(room is PaperPleatProgressGlView) room.onResume(); readMotion(); wake()
    }
    override fun onDetachedFromWindow() {
        context.contentResolver.unregisterContentObserver(observer); Choreographer.getInstance().removeFrameCallback(this); queued=false
        if(room is PaperPleatProgressGlView) room.onPause(); super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if(visibility==VISIBLE) { if(room is PaperPleatProgressGlView) room.onResume(); wake() }
        else { Choreographer.getInstance().removeFrameCallback(this); queued=false; if(room is PaperPleatProgressGlView) room.onPause() }
    }
    private fun wake() { if(isAttachedToWindow && windowVisibility==VISIBLE && ready && !queued) { queued=true; Choreographer.getInstance().postFrameCallback(this) } }
    override fun doFrame(frameTimeNanos: Long) {
        queued=false; if(!isAttachedToWindow || windowVisibility!=VISIBLE || !ready) return
        core.step(frameTimeNanos/1e9,autoPlay); val f=core.out
        if(room is PaperPleatProgressRoom) room.show(f); if(room is PaperPleatProgressGlView) room.show(f.copy())
        chrome.show(f); progress.percent=floor(100*f.pct+.5).toInt()
        repeat((core.dones-donesSeen).coerceAtLeast(0)) { completed() }; donesSeen=core.dones
        if(!core.settled) wake()
    }
    @Suppress("DEPRECATION") // Explicit completion announcement, only for a user-touched run.
    private fun completed() {
        performHapticFeedback(if(Build.VERSION.SDK_INT>=30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        progress.announceForAccessibility("Exported")
    }
    override fun onLayout(changed: Boolean,left: Int,top: Int,right: Int,bottom: Int) {
        super.onLayout(changed,left,top,right,bottom); val sy=height/density/852f
        val pictureTop=(150*sy*density).roundToInt(); val progressTop=(690*sy*density).roundToInt()
        sculpture.layout(0,pictureTop,(393*density).roundToInt(),pictureTop+(589.5*density).roundToInt())
        progress.layout((24*density).roundToInt(),progressTop,(369*density).roundToInt(),progressTop+(130*sy*density).roundToInt())
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { held=true; val y=event.y/density; val top=150*height/density/852
                core.touchDown(event.x/density<=393 && y>=top && y<=top+589.5); parent?.requestDisallowInterceptTouchEvent(true); wake() }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> { if(held) core.touchUp(); held=false; parent?.requestDisallowInterceptTouchEvent(false); wake() }
        }; return true
    }
}

private class PaperPleatProgressRange(context: Context): View(context) {
    var percent=0
        set(value) {
            if(field==value) return
            field=value; contentDescription=if(value>=100) "Exported" else "Exporting, $value percent"
        }
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES; contentDescription="Exporting, 0 percent" }
    @Suppress("DEPRECATION") // RangeInfo.obtain is required on supported API 26–32.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info); info.className="android.widget.ProgressBar"
        info.contentDescription=if(percent>=100) "Exported" else "Exporting, $percent percent"
        info.rangeInfo=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0f,100f,percent.toFloat())
    }
}
private class PaperPleatProgressRoom(context: Context): View(context) {
    private val images=paperPleatProgressImages(context)
    private val shader=PaperPleatProgressShader(images)
    private var frame=PaperPleatProgressMotion.Frame()
    fun show(f: PaperPleatProgressMotion.Frame) { frame=f; invalidate() }
    override fun onDraw(canvas: Canvas) {
        canvas.save(); val density=resources.displayMetrics.density; canvas.scale(density,density)
        shader.update(frame,height/density/852); canvas.drawRect(0f,0f,width/density,height/density,shader.paint); canvas.restore()
    }
}

/** Precomputed contextual, unhinted advances; tracking is added after shaping. */
private class PaperPleatProgressChrome(context: Context): View(context) {
    data class Line(val text: String,val font: Typeface,val size: Float,val positions: FloatArray)
    private val density=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
    private var f=PaperPleatProgressMotion.Frame()
    private fun font(name: String)=Typeface.createFromAsset(context.assets,"paperpleatprogress/paperpleatprogress_$name.ttf")
    private fun line(text: String,font: Typeface,size: Float,tracking: Float=0f): Line {
        paint.typeface=font; paint.textSize=size; paint.isLinearText=true
        val advances=FloatArray(text.length); paint.getTextRunAdvances(text.toCharArray(),0,text.length,0,text.length,false,advances,0)
        var x=0f; val positions=FloatArray(text.length) { i -> val at=x; x+=advances[i]+tracking; at }
        return Line(text,font,size,positions)
    }
    private val title=font("InstrumentSerif-Regular"); private val bold=font("InterDisplay-Bold"); private val medium=font("Inter-Medium")
    private val folding=line("Folding your",title,60f,-1.5f); private val exportLine=line("export",title,60f,-1.5f); private val exported=line("Exported.",title,60f,-1.5f)
    private val percents=List(101) { line("$it%",bold,64f,-2f) }; private val left=List(26) { line("Annual report.pdf · 18 MB · $it s left",medium,15f) }
    private val saved=line("Annual report.pdf · 18 MB · saved to Files",medium,15f)
    fun show(frame: PaperPleatProgressMotion.Frame) { f=frame; invalidate() }
    private fun draw(c: Canvas,l: Line,y: Float,color: Int) {
        paint.color=color; paint.typeface=l.font; paint.textSize=l.size
        for(i in l.text.indices) c.drawText(l.text,i,i+1,30+l.positions[i],y,paint)
    }
    override fun onDraw(c: Canvas) {
        c.save(); c.scale(density,density); val sy=height/density/852
        draw(c,if(f.done) exported else folding,140*sy,0xFF2B2420.toInt()); if(!f.done) draw(c,exportLine,199*sy,0xFF2B2420.toInt())
        draw(c,percents[floor(100*f.pct+.5).toInt().coerceIn(0,100)],754*sy,0xFFE0613F.toInt())
        draw(c,if(f.done) saved else left[ceil(25*(1-f.pct)).toInt().coerceIn(0,25)],786*sy,0xFF7B6E64.toInt())
        paint.color=0x1F2B2420; c.drawRoundRect(30f,806*sy,363f,806*sy+4,2f,2f,paint)
        if(f.pct>0) { paint.color=0xFFE0613F.toInt(); c.drawRoundRect(30f,806*sy,30+333*f.pct.toFloat(),806*sy+4,2f,2f,paint) }; c.restore()
    }
}
