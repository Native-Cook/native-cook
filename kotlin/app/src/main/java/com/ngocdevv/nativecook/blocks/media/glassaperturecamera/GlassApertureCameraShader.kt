package com.ngocdevv.nativecook.blocks.media.glassaperturecamera

import android.graphics.*
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class GlassApertureCameraShader(bitmap: Bitmap) {
    private val shader=RuntimeShader(SOURCE)
    private val photo=BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
    private val matrix=Matrix()
    private val bw=bitmap.width.toFloat(); private val bh=bitmap.height.toFloat()
    fun at(w: Float,photoH: Float,f: GlassApertureCameraMotion.Frame,ix: Float,iy: Float,r: Float): Shader {
        matrix.setScale(w/bw,photoH/bh); photo.setLocalMatrix(matrix)
        shader.setInputShader("photo",photo)
        shader.setFloatUniform("size",w,photoH)
        shader.setFloatUniform("camera",f.fnum.toFloat(),f.focus.toFloat(),f.iris.toFloat(),f.snap.toFloat())
        shader.setFloatUniform("iris",ix,iy,r)
        return shader
    }
    companion object {
        private const val SOURCE="""
        uniform shader photo;
        uniform float2 size;
        uniform float4 camera;
        uniform float3 iris;
        const float PI=3.14159265; const float TAU=6.2831853;
        float sq(float x){return x*x;}
        float3 samplePhoto(float2 p){return pow(max(photo.eval(clamp(p,float2(.001)*size,float2(.999)*size)).rgb,float3(0)),float3(2.2));}
        float depth(float2 p){
          float2 uv=p/size; float d=1.-clamp(length((uv-float2(.36,.64))*float2(1.5,1.))*1.9,0.,1.)*.8;
          float sky=smoothstep(.2,.26,uv.x)*(1.-smoothstep(.56,.62,uv.x+(.5-uv.y)*.5))*(1.-smoothstep(.46,.52,uv.y)); d=max(d,sky*.97);
          float right=smoothstep(.52,.6,uv.x)*(1.-smoothstep(.55,.6,uv.y)); d=mix(d,.55+.35*(1.-uv.x)+.15*(.55-uv.y),right*.9);
          float van=smoothstep(.48,.52,uv.x)*smoothstep(.565,.59,uv.y)*(1.-smoothstep(.89,.92,uv.y)); d=mix(d,.2,van); d=mix(d,.04,smoothstep(.82,.98,uv.y)*(1.-van)); return clamp(d,0.,1.);
        }
        float coc(float d){return min(abs(d-camera.y)*26.*(1.4/camera.x),22.);}
        float nonagon(float th,float turn){float k=TAU/9.; float a=th-turn; float m=a-k*floor(a/k)-k*.5; return cos(k*.5)/cos(m);}
        half4 main(float2 s){
          float c0=coc(depth(s)); float3 col=samplePhoto(s);
          if(c0>=.35){float3 acc=float3(0); float total=0.; float jit=fract(sin(dot(floor(s),float2(12.9898,78.233)))*43758.5453)*TAU;
            for(int i=0;i<24;i++){float fi=float(i)+.5;float rr=sqrt(fi/24.);float th=fi*2.39996323+jit;float2 off=float2(cos(th),sin(th))*rr*nonagon(th,.2)*c0;float2 q=s+off;float3 c=samplePhoto(q);float reach=smoothstep(length(off)-1.,length(off)+1.,max(coc(depth(q)),c0*.5));float lum=dot(c,float3(.3,.55,.15));float weight=reach*(1.+14.*max(lum-.42,0.));acc+=c*weight;total+=weight;} col=acc/max(total,.0001);
          }
          float show=camera.z;float snap=camera.w;
          if(show>.001 || snap>.001){float2 p=s-iris.xy;float R=iris.z;float r=length(p);float th=atan(p.y,p.x);float u=log(camera.x/1.4)/log(16./1.4);float open=R*mix(.86,.2,u)*(1.-snap);float turn=.2+u*.9+snap*.5;float edge=open*nonagon(th,turn);float a=max(show,snap);
            if(r<R+9.){if(r>edge && r<R){float k=TAU/9.;float bent=th-turn-1.2*(r-edge)/R;float f=fract(bent/k);float idx=floor(bent/k);float3 refr=samplePhoto(s-p/max(r,1.)*10.*(1.-(r-edge)/R));float shade=.22+.06*sin(idx*2.3);float3 g=refr*shade*pow(float3(.85,.9,1.),float3(2.2));g+=float3(.9,.95,1.)*.12*pow(max(sin(th+.8),0.),6.);g+=float3(1)*.5*exp(-sq(f/.012))*smoothstep(edge,edge+10.,r);g*=1.-.6*exp(-sq((1.-f)/.02));g+=float3(1.,.97,.92)*.6*exp(-sq((r-edge)/1.2));col=mix(col,g,a);
              } else if(r>=R){float2 n=p/max(r,1.);float studio=.55+.45*sin(atan(n.y,n.x)*2.+.6);float3 m=float3(.42,.43,.46)*studio+float3(.15)*fract(sin(r*91.)*437.)*.3;col=mix(col,m*m,a);}else{col*=1.-.25*a*smoothstep(edge*.6,edge,r);}}
            col*=1.-.25*show*smoothstep(R+8.,R+40.,r);
          }
          return half4(half3(pow(clamp(col,float3(0),float3(1)),float3(1./2.2))),1);
        }
        """
    }
}
