package com.ngocdevv.nativecook.blocks.inputs.honeydrizzlestepper

import android.content.Context
import android.database.ContentObserver
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun HoneyDrizzleStepper(autoPlay: Boolean = false) {
    AndroidView(factory = { HoneyDrizzleStepperView(it) }, modifier = Modifier.fillMaxSize(), update = { it.autoPlay = autoPlay })
}

/** Draw-time updates stay inside the View; the Compose screen never recomposes per frame. */
internal class HoneyDrizzleStepperView(context: Context): FrameLayout(context), Choreographer.FrameCallback {
    var autoPlay = false
    val core = HoneyDrizzleStepperMotion()
    private val density = resources.displayMetrics.density
    private val photo = context.assets.open("honeydrizzlestepper/honeydrizzlestepper_honey.webp").use {
        BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {
            inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        })!!
    }
    private val shader: Any? = if(Build.VERSION.SDK_INT >= 33) HoneyDrizzleStepperShader(photo) else null
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val photoShader = BitmapShader(photo,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
    private fun font(file: String) = Typeface.createFromAsset(context.assets,"honeydrizzlestepper/honeydrizzlestepper_$file.ttf")
    private val inter = font("Inter-SemiBold"); private val medium = font("Inter-Medium")
    private val fraunces = font("Fraunces-SemiBold"); private val anton = font("Anton-Regular")
    private var copy: Bitmap? = null
    private var queued = false; private var ticksSeen = 0; private var reduced = false
    private val area = HoneyDrizzleStepperAdjustable(context)
    private val minus = Button(context); private val plus = Button(context)
    private val observer = object: ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { readMotion(); wake() }
    }
    init {
        setWillNotDraw(false); clipChildren = true
        area.adjust = { core.stepBy(it); wake() }
        area.core = core; addView(area)
        for((button, dir) in listOf(minus to -1,plus to 1)) {
            button.background = null; button.text = ""; button.isSoundEffectsEnabled = false; button.isHapticFeedbackEnabled = false
            button.contentDescription = if(dir < 0) "Less honey" else "More honey"
            button.setOnTouchListener { _, event ->
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { core.touchDown(); core.stepBy(dir); wake() }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { core.touchUp(); wake() }
                }; true
            }
            button.setOnClickListener { core.stepBy(dir); wake() }
            addView(button)
        }
        area.touch = { event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { area.downY = event.y; core.touchDown(); core.dragStart(); parent?.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_MOVE -> core.dragTo(((event.y-area.downY)/density).toDouble())
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { core.dragEnd(); core.touchUp(); parent?.requestDisallowInterceptTouchEvent(false) }
            }; wake()
        }
    }
    private fun readMotion() {
        reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
        core.setStill(reduced)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        readMotion(); wake()
    }
    override fun onDetachedFromWindow() {
        context.contentResolver.unregisterContentObserver(observer); Choreographer.getInstance().removeFrameCallback(this); queued = false
        super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if(visibility == VISIBLE) wake() }
    private fun wake() { if(isAttachedToWindow && windowVisibility == VISIBLE && !queued) { queued = true; Choreographer.getInstance().postFrameCallback(this) } }
    override fun doFrame(frameTimeNanos: Long) {
        queued = false; if(!isAttachedToWindow || windowVisibility != VISIBLE) return
        core.step(frameTimeNanos/1e9,autoPlay)
        repeat((core.ticks-ticksSeen).coerceAtLeast(0)) { tick() }; ticksSeen = core.ticks
        area.contentDescription = "Honey, ${core.out.number} ${if(core.out.number == 1) "teaspoon" else "teaspoons"}"
        invalidate()
        if(!reduced || core.age >= 0 || core.dragging) wake()
    }
    private fun tick() {
        // Native system segment feedback; never a raw vibrator pulse. Segment tick first exists on API 34.
        performHapticFeedback(if(Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        copy?.recycle(); if(w <= 0 || h <= 0) return
        copy = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { bitmap ->
            val c = Canvas(bitmap); c.scale(density,density); val sy = h/density/852f; val top = 776*sy
            text(c,"BUZZ · BREAKFAST",inter,12f,28f,318*sy,0xFFFFE3C7.toInt(),4f)
            text(c,"How",fraunces,56f,26f,376*sy,0xFFFFF6EA.toInt(),-2f)
            text(c,"sweet?",fraunces,56f,26f,431*sy,0xFFFFF6EA.toInt(),-2f)
            text(c,"Drag the dipper up for more.",medium,14f,104f,760*sy,0xFFFFD1A8.toInt())
            text(c,"tsp honey",inter,20f,104f,786*sy,0xFFFFE3C7.toInt())
            textPaint.color = 0xFFFFF6EA.toInt(); c.drawRoundRect(239f,top,361f,top+58,29f,29f,textPaint)
            text(c,"−",inter,28f,269f,top+38,0xFFC23A00.toInt(),center = true)
            text(c,"+",inter,28f,331f,top+38,0xFFC23A00.toInt(),center = true)
            textPaint.color = 0xFFF2C9A8.toInt(); c.drawRect(299.5f,top+16,300.5f,top+42,textPaint)
        }
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        val sy = height/density/852f
        area.layout(0,0,width,(556*sy*density).toInt())
        minus.layout((239*density).toInt(),(776*sy*density).toInt(),(300*density).toInt(),((776*sy+58)*density).toInt())
        plus.layout((300*density).toInt(),(776*sy*density).toInt(),(361*density).toInt(),((776*sy+58)*density).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        val sy = height/density/852f; val f = core.out
        canvas.save(); canvas.scale(density,density)
        if(Build.VERSION.SDK_INT >= 33) {
            val effect = shader as HoneyDrizzleStepperShader; effect.update(f,sy)
            canvas.drawRect(0f,0f,width/density,height/density,effect.paint)
        } else {
            // API 26–32: original photo, no flow/growth. Value, touch, accessibility and eased number remain usable.
            val m = Matrix(); m.setScale(520f/1024,520f/1024); m.postTranslate(-12f,10*sy)
            photoShader.setLocalMatrix(m); photoPaint.shader = photoShader
            canvas.drawRect(0f,0f,width/density,height/density,photoPaint)
        }
        canvas.restore(); copy?.let { canvas.drawBitmap(it,0f,0f,null) }
        canvas.save(); canvas.scale(density,density); canvas.translate(30f,804*sy); canvas.scale(f.pop.toFloat(),f.pop.toFloat())
        text(canvas,f.number.toString(),anton,128f,0f,0f,0xFFFFF6EA.toInt()); canvas.restore()
    }
    private fun text(c: Canvas,s: String,font: Typeface,size: Float,x: Float,y: Float,color: Int,tracking: Float = 0f,center: Boolean = false) {
        textPaint.typeface = font; textPaint.textSize = size; textPaint.color = color
        // Shape with the full line as context: fractional, kerned advances, plus the reference's explicit tracking.
        val width = textPaint.getRunAdvance(s,0,s.length,0,s.length,false,s.length)+tracking*s.length
        val left = x-if(center) width/2 else 0f
        for(i in s.indices) {
            val at = textPaint.getRunAdvance(s,0,s.length,0,s.length,false,i)+tracking*i
            c.drawTextRun(s,i,i+1,0,s.length,left+at,y,false,textPaint)
        }
    }
}

internal class HoneyDrizzleStepperAdjustable(context: Context): View(context) {
    lateinit var core: HoneyDrizzleStepperMotion
    var adjust: ((Int) -> Unit)? = null
    var touch: ((MotionEvent) -> Unit)? = null
    var downY = 0f
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; isFocusable = true; contentDescription = "Honey, 2 teaspoons" }
    override fun onTouchEvent(event: MotionEvent): Boolean { touch?.invoke(event); if(event.actionMasked == MotionEvent.ACTION_UP) performClick(); return true }
    override fun performClick(): Boolean { super.performClick(); return true }
    @Suppress("DEPRECATION") // RangeInfo.obtain keeps the same semantics on the API 26 minimum.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info); info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,1f,5f,core.out.number.toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
    }
    override fun performAccessibilityAction(action: Int,arguments: Bundle?): Boolean {
        when(action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> adjust?.invoke(1)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> adjust?.invoke(-1)
            android.R.id.accessibilityActionSetProgress -> {
                val value = arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false
                val target = kotlin.math.floor(value+.5f).toInt().coerceIn(1,5)
                adjust?.invoke(target-(if(core.age >= 0) core.e1.toInt() else core.out.number))
            }
            else -> return super.performAccessibilityAction(action,arguments)
        }; return true
    }
}
