package com.ngocdevv.nativecook.blocks.cards.glasspiggysavings

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

internal class GlassPiggySavingsResources(context:Context) {
    private val root="glasspiggysavings/glasspiggysavings_"
    val pig=context.assets.open(root+"pig.png").use { BitmapFactory.decodeStream(it) }
    val faces=mapOf("Black" to Typeface.createFromAsset(context.assets,root+"Nunito-Black.ttf"),
        "ExtraBold" to Typeface.createFromAsset(context.assets,root+"Nunito-ExtraBold.ttf"),
        "SemiBold" to Typeface.createFromAsset(context.assets,root+"Nunito-SemiBold.ttf"))
    val families=faces.keys.associateWith { FontFamily(Font(root+"Nunito-$it.ttf",context.assets)) }
    val runtime=if(Build.VERSION.SDK_INT>=33) GlassPiggySavingsRuntime(pig) else null
}

@RequiresApi(33)
internal class GlassPiggySavingsRuntime(pig:Bitmap) {
    private val shader=RuntimeShader("""
        uniform shader pig;
        uniform float pos; uniform float strength; uniform float w;
        half4 main(float2 xy) {
            half4 c=pig.eval(xy);
            float band=exp(-pow((xy.x+xy.y*0.55-pos)/(w*0.07),2.0));
            return half4(c.rgb+half3(band*strength)*c.a,c.a);
        }
    """)
    private val image=BitmapShader(pig,Shader.TileMode.DECAL,Shader.TileMode.DECAL).apply {
        // RuntimeShader inputs default to nearest sampling even when Paint filters bitmaps.
        // The reference explicitly uses Skia FilterMode.Linear.
        setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
    }
    private val matrix=Matrix()
    fun paint(x:Float,y:Float,pos:Float,strength:Float):Shader {
        matrix.setScale((340.0/900).toFloat(),(340.0/900).toFloat()); matrix.postTranslate(x,y)
        image.setLocalMatrix(matrix); shader.setInputShader("pig",image)
        shader.setFloatUniform("pos",pos); shader.setFloatUniform("strength",strength); shader.setFloatUniform("w",340f)
        return shader
    }
}

internal class GlassPiggySavingsAudio(context:Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready=mutableSetOf<Int>()
    private val streams=mutableMapOf<String,Int>()
    private val sounds=mutableMapOf<String,Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0) ready.add(id) }
        for(name in listOf("clink","wiggle","tap")) {
            context.assets.openFd("glasspiggysavings/glasspiggysavings_gps-$name.wav").use { sounds[name]=pool.load(it,1) }
        }
    }
    fun play(name:String) {
        val id=sounds[name] ?: return
        if(id !in ready || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL) return
        streams[name]?.let { pool.stop(it) }
        val volume=if(name=="tap") 0.5f else 0.85f
        streams[name]=pool.play(id,volume,volume,1,0,1f)
    }
    fun stop() { streams.values.forEach { pool.stop(it) }; streams.clear() }
    fun close() { pool.release() }
}
