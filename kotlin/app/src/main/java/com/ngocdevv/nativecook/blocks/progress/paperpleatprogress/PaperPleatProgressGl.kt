package com.ngocdevv.nativecook.blocks.progress.paperpleatprogress

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** API 26–32: the same seven-input polar fold, with no mipmaps or extra colour transform. */
internal class PaperPleatProgressGlView(context: Context,onReady: () -> Unit): GLSurfaceView(context) {
    private val renderer=PaperPleatProgressGl(context.applicationContext) { post(onReady) }
    init {
        setEGLContextClientVersion(3); setEGLConfigChooser(8,8,8,8,0,0)
        preserveEGLContextOnPause=true; setRenderer(renderer); renderMode=RENDERMODE_WHEN_DIRTY
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun show(f: PaperPleatProgressMotion.Frame) { renderer.frame=f; requestRender() }
}
private class PaperPleatProgressGl(private val context: Context,private val onReady: () -> Unit): GLSurfaceView.Renderer {
    @Volatile var frame=PaperPleatProgressMotion.Frame()
    private var program=0; private var w=1; private var h=1
    private val textures=IntArray(7); private val locations=mutableMapOf<String,Int>()
    private fun location(name: String)=locations.getOrPut(name) { glGetUniformLocation(program,name) }
    private fun compile(type: Int,source: String): Int {
        val s=glCreateShader(type); glShaderSource(s,source); glCompileShader(s); val ok=IntArray(1); glGetShaderiv(s,GL_COMPILE_STATUS,ok,0)
        check(ok[0]!=0) { "PaperPleatProgress shader: "+glGetShaderInfoLog(s) }; return s
    }
    override fun onSurfaceCreated(gl: GL10?,config: EGLConfig?) {
        val v=compile(GL_VERTEX_SHADER,"""#version 300 es
            void main(){gl_Position=vec4(gl_VertexID==1?3.:-1.,gl_VertexID==2?3.:-1.,0.,1.);}
        """.trimIndent()); val f=compile(GL_FRAGMENT_SHADER,PAPER_PLEAT_PROGRESS_GLSL)
        program=glCreateProgram(); glAttachShader(program,v); glAttachShader(program,f); glLinkProgram(program)
        val ok=IntArray(1); glGetProgramiv(program,GL_LINK_STATUS,ok,0); check(ok[0]!=0) { glGetProgramInfoLog(program) }
        glDeleteShader(v); glDeleteShader(f); locations.clear(); glUseProgram(program); glGenTextures(7,textures,0)
        val names=listOf("uPhoto","uPlate","uMask","uPhoto4","uPlate5","uPlate6","uMask5")
        paperPleatProgressImages(context).forEachIndexed { i,image ->
            glActiveTexture(GL_TEXTURE0+i); glBindTexture(GL_TEXTURE_2D,textures[i])
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GL_TEXTURE_2D,0,image,0); image.recycle(); glUniform1i(location(names[i]),i)
        }; onReady()
    }
    override fun onSurfaceChanged(gl: GL10?,width: Int,height: Int) { w=width; h=height; glViewport(0,0,w,h) }
    override fun onDrawFrame(gl: GL10?) {
        val d=context.resources.displayMetrics.density; val f=frame; glUseProgram(program)
        glUniform2f(location("uRes"),w.toFloat(),h.toFloat()); glUniform1f(location("uDensity"),d); glUniform2f(location("uOrigin"),0f,150*h/d/852)
        for((name,value) in listOf("uP" to f.p,"uSw" to f.sw,"uT" to f.t,"uBoost" to f.boost,"uFade" to f.fade,"uLift" to f.lift)) glUniform1f(location(name),value.toFloat())
        for(i in textures.indices) { glActiveTexture(GL_TEXTURE0+i); glBindTexture(GL_TEXTURE_2D,textures[i]) }
        glDrawArrays(GL_TRIANGLES,0,3)
    }
}
