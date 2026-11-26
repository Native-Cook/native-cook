package com.ngocdevv.nativecook.blocks.playground.spinningtoptoy

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// GLES 3 serves the complete supported API range, without reducing the trail or raster resolution.
internal class SpinningTopToyRenderer(private val context: Context,private val density: Float): GLSurfaceView.Renderer {
    @Volatile var frame=SpinningTopToyFrame()
    @Volatile var ready=false
    private var program=0; private var texture=0; private var width=0; private var height=0
    private val uniforms=mutableMapOf<String,Int>()
    private fun location(name: String)=uniforms.getOrPut(name) { glGetUniformLocation(program,name) }
    private fun shader(type: Int,source: String): Int {
        val id=glCreateShader(type); glShaderSource(id,source); glCompileShader(id)
        val ok=IntArray(1); glGetShaderiv(id,GL_COMPILE_STATUS,ok,0)
        check(ok[0]!=0) { glGetShaderInfoLog(id) }; return id
    }
    override fun onSurfaceCreated(gl: GL10?,config: EGLConfig?) {
        ready=false
        val v=shader(GL_VERTEX_SHADER,"#version 300 es\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0,1);}")
        val f=shader(GL_FRAGMENT_SHADER,FRAGMENT)
        program=glCreateProgram(); glAttachShader(program,v); glAttachShader(program,f); glLinkProgram(program)
        val ok=IntArray(1); glGetProgramiv(program,GL_LINK_STATUS,ok,0)
        check(ok[0]!=0) { glGetProgramInfoLog(program) }; glDeleteShader(v); glDeleteShader(f); uniforms.clear()
        val ids=IntArray(1); glGenTextures(1,ids,0); texture=ids[0]; glBindTexture(GL_TEXTURE_2D,texture)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        val bitmap=context.assets.open("spinningtoptoy/spinningtoptoy_top.webp").use {
            BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inScaled=false; inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888 })!!
        }
        GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0); bitmap.recycle()
        glDisable(GL_DITHER); glDisable(GL_BLEND)
        ready=true
    }
    override fun onSurfaceChanged(gl: GL10?,w: Int,h: Int) { width=w; height=h; glViewport(0,0,w,h) }
    override fun onDrawFrame(gl: GL10?) {
        val f=frame; glUseProgram(program); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,texture)
        glUniform1i(location("uImg"),0); glUniform2f(location("uRes"),width.toFloat(),height.toFloat())
        glUniform1f(location("uDensity"),density); glUniform2f(location("uTip"),f.x.toFloat(),f.y.toFloat())
        glUniform2f(location("uOrigin"),-87.5f,0f)
        for((name,value) in listOf("uA" to f.angle,"uSpin" to f.spin,"uW" to f.speed,"uGlow" to f.glow,"uAlpha" to f.alpha,"uFall" to f.fall)) glUniform1f(location(name),value.toFloat())
        glUniform3fv(location("uTrail"),24,f.trail,0); glDrawArrays(GL_TRIANGLES,0,3)
    }
    companion object {
        private val FRAGMENT="""
#version 300 es
precision highp float; out vec4 O;

uniform sampler2D uImg; uniform vec2 uRes; uniform float uDensity;
uniform vec2 uTip; uniform float uA; uniform float uSpin; uniform float uW; uniform float uGlow; uniform float uAlpha;
uniform float uFall; uniform vec2 uOrigin; uniform vec3 uTrail[24];
const float K=.5547; const vec2 TIP=vec2(525.,1088.); const float BANDY=875.; const float CXT=531.; const float BANDW=222.;
vec3 lin(vec3 c){return pow(c,vec3(2.2));}
vec3 img(vec2 q){return texture(uImg,q/vec2(1024.,1536.)).rgb;}
// p already has top-left design coordinates, independent of the GLES pixel density.
vec3 dither(vec3 c,vec2 p){float n=fract(52.9829189*fract(dot(floor(p*3.),vec2(.06711056,.00583715))));return c+(n-.5)/255.;}
void main(){vec2 p=vec2(gl_FragCoord.x,uRes.y-gl_FragCoord.y)/uDensity;
 // clean plate: the photo's own grey, taken beside the top at the same height
 vec2 pp=(p-uOrigin)/K;vec3 col=lin(mix(img(vec2(70.,pp.y)),img(vec2(960.,pp.y)),clamp(pp.x/1024.,0.,1.)));
 // floor light: a pool under the band, and the band's footprint ring at each past position
 vec3 OR=lin(vec3(1.,.42,.06));
 vec2 dp=p-uTip;float px=dp.x/85.,py=(dp.y-3.)/11.;col+=OR*.40*uGlow*uAlpha*exp(-px*px-py*py);
 for(int i=0;i<24;i++){vec3 tr=uTrail[i];if(tr.z>0.){vec2 d=(p-tr.xy-vec2(0.,2.))/vec2(124.,13.);float e=(length(d)-1.)*13./1.6;col+=OR*tr.z*.16*exp(-e*e);}}
 // contact shadow: a dot under the tip, stretching along the floor toward the rim as the top goes down
 {vec2 a=uTip;vec2 ba=vec2(-174.*uFall,0.);vec2 pa=p-a;float bb=dot(ba,ba);float h=bb>1.?clamp(dot(pa,ba)/bb,0.,1.):0.;vec2 q=pa-ba*h;
  float sx=q.x/(8.+6.*uFall),sy=(q.y-1.5)/(3.+2.*uFall);col*=1.-(.22+.18*uFall)*uAlpha*exp(-sx*sx-sy*sy);}
 // the top: rotated rigidly about its tip by uA
 vec2 v=p-uTip;float c=cos(uA),s=sin(uA);vec2 l=vec2(c*v.x+s*v.y,-s*v.x+c*v.y);vec2 q=TIP+l/K;
 if(q.y<TIP.y+4.&&q.x>270.&&q.x<800.&&q.y>520.){
  vec3 t=img(q);vec3 b=img(vec2(70.,q.y));
  float a=smoothstep(.10,.20,length(t-b))*(1.-smoothstep(TIP.y-2.,TIP.y+4.,q.y));
  float lu=dot(t,vec3(.3,.59,.11));a*=mix(1.,1.-smoothstep(.22,.34,lu),smoothstep(905.,935.,q.y)); // below the band only the black cone, never the floor glow
  vec3 tl=lin(t);
  float orng=smoothstep(.25,.45,t.r-t.b)*smoothstep(.5,.7,t.r);
  tl=mix(tl,tl*(.25+.85*uGlow),orng);
  tl=mix(tl,tl*vec3(1.,.42,.28),orng*(1.-smoothstep(.2,.55,uGlow)));
  // three hot spots run round the band, blurred by speed
  float len=clamp(uW*.022*BANDW,3.,110.);
  for(int i=0;i<3;i++){float ph=uSpin+float(i)*2.0943951;float front=cos(ph);if(front>0.){float xs=CXT+BANDW*sin(ph);
   float hx=(q.x-xs)/len,hy=(q.y-BANDY)/5.;tl+=lin(vec3(1.,.75,.35))*1.6*front*uGlow*exp(-hx*hx-hy*hy);}}
  col=mix(col,tl,a*uAlpha);}
 O=vec4(dither(pow(clamp(col,0.,1.),vec3(1./2.2)),p),1.);}

""".trimIndent()
    }
}
