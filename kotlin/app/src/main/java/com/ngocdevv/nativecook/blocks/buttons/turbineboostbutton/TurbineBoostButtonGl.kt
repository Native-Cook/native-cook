package com.ngocdevv.nativecook.blocks.buttons.turbineboostbutton

import android.content.Context
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** API 26–32 uses the same 40 samples at the surface's native resolution. */
internal class TurbineBoostButtonGlView(context: Context,onReady: () -> Unit): GLSurfaceView(context) {
    private val renderer=TurbineBoostButtonGl(context.applicationContext) { post(onReady) }
    init {
        setEGLContextClientVersion(3); setEGLConfigChooser(8,8,8,8,0,0)
        preserveEGLContextOnPause=true; setRenderer(renderer); renderMode=RENDERMODE_WHEN_DIRTY
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun show(f: TurbineBoostButtonMotion.Frame) { renderer.frame=f; requestRender() }
}
private class TurbineBoostButtonGl(private val context: Context,private val onReady: () -> Unit): GLSurfaceView.Renderer {
    @Volatile var frame=TurbineBoostButtonMotion.frame(0.0)
    private var program=0; private var w=1; private var h=1
    private val locations=mutableMapOf<String,Int>()
    private fun location(name: String)=locations.getOrPut(name) { glGetUniformLocation(program,name) }
    private fun compile(type: Int,source: String): Int {
        val shader=glCreateShader(type); glShaderSource(shader,source); glCompileShader(shader)
        val ok=IntArray(1); glGetShaderiv(shader,GL_COMPILE_STATUS,ok,0)
        check(ok[0]!=0) { "TurbineBoostButton shader: "+glGetShaderInfoLog(shader) }; return shader
    }
    override fun onSurfaceCreated(gl: GL10?,config: EGLConfig?) {
        val v=compile(GL_VERTEX_SHADER,"""#version 300 es
            void main(){gl_Position=vec4(gl_VertexID==1?3.:-1.,gl_VertexID==2?3.:-1.,0.,1.);}
        """.trimIndent())
        val f=compile(GL_FRAGMENT_SHADER,TURBINE_BOOST_BUTTON_GLSL)
        program=glCreateProgram(); glAttachShader(program,v); glAttachShader(program,f); glLinkProgram(program)
        val ok=IntArray(1); glGetProgramiv(program,GL_LINK_STATUS,ok,0)
        check(ok[0]!=0) { "TurbineBoostButton link: "+glGetProgramInfoLog(program) }
        glDeleteShader(v); glDeleteShader(f); locations.clear()
        val ids=IntArray(1); glGenTextures(1,ids,0); glBindTexture(GL_TEXTURE_2D,ids[0])
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        val photo=turbineBoostButtonPhoto(context); GLUtils.texImage2D(GL_TEXTURE_2D,0,photo,0); photo.recycle()
        glUseProgram(program); glUniform1i(location("uFan"),0); onReady()
    }
    override fun onSurfaceChanged(gl: GL10?,width: Int,height: Int) { w=width; h=height; glViewport(0,0,w,h) }
    override fun onDrawFrame(gl: GL10?) {
        val d=context.resources.displayMetrics.density; val f=frame
        glUseProgram(program); glUniform2f(location("uRes"),w.toFloat(),h.toFloat()); glUniform1f(location("uDensity"),d)
        glUniform2f(location("uOff"),(w/d-393)/2,252.1f*(h/d/852-1))
        glUniform1f(location("uA"),f.angle.toFloat()); glUniform1f(location("uArc"),f.arc.toFloat()); glUniform1f(location("uB"),f.boost.toFloat())
        glDrawArrays(GL_TRIANGLES,0,3)
    }
}
