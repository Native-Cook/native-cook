package com.ngocdevv.nativecook.blocks.modals.lanternwishsend

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import androidx.core.view.WindowCompat
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.InputFilter
import android.view.Choreographer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.*

@Composable
fun LanternWishSend(autoPlay: Boolean = false) {
    AndroidView(factory = { LanternWishSendView(it, autoPlay) }, modifier = Modifier.fillMaxSize())
}

internal class LanternWishSendView(context: Context, private val autoPlay: Boolean) : FrameLayout(context), Choreographer.FrameCallback {
    private val density = resources.displayMetrics.density
    private val art = LanternWishSendArt(context)
    private val driver = LanternWishSendDriver()
    private val reduced get() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    private var sample = LanternWishSendMotion.Frame()
    private var previous = 0L
    private var counted = false
    private var previousStatusAppearance: Boolean? = null
    private fun statusController() = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>().firstOrNull()?.let { WindowCompat.getInsetsController(it.window, this) }
    private var tracker: VelocityTracker? = null
    private val serif = Typeface.createFromAsset(context.assets, "lanternwishsend/lanternwishsend_InstrumentSerif-Italic.ttf")
    private val regular = Typeface.createFromAsset(context.assets, "lanternwishsend/lanternwishsend_Inter-Regular.ttf")
    private val semi = Typeface.createFromAsset(context.assets, "lanternwishsend/lanternwishsend_Inter-SemiBold.ttf")
    private val icons = Typeface.createFromAsset(context.assets, "lanternwishsend/lanternwishsend_Ionicons.ttf")
    private fun dp(v: Double) = (v * density).roundToInt()
    private fun text(value: String, font: Typeface, size: Float, color: Int) = TextView(context).apply {
        text = value; typeface = font; textSize = size; setTextColor(color); includeFontPadding = false
        setPadding(0,0,0,0)
    }
    private val title = text("Send a wish", serif, 40f, Color.rgb(255,241,222))
    private val count = text("1,248 lanterns lit tonight", regular, 14f, Color.rgb(217,198,242))
    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = dp(180.0)
        setPadding(dp(20.0),dp(20.0),dp(20.0),dp(20.0))
        background = GradientDrawable().apply { setColor(Color.rgb(255,246,233)); cornerRadius = dp(20.0).toFloat() }
        elevation = dp(8.0).toFloat()
    }
    private val recipient = text("To Grandma Lin", semi, 13f, Color.rgb(176,112,90)).apply { letterSpacing = 0.4f / 13 }
    private val note = EditText(context).apply {
        typeface = serif; textSize = 26f; includeFontPadding = false
        setTextColor(Color.rgb(58,31,79)); setHintTextColor(Color.rgb(182,154,138))
        background = null; setPadding(0,0,0,0); gravity = Gravity.TOP
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        filters = arrayOf(InputFilter.LengthFilter(120))
        setLineSpacing(dp(32.0) - paint.fontSpacing, 1f)
        setText("Happy 80th, Grandma Lin. Thank you for every bedtime story.")
        hint = "Write a wish…"; contentDescription = "Your wish"
    }
    private val button = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(Color.rgb(255,196,107)); cornerRadius = dp(29.0).toFloat() }
        isClickable = true; isFocusable = true; contentDescription = "Light and send the lantern"
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.className = "android.widget.Button"
            }
        }
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            driver.send(); startFrames()
        }
        setOnTouchListener { _, e ->
            val scale = if (e.actionMasked == MotionEvent.ACTION_DOWN) 0.96f else 1f
            scaleX = scale; scaleY = scale; false
        }
    }
    private val hit = View(context)
    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.rgb(42,26,74))
        title.gravity = Gravity.CENTER; count.gravity = Gravity.CENTER
        addView(title); addView(count); addView(hit); addView(card); addView(button)
        card.addView(recipient, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        card.addView(note, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8.0) })
        val flame = text("\uF313", icons, 20f, Color.rgb(58,31,79)).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        val caption = text("Light & send", semi, 17f, Color.rgb(58,31,79)).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        button.addView(flame, LinearLayout.LayoutParams(dp(20.0), dp(20.0)))
        button.addView(caption, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8.0) })
        hit.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) { tracker?.recycle(); tracker = VelocityTracker.obtain() }
            tracker?.addMovement(e)
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                tracker?.computeCurrentVelocity(1000)
                driver.flickUp((tracker?.yVelocity ?: 0f) / density)
                tracker?.recycle(); tracker = null; startFrames()
            } else if (e.actionMasked == MotionEvent.ACTION_CANCEL) { tracker?.recycle(); tracker = null }
            true
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w,h)
        fun measure(v: View, width: Int, height: Int, exact: Boolean = true) {
            v.measure(MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height.coerceAtLeast(0), if(exact) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST))
        }
        measure(title,w-dp(48.0),dp(60.0),false); measure(count,w-dp(48.0),dp(30.0),false)
        measure(card,w-dp(80.0),h,false); measure(button,w-dp(40.0),dp(58.0))
        val top = rootWindowInsets?.systemWindowInsetTop ?: 0
        measure(hit,w,((h*0.6).roundToInt()-dp(200.0)-top).coerceAtLeast(0))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val insetTop = rootWindowInsets?.systemWindowInsetTop ?: 0
        val insetBottom = rootWindowInsets?.systemWindowInsetBottom ?: 0
        title.layout(dp(24.0),insetTop+dp(16.0),width-dp(24.0),insetTop+dp(16.0)+title.measuredHeight)
        count.layout(dp(24.0),title.bottom+dp(4.0),width-dp(24.0),title.bottom+dp(4.0)+count.measuredHeight)
        val cy = (height*0.6).roundToInt()
        card.layout(dp(40.0),cy-dp(90.0),width-dp(40.0),cy-dp(90.0)+card.measuredHeight)
        hit.layout(0,insetTop+dp(100.0),width,cy-dp(100.0))
        button.layout(dp(20.0),height-insetBottom-dp(76.0),width-dp(20.0),height-insetBottom-dp(18.0))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(density,density)
        art.draw(canvas,width/density.toDouble(),height/density.toDouble(),sample,reduced)
        canvas.restore()
    }
    private fun startFrames() {
        Choreographer.getInstance().removeFrameCallback(this)
        previous = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        statusController()?.let { previousStatusAppearance = it.isAppearanceLightStatusBars; it.isAppearanceLightStatusBars = false }
        startFrames()
    }
    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this); tracker?.recycle(); tracker = null
        previousStatusAppearance?.let { old -> statusController()?.isAppearanceLightStatusBars = old }
        super.onDetachedFromWindow()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) startFrames() else Choreographer.getInstance().removeFrameCallback(this)
    }
    override fun doFrame(nanos: Long) {
        val wall = if(previous == 0L) 0.016 else ((nanos-previous)/1e9).coerceIn(0.0,0.5)
        previous = nanos
        sample = driver.step(wall, autoPlay && !reduced)
        val f = if(sample.sending) sample.fold else 0.0
        card.alpha = (if(sample.sending) 1-f else sample.back).toFloat()
        card.translationY = ((if(sample.sending) f*40 else (1-sample.back)*20)*density).toFloat()
        card.scaleX = (1-0.45*f).toFloat(); card.scaleY = (1-0.7*f).toFloat()
        val reached = sample.count == 1249
        if(reached && !counted) performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        )
        counted = reached
        val countText = "${if(reached) "1,249" else "1,248"} lanterns lit tonight"
        if (count.text.toString() != countText) count.text = countText
        invalidate()
        if(!reduced || driver.now-driver.at < 7 || driver.now-driver.lastTouch <= 3.5)
            Choreographer.getInstance().postFrameCallback(this)
    }
}
