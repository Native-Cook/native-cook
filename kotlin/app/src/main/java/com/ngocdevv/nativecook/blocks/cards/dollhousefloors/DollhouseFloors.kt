package com.ngocdevv.nativecook.blocks.cards.dollhousefloors

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.*

@Composable
fun DollhouseFloors(autoPlay: Boolean = false) {
    val host = LocalView.current
    DisposableEffect(host) {
        val activity = generateSequence(host.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, host) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    AndroidView(factory = { DollhouseFloorsView(it, autoPlay) }, modifier = Modifier.fillMaxSize(), onReset = null, onRelease = { it.stop() })
}

/** Small native labels and cached vector art avoid recomposing the screen on every frame. */
internal class DollhouseFloorsView(context: Context, private val autoPlay: Boolean) : FrameLayout(context) {
    private val d = resources.displayMetrics.density.toDouble()
    private val driver = DollhouseFloorsDriver()
    private var art: DollhouseFloorsArt? = null
    private var frame = DollhouseFloorsMotion.Frame()
    private var previousSelection = Int.MIN_VALUE
    private var previousOpen: Boolean? = null
    private var previous = 0L
    private var running = false
    private val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    private val ink = 0xFF2A2420.toInt()
    private val muted = 0xFF7A706A.toInt()
    private val regular = Typeface.createFromAsset(context.assets, "dollhousefloors/dollhousefloors_Inter-Regular.ttf")
    private val semi = Typeface.createFromAsset(context.assets, "dollhousefloors/dollhousefloors_Inter-SemiBold.ttf")
    private val bold = Typeface.createFromAsset(context.assets, "dollhousefloors/dollhousefloors_Inter-Bold.ttf")
    private val icons = Typeface.createFromAsset(context.assets, "dollhousefloors/dollhousefloors_Ionicons.ttf")
    private val labels = mutableMapOf<String, TextView>()
    private val chips = mutableListOf<TextView>()
    private val info = FrameLayout(context)
    private val model = View(context)
    private val back: TextView
    private val heart: TextView
    private val cta: TextView
    private var startY = 0f
    private var startTime = 0L
    private var dragging = false
    private var tracker: VelocityTracker? = null
    private val ticker = object : Runnable {
        override fun run() {
            if (!running || !isAttachedToWindow || windowVisibility != VISIBLE) return
            val now = SystemClock.elapsedRealtimeNanos()
            val wall = if (previous == 0L) 0.016 else ((now - previous) / 1e9).coerceIn(0.0, 0.5)
            previous = now
            frame = driver.step(wall, autoPlay && !reduced)
            updateLabels(); positionChildren(); invalidate()
            if ((autoPlay && !reduced) || !driver.settled()) postOnAnimation(this) else { running = false; previous = 0L }
        }
    }
    init {
        setWillNotDraw(false); setBackgroundColor(0xFFFBF6EE.toInt()); clipChildren = false
        back = button("",44.0,22.0); icon(back,61993,22.0); back.contentDescription = "Back"
        heart = button("",44.0,22.0); icon(heart,62327,21.0); heart.contentDescription = "Save home"
        label("kicker","FOR SALE · OPEN HOUSE SAT",semi,12.0,0xFFB07A45.toInt()).letterSpacing = (1.1 / 12).toFloat()
        label("title","Maple Row Townhouse",bold,26.0,ink)
        label("meta","3 bd · 2 ba · 122 m² · Built 2019",regular,15.0,muted)
        addView(model)
        model.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        model.accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info); info.className = "android.widget.SeekBar"
            }
        }
        model.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY=event.y; startTime=event.eventTime; dragging=false; tracker?.recycle(); tracker=VelocityTracker.obtain(); tracker?.addMovement(event); true
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(event)
                    val dy=(event.y-startY)/d
                    if (!dragging && abs(dy)>8) { dragging=true; driver.panStart(); parent?.requestDisallowInterceptTouchEvent(true) }
                    if (dragging) { driver.pan(dy); wake() }; true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(event); tracker?.computeCurrentVelocity(1000)
                    if (dragging) { driver.panEnd((tracker?.yVelocity?:0f)/d); buzz(false) }
                    else if (event.actionMasked==MotionEvent.ACTION_UP && event.eventTime-startTime<=500) {
                        if (driver.tap((event.y+model.top)/d,width/d,height/d)) buzz(true)
                        model.performClick()
                    }
                    tracker?.recycle(); tracker=null; wake(); true
                }
                else -> true
            }
        }
        addView(info); info.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        info.accessibilityLiveRegion=ACCESSIBILITY_LIVE_REGION_POLITE
        label("infoTitle","Tap a floor to look inside",semi,16.0,ink,info)
        label("infoText","3 levels · 140 m² with the attic",regular,14.0,muted,info)
        listOf("Roof","First","Ground","").forEachIndexed { k,name ->
            val b=button(name,40.0,20.0); b.tag=if(k==3)-1 else 2-k
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,14f)
            b.typeface=semi
            b.contentDescription=if(k==3)"Pull the floors apart" else "$name floor"
            b.setOnClickListener { buzz(true); driver.pick(b.tag as Int); wake() }
            chips.add(b)
        }
        label("price","$684,000",bold,22.0,ink)
        label("est","Est. $3,210/mo",regular,13.0,muted)
        cta=button("Book a viewing",56.0,28.0,ink); cta.typeface=semi; cta.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,16f); cta.setTextColor(-1)
        cta.contentDescription="Book a viewing"
        updateLabels()
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets -> positionChildren(); insets }
    }
    private fun label(key: String,text: String,font: Typeface,size: Double,color: Int,parent: FrameLayout=this): TextView {
        val v=TextView(context); v.text=text; v.typeface=font; v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,size.toFloat()); v.setTextColor(color); v.includeFontPadding=false; v.setSingleLine(true)
        labels[key]=v; parent.addView(v); return v
    }
    private fun background(color: Int,radius: Double,border: Int?=null): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius=(radius*d).toFloat(); if(border!=null)setStroke(max(1,d.roundToInt()),border)
    }
    private fun button(text: String,height: Double,radius: Double,color: Int=-1): TextView {
        val b=TextView(context); b.text=text; b.gravity=android.view.Gravity.CENTER; b.setTextColor(ink); b.includeFontPadding=false; b.isClickable=true; b.isFocusable=true
        b.background=background(color,radius)
        b.accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info); info.className = "android.widget.Button"
            }
        }
        addView(b)
        b.setOnTouchListener { v,e ->
            if(e.actionMasked==MotionEvent.ACTION_DOWN){v.scaleX=0.96f;v.scaleY=0.96f}
            if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL){v.scaleX=1f;v.scaleY=1f}
            false
        }
        return b
    }
    private fun icon(v: TextView,code: Int,size: Double) { v.text=String(Character.toChars(code)); v.typeface=icons; v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,size.toFloat()) }
    private fun buzz(selection: Boolean) { performHapticFeedback(if(selection)HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.VIRTUAL_KEY) }
    private fun updateLabels() {
        if (frame.sel != previousSelection) {
            val names=listOf("Ground floor · 68 m²","First floor · 54 m²","Roof · 18 m²")
            val rooms=listOf("Living room, kitchen, half bath","2 bedrooms, bath, study nook","Slate roof, attic storage, solar ready")
            labels["infoTitle"]?.text=if(frame.sel>=0)names[frame.sel] else "Tap a floor to look inside"
            labels["infoText"]?.text=if(frame.sel>=0)rooms[frame.sel] else "3 levels · 140 m² with the attic"
            chips.forEach { b ->
                if((b.tag as Int)>=0) {
                    val on=b.tag==frame.sel; b.isSelected=on; b.setTextColor(if(on)-1 else ink); b.background=background(if(on)ink else -1,20.0,if(on)ink else 0xFFEADFCF.toInt())
                }
            }
            previousSelection=frame.sel
        }
        val open=frame.e>0.5
        if(open != previousOpen) {
            if(chips.size==4){icon(chips[3],if(open)62090 else 62384,18.0);chips[3].contentDescription=if(open)"Stack the floors" else "Pull the floors apart"}
            model.contentDescription=if(open)"Floors pulled apart. Tap a floor to focus it, drag down to close" else "Cut-away model of the house. Drag up to see each floor"
            previousOpen=open
        }
    }
    private fun lh(key: String): Double { val f=labels.getValue(key).paint.fontMetrics;return ceil((f.descent-f.ascent)/d) }
    private fun place(v: View,x: Double,y: Double,w: Double,h: Double) {
        val l=(x*d).roundToInt();val t=(y*d).roundToInt();val wi=(w*d).roundToInt().coerceAtLeast(0);val he=(h*d).roundToInt().coerceAtLeast(0)
        v.measure(MeasureSpec.makeMeasureSpec(wi,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(he,MeasureSpec.EXACTLY));v.layout(l,t,l+wi,t+he)
    }
    private fun text(key: String,x: Double,y: Double,w: Double,center: Boolean=false) { val v=labels.getValue(key);v.gravity=if(center)android.view.Gravity.CENTER else android.view.Gravity.CENTER_VERTICAL;place(v,x,y,w,lh(key)) }
    private fun positionChildren() {
        if(width==0)return
        val w=width/d;val h=height/d;val base=round(h*0.63)
        val insets=ViewCompat.getRootWindowInsets(this)
        val top=(insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top?:0)/d
        val bottom=(insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom?:0)/d
        place(back,16.0,top+6,w=44.0,h=44.0);place(heart,w-60,top+6,44.0,44.0)
        text("kicker",20.0,top+62,w-40)
        val ty=top+62+lh("kicker")+4
        text("title",20.0,ty,w-40);text("meta",20.0,ty+lh("title")+4,w-40)
        place(model,0.0,top+150,w,base-top-130)
        val v=((frame.e-0.6)/0.4).coerceIn(0.0,1.0)
        place(info,20.0,base+40+(1-v)*10,w-40,lh("infoTitle")+3+lh("infoText"));info.alpha=v.toFloat()
        text("infoTitle",0.0,0.0,w-40,true);text("infoText",0.0,lh("infoTitle")+3,w-40,true)
        val widths=chips.map { if((it.tag as Int)<0)44.0 else ceil(it.paint.measureText(it.text.toString())/d)+38 }
        var x=(w-widths.sum()-24)/2
        chips.forEachIndexed { i,b ->place(b,x,h-bottom-136,widths[i],40.0);x+=widths[i]+8 }
        val by=h-bottom-72;val ph=lh("price")+2+lh("est")
        text("price",20.0,by+(56-ph)/2,160.0);text("est",20.0,by+(56-ph)/2+lh("price")+2,160.0)
        val cw=ceil(cta.paint.measureText("Book a viewing")/d)+52
        place(cta,w-20-cw,by,cw,56.0)
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) { positionChildren() }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) { super.onSizeChanged(w,h,oldw,oldh);art=DollhouseFloorsArt(w/d,h/d) }
    override fun onDraw(c: Canvas) { super.onDraw(c);c.save();c.scale(d.toFloat(),d.toFloat());art?.draw(c,frame,reduced);c.restore() }
    private fun wake() { if(!running){previous=0L;running=true;postOnAnimation(ticker)} }
    fun stop() { running=false;previous=0L;removeCallbacks(ticker);tracker?.recycle();tracker=null }
    override fun onAttachedToWindow() { super.onAttachedToWindow();if((autoPlay&&!reduced)||!driver.settled())wake() }
    override fun onDetachedFromWindow() { stop();super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE){previous=0L;if((autoPlay&&!reduced)||!driver.settled())wake()}else stop() }
}
