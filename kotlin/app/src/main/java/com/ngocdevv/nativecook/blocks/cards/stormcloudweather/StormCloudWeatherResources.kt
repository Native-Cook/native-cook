package com.ngocdevv.nativecook.blocks.cards.stormcloudweather

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class StormCloudWeatherResources(context:Context) {
    val title = Typeface.createFromAsset(context.assets,"stormcloudweather/stormcloudweather_Outfit-Medium.ttf")
    val regular = Typeface.createFromAsset(context.assets,"stormcloudweather/stormcloudweather_Inter-Regular.ttf")
    val medium = Typeface.createFromAsset(context.assets,"stormcloudweather/stormcloudweather_Inter-Medium.ttf")
    val semibold = Typeface.createFromAsset(context.assets,"stormcloudweather/stormcloudweather_Inter-SemiBold.ttf")
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    // The blurred cloud bank is static artwork. Never allocate or blur it inside the frame loop.
    val banks = listOf(0xff5f7591.toInt(),0xff8fa6c4.toInt()).map { color ->
        val bitmap=Bitmap.createBitmap(440,240,Bitmap.Config.ARGB_8888)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color;maskFilter=BlurMaskFilter(30f,BlurMaskFilter.Blur.NORMAL) }
        Canvas(bitmap).drawOval(90f,90f,350f,150f,p)
        bitmap
    }
    val tabs = listOf(
        "M4 11 12 4l8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z",
        "M11 4a7 7 0 1 0 0.01 0 M16 16l4.5 4.5",
        "M12 12a4 4 0 1 0 0.01 0 M4.5 20.5c1.2-3.6 4-5.5 7.5-5.5s6.3 1.9 7.5 5.5",
        "M12 9a3 3 0 1 0 0.01 0 M12 2.5v3 M12 18.5v3 M2.5 12h3 M18.5 12h3 M5.3 5.3l2.1 2.1 M16.6 16.6l2.1 2.1 M5.3 18.7l2.1-2.1 M16.6 7.4l2.1-2.1"
    ).map { androidx.core.graphics.PathParser.createPathFromPathData(it)!! }
}
internal class StormCloudWeatherAudio(context: Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val streams=mutableMapOf<String,Int>()
    private val ids:Map<String,Int>
    init{
        pool.setOnLoadCompleteListener { _,id,status->if(status==0)loaded.add(id) }
        ids=listOf("thunder","morph","tap").associateWith { name ->
            context.assets.openFd("stormcloudweather/stormcloudweather_scw-$name.wav").use { pool.load(it,1) }
        }
    }
    fun play(name:String){
        val id=ids[name]?:return
        if(id !in loaded||manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        streams[name]?.let{pool.stop(it)}
        val volume=if(name=="thunder").5f else .7f
        streams[name]=pool.play(id,volume,volume,1,0,1f)
    }
    fun close(){pool.release()}
}
