package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import androidx.annotation.RequiresApi

/** Direct AGSL ports of the SkSL effects used by the React Native reference. */
@RequiresApi(33)
internal class RdsBallShader {
    private val shader = RuntimeShader(
        """
        uniform float2 c;
        uniform float r;
        uniform float3 rot;
        uniform float alpha;
        uniform float blurX;
        uniform float scale;
        float3 toP3(float3 c0) {
          float3 l = pow(clamp(c0, 0.0, 1.0), float3(2.2));
          float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
          return pow(m, float3(1.0 / 2.2));
        }
        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float noise(float2 p) {
          float2 i = floor(p); float2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
          return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), f.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), f.x), f.y);
        }
        float3 rx(float3 v, float a) { float s = sin(a); float k = cos(a); return float3(v.x, k * v.y - s * v.z, s * v.y + k * v.z); }
        float3 ry(float3 v, float a) { float s = sin(a); float k = cos(a); return float3(k * v.x + s * v.z, v.y, -s * v.x + k * v.z); }
        float3 rz(float3 v, float a) { float s = sin(a); float k = cos(a); return float3(k * v.x - s * v.y, s * v.x + k * v.y, v.z); }
        float3 seam(float t) {
          float A = 0.78; float B = 0.22;
          return float3(A * cos(t) + B * cos(3.0 * t), A * sin(t) - B * sin(3.0 * t), 2.0 * sqrt(A * B) * sin(2.0 * t));
        }
        half4 main(float2 p) {
          float2 q = (p - c) / r; q.x /= 1.0 + blurX;
          float d2 = dot(q, q); float edge = 1.5 * scale / r;
          float cover = 1.0 - smoothstep(1.0 - edge, 1.0, sqrt(d2));
          if (cover <= 0.0) return half4(0.0);
          float3 n = float3(q.x, -q.y, sqrt(max(0.0, 1.0 - d2)));
          float3 o = rz(ry(rx(n, rot.x), rot.y), rot.z);
          float best = 9.0; float bt = 0.0;
          for (int i = 0; i < 40; i++) { float t = float(i) * 0.15708; float dd = distance(o, seam(t)); if (dd < best) { best = dd; bt = t; } }
          for (int i = 0; i < 6; i++) { float h = 0.08 / float(i + 1); float da = distance(o, seam(bt - h)); float db = distance(o, seam(bt + h)); if (da < best) { best = da; bt -= h; } if (db < best) { best = db; bt += h; } }
          float3 sp = seam(bt); float3 tg = normalize(seam(bt + 0.01) - seam(bt - 0.01));
          float side = dot(o - sp, cross(sp, tg)); float v = abs(side); float u = bt * 24.0 / 6.2831853;
          float stitchLine = abs(fract(u * 4.0 + v * 9.0) - 0.5);
          float stitch = (1.0 - smoothstep(0.1, 0.2, stitchLine)) * smoothstep(0.018, 0.03, v) * (1.0 - smoothstep(0.075, 0.09, v));
          float groove = 1.0 - smoothstep(0.0, 0.016, v);
          float3 leather = float3(0.97, 0.95, 0.91) * (0.96 + 0.04 * noise(o.xy * 40.0));
          float3 L = normalize(float3(-0.5, 0.6, 0.65)); float diff = 0.5 + 0.5 * dot(n, L);
          float spec = pow(max(0.0, dot(reflect(-L, n), float3(0.0, 0.0, 1.0))), 22.0); float rim = pow(1.0 - n.z, 2.2);
          float3 col = leather * (0.42 + 0.62 * diff); col = mix(col, col * 0.8, groove * 0.6); col = mix(col, float3(0.78, 0.14, 0.16), stitch);
          col += float3(0.55, 0.35, 1.0) * rim * 0.45; col += spec * 0.35; col *= 1.0 - 0.35 * smoothstep(0.2, -0.9, n.y);
          float a = cover * alpha; return half4(half3(toP3(col)) * a, a);
        }
        """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam),
    )

    fun draw(canvas: Canvas, paint: Paint, x: Float, y: Float, radius: Float, rx: Float, ry: Float, rz: Float, alpha: Float, scale: Float, blurX: Float = 0f) {
        shader.setFloatUniform("c", x, y)
        shader.setFloatUniform("r", radius)
        shader.setFloatUniform("rot", rx, ry, rz)
        shader.setFloatUniform("alpha", alpha)
        shader.setFloatUniform("blurX", blurX)
        shader.setFloatUniform("scale", scale)
        paint.reset(); paint.isAntiAlias = true; paint.shader = shader
        val ex = radius * (1f + blurX) + 2f
        canvas.drawRect(x - ex, y - radius - 2f, x + ex, y + radius + 2f, paint)
        paint.shader = null
    }
}

@RequiresApi(33)
internal class RdsRaysShader {
    private val shader = RuntimeShader(
        """
        uniform float2 c; uniform float strength; uniform float time; uniform float scale;
        float3 toP3(float3 c0) { float3 l = pow(clamp(c0,0.0,1.0),float3(2.2)); float3 m=float3(dot(l,float3(0.8225,0.1774,0.0)),dot(l,float3(0.0332,0.9669,0.0)),dot(l,float3(0.0171,0.0724,0.9108))); return pow(m,float3(1.0/2.2)); }
        half4 main(float2 p) { float2 d=(p-c)/scale; float r=length(d); float a=atan(d.y,d.x); float rays=pow(0.5+0.5*cos(a*9.0+time*0.4),3.0)*0.8+0.2; float fall=exp(-r/70.0)*smoothstep(8.0,30.0,r); float core=exp(-r*r/1800.0); float3 col=float3(0.45,0.24,0.95); float k=(rays*fall*0.9+core*0.5)*strength; return half4(half3(toP3(col))*k,k); }
        """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam),
    )

    fun draw(canvas: Canvas, paint: Paint, bounds: RectF, x: Float, y: Float, strength: Float, time: Float, scale: Float) {
        shader.setFloatUniform("c", x, y); shader.setFloatUniform("strength", strength); shader.setFloatUniform("time", time); shader.setFloatUniform("scale", scale)
        paint.reset(); paint.isAntiAlias = true; paint.shader = shader; canvas.drawRect(bounds, paint); paint.shader = null
    }
}

@RequiresApi(33)
internal class RdsLiquidShader {
    private val shader = RuntimeShader(
        """
        uniform float2 origin; uniform float2 size; uniform float fill; uniform float drain; uniform float2 from; uniform float time; uniform float scale;
        float3 toP3(float3 c0) { float3 l=pow(clamp(c0,0.0,1.0),float3(2.2)); float3 m=float3(dot(l,float3(0.8225,0.1774,0.0)),dot(l,float3(0.0332,0.9669,0.0)),dot(l,float3(0.0171,0.0724,0.9108))); return pow(m,float3(1.0/2.2)); }
        half4 main(float2 p) {
          float2 q=p-origin; float x=q.x/size.x; float slosh=(sin(x*9.0+time*9.0)*2.2+sin(x*21.0-time*13.0)*0.9)*scale;
          float level=mix(size.y+6.0*scale,-8.0*scale,fill)+slosh*(1.0-fill*0.85); float under=smoothstep(level-0.8*scale,level+0.8*scale,q.y);
          float r=length(p-from); float ang=atan(p.y-from.y,p.x-from.x); float reach=max(from.x-origin.x,origin.x+size.x-from.x)+10.0*scale;
          float edge=drain*reach+sin(ang*7.0+time*12.0)*3.0*scale*drain*(1.0-drain); float gone=drain>0.0?smoothstep(edge-scale,edge+scale,r):1.0;
          float a=under*gone; if(a<=0.001)return half4(0.0); float y=q.y/size.y;
          float3 gold=mix(float3(1.0,0.94,0.77),float3(0.95,0.77,0.39),smoothstep(0.0,0.5,y)); gold=mix(gold,float3(0.77,0.54,0.17),smoothstep(0.5,1.0,y));
          float men=exp(-pow((q.y-level)/(1.6*scale),2.0))*(1.0-fill*0.5); float rim=drain>0.0?exp(-pow((r-edge)/(1.8*scale),2.0)):0.0; gold+=float3(1.0,0.98,0.9)*(men*0.7+rim*0.6);
          return half4(half3(toP3(min(gold,float3(1.0))))*a,a);
        }
        """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam),
    )

    fun configure(paint: Paint, x: Float, y: Float, width: Float, height: Float, fill: Float, drain: Float, fromX: Float, fromY: Float, time: Float, scale: Float) {
        shader.setFloatUniform("origin", x, y); shader.setFloatUniform("size", width, height); shader.setFloatUniform("fill", fill); shader.setFloatUniform("drain", drain)
        shader.setFloatUniform("from", fromX, fromY); shader.setFloatUniform("time", time); shader.setFloatUniform("scale", scale)
        paint.reset(); paint.isAntiAlias = true; paint.shader = shader
    }
}

@RequiresApi(33)
internal class RdsGlassShader {
    private val shader = RuntimeShader(
        """
        uniform shader image;
        uniform float4 lens;
        uniform float radius;
        uniform float k;
        uniform float scale;
        float box(float2 p, float2 b, float r) {
          float2 q = abs(p) - b + r;
          return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }
        half4 tex(float2 p) {
          float2 f = fract(p - .5), lo = floor(p - .5);
          return mix(mix(image.eval(lo + .5), image.eval(lo + float2(1.5, .5)), f.x),
                     mix(image.eval(lo + float2(.5, 1.5)), image.eval(lo + 1.5), f.x), f.y);
        }
        half4 main(float2 p) {
          half4 base = image.eval(p);
          if (k <= 0.001) return base;
          float2 c = lens.xy + lens.zw * 0.5;
          float2 b = lens.zw * 0.5;
          float d = box(p - c, b, radius);
          if (d > 1.5 * scale) return base;
          float e = 0.6 * scale;
          float2 grad = normalize(float2(
            box(p - c + float2(e, 0.0), b, radius) - box(p - c - float2(e, 0.0), b, radius),
            box(p - c + float2(0.0, e), b, radius) - box(p - c - float2(0.0, e), b, radius)
          ) + 1e-5);
          float bevel = 22.0 * scale;
          float edge = clamp(1.0 + d / bevel, 0.0, 1.0);
          float disp = edge * edge * 5.0 * scale * k;
          float2 q = c + (p - grad * disp - c) * (1.0 - 0.012 * k);
          half4 col = tex(q);
          float rim = exp(-abs(d) / (1.4 * scale)) * (0.35 + 0.4 * clamp(dot(-grad, normalize(float2(1.0, 1.0))), 0.0, 1.0));
          float inner = edge * edge * 0.12;
          col.rgb = half3(float3(col.rgb) + float3(0.92, 0.88, 1.0) * (rim + inner) * k);
          col.a = max(col.a, half(clamp((rim + inner) * k, 0.0, 1.0)));
          return mix(base, col, half(smoothstep(1.5 * scale, -1.5 * scale, d) * k));
        }
        """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam),
    )
    private val layer = RdsHardwareCardLayer()
    private val linearPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = false }
    private val destination = RectF()
    private var cachedBitmap: Bitmap? = null

    fun drawFiltered(
        canvas: Canvas,
        width: Int,
        height: Int,
        lens: FloatArray,
        radius: Float,
        strength: Float,
        scaleX: Float,
        scaleY: Float,
        pixelRatio: Float,
        content: (Canvas) -> Unit,
    ) {
        shader.setFloatUniform("lens", lens[0] * scaleX, lens[1] * scaleY, lens[2] * scaleX, lens[3] * scaleY)
        shader.setFloatUniform("radius", radius * scaleX)
        shader.setFloatUniform("k", strength)
        shader.setFloatUniform("scale", 1f)
        val rendered = layer.render(width, height, RenderEffect.createRuntimeShaderEffect(shader, "image")) { recording ->
            recording.clipRect(0f, (RdsLayout.cardY - 30f) * scaleY, width.toFloat(), (RdsLayout.cardY + RdsLayout.cardH + 30f) * scaleY)
            content(recording)
        } ?: return
        // Copy out of the external buffer before it is reused by the next render.
        val bitmap = rendered.copy(Bitmap.Config.ARGB_8888, false)
        cachedBitmap?.recycle()
        cachedBitmap = bitmap
        destination.set(0f, 0f, width * pixelRatio, height * pixelRatio)
        canvas.drawBitmap(bitmap, null, destination, linearPaint)
    }

    fun close() { cachedBitmap?.recycle(); cachedBitmap = null; layer.close() }

}

@RequiresApi(33)
internal class RdsHardwareCardLayer {
    private var width = 0
    private var height = 0
    private var reader: ImageReader? = null
    private var renderer: HardwareRenderer? = null
    private var node: RenderNode? = null
    private var image: Image? = null
    private var bitmap: Bitmap? = null

    fun render(width: Int, height: Int, effect: RenderEffect? = null, draw: (Canvas) -> Unit): Bitmap? {
        ensureSurface(width, height)
        val renderNode = node ?: return null
        renderNode.setRenderEffect(effect)
        val recording = renderNode.beginRecording(width, height)
        draw(recording)
        renderNode.endRecording()
        image?.close()
        image = null
        bitmap?.recycle()
        bitmap = null
        renderer?.createRenderRequest()?.setWaitForPresent(true)?.syncAndDraw()
        val next = reader?.acquireLatestImage() ?: return null
        image = next
        val buffer = next.hardwareBuffer ?: return null
        return Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)).also { bitmap = it }
    }

    private fun ensureSurface(width: Int, height: Int) {
        if (this.width == width && this.height == height && renderer != null) return
        close()
        this.width = width
        this.height = height
        val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3, usage)
        node = RenderNode("RaffleDrumSpinCardLayer").apply { setPosition(0, 0, width, height) }
        renderer = HardwareRenderer().apply {
            setName("RaffleDrumSpinCardRenderer")
            setOpaque(false)
            setSurface(reader!!.surface)
            setContentRoot(node!!)
            start()
        }
    }

    fun close() {
        image?.close()
        image = null
        bitmap?.recycle()
        bitmap = null
        renderer?.stop()
        renderer?.destroy()
        renderer = null
        reader?.close()
        reader = null
        node = null
        width = 0
        height = 0
    }
}

@RequiresApi(33)
internal class RdsFoilShader(ticketBitmap: Bitmap) {
    private val shader = RuntimeShader(
        """
        uniform shader ticket;
        uniform float2 origin;
        uniform float2 size;
        uniform float2 img;
        uniform float2 tilt;
        uniform float lit;
        uniform float sweep;
        uniform float mode;
        uniform float burn;
        uniform float body;
        uniform float scale;
        float3 toP3(float3 c0) {
          float3 l = pow(clamp(c0, 0.0, 1.0), float3(2.2));
          float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
          return pow(m, float3(1.0 / 2.2));
        }
        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float lum(half4 c) { return c.a > 0.004 ? dot(float3(c.rgb) / float(c.a), float3(0.3, 0.55, 0.15)) * float(c.a) : 0.0; }
        float vnoise(float2 p) {
          float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
          return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
        }
        float3 tiltN(float3 n) {
          float sy = sin(tilt.x); float cy = cos(tilt.x);
          float3 a = float3(cy * n.x + sy * n.z, n.y, -sy * n.x + cy * n.z);
          float sx = sin(tilt.y); float cx = cos(tilt.y);
          return float3(a.x, cx * a.y - sx * a.z, sx * a.y + cx * a.z);
        }
        half4 main(float2 p) {
          float2 q = (p - origin) / size;
          float2 uv = q * img;
          half4 c = ticket.eval(uv);
          float a = float(c.a);
          if (mode > 0.5) {
            float front = body + 0.09 - burn * (body + 0.27);
            float n = vnoise(q * float2(28.0, 11.0)) * 0.65 + vnoise(q * float2(90.0, 34.0)) * 0.35;
            float d = q.x - front + (n - 0.5) * 0.16;
            if (mode < 1.5) {
              float keep = 1.0 - smoothstep(-0.003, 0.003, d);
              return half4(0.0, 0.0, 0.0, keep);
            }
            float live = step(0.0005, burn) * (1.0 - step(0.9995, burn)) * step(q.x, body);
            float e = exp(-pow(max(-d, 0.0) / 0.03, 1.4)) * step(d, 0.004) * a * live;
            float3 hot = mix(float3(1.0, 0.45, 0.08), float3(1.0, 0.96, 0.84), exp(-pow(d / 0.007, 2.0)));
            return half4(half3(toP3(hot)) * e, e);
          }
          if (a < 0.002) return half4(0.0);
          float3 albedo = float3(c.rgb) / a;
          float2 px = img / size * (1.4 * scale);
          float hx = lum(ticket.eval(uv + float2(px.x, 0.0))) - lum(ticket.eval(uv - float2(px.x, 0.0)));
          float hy = lum(ticket.eval(uv + float2(0.0, px.y))) - lum(ticket.eval(uv - float2(0.0, px.y)));
          float3 n = tiltN(normalize(float3(-hx * 2.6, -hy * 2.6, 1.0)));
          float3 L = normalize(float3(-0.35, -0.6, 1.0));
          float3 H = normalize(L + float3(0.0, 0.0, 1.0));
          float diff = clamp(dot(n, L), 0.0, 1.0);
          float spec = pow(clamp(dot(n, H), 0.0, 1.0), 70.0);
          float2 cq = q - 0.5;
          float band0 = dot(cq * float2(1.0, 0.36), normalize(float2(1.0, 0.6))) + tilt.x * 0.9 + tilt.y * 0.5;
          float sheen = exp(-pow(band0 / 0.13, 2.0));
          float sw = sweep > 0.0 ? exp(-pow((dot(cq * float2(1.0, 0.36), normalize(float2(1.0, 0.5))) - mix(-0.75, 0.75, sweep)) / 0.06, 2.0)) : 0.0;
          float2 gp = p / (3.0 * scale);
          float2 cell = floor(gp);
          float2 off = float2(hash(cell + 5.7), hash(cell + 9.1)) * 0.6 + 0.2;
          float dot0 = 1.0 - smoothstep(0.05, 0.32, length(fract(gp) - off));
          float3 fn = tiltN(normalize(float3((hash(cell) - 0.5) * 0.8, (hash(cell + 17.3) - 0.5) * 0.8, 1.0)));
          float glint = pow(clamp(dot(fn, H), 0.0, 1.0), 600.0) * step(0.9, hash(cell + 3.1)) * dot0;
          float busy = clamp(length(float2(hx, hy)) * 7.0, 0.0, 1.0);
          float3 film = 0.5 + 0.5 * cos(6.2831853 * (n.z * 1.6 + tilt.x * 0.8 + float3(0.0, 0.33, 0.67)));
          float3 col = albedo * (0.84 + 0.3 * diff);
          col += float3(1.0, 0.9, 0.68) * (spec * 0.5 + sheen * 0.2 + sw * 0.6);
          col += float3(1.0, 0.95, 0.82) * glint * 0.9;
          col = mix(col, col * (0.75 + 0.5 * film), busy * 0.14);
          col = mix(albedo, col, lit);
          return half4(half3(toP3(min(col, float3(1.0)))) * a, a);
        }
        """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam),
    )

    init {
        shader.setInputShader("ticket", BitmapShader(ticketBitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) })
    }

    fun configure(
        paint: Paint,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
        imageWidth: Float,
        imageHeight: Float,
        frame: RdsFrame,
        mode: Float,
        sweep: Float = frame.foil,
        lit: Float = frame.lit,
        tiltX: Float = frame.tiltX,
        tiltY: Float = frame.tiltY,
    ) {
        shader.setFloatUniform("origin", originX, originY)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("img", imageWidth, imageHeight)
        shader.setFloatUniform("tilt", tiltX, tiltY)
        shader.setFloatUniform("lit", lit)
        shader.setFloatUniform("sweep", sweep)
        shader.setFloatUniform("mode", mode)
        shader.setFloatUniform("burn", frame.burn)
        shader.setFloatUniform("body", .729f)
        shader.setFloatUniform("scale", width / 300f)
        paint.reset(); paint.isAntiAlias = true; paint.shader = shader
    }
}

@RequiresApi(33)
internal class RdsLightShader {
    private val shader = RuntimeShader(
        """
        uniform float2 size;
        uniform float2 ball;
        uniform float streak;
        uniform float2 core;
        uniform float rays;
        uniform float rot;
        uniform float2 ringC;
        uniform float ringR;
        uniform float ring;
        uniform float2 stage;
        uniform float aurora;
        uniform float time;
        uniform float scale;
        float3 toP3(float3 c0) {
          float3 l = pow(clamp(c0, 0.0, 1.0), float3(2.2));
          float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
          return pow(m, float3(1.0 / 2.2));
        }
        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float noise(float2 p) {
          float2 i = floor(p); float2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
          return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), f.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), f.x), f.y);
        }
        float band(float x, float c, float w) { return exp(-pow((x - c) / w, 2.0)); }
        half4 main(float2 p) {
          float3 col = float3(0.0);
          if (streak > 0.0) {
            float2 d = p - ball;
            float s = exp(-abs(d.y) / (2.2 * scale)) * exp(-pow(d.x / (48.0 * scale), 2.0));
            col += float3(1.0, 0.93, 0.8) * s * 0.4 * streak;
          }
          if (rays > 0.0) {
            float2 d = p - core;
            float r = length(d) / scale;
            float a = atan(d.y, d.x) + rot;
            float spoke = pow(max(0.0, cos(a * 9.0)), 10.0) * 0.6 + pow(max(0.0, cos(a * 14.0 + 1.3)), 16.0) * 0.4;
            float len = 80.0 + 90.0 * noise(float2(floor(a * 14.0 / 6.2831853 * 4.0), 3.0));
            float fall = exp(-r / len) * smoothstep(8.0, 40.0, r);
            float glow = exp(-r * r / (2.0 * 55.0 * 55.0));
            col += float3(1.0, 0.88, 0.62) * (spoke * fall * 0.42 + glow * 0.22) * rays;
          }
          if (ring > 0.0) {
            float r = length(p - ringC);
            float w = 9.0 * scale;
            float3 split = float3(band(r, ringR * 1.008, w), band(r, ringR, w), band(r, ringR * 0.992, w));
            col += mix(float3(band(r, ringR, w)), split, 0.55) * float3(1.0, 0.93, 0.8) * 0.4 * ring + float3(1.0, 0.9, 0.7) * band(r, ringR * 0.92, w * 3.0) * 0.12 * ring;
          }
          if (aurora > 0.0) {
            float2 q = (p - stage) / (size.x * 0.5);
            float mask = exp(-dot(q * float2(0.85, 2.6), q * float2(0.85, 2.6)));
            float2 w = q * 2.2 + float2(noise(q * 1.7 + time * 0.25), noise(q * 1.9 - time * 0.2)) * 1.4;
            float silk = band(sin(w.x * 2.1 + w.y * 1.3 + time * 0.6), 0.6, 0.35) + 0.6 * band(sin(w.x * -1.4 + w.y * 2.6 - time * 0.45), 0.4, 0.3);
            float3 c1 = mix(float3(0.45, 0.25, 0.95), float3(1.0, 0.72, 0.35), smoothstep(-0.4, 0.6, q.x + 0.3 * sin(time * 0.7)));
            col += c1 * silk * mask * 0.38 * aurora;
          }
          float3 o = toP3(min(col, float3(1.0)));
          return half4(half3(o), half(max(o.r, max(o.g, o.b))));
        }
        """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam),
    )

    fun draw(canvas: Canvas, paint: Paint, width: Float, height: Float, frame: RdsFrame, sx: Float, sy: Float) {
        val stageX = RdsLayout.revealX * sx
        val stageY = 432f * sy
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("ball", frame.streakAt[0], frame.streakAt[1])
        shader.setFloatUniform("streak", frame.streak)
        shader.setFloatUniform("core", stageX, stageY)
        shader.setFloatUniform("rays", frame.rays)
        shader.setFloatUniform("rot", frame.t * .22f)
        shader.setFloatUniform("ringC", stageX, stageY)
        shader.setFloatUniform("ringR", frame.ringR)
        shader.setFloatUniform("ring", frame.ring)
        shader.setFloatUniform("stage", stageX, stageY)
        shader.setFloatUniform("aurora", frame.aurora)
        shader.setFloatUniform("time", frame.t + frame.idleT)
        shader.setFloatUniform("scale", sx)
        paint.reset(); paint.isAntiAlias = true; paint.shader = shader
        if (frame.streak <= .001f && frame.rays <= .001f && frame.ring <= .001f) {
            canvas.drawRect(0f, stageY - 240f * sy, width, stageY + 240f * sy, paint)
        } else {
            canvas.drawRect(0f, 0f, width, height, paint)
        }
        paint.shader = null
    }
}

@RequiresApi(33)
internal class RdsMorphShader(private val ticketBitmap: Bitmap) {
    private val shader = RuntimeShader(
        """
        uniform shader ticket;
        uniform float2 c; uniform float r; uniform float3 rot; uniform float stitches; uniform float morph;
        uniform float2 tc; uniform float2 tsize; uniform float body; uniform float2 img; uniform float corner;
        uniform float glow; uniform float alpha; uniform float scale;
        float3 toP3(float3 c0) { float3 l=pow(clamp(c0,0.0,1.0),float3(2.2)); float3 m=float3(dot(l,float3(0.8225,0.1774,0.0)),dot(l,float3(0.0332,0.9669,0.0)),dot(l,float3(0.0171,0.0724,0.9108))); return pow(m,float3(1.0/2.2)); }
        float3 rx(float3 v,float a){float s=sin(a);float k=cos(a);return float3(v.x,k*v.y-s*v.z,s*v.y+k*v.z);}
        float3 ry(float3 v,float a){float s=sin(a);float k=cos(a);return float3(k*v.x+s*v.z,v.y,-s*v.x+k*v.z);}
        float3 seam(float t){float A=0.78;float B=0.22;return float3(A*cos(t)+B*cos(3.0*t),A*sin(t)-B*sin(3.0*t),2.0*sqrt(A*B)*sin(2.0*t));}
        float box(float2 p,float2 b,float rr){float2 q=abs(p)-b+rr;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-rr;}
        float4 seamAt(float2 p,float2 cc){
          float2 q=(p-cc)/r;float l=length(q);float2 qq=l>0.999?q/l*0.999:q;
          float3 n=float3(qq.x,-qq.y,sqrt(max(0.0,1.0-dot(qq,qq))));float3 o=ry(rx(n,rot.x),rot.y);
          float best=9.0;float bt=0.0;
          for(int i=0;i<40;i++){float t=float(i)*0.15708;float dd=distance(o,seam(t));if(dd<best){best=dd;bt=t;}}
          for(int i=0;i<6;i++){float h=0.08/float(i+1);float da=distance(o,seam(bt-h));float db=distance(o,seam(bt+h));if(da<best){best=da;bt-=h;}if(db<best){best=db;bt+=h;}}
          float3 sp=seam(bt);float3 tg=normalize(seam(bt+0.01)-seam(bt-0.01));float side=dot(o-sp,cross(sp,tg));return float4(side,bt,abs(side),n.z);
        }
        float3 leather(float2 p,float2 cc,float4 s){
          float2 q=(p-cc)/r;float3 n=float3(q.x,-q.y,sqrt(max(0.0,1.0-dot(q,q))));float v=s.z;float u=s.y*24.0/6.2831853;
          float line=abs(fract(u*4.0+v*9.0)-0.5);float popped=step(fract(s.y/6.2831853+0.25),stitches);
          float stitch=(1.0-smoothstep(0.1,0.2,line))*smoothstep(0.018,0.03,v)*(1.0-smoothstep(0.075,0.09,v))*(1.0-popped);
          float groove=1.0-smoothstep(0.0,0.016,v);float3 L=normalize(float3(-0.5,0.6,0.65));float diff=0.5+0.5*dot(n,L);
          float spec=pow(max(0.0,dot(reflect(-L,n),float3(0.0,0.0,1.0))),22.0);float rim=pow(1.0-n.z,2.2);
          float3 col=float3(0.97,0.95,0.91)*(0.42+0.62*diff);col=mix(col,col*0.8,groove*0.6);col=mix(col,float3(0.78,0.14,0.16),stitch);
          col+=float3(0.55,0.35,1.0)*rim*0.45+spec*0.35;col+=float3(1.0,0.85,0.5)*glow*rim*0.7;col*=1.0-0.35*smoothstep(0.2,-0.9,n.y);
          float sg=max(stitches*0.5,smoothstep(0.0,0.2,morph));col=mix(col,float3(1.0,0.86,0.5)*1.3,sg*(1.0-smoothstep(0.0,0.035+0.05*sg,v)));return col;
        }
        float ease(float x){x=clamp(x,0.0,1.0);return x*x*(3.0-2.0*x);}
        float smax(float a,float b,float s){float h=max(s-abs(a-b),0.0)/s;return max(a,b)+h*h*s*0.25;}
        half4 main(float2 p){
          float k=morph;float crack=smoothstep(0.0,0.2,k);float peel=ease((k-0.1)/0.5);float shape=ease((k-0.3)/0.58);
          float open=r*0.34*peel;float turn=1.0-0.24*sin(3.14159*peel)*(1.0-shape);float edge=scale*(1.0+3.0*shape*(1.0-shape));
          float2 bodyC=float2(tc.x-tsize.x*0.5+tsize.x*body*0.5,tc.y);float2 stubC=float2(tc.x+tsize.x*0.5-tsize.x*(1.0-body)*0.5,tc.y);
          float3 acc=float3(0.0);float aacc=0.0;bool late=shape>0.985;float2 uv=(p-(tc-tsize*0.5))/tsize*img;half4 tk=ticket.eval(uv);
          float3 foil=tk.a>0.001?float3(tk.rgb)/float(tk.a):float3(0.95,0.74,0.36);float tint=smoothstep(0.3,0.75,k);float3 gold=float3(1.0,0.84,0.48);
          bool still=peel<=0.0;float4 s0=(late||!still||length(p-c)>r+2.0*scale)?float4(0.0):seamAt(p,c);
          for(int piece=0;piece<2;piece++){
            float sgn=piece==0?1.0:-1.0;float2 cc=c+float2(-sgn*open,0.0);float2 mid=cc+float2(-sgn*0.42*r,0.0);float2 pp=float2(mid.x+(p.x-mid.x)/turn,p.y);
            float2 rc=piece==0?bodyC:stubC;float2 rb=piece==0?float2(tsize.x*body*0.5,tsize.y*0.5):float2(tsize.x*(1.0-body)*0.5,tsize.y*0.5);
            float2 rc0=mid;float2 rb0=float2(0.5*r*turn,0.9*r);float2 rcK=mix(rc0,rc,shape);float2 rbK=mix(rb0,rb,shape);float cr=mix(0.45*r,corner,shape);
            float dRect=box(p-rcK,rbK,min(cr,min(rbK.x,rbK.y)));float dSphere=(length((pp-cc)/r)-1.0)*r;if(!late&&mix(dSphere,dRect,shape)>edge)continue;
            float4 s=late?float4(0.0):(still?s0:seamAt(pp,cc));float dBall=late?1e4:smax(dSphere,-sgn*s.x*r,0.22*r);float d=late?box(p-rc,rb,corner):mix(dBall,dRect,shape);
            float cover=1.0-smoothstep(-edge,edge,d);if(cover<=0.001)continue;float3 lc=late?float3(0.0):leather(pp,cc,s);float3 col=mix(lc,foil,tint);
            float rim=exp(-abs(d)/(scale*(1.5+4.0*shape)))*crack*(1.0-smoothstep(0.75,1.0,k));col=mix(col,gold*1.25,rim*0.8);col+=gold*sin(3.14159*shape)*0.2;
            float a=cover*mix(1.0,float(tk.a),smoothstep(0.85,1.0,k));acc+=col*a;aacc+=a;
          }
          aacc=min(aacc,1.0);float3 col=aacc>0.0?acc/max(aacc,0.0001):float3(0.0);float rr=length((p-c)/r);
          float core=crack*(1.0-smoothstep(0.5,0.8,k))*(1.0-smoothstep(0.55,1.2+0.4*peel,rr));float3 coreC=mix(gold,float3(1.0,0.97,0.88),exp(-rr*rr*3.0));
          float halo=0.0;if(k<0.5){halo=glow*exp(-max(rr-1.0,0.0)*3.2)*(1.0-smoothstep(1.5,1.9,rr))*step(1.0,rr)*(1.0-k*2.0);}
          float under=clamp(core+halo,0.0,1.0);float3 underC=core>halo?coreC:float3(1.0,0.86,0.55);float outA=clamp(aacc+under*(1.0-aacc),0.0,1.0);
          float3 outC=(col*aacc+underC*under*(1.0-aacc))/max(outA,0.0001);outA*=alpha;return half4(half3(toP3(min(outC,float3(1.0))))*outA,outA);
        }
        """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam),
    )

    init {
        shader.setInputShader("ticket", BitmapShader(ticketBitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) })
    }

    fun draw(canvas: Canvas, paint: Paint, frame: RdsFrame, sx: Float, sy: Float) {
        val hero = frame.hero
        val cx = hero?.x ?: 201f * sx
        val cy = hero?.y ?: 432f * sy
        val radius = hero?.radius ?: 56f * sx
        val rx = hero?.rx ?: (4f + (frame.t - RdsTime.eject1) * 1.2f)
        val ry = hero?.ry ?: .5f
        val ticketW = 300f * sx
        val ticketH = 111f * sx
        shader.setFloatUniform("c", cx, cy); shader.setFloatUniform("r", radius); shader.setFloatUniform("rot", rx, ry, 0f)
        shader.setFloatUniform("stitches", frame.stitches); shader.setFloatUniform("morph", frame.morph)
        shader.setFloatUniform("tc", 201f * sx, 432f * sy); shader.setFloatUniform("tsize", ticketW, ticketH)
        shader.setFloatUniform("body", .729f); shader.setFloatUniform("img", ticketBitmap.width.toFloat(), ticketBitmap.height.toFloat()); shader.setFloatUniform("corner", 12f * sx)
        shader.setFloatUniform("glow", hero?.glow ?: 1f); shader.setFloatUniform("alpha", 1f); shader.setFloatUniform("scale", sx)
        paint.reset(); paint.isAntiAlias = true; paint.shader = shader
        val pad = 1.95f * radius
        val left = minOf(cx - pad, 201f * sx - ticketW / 2f - 8f * sx)
        val top = minOf(cy - pad, 432f * sy - ticketH / 2f - 8f * sx)
        val right = maxOf(cx + pad, 201f * sx + ticketW / 2f + 8f * sx)
        val bottom = maxOf(cy + pad, 432f * sy + ticketH / 2f + 8f * sx)
        canvas.save()
        val squash = hero?.squash ?: 1f
        if (squash != 1f) canvas.scale(1f / kotlin.math.sqrt(squash), squash, cx, cy + radius)
        canvas.drawRect(left, top, right, bottom, paint)
        canvas.restore(); paint.shader = null
    }
}
