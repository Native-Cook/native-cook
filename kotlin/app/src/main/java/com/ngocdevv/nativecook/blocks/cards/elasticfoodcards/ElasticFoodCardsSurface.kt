package com.ngocdevv.nativecook.blocks.cards.elasticfoodcards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Continuous AGSL relief on33+; a96×72cell opaque color mesh samples the same normals on26–32. */
internal class ElasticFoodCardsSurfacePainter {
    private val shader: Any? = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(ELASTIC_FOOD_CARDS_SURFACE_AGSL) else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val columns = 96
    private val rows = 72
    private val positions = FloatArray((columns + 1) * (rows + 1) * 2) { index ->
        val node = index / 2
        if (index % 2 == 0) (node % (columns + 1) * 393.0 / columns).toFloat() else (node / (columns + 1) * 300.0 / rows).toFloat()
    }
    private val colors = IntArray((columns + 1) * (rows + 1))
    private val white = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE); density = Bitmap.DENSITY_NONE; prepareToDraw() }
    private val ends = arrayOf(
        doubleArrayOf(8.0,32.0,-8.0,0.68),doubleArrayOf(25.0,110.0,13.0,0.82),doubleArrayOf(67.0,185.0,-11.0,0.92),
        doubleArrayOf(94.0,222.0,16.0,0.78),doubleArrayOf(167.0,250.0,-6.0,1.0),doubleArrayOf(235.0,248.0,9.0,0.91),
        doubleArrayOf(308.0,221.0,-15.0,0.83),doubleArrayOf(332.0,183.0,10.0,0.95),doubleArrayOf(364.0,112.0,-13.0,0.76),doubleArrayOf(388.0,30.0,7.0,0.65))
    // D.x,D.y,L²,N.x,N.y,bend,weight are cached once per frame for the CPU fallback.
    private val rays = Array(10) { DoubleArray(7) }
    private fun smooth(a: Double, b: Double, x: Double): Double { val u = ((x - a) / (b - a)).coerceIn(0.0,1.0); return u * u * (3 - 2 * u) }
    private fun derivative(a: Double, b: Double, x: Double): Double { if (x <= a || x >= b) return 0.0; val u = (x-a)/(b-a); return 6*u*(1-u)/(b-a) }
    fun draw(canvas: Canvas, frame: ElasticFoodCardsFrame) {
        val amplitude = frame.reliefAmplitude.coerceIn(0.0,24.0); val fan = frame.fanWidth.coerceIn(0.0,378.0)
        if (amplitude <= 0 || fan <= 0) return
        val tail = frame.tailY.coerceIn(160.0,263.0); val contact = frame.grip.coerceIn(0.0,1.0)
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true; paint.color = Color.WHITE; paint.alpha = 255
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            val runtime = shader as RuntimeShader
            runtime.setFloatUniform("anchor",frame.gatherX.toFloat(),frame.gatherY.toFloat())
            runtime.setFloatUniform("amplitude",amplitude.toFloat()); runtime.setFloatUniform("fanWidth",fan.toFloat())
            runtime.setFloatUniform("tailY",tail.toFloat()); runtime.setFloatUniform("contact",contact.toFloat())
            paint.shader = runtime; canvas.drawRect(0f,0f,393f,300f,paint)
        } else {
            val scale = fan / 378; val strength = amplitude * smooth(0.0,24.0,fan)
            for (i in 0..9) {
                val e = ends[i]; val ray = rays[i]
                val dx = (e[0]-190)*scale*1.2; val dy = e[1]*tail/250-frame.gatherY; val l2 = max(dx*dx+dy*dy,1.0); val l = sqrt(l2)
                ray[0]=dx;ray[1]=dy;ray[2]=l2;ray[3]=-dy/l;ray[4]=dx/l;ray[5]=e[2]*scale;ray[6]=e[3]
            }
            for (i in colors.indices) {
                val x=positions[i*2].toDouble();val y=positions[i*2+1].toDouble()
                val factor=factor(x,y,frame.gatherX,frame.gatherY,strength,tail,contact)
                colors[i]=Color.rgb((223*factor).roundToInt().coerceIn(0,255),(223*factor).roundToInt().coerceIn(0,255),(221*factor).roundToInt().coerceIn(0,255))
            }
            canvas.drawBitmapMesh(white,columns,rows,positions,0,colors,0,paint)
        }
    }
    private fun factor(x: Double,y: Double,gx: Double,gy: Double,strength: Double,tail: Double,contact: Double): Double {
        val left=smooth(0.0,14.0,x);val right=smooth(379.0,393.0,x);val top=smooth(40.0,56.0,y);val bottom=smooth(tail-40,tail+8,y)
        val bx=left*(1-right);val by=top*(1-bottom);val boundary=bx*by
        if(boundary<=0)return 1.0
        val dbx=(derivative(0.0,14.0,x)*(1-right)-left*derivative(379.0,393.0,x))*by
        val dby=(derivative(40.0,56.0,y)*(1-bottom)-top*derivative(tail-40,tail+8,y))*bx
        var h=0.0;var hx=0.0;var hy=0.0;var valley=0.0
        for(ray in rays){
            val t=((x-gx)*ray[0]+(y-gy)*ray[1])/ray[2]
            if(t<=0||t>=1)continue
            val tx=ray[0]/ray[2];val ty=ray[1]/ray[2]
            val d=(x-gx)*ray[3]+(y-gy)*ray[4]-ray[5]*4*t*(1-t)
            val ddx=ray[3]-ray[5]*4*(1-2*t)*tx;val ddy=ray[4]-ray[5]*4*(1-2*t)*ty
            val wn=2.4+4.5*t;val wb=5+6*t;val sn=d/wn;val sb=d/wb
            val qn=exp(-0.5*sn*sn);val qb=exp(-0.5*sb*sb)
            val nx=-qn*sn*(ddx/wn-d*4.5*tx/(wn*wn));val ny=-qn*sn*(ddy/wn-d*4.5*ty/(wn*wn))
            val sx=-qb*sb*(ddx/wb-d*6*tx/(wb*wb));val sy=-qb*sb*(ddy/wb-d*6*ty/(wb*wb))
            val profile=0.55*qb-qn;val envelope=16*t*t*(1-t)*(1-t);val de=32*t*(1-t)*(1-2*t);val gain=strength*ray[6]
            h+=gain*envelope*profile;hx+=gain*(envelope*(0.55*sx-nx)+profile*de*tx);hy+=gain*(envelope*(0.55*sy-ny)+profile*de*ty)
            valley+=ray[6]*envelope*qn
        }
        val dx=boundary*hx+h*dbx;val dy=boundary*hy+h*dby;val nl=sqrt(dx*dx+dy*dy+1)
        val nx=-dx/nl;val ny=-dy/nl;val nz=1/nl;val ll=sqrt(0.4*0.4+0.6*0.6+0.7*0.7)
        val lx=-0.4/ll;val ly=-0.6/ll;val lz=0.7/ll;val diffuse=max(0.0,nx*lx+ny*ly+nz*lz)
        val lighting=(0.72+0.28*diffuse)/(0.72+0.28*lz)
        val ao=min(0.18,0.055*(strength/24)*boundary*valley*(0.3+0.7*contact))
        val hl=sqrt(lx*lx+ly*ly+(lz+1)*(lz+1));val hhx=lx/hl;val hhy=ly/hl;val hhz=(lz+1)/hl
        val specular=0.035*max(0.0,max(0.0,nx*hhx+ny*hhy+nz*hhz).pow(8)-hhz.pow(8))
        return lighting*(1-ao)+specular
    }
}

private const val ELASTIC_FOOD_CARDS_SURFACE_AGSL = """

uniform float2 anchor;
uniform float amplitude;
uniform float fanWidth;
uniform float tailY;
uniform float contact;

float2 surfaceSmooth(float a, float b, float x) {
  float u = clamp((x - a) / (b - a), 0.0, 1.0);
  float derivative = (x > a && x < b)
    ? 6.0 * u * (1.0 - u) / (b - a) : 0.0;
  return float2(u * u * (3.0 - 2.0 * u), derivative);
}

float3 surfaceGaussian(float d, float2 dd, float width, float2 dw) {
  float s = d / width;
  float value = exp(-0.5 * s * s);
  float2 ds = dd / width - d * dw / (width * width);
  return float3(value, -value * s * ds);
}

// Returns height, d(height)/dx, d(height)/dy and occlusion valley weight.
float4 surfaceRay(float2 position, float2 canonicalEnd, float bendValue,
                  float weight, float widthScale, float strength) {
  float2 end = float2(
    anchor.x + (canonicalEnd.x - 190.0) * widthScale * 1.2,
    canonicalEnd.y * tailY / 250.0
  );
  float2 D = end - anchor;
  float lengthSquared = max(dot(D, D), 1.0);
  float inverseLength = 1.0 / sqrt(lengthSquared);
  float2 N = float2(-D.y, D.x) * inverseLength;
  float2 r = position - anchor;
  float rawT = dot(r, D) / lengthSquared;
  float t = clamp(rawT, 0.0, 1.0);
  if (rawT <= 0.0 || rawT >= 1.0) return float4(0.0);
  float2 dt = D / lengthSquared;
  float bend = bendValue * widthScale;
  float d = dot(r, N) - bend * 4.0 * t * (1.0 - t);
  float2 dd = N - bend * 4.0 * (1.0 - 2.0 * t) * dt;
  float width = 2.4 + 4.5 * t;
  float shoulderWidth = 5.0 + 6.0 * t;
  float3 narrow = surfaceGaussian(d, dd, width, 4.5 * dt);
  float3 broad = surfaceGaussian(d, dd, shoulderWidth, 6.0 * dt);
  float profile = 0.55 * broad.x - narrow.x;
  float2 profileGradient = 0.55 * broad.yz - narrow.yz;
  float envelope = 16.0 * t * t * (1.0 - t) * (1.0 - t);
  float envelopeDerivative = 32.0 * t * (1.0 - t) * (1.0 - 2.0 * t);
  float gain = strength * weight;
  return float4(
    gain * envelope * profile,
    gain * (envelope * profileGradient + profile * envelopeDerivative * dt),
    weight * envelope * narrow.x
  );
}

// Input is intended encoded sRGB. Skia's destination can be P3 or sRGB.
// Decode explicitly, then use the SkSL intrinsic for the destination gamut.
float3 surfaceSrgbToLinear(float3 encoded) {
  float3 lo = encoded / 12.92;
  float3 hi = pow((encoded + 0.055) / 1.055, float3(2.4));
  return mix(lo, hi, step(float3(0.04045), encoded));
}

half4 surfaceOutput(float factor) {
  float3 encoded = clamp(float3(223.0, 223.0, 221.0) / 255.0 * factor, 0.0, 1.0);
  return half4(fromLinearSrgb(surfaceSrgbToLinear(encoded)), 1.0);
}

half4 main(float2 position) {
  if (amplitude <= 0.0 || fanWidth <= 0.0) return surfaceOutput(1.0);
  float2 left = surfaceSmooth(0.0, 14.0, position.x);
  float2 right = surfaceSmooth(379.0, 393.0, position.x);
  float2 top = surfaceSmooth(40.0, 56.0, position.y);
  float2 bottom = surfaceSmooth(tailY - 40.0, tailY + 8.0, position.y);
  float bx = left.x * (1.0 - right.x);
  float by = top.x * (1.0 - bottom.x);
  float boundary = bx * by;
  if (boundary <= 0.0) return surfaceOutput(1.0);
  float2 boundaryGradient = float2(
    (left.y * (1.0 - right.x) - left.x * right.y) * by,
    (top.y * (1.0 - bottom.x) - top.x * bottom.y) * bx
  );
  float widthScale = clamp(fanWidth, 0.0, 378.0) / 378.0;
  float fanGain = surfaceSmooth(0.0, 24.0, fanWidth).x;
  float strength = clamp(amplitude, 0.0, 24.0) * fanGain;
  float4 field = float4(0.0);
  // Explicit calls avoid version-sensitive SkSL constant-array constructors.
  field += surfaceRay(position, float2(8.0, 32.0), -8.0, 0.68, widthScale, strength);
  field += surfaceRay(position, float2(25.0, 110.0), 13.0, 0.82, widthScale, strength);
  field += surfaceRay(position, float2(67.0, 185.0), -11.0, 0.92, widthScale, strength);
  field += surfaceRay(position, float2(94.0, 222.0), 16.0, 0.78, widthScale, strength);
  field += surfaceRay(position, float2(167.0, 250.0), -6.0, 1.0, widthScale, strength);
  field += surfaceRay(position, float2(235.0, 248.0), 9.0, 0.91, widthScale, strength);
  field += surfaceRay(position, float2(308.0, 221.0), -15.0, 0.83, widthScale, strength);
  field += surfaceRay(position, float2(332.0, 183.0), 10.0, 0.95, widthScale, strength);
  field += surfaceRay(position, float2(364.0, 112.0), -13.0, 0.76, widthScale, strength);
  field += surfaceRay(position, float2(388.0, 30.0), 7.0, 0.65, widthScale, strength);

  float2 heightGradient = boundary * field.yz + field.x * boundaryGradient;
  float3 normal = normalize(float3(-heightGradient.x, -heightGradient.y, 1.0));
  float3 light = normalize(float3(-0.4, -0.6, 0.7));
  float diffuse = max(0.0, dot(normal, light));
  float flatLighting = 0.72 + 0.28 * light.z;
  float lighting = (0.72 + 0.28 * diffuse) / flatLighting;
  float ao = min(0.18, 0.055 * (strength / 24.0) * boundary * field.w
    * (0.3 + 0.7 * clamp(contact, 0.0, 1.0)));
  float3 halfVector = normalize(light + float3(0.0, 0.0, 1.0));
  float specular = 0.035 * max(0.0,
    pow(max(0.0, dot(normal, halfVector)), 8.0) - pow(halfVector.z, 8.0));
  float factor = lighting * (1.0 - ao) + specular;
  return surfaceOutput(factor);
}

"""
