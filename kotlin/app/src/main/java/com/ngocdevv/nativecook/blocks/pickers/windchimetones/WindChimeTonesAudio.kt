package com.ngocdevv.nativecook.blocks.pickers.windchimetones

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/** A rotated PCM piece lets SoundPool resume at the same sample as expo-audio's seekTo. */
internal class WindChimeTonesAudio(private val context: Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).build()
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var generation=0
    private var closed=false
    private var stream=0
    private val pending=mutableMapOf<Int,Pair<Int,File>>()
    private var loaded=0
    init {
        pool.setOnLoadCompleteListener { _,id,status ->
            val request=pending.remove(id)?:return@setOnLoadCompleteListener
            request.second.delete()
            if(status==0 && !closed && request.first==generation && manager?.ringerMode==AudioManager.RINGER_MODE_NORMAL) {
                if(loaded!=0) pool.unload(loaded)
                loaded=id; stream=pool.play(id,.8f,.8f,1,-1,1f)
            } else pool.unload(id)
        }
    }
    fun set(on: Boolean,at: Double) {
        generation++
        if(stream!=0) { pool.stop(stream); stream=0 }
        if(!on || closed || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL) return
        val request=generation
        worker.execute {
            val data=context.assets.open("windchimetones/windchimetones_wct-chimes.wav").use { it.readBytes() }
            val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            var cursor=12; var start=44; var length=data.size-44; var block=2; var rate=44100
            while(cursor+8<=data.size) {
                val name=String(data,cursor,4,Charsets.US_ASCII); val size=b.getInt(cursor+4)
                if(name=="fmt ") { rate=b.getInt(cursor+12); block=b.getShort(cursor+20).toInt() }
                if(name=="data") { start=cursor+8; length=size; break }
                cursor+=8+size+(size and 1)
            }
            val offset=(((at%8)*rate).toInt()*block).coerceIn(0,length)
            val rotated=data.copyOf()
            System.arraycopy(data,start+offset,rotated,start,length-offset)
            System.arraycopy(data,start,rotated,start+length-offset,offset)
            val file=File.createTempFile("windchimetones-",".wav",context.cacheDir)
            file.writeBytes(rotated)
            handler.post {
                if(closed || generation!=request) file.delete()
                else { val id=pool.load(file.absolutePath,1); pending[id]=request to file }
            }
        }
    }
    fun close() {
        closed=true; generation++; worker.shutdown(); pool.release()
        pending.values.forEach { it.second.delete() }; pending.clear()
    }
}
