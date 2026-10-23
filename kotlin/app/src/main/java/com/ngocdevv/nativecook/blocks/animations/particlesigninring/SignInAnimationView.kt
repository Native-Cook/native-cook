package com.ngocdevv.nativecook.blocks.animations.particlesigninring

import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.View
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Hardware accelerated Android Canvas rendering driven by the display vsync. */
class SignInAnimationView(context: Context) : View(context), Choreographer.FrameCallback {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val destination = RectF()
    private val scene = SignInMotion.Scene()
    private val avatar = loadBitmap("avatar.png")
    private val icons = arrayOf("photos.png","weather.png","app-store.png","messages.png").map(::loadBitmap)
    private val appleFont = Typeface.createFromAsset(context.assets,"$ASSET_DIR/FontAwesome.ttf")
    private val choreographer = Choreographer.getInstance()
    private var previousFrameNanos = 0L
    private var running = false
    var elapsedMs = 0.0
    var playbackSpeed = 1.0
        set(value) { field = if (value.isFinite()) value.coerceIn(0.25,4.0) else 1.0 }

    init {
        setBackgroundColor(Color.WHITE)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun loadBitmap(name: String): Bitmap = context.assets.open("$ASSET_DIR/$name").use { BitmapFactory.decodeStream(it) }

    fun resumeAnimation() {
        if (running) return
        running = true
        previousFrameNanos = 0L
        choreographer.postFrameCallback(this)
    }
    fun pauseAnimation() {
        running = false
        choreographer.removeFrameCallback(this)
        previousFrameNanos = 0L
    }
    fun replay() {
        elapsedMs = 0.0
        previousFrameNanos = 0L
        invalidate()
    }
    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val delta = if (previousFrameNanos == 0L) null else (frameTimeNanos-previousFrameNanos)/1_000_000.0
        elapsedMs += SignInMotion.frameDelta(delta,playbackSpeed)
        previousFrameNanos = frameTimeNanos
        invalidate()
        choreographer.postFrameCallback(this)
    }
    override fun onDetachedFromWindow() {
        pauseAnimation()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        SignInMotion.sample(elapsedMs,into=scene)
        val save = canvas.save()
        canvas.scale(density,density)
        val screenWidth = width/density
        val screenHeight = height/density
        // App.tsx centers the 331 + 48 + 62 group, then translates it down 64 dp.
        val groupTop = (screenHeight-441f)/2+64
        canvas.translate(screenWidth/2 + (SignInMotion.STAGE_SIZE*SignInMotion.STAGE_OFFSET_X).toFloat(),
            groupTop + (SignInMotion.STAGE_SIZE/2 + SignInMotion.STAGE_SIZE*SignInMotion.STAGE_OFFSET_Y).toFloat())
        paint.typeface = null
        paint.style = Paint.Style.FILL
        for (dot in scene.dots) {
            if (dot.alpha <= 0 || dot.radius <= 0) continue
            paint.color = Color.argb(channel(dot.alpha),channel(dot.red),channel(dot.green),channel(dot.blue))
            canvas.drawCircle(dot.x.toFloat(),dot.y.toFloat(),dot.radius.toFloat(),paint)
        }
        drawIdentity(canvas)
        for (index in 0..3) {
            val frame = scene.icons[index]
            if (frame.glyphOpacity <= 0) continue
            val meta = SignInMotion.particles[SignInMotion.carrierIndices[index]]
            val angle = meta.angle+scene.rotation
            val radius = SignInMotion.STAGE_SIZE*meta.radius*scene.radialScale
            val iconSave = canvas.save()
            canvas.translate((cos(angle)*radius).toFloat(),(sin(angle)*radius).toFloat())
            canvas.scale(frame.scale.toFloat(),frame.scale.toFloat())
            // Original CircularServiceIcon is a 90-dp mask around 112-dp artwork.
            drawCircularImage(canvas,icons[index],90f,112f,frame.glyphOpacity)
            canvas.restoreToCount(iconSave)
        }
        paint.alpha = 255
        canvas.restoreToCount(save)
    }

    private fun drawIdentity(canvas: Canvas) {
        val identity = scene.identity
        if (identity.appleOpacity > 0) {
            val appleSave = canvas.save()
            canvas.scale(identity.appleScale.toFloat(),identity.appleScale.toFloat())
            paint.typeface = appleFont
            paint.textSize = (SignInMotion.STAGE_SIZE*SignInMotion.APPLE_SIZE).toFloat()
            paint.textAlign = Paint.Align.CENTER
            val baseline = -(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2
            // Two real font layers reproduce the source blue → black crossfade.
            paint.color = Color.rgb(6,105,210)
            paint.alpha = channel(identity.appleOpacity*identity.blueOpacity)
            canvas.drawText("\uf179",0f,baseline,paint)
            paint.color = Color.BLACK
            paint.alpha = channel(identity.appleOpacity*identity.blackOpacity)
            canvas.drawText("\uf179",0f,baseline,paint)
            paint.typeface = null
            canvas.restoreToCount(appleSave)
        }
        if (identity.avatarOpacity > 0) {
            val avatarSave = canvas.save()
            canvas.scale(identity.avatarScale.toFloat(),identity.avatarScale.toFloat())
            val size = (SignInMotion.STAGE_SIZE*SignInMotion.AVATAR_SIZE).toFloat()
            paint.color = Color.rgb(242,242,247)
            paint.alpha = channel(identity.avatarOpacity)
            canvas.drawCircle(0f,0f,size/2,paint)
            drawCircularImage(canvas,avatar,size,size,identity.avatarOpacity)
            paint.color = Color.WHITE
            paint.alpha = channel(identity.avatarOpacity*0.92)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            canvas.drawCircle(0f,0f,(size-1.5f)/2,paint)
            paint.style = Paint.Style.FILL
            canvas.restoreToCount(avatarSave)
        }
    }

    private fun drawCircularImage(canvas: Canvas, bitmap: Bitmap, diameter: Float, artworkSize: Float, opacity: Double) {
        val save = canvas.save()
        clip.reset()
        clip.addCircle(0f,0f,diameter/2,Path.Direction.CW)
        canvas.clipPath(clip)
        paint.alpha = channel(opacity)
        destination.set(-artworkSize/2,-artworkSize/2,artworkSize/2,artworkSize/2)
        canvas.drawBitmap(bitmap,null,destination,paint)
        canvas.restoreToCount(save)
    }
    private fun channel(value: Double) = (value.coerceIn(0.0,1.0)*255).roundToInt()

    private companion object {
        /** The block's folder under the app's `assets/` (the original app kept these files at the root). */
        const val ASSET_DIR = "particlesigninring"
    }
}
