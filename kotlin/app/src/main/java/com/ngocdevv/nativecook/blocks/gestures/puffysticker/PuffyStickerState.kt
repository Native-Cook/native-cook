package com.ngocdevv.nativecook.blocks.gestures.puffysticker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import kotlin.math.*

internal class PuffyStickerAudio(context: Context) {
    private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pool=SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val sounds=mutableMapOf<String,Int>();private val streams=mutableMapOf<String,Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0) synchronized(loaded) { loaded.add(id) } }
        for(cue in listOf("inflate","morph","land","tick")) context.assets.openFd("puffysticker/puffysticker_pst-$cue.wav").use { sounds[cue]=pool.load(it,1) }
    }
    fun play(cue: String) {
        if(manager.ringerMode!=AudioManager.RINGER_MODE_NORMAL) return
        val id=sounds[cue] ?: return
        if(!synchronized(loaded) { id in loaded }) return
        streams[cue]?.let(pool::stop)
        val volume=when(cue) { "tick"->.35f;"inflate"->.55f;else->.7f }
        streams[cue]=pool.play(id,volume,volume,1,0,1f)
    }
    fun stop(cue: String) { streams.remove(cue)?.let(pool::stop) }
    fun close() { pool.release() }
}

internal class PuffyStickerState(val play: (String)->Unit, val stop: (String)->Unit, val buzz: (String)->Unit) {
    var auto=false;var reduced=false;var time=0f;var autoStart=0f;var manual=true;var lastTouch=-100f;var resuming=false
    private var morphLanding=Float.POSITIVE_INFINITY
    var holding=false;var dragging=false;var holdStart=0f;var holdFrom=0f;var quarter=0
    val puff=PuffyStickerChannel();val shape=PuffyStickerChannel();val squish=PuffyStickerChannel();val x=PuffyStickerChannel();val y=PuffyStickerChannel()
    private val finger=PuffyStickerChannel()
    var thumbDown=-1
    var axis=0f;var touch=0f;var tapAt=-1;var tapPress=0f;var flatPress=0f
    fun frame(t: Float) {
        time=t
        if(!manual) {
            val f=PuffyStickerMotion.frameAt((t-autoStart)%PuffyStickerMotion.LOOP)
            puff.set(f.puff);shape.set(f.shape);squish.set(f.squish);axis=0f;touch=f.touch;finger.set(touch);tapAt=f.tapAt;tapPress=f.tapPress;flatPress=f.flatPress
        } else {
            puff.at(t);shape.at(t);squish.at(t);x.at(t);y.at(t)
            if(t>=morphLanding) {morphLanding=Float.POSITIVE_INFINITY;squish.sequence(t,listOf(.09f to .05f),.6f,.3f)}
            touch=finger.at(t)
            if(holding && !dragging) {
                val held=t-holdStart-.12f
                if(held>0) {
                    puff.set(holdFrom+(1-holdFrom)*PuffyStickerMotion.inflate(held))
                    val q=floor(puff.value*4+.001f).toInt()
                    if(q>quarter) {quarter=q;cue("tick");buzz("select")}
                }
            } else if(auto && !reduced && !holding && t-lastTouch>2 && !resuming) {
                resuming=true;morphLanding=Float.POSITIVE_INFINITY;puff.tween(0f,t,.7f,::puffyStickerEase);shape.tween(0f,t,.7f+.26f*shape.value,::puffyStickerEase);x.spring(0f,t,.5f,.8f);y.spring(0f,t,.5f,.8f)
            } else if(resuming && puff.value<.002f && shape.value<.002f && t-lastTouch>3.4f) {
                resuming=false;autoStart=t;manual=false
            }
        }
    }
    private fun cue(name: String) { if(!reduced) play(name) }
    fun takeOver() {
        lastTouch=time;resuming=false
        if(!manual) { manual=true;finger.tween(0f,time,.15f);tapPress=0f;flatPress=0f;squish.spring(0f,time,.5f,.35f) }
    }
    fun goShape(i: Int) {
        takeOver();val delta=abs(shape.value-i);if(delta<.01f) return
        cue("morph");buzz("light")
        val duration=if(reduced) .18f else .65f*(.7f+.3f*delta)
        shape.tween(i.toFloat(),time,duration,if(reduced) ::puffyStickerDefaultEase else ::puffyStickerEase)
        morphLanding=if(reduced) Float.POSITIVE_INFINITY else time+duration
    }
    fun scrub(value: Float) {morphLanding=Float.POSITIVE_INFINITY;lastTouch=time;shape.set(value.coerceIn(0f,3f))}
    fun snapShape() {morphLanding=Float.POSITIVE_INFINITY;lastTouch=time;val snap=shape.value.roundToInt().coerceIn(0,3).toFloat();if(reduced) shape.set(snap) else shape.spring(snap,time,.42f,.7f)}
    fun setPuff(on: Boolean) {
        takeOver();buzz("light");if(on) cue("inflate")
        if(reduced) puff.tween(if(on) 1f else 0f,time,.2f) else {
            puff.spring(if(on) 1f else 0f,time,.7f,if(on) .55f else .8f)
            squish.sequence(time,listOf(.14f to if(on) -.05f else .07f),.7f,.3f)
        }
    }
    fun cancel() { takeOver();morphLanding=Float.POSITIVE_INFINITY;puff.tween(0f,time,if(reduced) .18f else .42f,::puffyStickerEase);shape.tween(0f,time,if(reduced) .18f else .42f+.2f*shape.value,::puffyStickerEase) }
    fun done() {
        takeOver();buzz("success");cue("land");if(reduced) return
        axis=0f;squish.sequence(time,listOf(.11f to .08f,.25f to -.1f,.41f to .06f),.6f,.35f)
        y.hop(time)
    }
    fun begin() { takeOver();holding=true;dragging=false;holdStart=time;holdFrom=puff.value;quarter=floor(puff.value*4+.001f).toInt();if(!reduced) squish.tween(-.03f,time,.12f);if(puff.value<.98f) cue("inflate") }
    fun drag(dx: Float,dy: Float,vx: Float,vy: Float) {
        lastTouch=time
        if(!dragging && hypot(dx,dy)>10) {dragging=true;stop("inflate")}
        if(dragging) {x.set(dx);y.set(dy);if(!reduced) {squish.set(-min(.14f,hypot(vx,vy)/9000));axis=atan2(vy,vx)}}
    }
    fun end(vx: Float,vy: Float) {
        lastTouch=time;holding=false;stop("inflate")
        if(dragging) {if(reduced) {x.set(0f);y.set(0f)} else {x.spring(0f,time,.56f,.6f,vx);y.spring(0f,time,.56f,.6f,vy)};if(!reduced) squish.sequence(time,listOf(.12f to min(.1f,hypot(vx,vy)/12000+.03f)),.7f,.3f)}
        else if(!reduced) squish.sequence(time,listOf(.1f to if(time-holdStart<.15f) .09f else .07f),.8f,.25f)
        dragging=false
    }
}
