package com.ngocdevv.nativecook.blocks.forms.selfieverifybadge

import android.content.Context
import android.database.ContentObserver
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.*
import android.widget.FrameLayout
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.forms.selfieverifybadge.SelfieVerifyBadgeTheme.Companion as C
import com.ngocdevv.nativecook.blocks.forms.selfieverifybadge.SelfieVerifyBadgeMotion as M

@Composable
fun SelfieVerifyBadge(autoPlay: Boolean=false) {
    val density=LocalDensity.current
    val top=WindowInsets.statusBars.getTop(density)/density.density
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density
    AndroidView(factory={SelfieVerifyBadgeView(it)},modifier=Modifier.fillMaxSize(),update={it.configure(autoPlay,top.toDouble(),bottom.toDouble())})
}

// Only this view is invalidated per frame; Compose does not recompose the page's loop.
internal class SelfieVerifyBadgeView(context: Context): FrameLayout(context) {
    private val d=resources.displayMetrics.density
    private val theme=SelfieVerifyBadgeTheme(context)
    private val driver=SelfieVerifyBadgeDriver()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var auto=false
    private var top=0.0
    private var bottom=0.0
    private var reduced=false
    private var l=M.Layout(402.0,874.0,0.0,0.0)
    private val runtime=if(Build.VERSION.SDK_INT>=33) SelfieVerifyBadgeRuntime() else null
    private var wasSuccess=false
    private var wasDone=false
    private var originalLightStatus=false
    private var originalLegacyUi=0
    private val face=View(context).apply {
        isClickable=true;isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription="Camera preview. Drag to turn the head."
    }
    private val shutter=Button(context).apply {background=null;contentDescription="Take selfie";text="";minWidth=0;minHeight=0;minimumWidth=0;minimumHeight=0}
    private val next=Button(context).apply {background=null;contentDescription="Continue";text="";minWidth=0;minHeight=0;minimumWidth=0;minimumHeight=0}
    private val header=View(context).apply {importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;contentDescription="Selfie verification. Take a clear selfie to confirm your identity and keep your account safe.";if(Build.VERSION.SDK_INT>=28) isAccessibilityHeading=true}
    private val hint=View(context).apply {importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;contentDescription="Center your face in the frame";accessibilityLiveRegion=ACCESSIBILITY_LIVE_REGION_POLITE}
    private var detectedHint=false
    private val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {override fun onChange(selfChange: Boolean) {readMotionPreference()}}
    init {
        setWillNotDraw(false);clipChildren=true
        addView(face);addView(header);addView(hint);addView(shutter);addView(next)
        shutter.setOnClickListener {if(driver.capture(auto,reduced)) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);invalidate()}
        next.setOnClickListener {driver.back(auto,reduced);invalidate()}
        face.setOnClickListener {if(driver.spin(auto,reduced)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);invalidate()}
        face.setOnTouchListener(object: OnTouchListener {
            var downX=0f;var downY=0f;var moved=false;var tracker: VelocityTracker?=null
            override fun onTouch(v: View,e: MotionEvent): Boolean {
                when(e.actionMasked) {
                    MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;moved=false;tracker=VelocityTracker.obtain();tracker?.addMovement(e);driver.takeOver(driver.now,auto,reduced);parent?.requestDisallowInterceptTouchEvent(true)}
                    MotionEvent.ACTION_MOVE->{tracker?.addMovement(e);if(hypot(e.x-downX,e.y-downY)>8*d) moved=true;driver.drag((e.x-downX)/d.toDouble())}
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{tracker?.addMovement(e);tracker?.computeCurrentVelocity(1000)
                        driver.release((tracker?.xVelocity ?: 0f)/d.toDouble(),reduced);tracker?.recycle();tracker=null
                        if(!moved && e.actionMasked==MotionEvent.ACTION_UP) v.performClick()
                        parent?.requestDisallowInterceptTouchEvent(false)}
                }
                invalidate();return true
            }
        })
    }
    fun configure(autoPlay: Boolean,safeTop: Double,safeBottom: Double) {
        auto=autoPlay;top=safeTop;bottom=safeBottom
        l=M.Layout(width/d.toDouble(),height/d.toDouble(),top,bottom)
        requestLayout();invalidate()
    }
    private fun readMotionPreference() {reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f;invalidate()}
    override fun onAttachedToWindow() {super.onAttachedToWindow();
        if(Build.VERSION.SDK_INT>=30) originalLightStatus=((windowInsetsController?.systemBarsAppearance ?: 0) and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)!=0
        else { @Suppress("DEPRECATION") originalLegacyUi=systemUiVisibility }
        updateStatus(false);readMotionPreference();context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)}
    override fun onDetachedFromWindow() {updateStatus(originalLightStatus);if(Build.VERSION.SDK_INT<30) { @Suppress("DEPRECATION") systemUiVisibility=originalLegacyUi };context.contentResolver.unregisterContentObserver(observer);super.onDetachedFromWindow()}
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {l=M.Layout(w/d.toDouble(),h/d.toDouble(),top,bottom)}
    override fun onLayout(changed: Boolean,left: Int,top: Int,right: Int,bottom: Int) {
        fun place(v: View,x: Double,y: Double,w: Double,h: Double) {v.layout((x*d).roundToInt(),(y*d).roundToInt(),((x+w)*d).roundToInt(),((y+h)*d).roundToInt())}
        place(face,l.frameX-l.frame,l.frameY-l.frame,l.frame*2,l.frame*2)
        place(header,40.0,l.titleTop,l.w-80,90.0)
        place(hint,0.0,l.hintTop,l.w,20.0)
        place(shutter,l.frameX-36,l.shutterY-36,72.0,72.0)
        place(next,32.0,l.shutterY-28,l.w-64,56.0)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now=driver.now;val f=driver.frame(now,auto,reduced);val v=M.verified(l,f.link)
        canvas.drawColor(C.DARK)
        drawArt(canvas,f,v,now,1.0)
        canvas.save();canvas.scale(d,d)
        val light=v.light
        copy(canvas,false,1-light);copy(canvas,true,light)
        val hintAlpha=(1-light)*(1-v.puff)
        label(canvas,"Center your face in the frame",0.0,l.hintTop,l.w,14.0,C.BODY,hintAlpha*(1-f.detected),true)
        label(canvas,"Face detected",0.0,l.hintTop,l.w,14.0,C.LIME,hintAlpha*f.detected,true)
        val scale=(1-0.06*f.shutter)*(1-0.2*v.light)
        canvas.save();canvas.translate(l.frameX.toFloat(),l.shutterY.toFloat());canvas.scale(scale.toFloat(),scale.toFloat())
        paint.style=Paint.Style.FILL;paint.shader=null;paint.color=mixColor(C.BUTTON,C.LIME,f.detected);paint.alpha=((1-light)*255).roundToInt()
        canvas.drawCircle(0f,0f,36f,paint);camera(canvas,1-light);canvas.restore()
        paint.style=Paint.Style.FILL;paint.color=C.INK;paint.alpha=(light*255).roundToInt()
        val cy=l.shutterY-28+(1-light)*16
        canvas.drawRoundRect(32f,cy.toFloat(),(l.w-32).toFloat(),(cy+56).toFloat(),28f,28f,paint)
        label(canvas,"Continue",32.0,cy+18,l.w-64,16.0,Color.WHITE,light,true)
        paint.color=Color.WHITE;paint.alpha=(f.flash*0.7*255).roundToInt();canvas.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),paint)
        canvas.restore()
        val done=f.link>0.5
        shutter.visibility=if(done) INVISIBLE else VISIBLE;next.visibility=if(done) VISIBLE else INVISIBLE
        if(done!=wasDone) {
            updateStatus(done)
            face.contentDescription=if(done) "Verified badge. Tap to spin it." else "Camera preview. Drag to turn the head."
            header.contentDescription=if(done) "You’re verified. Your identity is confirmed. Sending and receiving have no limits now." else "Selfie verification. Take a clear selfie to confirm your identity and keep your account safe."
            wasDone=done
        }
        val found=f.detected>0.95
        if(found!=detectedHint) {hint.contentDescription=if(found) "Face detected" else "Center your face in the frame";detectedHint=found}
        hint.importantForAccessibility=if(hintAlpha>0.5) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
        val success=f.link>0.86
        if(success && !wasSuccess && driver.isManual(now,auto,reduced)) performHapticFeedback(if(Build.VERSION.SDK_INT>=30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        wasSuccess=success
        if(windowVisibility==VISIBLE && (!reduced || now-driver.lastTouch<0.4)) postInvalidateOnAnimation()
    }
    private fun updateStatus(light: Boolean) {
        if(Build.VERSION.SDK_INT>=30) windowInsetsController?.setSystemBarsAppearance(if(light) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        else {
            @Suppress("DEPRECATION")
            val flags=systemUiVisibility
            @Suppress("DEPRECATION")
            systemUiVisibility=if(light) flags or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else flags and SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }
    private fun drawArt(canvas: Canvas,f: M.Frame,v: M.Verified,now: Double,alpha: Double) {
        if(alpha<=0) return
        val saved=canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(alpha*255).roundToInt())
        if(Build.VERSION.SDK_INT>=33 && runtime!=null) runtime.bust(canvas,l,f,v,now,d,reduced)
        else {
            canvas.save();canvas.scale(d,d);paint.shader=null;paint.style=Paint.Style.FILL;paint.color=mixColor(C.BRACKET,C.LIME,f.detected);paint.alpha=(v.bust*170).roundToInt()
            canvas.drawOval((l.frameX-54).toFloat(),(l.frameY-84).toFloat(),(l.frameX+54).toFloat(),(l.frameY+84).toFloat(),paint);canvas.restore()
        }
        canvas.save();canvas.scale(d,d)
        val sc=1-0.18*(1-v.brackets);canvas.scale(sc.toFloat(),sc.toFloat(),l.frameX.toFloat(),l.frameY.toFloat())
        val p=Path()
        for(corner in arrayOf(doubleArrayOf(-1.0,-1.0,1.0,1.0),doubleArrayOf(1.0,-1.0,-1.0,1.0),doubleArrayOf(-1.0,1.0,1.0,-1.0),doubleArrayOf(1.0,1.0,-1.0,-1.0))) {
            val(sx,sy,dx,dy)=corner;val x=l.frameX+sx*l.frame;val y=l.frameY+sy*l.frame
            p.moveTo(x.toFloat(),(y+dy*30).toFloat());p.lineTo(x.toFloat(),(y+dy*10).toFloat());p.quadTo(x.toFloat(),y.toFloat(),(x+dx*10).toFloat(),y.toFloat());p.lineTo((x+dx*30).toFloat(),y.toFloat())
        }
        paint.shader=null;paint.color=mixColor(C.BRACKET,C.LIME,f.detected);paint.alpha=(v.brackets*255).roundToInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=2.5f;paint.strokeCap=Paint.Cap.ROUND;paint.pathEffect=DashPathEffect(floatArrayOf(9f,6f),0f)
        canvas.drawPath(p,paint);paint.pathEffect=null;paint.style=Paint.Style.FILL;paint.alpha=255;canvas.restore()
        if(Build.VERSION.SDK_INT>=33 && runtime!=null) runtime.cushion(canvas,l,f,v,d)
        else fallbackCushion(canvas,f,v)
        canvas.restoreToCount(saved)
    }
    private fun fallbackCushion(canvas: Canvas,f: M.Frame,v: M.Verified) {
        if(f.link<=0) return
        canvas.save();canvas.scale(d,d)
        fun flower(radius: Double): Path {
            val p=Path()
            for(i in 0..192) {val a=i*2*PI/192;val r=radius*(1+0.09*cos(8*a));val x=(l.frameX+r*cos(a)).toFloat();val y=(l.frameY+r*sin(a)).toFloat();if(i==0) p.moveTo(x,y) else p.lineTo(x,y)}
            p.close();return p
        }
        paint.shader=null;paint.color=Color.WHITE;paint.alpha=(min(1.0,f.link/0.04)*255).roundToInt();paint.style=if(v.puff<0.1) Paint.Style.STROKE else Paint.Style.FILL;paint.strokeWidth=2f
        canvas.drawPath(flower(v.size),paint)
        if(v.badge>0.5) {
            canvas.save();canvas.scale(max(0.035,abs(cos(v.yaw+f.spin))).toFloat(),1f,l.frameX.toFloat(),l.frameY.toFloat())
            paint.color=C.INK;paint.style=Paint.Style.FILL;paint.alpha=255;canvas.drawPath(flower(v.badge),paint)
            val tick=Path().apply {moveTo((l.frameX-v.badge*0.34).toFloat(),(l.frameY+v.badge*0.02).toFloat());lineTo((l.frameX-v.badge*0.1).toFloat(),(l.frameY+v.badge*0.26).toFloat());lineTo((l.frameX+v.badge*0.36).toFloat(),(l.frameY-v.badge*0.22).toFloat())}
            val partial=Path();val measure=PathMeasure(tick,false);measure.getSegment(0f,(measure.length*v.check).toFloat(),partial,true)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=(v.badge*0.15).toFloat();paint.color=C.BRACKET;canvas.drawPath(partial,paint);canvas.restore()
        }
        paint.style=Paint.Style.FILL;canvas.restore()
    }
    private fun copy(canvas: Canvas,light: Boolean,alpha: Double) {
        label(canvas,if(light) "You’re verified" else "Selfie verification",40.0,l.titleTop,l.w-80,20.0,if(light) C.INK else C.TITLE,alpha,true,-0.4)
        label(canvas,if(light) "Your identity is confirmed. Sending and receiving have no limits now." else "Take a clear selfie to confirm your identity and keep your account safe.",40.0,l.titleTop+34,l.w-80,15.0,if(light) C.INK_BODY else C.BODY,alpha,false,0.0,21f)
    }
    private data class TextKey(val text: String,val width: Int,val size: Float,val bold: Boolean,val tracking: Float,val lineHeight: Float)
    private val labels=mutableMapOf<TextKey,StaticLayout>()
    private fun label(canvas: Canvas,text: String,x: Double,y: Double,w: Double,size: Double,color: Int,alpha: Double,bold: Boolean,tracking: Double=0.0,lineHeight: Float=0f) {
        if(alpha<=0) return
        val key=TextKey(text,w.roundToInt().coerceAtLeast(1),size.toFloat(),bold,tracking.toFloat(),lineHeight)
        val layout=labels.getOrPut(key) {
            val tp=TextPaint(textPaint).apply {typeface=if(bold) theme.semibold else theme.regular;textSize=size.toFloat();letterSpacing=(tracking/size).toFloat()}
            StaticLayout.Builder.obtain(text,0,text.length,tp,key.width).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false)
                .setLineSpacing(if(lineHeight>0) lineHeight-(tp.fontMetrics.descent-tp.fontMetrics.ascent) else 0f,1f).build()
        }
        layout.paint.color=color;layout.paint.alpha=(alpha.coerceIn(0.0,1.0)*255).roundToInt()
        canvas.save();canvas.translate(x.toFloat(),y.toFloat());layout.draw(canvas);canvas.restore()
    }
    private fun camera(canvas: Canvas,alpha: Double) {
        canvas.save();canvas.translate(-13f,-13f);canvas.scale(26f/24,26f/24)
        val p=Path().apply {moveTo(4f,8.5f);arcTo(RectF(4f,6f,9f,11f),180f,90f);lineTo(7.7f,6f);lineTo(9f,4f);lineTo(15f,4f);lineTo(16.3f,6f);lineTo(17.5f,6f);arcTo(RectF(15f,6f,20f,11f),270f,90f);lineTo(20f,16.5f);arcTo(RectF(15f,14f,20f,19f),0f,90f);lineTo(6.5f,19f);arcTo(RectF(4f,14f,9f,19f),90f,90f);close()}
        paint.color=C.TITLE;paint.alpha=(alpha*255).roundToInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=1.7f;canvas.drawPath(p,paint);canvas.drawCircle(12f,12.5f,3.4f,paint);paint.style=Paint.Style.FILL;canvas.restore()
    }
    private fun mixColor(a: Int,b: Int,t: Double): Int {
        fun channel(x: Int,y: Int): Int = (((x/255.0).pow(2.2)+((y/255.0).pow(2.2)-(x/255.0).pow(2.2))*t).pow(1/2.2)*255).roundToInt().coerceIn(0,255)
        return Color.rgb(channel(Color.red(a),Color.red(b)),channel(Color.green(a),Color.green(b)),channel(Color.blue(a),Color.blue(b)))
    }
}

@RequiresApi(33)
private class SelfieVerifyBadgeRuntime {
    private val bust=RuntimeShader(SelfieVerifyBadgeShader.BUST)
    private val cushion=RuntimeShader(SelfieVerifyBadgeShader.CUSHION)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private fun RuntimeShader.uniform(name: String,v: Double) {setFloatUniform(name,v.toFloat())}
    fun bust(canvas: Canvas,l: M.Layout,f: M.Frame,v: M.Verified,now: Double,density: Float,reduced: Boolean) {
        bust.setFloatUniform("uCentre",l.frameX.toFloat(),(l.frameY+l.frame*0.04*(1-v.collapse)).toFloat())
        bust.uniform("uUnit",l.frame*1.55);bust.uniform("uYaw",f.yaw+if(reduced) 0.0 else 0.38*sin(2*PI*now/M.LOOP))
        bust.uniform("uAlpha",v.bust);bust.uniform("uScan",f.scan);bust.setFloatUniform("uPixelRatio",density)
        bust.uniform("uDetect",f.detected);bust.uniform("uCollapse",v.collapse);bust.setFloatUniform("uDensity",density)
        paint.shader=bust;paint.alpha=255;canvas.drawRect(0f,0f,(l.w*density).toFloat(),(l.h*density).toFloat(),paint)
    }
    fun cushion(canvas: Canvas,l: M.Layout,f: M.Frame,v: M.Verified,density: Float) {
        if(f.link<=0) return
        cushion.setFloatUniform("uCentre",l.frameX.toFloat(),l.frameY.toFloat());cushion.uniform("uSize",v.size)
        cushion.uniform("uShape",v.shape);cushion.uniform("uPuff",v.puff);cushion.uniform("uBadge",v.badge)
        cushion.uniform("uYaw",v.yaw+f.spin);cushion.uniform("uCheck",v.check);cushion.setFloatUniform("uPixelRatio",density);cushion.setFloatUniform("uDensity",density)
        paint.shader=cushion;paint.alpha=(min(1.0,f.link/0.04)*255).roundToInt();canvas.drawRect(0f,0f,(l.w*density).toFloat(),(l.h*density).toFloat(),paint)
    }
}
