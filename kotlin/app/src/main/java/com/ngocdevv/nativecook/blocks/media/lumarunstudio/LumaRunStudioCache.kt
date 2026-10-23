package com.ngocdevv.nativecook.blocks.media.lumarunstudio

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors

internal data class LumaRunStudioBitmap(val image:Bitmap,val params:LumaRunStudioParams)
internal data class LumaRunStudioPair(val a:LumaRunStudioBitmap,val b:LumaRunStudioBitmap=a,val mix:Double=0.0)
internal data class LumaRunStudioScene(val pair:LumaRunStudioPair,val params:LumaRunStudioParams,val appearance:LumaRunStudioAppearance=LumaRunStudioAppearance(),val compare:Double=0.0,val kept:Boolean=false)

/** Main-thread queue state, one serial original-tuple worker, latest pending input only. */
internal class LumaRunStudioCache(private val resources:LumaRunStudioResources) {
    private class Token {@Volatile var cancelled=false}
    private data class Manual(val p:LumaRunStudioParams,val generation:Int,val sequence:Int)
    private data class Recovery(val p:List<LumaRunStudioParams>,val generation:Int,val sequence:Int)
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor {r->Thread(r,"LumaRunStudio.sort").apply {isDaemon=true}}
    private val sorter by lazy {LumaRunStudioSorter(resources.canonical)}
    private val lru=ArrayList<LumaRunStudioBitmap>()
    private var loop=emptyList<LumaRunStudioBitmap>()
    private var generation=0;private var sequence=0;private var accepted= -1
    private var token:Token?=null;private var busy=false;private var startup=false;private var closed=false
    private var pending:Manual?=null;private var recovery:Recovery?=null;private var requested:LumaRunStudioParams?=null
    private var lastStart= -1e9;private var wake:Runnable?=null
    var onImage:((Int,Int,LumaRunStudioBitmap)->Unit)?=null
    var onRecovery:((Int,List<LumaRunStudioBitmap>)->Unit)?=null
    fun prepare(done:(List<LumaRunStudioBitmap>)->Unit) {
        if(loop.size==64){done(loop);return};if(startup||closed)return
        startup=true;busy=true;val t=Token();token=t
        worker.execute {val frames=resources.loop();main.post {startup=false;busy=false;token=null
            if(!closed&&!t.cancelled){loop=frames;done(frames);pump()}}}
    }
    fun invalidate(g:Int) {generation=g;accepted=sequence;pending=null;recovery=null;requested=null;if(!startup)token?.cancelled=true;wake?.let(main::removeCallbacks);wake=null}
    private fun lookup(p:LumaRunStudioParams):LumaRunStudioBitmap? {
        if(p.q==0.0)return LumaRunStudioBitmap(resources.canonical,p)
        loop.firstOrNull {it.params==p}?.let{return it}
        val index=lru.indexOfFirst {it.params==p};if(index>=0){val b=lru.removeAt(index);lru+=b;return b};return null
    }
    private fun remember(b:LumaRunStudioBitmap){lru.removeAll {it.params==b.params};lru+=b;while(lru.size>6)lru.removeAt(0)}
    fun request(p:LumaRunStudioParams,g:Int){if(closed)return;if(g!=generation)invalidate(g);val key=LumaRunStudioMath.quantized(p);if(key==requested)return
        requested=key;sequence++;recovery=null
        lookup(key)?.let {pending=null;token?.cancelled=true;accepted=sequence;onImage?.invoke(g,sequence,it);return}
        pending=Manual(key,g,sequence);pump()
    }
    fun recovery(params:List<LumaRunStudioParams>,g:Int){if(closed)return;if(g!=generation)invalidate(g);check(params.size==6)
        sequence++;pending=null;requested=null;token?.cancelled=true;wake?.let(main::removeCallbacks);wake=null;recovery=Recovery(params,g,sequence);pump()}
    private fun pump(){if(closed||busy||startup)return
        val r=recovery
        if(r!=null){recovery=null;busy=true;val t=Token();token=t
            worker.execute {val frames=ArrayList<LumaRunStudioBitmap>();for(p in r.p){if(t.cancelled)break;val image=sorter.render(p){t.cancelled}?:break;frames+=LumaRunStudioBitmap(image,p)}
                main.post {busy=false;token=null;if(!closed&&!t.cancelled&&r.generation==generation&&frames.size==6){frames.forEach(::remember);onRecovery?.invoke(generation,frames)};pump()}};return}
        val request=pending?:return;val now=SystemClock.elapsedRealtime()/1000.0;val delay=.125-(now-lastStart)
        if(delay>0){if(wake==null){wake=Runnable {wake=null;pump()};main.postDelayed(wake!!,(delay*1000).toLong()+1)};return}
        pending=null;busy=true;lastStart=now;val t=Token();token=t
        worker.execute {val image=sorter.render(request.p){t.cancelled};main.post {busy=false;token=null
            if(!closed&&!t.cancelled&&request.generation==generation&&request.sequence>accepted&&image!=null){val b=LumaRunStudioBitmap(image,request.p);remember(b);accepted=request.sequence;onImage?.invoke(generation,accepted,b)};pump()}}
    }
    fun close(){closed=true;token?.cancelled=true;wake?.let(main::removeCallbacks);wake=null;pending=null;recovery=null;onImage=null;onRecovery=null;worker.shutdownNow()}
}
