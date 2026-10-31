package com.ngocdevv.nativecook.blocks.modals.chromematchreveal

import android.content.Context
import android.app.Activity
import android.content.ContextWrapper
import android.util.Log
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.semantics.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

private const val LOOP = 8.0
private fun smooth(v: Double): Double { val x = v.coerceIn(0.0, 1.0); return x*x*(3-2*x) }
private fun back(v: Double): Double { val x = v.coerceIn(0.0, 1.0)-1; return 1+2.3*x*x*x+1.3*x*x }
private data class ChromeMatchRevealChannels(val k: Double, val squash: Double, val star: Double, val look: Double, val copy: Double, val buttons: Double, val glint: Double)
private fun chromeMatchRevealChannels(a: Double, b: Double, reduced: Boolean): ChromeMatchRevealChannels {
    val meet = a - .95
    val out = if (b >= 0) (b/.7).coerceIn(0.0,1.0).pow(3) else 0.0
    return ChromeMatchRevealChannels(back((a-.25)/.8)*(1-out), if (!reduced && meet >= 0 && meet < .5) sin(meet*26)*exp(-meet*10)*.06 else 0.0,
        if (meet >= 0) back(meet/.4)*(1-smooth(b/.3)) else 0.0,
        if (reduced) 0.0 else smooth((a-1.2)/.6)*(1-smooth(b/.4)), smooth((a-1.2)/.5)*(1-smooth(b/.3)), smooth((a-1.5)/.4)*(1-smooth(b/.3)), meet)
}
private class ChromeMatchRevealAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = mutableSetOf<Int>()
    private val ids: Map<String,Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        ids = listOf("slide", "clink", "part").associateWith { name -> context.assets.openFd("chromematchreveal/chromematchreveal_cmr-$name.wav").use { pool.load(it,1) } }
    }
    fun play(name: String) { val id = ids[name] ?: return; if (id in loaded && manager?.ringerMode == AudioManager.RINGER_MODE_NORMAL) { val v = if (name == "slide") .55f else .75f; pool.play(id,v,v,1,0,1f) } }
    fun close() = pool.release()
}
private class ChromeMatchRevealDriver {
    var time = 0.0; var clock = 0.0; var last = -10.0; var a0 = -100.0; var b0 = -100.0; var met = false
    var gazeOn = false; var gazeX = 0.0; var gazeY = 0.0; var gazeK = 0.0
    fun step(dt: Double, auto: Boolean, reduced: Boolean): Pair<ChromeMatchRevealChannels, Boolean> {
        clock += dt
        var a = 10.0; var b = -1.0
        if (auto && !gazeOn && clock-last >= 2 && a0 < -50) { time = (time+dt)%LOOP; a = time; b = if (time >= 6.6) time-6.6 else -1.0 }
        else { a = if (a0 > -50) clock-a0 else 10.0; b = if (b0 > -50) clock-b0 else -1.0
            if (b0 > -50 && b > 1.2) { a0 = clock; b0 = -100.0 }
            if (a0 > -50 && a > 3 && b0 < -50) a0 = -100.0
        }
        val c = chromeMatchRevealChannels(a,b,reduced)
        var clink = false
        if (c.glint >= 0 && c.glint < .1 && !met) { met = true; clink = !(auto && a0 < -50) }
        else if (c.glint < 0) met = false
        gazeK += ((if (gazeOn) 1.0 else 0.0)-gazeK)*(1-exp(-min(dt,.05)*8))
        return c to clink
    }
}

@Composable
fun ChromeMatchReveal(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val top = WindowInsets.statusBars.getTop(LocalDensity.current)/density
    val bottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val driver = remember { ChromeMatchRevealDriver() }
    val art = remember(context) { ChromeMatchRevealArt(context) }
    val audio = remember(context, reduced) { if (reduced) null else ChromeMatchRevealAudio(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    fun buzz(kind: Int) {
        if (Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(if (kind == 2) VibrationEffect.EFFECT_DOUBLE_CLICK else if (kind == 1) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK))
        else @Suppress("DEPRECATION") vibrator?.vibrate(if (kind == 1) 18 else 12)
    }
    // Gaze lives in the driver: redraw even when the timeline channels hold still.
    val channels = remember { mutableStateOf(chromeMatchRevealChannels(if (autoPlay && !reduced) 0.0 else 10.0,-1.0,reduced), neverEqualPolicy()) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    fun dislike() {
        if (driver.b0 < -50) { driver.last = driver.clock; if (driver.a0 < -50) driver.a0 = driver.clock-10; driver.b0 = driver.clock; audio?.play("part"); buzz(0) }
    }
    LaunchedEffect(lifecycle, autoPlay, reduced) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { n ->
                val dt = if (previous == 0L) .016 else ((n-previous)/1e9).coerceAtMost(.5); previous = n
                val (c, clink) = driver.step(dt,autoPlay && !reduced,reduced); channels.value = c
                if (clink) { audio?.play("clink"); buzz(2) }
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "It's a match. You and Purple liked each other."; customActions = listOf(CustomAccessibilityAction("Send a message") { buzz(1); true }, CustomAccessibilityAction("Dislike") { dislike(); true }) }
        .pointerInput(driver, reduced, top, bottom) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val x = down.position.x/density; val y = down.position.y/density
                val h = size.height/density; val w = size.width/density
                val buttonTop = h-bottom-18-128
                val target = if (x >= 22 && x <= w-22 && y >= buttonTop && y <= buttonTop+58) 1 else if (x >= 22 && x <= w-22 && y >= buttonTop+70 && y <= buttonTop+128) 2 else 0
                if (target == 0 && !reduced) { driver.gazeOn = true; driver.gazeX = x.toDouble(); driver.gazeY = y.toDouble(); driver.last = driver.clock }
                var end = down.position
                do { val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break; end = change.position
                    if (target == 0 && !reduced) { driver.gazeX = (end.x/density).toDouble(); driver.gazeY = (end.y/density).toDouble(); driver.last = driver.clock }
                    change.consume()
                } while (change.pressed)
                if (target == 0) { driver.gazeOn = false; driver.last = driver.clock }
                else if (hypot((end.x-down.position.x).toDouble(),(end.y-down.position.y).toDouble()) < 20*density) {
                    if (target == 1) buzz(1) else dislike()
                }
            }
        }) {
        drawIntoCanvas { c -> val canvas = c.nativeCanvas; canvas.save(); canvas.scale(density,density); art.draw(canvas,size.width/density,size.height/density,top,bottom,channels.value,driver,reduced); canvas.restore() }
    }
}

private class ChromeMatchRevealArt(context: Context) {
    private val hand = Typeface.createFromAsset(context.assets,"chromematchreveal/chromematchreveal_GochiHand-Regular.ttf")
    private val brush = Typeface.createFromAsset(context.assets,"chromematchreveal/chromematchreveal_CaveatBrush-Regular.ttf")
    private val semibold = Typeface.createFromAsset(context.assets,"chromematchreveal/chromematchreveal_Inter-SemiBold.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaders = if (Build.VERSION.SDK_INT >= 33) runCatching { arrayOf(RuntimeShader(ChromeMatchRevealShader.source),RuntimeShader(ChromeMatchRevealShader.source)) }.onFailure { Log.e("ChromeMatchReveal", "Bust shader failed; using fallback", it) }.getOrNull() else null
    private fun color(s: String) = Color.parseColor(s)
    private fun fill(s: String, alpha: Double = 1.0) { paint.reset(); paint.isAntiAlias = true; paint.color = color(s); paint.alpha = (255*alpha.coerceIn(0.0,1.0)).toInt() }
    private fun star(c: android.graphics.Canvas, x: Float, y: Float, r: Float, angle: Float, alpha: Double = 1.0, flat: String? = null) {
        val p = Path(); for (i in 0..7) { val a = i*PI/4-PI/2; val radius = if (i%2 == 0) 1.0 else .22; val px = (cos(a)*radius).toFloat(); val py = (sin(a)*radius).toFloat(); if (i == 0) p.moveTo(px,py) else p.quadTo(0f,0f,px,py) }; p.quadTo(0f,0f,0f,-1f); p.close()
        c.save(); c.translate(x,y); c.rotate(angle); c.scale(r,r)
        fill(flat ?: "#ffffff",alpha); if (flat == null) paint.shader = LinearGradient(-1f,-1f,1f,1f,intArrayOf(color("#ffffff"),color("#b9c7ff"),color("#7f8bb0"),color("#e8f0ff"),color("#9aa6d0")),floatArrayOf(0f,.3f,.5f,.75f,1f),Shader.TileMode.CLAMP); c.drawPath(p,paint); paint.shader = null; c.restore()
    }
    fun draw(c: android.graphics.Canvas,w: Float,h: Float,top: Float,bottom: Float,k: ChromeMatchRevealChannels,d: ChromeMatchRevealDriver,reduced: Boolean) {
        c.drawColor(color("#0b0a0d")); val cx = w/2; val cy = top+210
        val g = k.glint
        if (!reduced && g >= 0 && g < 1.6) {
            val fade = min(1.0,g/.12)*(1-smooth((g-.9)/.7)); val colors = arrayOf("#8b6cf6","#ff5ad8","#7fe3ff","#ffffff","#b8ff6a","#ff8a4a")
            for (i in 0..5) { val a = -PI/2+(i-2.5)*.32+sin(g*3.4+i*1.3)*.38; val sx=cx+4; val sy=cy+236*.62f; val ex=sx+cos(a).toFloat()*h; val ey=sy+sin(a).toFloat()*h
                fill(colors[i],.35*fade); paint.strokeWidth=10f; paint.strokeCap=Paint.Cap.ROUND; paint.maskFilter=BlurMaskFilter(8f,BlurMaskFilter.Blur.NORMAL); paint.shader=LinearGradient(sx,sy,ex,ey,color(colors[i]),Color.TRANSPARENT,Shader.TileMode.CLAMP); c.drawLine(sx,sy,ex,ey,paint)
                paint.maskFilter=null; paint.strokeWidth=2f; paint.alpha=(255*.85*fade).toInt(); c.drawLine(sx,sy,ex,ey,paint); paint.shader=null
            }
        }
        for (side in listOf(-1,1)) {
            val off = (1-k.k)*w*.75; val x = cx+side*62+side*(if(reduced) 0.0 else off).toFloat(); val y = cy+(if(side>0) 26 else -6)
            c.save(); c.translate(x,y); c.rotate((side*9+(1-k.k)*side*18).toFloat()); c.scale((1-k.squash).toFloat(),(1+k.squash).toFloat()); c.translate(-88f,-118f)
            if (reduced) c.saveLayerAlpha(0f,0f,176f,236f,(255*k.k.coerceIn(0.0,1.0)).toInt())
            fill("#241a3a",.75); c.drawRoundRect(0f,0f,176f,236f,26f,26f,paint)
            c.save(); val clip=Path().apply { addRoundRect(0f,0f,176f,236f,26f,26f,Path.Direction.CW) }; c.clipPath(clip)
            fill("#ffffff"); paint.shader=LinearGradient(0f,0f,176f,236f,0x1affffff,0x05ffffff,Shader.TileMode.CLAMP); c.drawRect(0f,0f,176f,236f,paint); paint.shader=null
            val fy = ((d.gazeX-(cx+side*62))/180).coerceIn(-.8,.8); val fp = ((d.gazeY-cy)/420).coerceIn(-.35,.35)
            val idle = .05*sin(2*PI*2*(g+.95)/LOOP+side); val yaw = if (reduced) 0.0 else (-side*.55*k.look+idle)*(1-d.gazeK)+fy*d.gazeK; val pitch = if (reduced) 0.0 else fp*d.gazeK
            val shader = shaders?.get(if(side<0) 0 else 1)
            if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                shader.setFloatUniform("c",88f,136f); shader.setFloatUniform("S",92f); shader.setFloatUniform("yaw",yaw.toFloat()); shader.setFloatUniform("pitch",pitch.toFloat()); shader.setFloatUniform("t",0f)
                if(side<0) shader.setFloatUniform("tint",.52f,.42f,1f) else shader.setFloatUniform("tint",1f,.36f,.86f)
                fill("#ffffff"); paint.shader=shader; c.drawRect(0f,0f,176f,236f,paint); paint.shader=null
            } else {
                val tint=if(side<0) "#8b6cf6" else "#ff5ad8"; fill(tint); paint.shader=LinearGradient(30f,20f,150f,210f,intArrayOf(color("#ffffff"),color(tint),color("#302344"),color(tint)),null,Shader.TileMode.CLAMP)
                c.drawOval(52f,28f,124f,133f,paint); c.drawRoundRect(73f,115f,103f,180f,15f,15f,paint); c.drawOval(12f,157f,164f,250f,paint); paint.shader=null
                fill("#eeeaff"); for(i in 0..8) c.drawCircle(56f+i*8f,55f+abs(4-i)*1.3f,if(i%2==0) 4f else 3f,paint)
            }
            c.restore(); fill(if(side<0) "#8b6cf6" else "#ff5ad8"); paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f; c.drawRoundRect(.75f,.75f,175.25f,235.25f,26f,26f,paint); paint.style=Paint.Style.FILL
            if(reduced) c.restore(); c.restore()
        }
        if (k.star > .01) star(c,cx+4,cy+6,(46*k.star).toFloat(),(-12+k.star*12).toFloat())
        if (!reduced && g >= 0 && g < .9) for(i in 0..9) { val a=i*PI*2/10+.3; val dist=(1-exp(-g*5))*(70+i%3*30); star(c,cx+4+(cos(a)*dist).toFloat(),cy+6+(sin(a)*dist).toFloat(),(6+i%2*4).toFloat(),0f,1-smooth((g-.35)/.55),if(i%2==0) "#ffffff" else "#d7c6ff") }
        c.save(); c.translate(0f,((1-k.copy)*12).toFloat()); fill("#ffffff",k.copy); paint.typeface=brush; paint.textSize=26f; paint.textAlign=Paint.Align.RIGHT; c.drawText("It's a match!",w-34,top+46-paint.ascent(),paint); c.restore()
        val copyY=cy+118+56+((1-k.copy)*12).toFloat(); fill("#ffffff",k.copy); paint.typeface=hand; paint.textSize=34f; paint.textAlign=Paint.Align.LEFT
        val first = arrayOf("YOU"," AND ","PURPLE"); val widths=first.map { paint.measureText(it) }; var tx=cx-widths.sum()/2
        for (i in first.indices) { paint.color=color(arrayOf("#a58bff","#ffffff","#ff6ad8")[i]); paint.alpha=(255*k.copy.coerceIn(0.0,1.0)).toInt(); c.drawText(first[i],tx,copyY-paint.ascent(),paint); tx+=widths[i] }
        paint.color=color("#ffffff"); paint.alpha=(255*k.copy.coerceIn(0.0,1.0)).toInt(); paint.textAlign=Paint.Align.CENTER; c.drawText("LIKED EACH OTHER",cx,copyY-paint.ascent()+38,paint)
        val by=h-bottom-18-128+((1-k.buttons)*18).toFloat()
        for(i in 0..1) { val y=by+i*70; fill(if(i==0) "#8b6cf6" else "#232227",k.buttons); c.drawRoundRect(22f,y,w-22,y+58,29f,29f,paint); fill("#ffffff",k.buttons); paint.typeface=semibold; paint.textSize=17f; paint.textAlign=Paint.Align.CENTER; c.drawText(if(i==0) "Send a message" else "Dislike",cx,y+29-(paint.ascent()+paint.descent())/2,paint) }
    }
}
