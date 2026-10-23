package com.ngocdevv.nativecook.blocks.animations.particlesigninring

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.ngocdevv.nativecook.R
import kotlin.math.roundToInt

// The original app declared this class in MainActivity.kt, next to the Activity. The Activity's
// own work (system bars, pause/resume, saved state) is in ParticleSignInRingDemo.
/** Real native buttons/text retain keyboard navigation and TalkBack semantics. */
internal class SignInScreen(context: Context) : FrameLayout(context) {
    val animation = SignInAnimationView(context)
    private val replay = ImageButton(context)
    private val nameLabel = TextView(context)
    private val statusLabel = TextView(context)
    private val speedBar = LinearLayout(context)
    private val speeds = doubleArrayOf(0.5,1.0,2.0)
    private val speedLabels = arrayOf("0.5×","1×","2×")
    private val speedButtons = Array(3) { Button(context) }
    private val density = resources.displayMetrics.density
    private var speedBarWidth = dp(24f) // two 8-dp gaps and two 4-dp paddings

    init {
        setBackgroundColor(Color.WHITE)
        clipChildren = false
        addView(animation,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
        replay.apply {
            setImageResource(R.drawable.particlesigninring_chevron_back)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            contentDescription = context.getString(R.string.particlesigninring_replay)
            background = ripple(Color.WHITE,24f)
            elevation = dp(4f).toFloat()
            setPadding(0,0,0,0)
            setOnClickListener { this@SignInScreen.animation.replay() }
        }
        addView(replay,LayoutParams(dp(48f),dp(48f)))
        nameLabel.apply {
            text = context.getString(R.string.particlesigninring_user_name)
            setTextColor(Color.rgb(5,5,5))
            setTextSize(TypedValue.COMPLEX_UNIT_SP,20f)
            typeface = Typeface.create("sans-serif",Typeface.BOLD)
            letterSpacing = -0.35f/20
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
        }
        statusLabel.apply {
            text = context.getString(R.string.particlesigninring_signing_in)
            setTextColor(Color.rgb(142,142,147))
            setTextSize(TypedValue.COMPLEX_UNIT_SP,16f)
            letterSpacing = -0.15f/16
            gravity = Gravity.CENTER
            includeFontPadding = false
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addView(nameLabel,LayoutParams(dp(300f),dp(25f)))
        addView(statusLabel,LayoutParams(dp(300f),dp(21f)))
        speedBar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE,22f)
            elevation = dp(4f).toFloat()
            setPadding(dp(4f),dp(4f),dp(4f),dp(4f))
        }
        for (index in speedButtons.indices) {
            val button = speedButtons[index]
            button.apply {
                text = speedLabels[index]
                contentDescription = "Animation speed ${speedLabels[index]}"
                isAllCaps = false
                setSingleLine(true)
                minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                setPadding(dp(12f),0,dp(12f),0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP,15f)
                typeface = Typeface.create("sans-serif-medium",Typeface.NORMAL)
                letterSpacing = -0.2f/15
                gravity = Gravity.CENTER
                includeFontPadding = false
                stateListAnimator = null
                setOnClickListener { selectSpeed(speeds[index]) }
            }
            val buttonWidth = maxOf(dp(52f),kotlin.math.ceil(button.paint.measureText(speedLabels[index])+dp(24f)).toInt())
            speedBarWidth += buttonWidth
            val params = LinearLayout.LayoutParams(buttonWidth,dp(36f))
            if (index > 0) params.marginStart = dp(8f)
            speedBar.addView(button,params)
        }
        addView(speedBar,LayoutParams(speedBarWidth,dp(44f)))
        selectSpeed(1.0)
    }

    fun selectSpeed(speed: Double) {
        animation.playbackSpeed = speed
        for (index in speedButtons.indices) {
            val selected = speeds[index] == speed
            speedButtons[index].isSelected = selected
            speedButtons[index].setTextColor(if (selected) Color.WHITE else Color.rgb(17,17,17))
            speedButtons[index].background = ripple(if (selected) Color.rgb(17,17,17) else Color.WHITE,18f)
            if (Build.VERSION.SDK_INT >= 30) speedButtons[index].stateDescription = if (selected) "Selected" else ""
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right-left; val height = bottom-top
        animation.layout(0,0,width,height)
        replay.layout(dp(20f),dp(68f),dp(68f),dp(116f))
        val textTop = ((height/density-441f)/2+64+331+48+331*SignInMotion.TEXT_OFFSET_Y).toFloat()
        val labelLeft = (width-dp(300f))/2
        nameLabel.layout(labelLeft,dp(textTop),labelLeft+dp(300f),dp(textTop+25))
        statusLabel.layout(labelLeft,dp(textTop+30),labelLeft+dp(300f),dp(textTop+51))
        val speedRight = width-dp(20f); val speedBottom = height-dp(48f)
        speedBar.layout(speedRight-speedBarWidth,speedBottom-dp(44f),speedRight,speedBottom)
    }
    private fun dp(value: Float) = (value*density).roundToInt()
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius*density
    }
    private fun ripple(color: Int, radius: Float) = RippleDrawable(
        ColorStateList.valueOf(0x22111111),rounded(color,radius),rounded(Color.WHITE,radius))
}
