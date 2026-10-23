package com.ngocdevv.nativecook.blocks.cards.cityeventhero

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class CityEventHeroApi33ScenePainter(private val assets: CityEventHeroAssets) {
    private val shader = RuntimeShader(CityEventHeroSceneAgsl)
    private val mist = RuntimeShader(CityEventHeroMistAgsl)
    private val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapShaders = LinkedHashMap<String, BitmapShader>()
    private val bitmaps = LinkedHashMap<String, Bitmap>()
    private val matrices = LinkedHashMap<String, Matrix>()
    private val sun = CityEventHeroSun()
    private val led = FloatArray(16)
    private val bursts = CityEventHeroFire.bursts

    init {
        addInput("photo", assets.cityBitmap)
        addInput("fill", assets.fill)
        addInput("towerMask", assets.tower)
        addInput("nearMask", assets.near)
        addInput("backDepth", assets.backDepth)
        addInput("depthMap", assets.depth)
        addInput("lightMap", assets.lights)
        addInput("keyMap", assets.key)
        addInput("bloomMap", assets.bloom)
        addInput("bloomKeyMap", assets.bloomKey)
        addInput("facade", assets.facade)
        addInput("glyphs", assets.glyphs)
        addInput("avatar", assets.viewer)
        shader.setInputShader("photo", bitmapShaders.getValue("photo"))
        shader.setInputShader("fill", bitmapShaders.getValue("fill"))
        shader.setInputShader("avatar", bitmapShaders.getValue("avatar"))
        shader.setInputBuffer("towerMask", bitmapShaders.getValue("towerMask"))
        shader.setInputBuffer("nearMask", bitmapShaders.getValue("nearMask"))
        shader.setInputBuffer("backDepth", bitmapShaders.getValue("backDepth"))
        shader.setInputBuffer("depthMap", bitmapShaders.getValue("depthMap"))
        shader.setInputBuffer("lightMap", bitmapShaders.getValue("lightMap"))
        shader.setInputBuffer("keyMap", bitmapShaders.getValue("keyMap"))
        shader.setInputBuffer("bloomMap", bitmapShaders.getValue("bloomMap"))
        shader.setInputBuffer("bloomKeyMap", bitmapShaders.getValue("bloomKeyMap"))
        shader.setInputBuffer("facade", bitmapShaders.getValue("facade"))
        shader.setInputBuffer("glyphs", bitmapShaders.getValue("glyphs"))
        paint.shader = shader
    }

    fun draw(
        canvas: Canvas,
        physicalRect: CityEventHeroPhotoRect,
        density: Float,
        referenceUnit: Float,
        frame: CityEventHeroFrame,
        tiltX: Float,
        tiltY: Float,
        mode: Int,
        alpha: Float,
    ) {
        // RN's shader uses dp for smoke and depth falloff, even on high-density screens.
        val rect = CityEventHeroPhotoRect(
            physicalRect.left / density,
            physicalRect.top / density,
            physicalRect.width / density,
            physicalRect.height / density,
        )
        val photoScale = rect.width / assets.cityBitmap.width
        updateInputMatrices(rect)
        val cameraX = frame.camX * CityEventHeroSpace.ampX + tiltX
        val cameraY = frame.camY * CityEventHeroSpace.ampY + tiltY
        val tip = CityEventHeroTip(rect, frame, tiltX, tiltY)
        val fire = frame.fire
        val fireOpacity = min(1f, frame.night / CityEventHeroNight.fireFade)
        val unit = referenceUnit / density * tip.third

        shader.setFloatUniform("geo", rect.left, rect.top, photoScale)
        shader.setFloatUniform("cam", cameraX, cameraY, frame.camZ)
        CityEventHeroSunAt(frame.day, sun)
        shader.setFloatUniform(
            "sun",
            sun.azimuth,
            sun.elevation,
            sun.amount * (1f - min(1f, frame.night / CityEventHeroNight.grade)),
            0f,
        )
        setBurstUniforms("f0", bursts[0], fire, fireOpacity, tip.first, tip.second, unit)
        setBurstUniforms("f1", bursts[1], fire, fireOpacity, tip.first, tip.second, unit)
        setBurstUniforms("f2", bursts[2], fire, fireOpacity, tip.first, tip.second, unit)
        setBurstColor("c0", bursts[0].color)
        setBurstColor("c1", bursts[1].color)
        setBurstColor("c2", bursts[2].color)
        shader.setFloatUniform(
            "smoke",
            smokeAge(bursts[0], fire),
            smokeAge(bursts[1], fire),
            smokeAge(bursts[2], fire),
        )
        shader.setFloatUniform("flare", tip.first, tip.second, if (fire >= 0f) CityEventHeroFlareAt(fire) * fireOpacity else 0f)
        CityEventHeroLedAt(frame.show, led)
        shader.setFloatUniform("ledA", led[0] * fireOpacity, led[1], led[2], led[3])
        shader.setFloatUniform("ledB", led[4], led[5], led[6], led[7])
        shader.setFloatUniform("ledC", led[8], led[9], led[10], led[11])
        shader.setFloatUniform("ledD", led[12], fireOpacity, 0f, 0f)
        shader.setFloatUniform("drift", frame.drift)
        shader.setFloatUniform("night", frame.night)
        shader.setFloatUniform("mode", mode.toFloat())

        paint.alpha = 255
        paint.shader = shader
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        val checkpoint = canvas.save()
        canvas.scale(density, density)
        canvas.drawRect(rect.left, rect.top, rect.left + rect.width, rect.top + rect.height, paint)
        canvas.restoreToCount(checkpoint)
        paint.alpha = 255
    }

    fun drawMist(canvas: Canvas, geometry: CityEventHeroGeometry, frame: CityEventHeroFrame) {
        val amount = .15f * frame.mist * (1f - min(1f, frame.night / CityEventHeroNight.grade))
        if (amount <= 0f) return
        mist.setFloatUniform("drift", frame.drift * 22f)
        mist.setFloatUniform("amt", amount)
        mist.setFloatUniform("tint", .8f, .9f, .93f)
        mistPaint.alpha = 255
        mistPaint.shader = mist
        val checkpoint = canvas.save()
        canvas.translate(0f, geometry.y(CityEventHeroCard.y) - CityEventHeroCard.y * geometry.unit)
        canvas.scale(geometry.unit, geometry.unit)
        canvas.drawRect(0f, CityEventHeroCard.y, CityEventHeroRefWidth, CityEventHeroCard.y + 1000f, mistPaint)
        canvas.restoreToCount(checkpoint)
    }

    private fun addInput(name: String, bitmap: Bitmap) {
        val tile = if (name in setOf("towerMask", "nearMask", "lightMap", "keyMap", "bloomMap", "bloomKeyMap")) Shader.TileMode.DECAL else Shader.TileMode.CLAMP
        val inputShader = BitmapShader(bitmap, tile, tile)
        inputShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        bitmapShaders[name] = inputShader
        bitmaps[name] = bitmap
        matrices[name] = Matrix()
    }

    private fun updateInputMatrices(rect: CityEventHeroPhotoRect) {
        bitmapShaders.forEach { (name, inputShader) ->
            val matrix = matrices.getValue(name)
            if (name == "glyphs" || name == "avatar") {
                matrix.reset()
            } else {
                val bitmap = bitmaps.getValue(name)
                matrix.setScale(rect.width / bitmap.width, rect.height / bitmap.height)
                matrix.postTranslate(rect.left, rect.top)
            }
            inputShader.setLocalMatrix(matrix)
        }
    }

    private fun setBurstUniforms(
        name: String,
        burst: CityEventHeroBurst,
        fire: Float,
        fireOpacity: Float,
        tipX: Float,
        tipY: Float,
        unit: Float,
    ) {
        val age = fire - burst.at
        val amount = if (fire >= 0f) CityEventHeroFlashAt(burst, age) * fireOpacity else 0f
        shader.setFloatUniform(name, tipX + burst.dx * unit, tipY + burst.dy * unit, amount)
    }

    private fun setBurstColor(name: String, color: Int) {
        val red = ((color shr 16) and 0xFF).toFloat() / 255f
        val green = ((color shr 8) and 0xFF).toFloat() / 255f
        val blue = (color and 0xFF).toFloat() / 255f
        shader.setFloatUniform(name, red, green, blue)
    }

    private fun smokeAge(burst: CityEventHeroBurst, fire: Float): Float {
        return if (fire >= 0f && fire > burst.at) fire - burst.at else -1f
    }
}

private const val CityEventHeroSceneAgsl = """

uniform shader photo;
uniform shader fill;
uniform shader towerMask;
uniform shader nearMask;
uniform shader backDepth;
uniform shader depthMap;
uniform shader lightMap;
uniform shader keyMap;
uniform shader bloomMap;
uniform shader bloomKeyMap;
uniform shader facade;
uniform shader glyphs;
uniform shader avatar;
uniform float mode;   // 0 = whole scene, 1 = tower and near layers only (over the title)
uniform float night;
uniform float3 geo;   // photo left, photo top (dp), dp per photo px
uniform float3 cam;   // camera offset x, y (photo px), dolly
uniform float4 sun;   // azimuth, elevation (rad), amount, -
uniform float3 f0;    // bursts: x, y (dp), light amount
uniform float3 f1;
uniform float3 f2;
uniform float3 c0;    // burst colours
uniform float3 c1;
uniform float3 c2;
uniform float3 smoke; // seconds since each burst (< 0 = none)
uniform float3 flare; // spire tip x, y (dp), amount
uniform float4 ledA;  // on, scan front (rows), page (-1 none), page's bottom row
uniform float4 ledB;  // letters lit, day number, portrait y, radius (photo px)
uniform float4 ledC;  // portrait visible, white flash, field phase (rows), spill
uniform float4 ledD;  // dissolve, smoke strength, -, -
uniform float drift;

float3 toSrgb(float3 p) {
  return p;
}
float3 toP3(float3 s) {
  return s;
}
float hash21(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 u = fract(p);
  u = u * u * (3.0 - 2.0 * u);
  return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x), mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x), u.y);
}
float sq(float x) { return x * x; }
float2 dp(float2 q) { return geo.xy + q * geo.z; }
float gateAt(float k, float front) {
  return smoothstep(k - 0.0350, k, front) * step(0.01, k);
}
float word0(float k) {
  if (k < 0.5) return 18.0;
  if (k < 1.5) return 12.0;
  if (k < 2.5) return 12.0;
  return 0.0;
}
float word1(float k) {
  if (k < 0.5) return 21.0;
  if (k < 1.5) return 16.0;
  if (k < 2.5) return 20.0;
  return 0.0;
}
float word2(float k) {
  if (k < 0.5) return 19.0;
  if (k < 1.5) return 13.0;
  if (k < 2.5) return 12.0;
  if (k < 3.5) return 17.0;
  if (k < 4.5) return 12.0;
  return 0.0;
}
float dateTail(float k) {
  if (k < 0.5) return 0.0;
  if (k < 1.5) return 0.0;
  if (k < 2.5) return 14.0;
  if (k < 3.5) return 11.0;
  if (k < 4.5) return 15.0;
  return 0.0;
}

// Where a layer with parallax share p shows its content at screen photo point P.
float2 layerUv(float2 P, float p) {
  float zoom = cam.z * 0.1000 + abs(cam.x) * 0.0025 + abs(cam.y) * 0.0017;
  float2 F = float2(428.0000, 650.0000);
  return F + (P - F - cam.xy * p) / (1.0 + zoom * p);
}

// Glyph index of letter k on page p (the words, then the date).
float pageGlyph(float p, float k) {
  if (p < 0.5) return word0(k);
  if (p < 1.5) return word1(k);
  if (p < 2.5) return word2(k);
  float tens = floor(ledB.y / 10.0);
  if (k < 0.5) return 1.0 + tens;
  if (k < 1.5) return 1.0 + ledB.y - 10.0 * tens;
  return dateTail(k);
}

// One LED cell of the façade: colour (already including the panel's brightness).
float3 ledContent(float col, float row) {
  float b = (col + 0.5) / 29.0000;
  // The boot scan: nothing above its front, a bright edge at it.
  if (row > ledA.y) return float3(0.0);
  // Colour field behind the message: waves travelling up the tower.
  float w = 0.5 + 0.5 * sin(6.2832 * (row - ledC.z) / 120.0000 + 2.2 * b);
  float3 c = mix(float3(0.0200, 0.6200, 0.7200), float3(0.2200, 0.1400, 0.6200), w) * 0.3000 * (0.8 + 0.4 * hash21(float2(col, row)));
  c += float3(0.8200, 0.9600, 1.0000) * exp(-sq((ledA.y - row) / 6.0)) * step(ledA.y, 9999.0);
  // A page of upright letters stacked top to bottom.
  if (ledA.z > -0.5) {
    float p = ledA.z;
    float date = step(2.5000, p);
    float n = date > 0.5 ? 5.0 : (p < 0.5 ? 3.0000 : (p < 1.5 ? 3.0000 : 5.0000));
    float h = n * 40.0000 - 5.0000 + date * 8.0000;
    float yy = h - 1.0 - (row - ledA.w);
    if (date > 0.5 && yy >= 80.0000) yy -= 8.0000;
    float k = floor(yy / 40.0000);
    float inner = yy - k * 40.0000;
    float gc = floor((col - 7.0000) / 3.0000);
    if (yy >= 0.0 && k < n && inner < 35.0000 && gc >= 0.0 && gc < 5.0) {
      float g = pageGlyph(p, k);
      float bit = glyphs.eval(float2(g * 5.0 + gc + 0.5, floor(inner / 5.0000) + 0.5)).r;
      float age = (ledB.x - k) * 0.0400;
      float lit = step(0.0, age);
      float3 tc = mix(float3(1.0000, 0.9800, 0.9400), float3(1.0000, 0.8400, 0.5200), smoothstep(0.0, 0.1800, age));
      c = mix(c, tc * (1.0 + 0.8 * exp(-max(age, 0.0) / 0.08)), bit * lit);
    }
  }
  // The viewer's portrait, wrapped round the tower, in a gold ring.
  if (ledC.x > 0.5 && ledB.w > 0.3) {
    float y = 1320.0000 - (row + 0.5) * 3.0000;
    float hw = facade.eval(dp(float2(427.5000, y))).r * 64.0;
    float x = ((col + 0.5) / 29.0000 - 0.5) * 3.14159 * hw;
    float2 d = float2(x, y - ledB.z) / ledB.w;
    float r = length(d);
    if (r < 1.0) {
      float3 face = toSrgb(float3(avatar.eval((d * 0.5 + 0.5) * 180.0).rgb));
      c = mix(face * 1.2, float3(1.0000, 0.7800, 0.3600) * 1.2, smoothstep(0.8, 0.88, r));
    }
  }
  return c;
}

half4 main(float2 xy) {
  float k = geo.z;
  float2 P = (xy - geo.xy) / k;

  // 1. Layers.
  float moving = step(0.001, abs(cam.x) + abs(cam.y) + cam.z);
  float2 uvB = P;
  float2 uvT = P;
  float2 uvN = P;
  float3 c = float3(0.0);
  float aT = 0.0;
  float aN = 0.0;
  if (moving > 0.5) {
    float pb = 0.0800 + 1.6640 * backDepth.eval(xy).r;
    uvB = layerUv(P, pb);
    uvT = layerUv(P, 0.5000);
    uvN = layerUv(P, 0.7800);
    float hole = smoothstep(0.0, 0.35, max(towerMask.eval(dp(uvB)).r, nearMask.eval(dp(uvB)).r));
    c = mix(toSrgb(float3(photo.eval(dp(uvB)).rgb)), toSrgb(float3(fill.eval(dp(uvB)).rgb)), hole);
    aT = towerMask.eval(dp(uvT)).r;
    aN = nearMask.eval(dp(uvN)).r;
    if (aT > 0.0) c = mix(c, toSrgb(float3(photo.eval(dp(uvT)).rgb)), aT);
    if (aN > 0.0) c = mix(c, toSrgb(float3(photo.eval(dp(uvN)).rgb)), aN);
  } else {
    c = toSrgb(float3(photo.eval(xy).rgb));
    aT = towerMask.eval(xy).r;
    aN = nearMask.eval(xy).r;
  }
  if (mode > 0.5 && max(aT, aN) < 0.002) return half4(0.0);
  float2 uv = aN > 0.5 ? uvN : (aT > 0.5 ? uvT : uvB);
  float2 at = dp(uv);
  float lum = dot(c, float3(0.299, 0.587, 0.114));
  float key = keyMap.eval(at).r;
  float obj = max(smoothstep(0.0, 0.04, key), max(aT, aN));

  // 2. Sundial.
  if (sun.z > 0.0) {
    float az = sun.x;
    float el = sun.y;
    float3 L = float3(sin(az) * cos(el), sin(el), cos(az) * cos(el));
    float3 sc = mix(float3(1.0000, 0.5200, 0.2400), float3(1.0000, 0.9300, 0.8000), smoothstep(0.05, 0.6, el));
    float3 ray = normalize(float3((uv.x - 425.0000) / 1000.0000, -(uv.y - 930.0000) / 1000.0000, 1.0));
    float ca = dot(ray, L);
    float glow = 0.9 * exp((ca - 1.0) / 0.05) + 0.35 * exp((ca - 1.0) / 0.45);
    float low = 1.0 - smoothstep(0.0, 0.45, el);
    float sunlit = 1.0;
    // The tower's shadow on the ground plane (the city behind the near towers).
    if (uvB.y > 932.0000 && uvB.y < 1320.0000 && aN < 0.5 && aT < 0.5) {
      float Z = 1000.0000 / (uvB.y - 930.0000);
      float2 rel = float2((uvB.x - 425.0000) * Z / 1000.0000 - 0.0077, Z - 2.5641);
      float2 d = float2(-sin(az), -cos(az));
      float len = 2.6333 / tan(max(el, 0.03));
      float along = dot(rel, d);
      float across = abs(rel.x * d.y - rel.y * d.x);
      float w = 0.1179 * clamp(1.0 - along / len, 0.0, 1.0);
      float inside = smoothstep(0.0, 0.3, along) * smoothstep(w + 0.03 + 0.05 * along, w * 0.3, across) * smoothstep(len, len * 0.7, along);
      sunlit = 1.0 - inside;
    }
    // Sunlit surfaces warm and brighten; the shadow keeps the overcast's cool light, a little darker.
    float3 warmed = c * mix(float3(1.0), sc * 1.2, 0.5500) * (1.0 + 0.2200 * (0.5 + 0.5 * obj));
    float3 shaded = c * (1.0 - 0.6200 * 0.5) * float3(0.8600, 0.9200, 1.0000);
    c = mix(c, mix(shaded, warmed, sunlit), sun.z);
    c += sc * sun.z * glow * (0.2 + smoothstep(0.3, 0.75, lum)) * 0.8000 * (1.0 - 0.7 * obj);
    c += float3(1.0000, 0.5200, 0.2400) * sun.z * low * 0.5500 * exp(-sq((uv.y - 890.0000) / 170.0)) * (0.35 + lum) * (1.0 - 0.6 * obj);
    c += float3(1.0, 0.95, 0.85) * sun.z * smoothstep(0.99988, 0.99996, ca) * (1.0 - obj);
    // Glass: the tower as a cylinder, a specular band where it mirrors the sun.
    if (aT > 0.0) {
      float hw = max(facade.eval(dp(float2(427.5000, uvT.y))).r * 64.0, 0.5);
      float s = clamp((uvT.x - 427.5000) / hw, -0.999, 0.999);
      float th = asin(s);
      float3 n = float3(sin(th), 0.0, -cos(th));
      float3 h = normalize(L + float3(0.0, 0.0, -1.0));
      float spec = pow(max(dot(n, h), 0.0), 60.0000);
      float diff = max(dot(n, L), 0.0);
      c += sc * sun.z * aT * (spec * 1.5000 + diff * 0.22);
    }
    // Edges facing the sun catch it (depth steps up into a building on its sun side).
    float gx = depthMap.eval(at + float2(3.0 * k, 0.0)).r - depthMap.eval(at - float2(3.0 * k, 0.0)).r;
    float side = L.x < 0.0 ? 1.0 : -1.0;
    c += sc * sun.z * clamp(gx * side * 18.0, 0.0, 1.0) * abs(L.x) * 0.4000 * (1.0 - aT);
  }

  // 3. Night (round 2).
  float g = smoothstep(0.0, 0.7000, night);
  float3 o = c;
  if (night > 0.0) {
    float front = -0.05 + 1.1 * smoothstep(0.1500, 1.0, night);
    float up = smoothstep(940.0000, 250.0000, uv.y);
    float3 sky = mix(float3(0.1000, 0.1100, 0.2200), float3(0.0250, 0.0450, 0.1100), up) * clamp(0.15 + 2.4 * (lum - 0.22), 0.08, 1.6);
    sky += float3(0.3400, 0.1700, 0.0900) * exp(-sq((uv.y - 930.0000) / 150.0)) * clamp(0.4 + 1.6 * (lum - 0.25), 0.2, 1.2) * smoothstep(0.3, 1.0, night);
    float3 nightCol = mix(sky, c * float3(0.1300, 0.1600, 0.2500), obj);
    float3 after = float3(0.2200, 0.0800, 0.1200) * exp(-sq((uv.y - 920.0000) / 160.0)) * sin(3.14159 * g) * (1.0 - obj);
    o = mix(c, nightCol, g) + after;
    float lights = lightMap.eval(at).r;
    float gate = gateAt(key, front);
    float edge = exp(-sq((front - key) / 0.0300));
    float warm = step(hash21(floor(uv / float2(5.0000, 6.0000))), 0.8000);
    float3 col = mix(float3(0.8000, 0.8800, 1.0000), float3(1.0000, 0.7400, 0.4200), warm);
    float3 Lw = col * lights * gate * (1.0 + 1.4000 * edge) * 1.5000;
    float3 B = float3(1.0000, 0.6200, 0.3200) * bloomMap.eval(at).r * gateAt(bloomKeyMap.eval(at).r, front) * 0.5500;
    o = 1.0 - (1.0 - o) * (1.0 - clamp(Lw + B, 0.0, 1.0));
  }

  // 4. LED façade and its light.
  if (ledA.x > 0.0) {
    float hwq = facade.eval(dp(float2(427.5000, uvT.y))).r * 64.0;
    if (aT > 0.0 && hwq > 0.5) {
      float s = clamp((uvT.x - 427.5000) / hwq, -0.999, 0.999);
      float th = asin(s);
      float yRef = uvT.y + (930.0000 - uvT.y) * 0.0350 * (1.0 - s * s);
      float fl = (1320.0000 - yRef) / 3.0000;
      float cu = (th / 3.14159 + 0.5) * 29.0000;
      float col = floor(cu);
      float row = floor(fl);
      float cellW = hwq * (3.14159 / 29.0000) * max(cos(th), 0.05);
      float2 m = abs(float2(fract(cu) - 0.5, fract(fl) - 0.5)) * float2(cellW, 3.0000);
      float px = 0.6 / max(1.0 + cam.z * 0.1000, 1.0);
      float dotx = smoothstep(cellW * 0.4 + px, cellW * 0.4 - px, m.x);
      float doty = smoothstep(1.1400 + px, 1.1400 - px, m.y);
      // Every second row is a floor slab: a darker scanline.
      float slab = 1.0 - 0.35 * step(0.5, fract(row * 0.5));
      float dotv = mix(dotx * doty, 0.55, smoothstep(2.2, 1.0, cellW)) * slab;
      float3 e = ledContent(col, row);
      // Cells go dark at random as the panel dissolves.
      e *= step(ledD.x, hash21(float2(col * 1.7, row)) * 0.999);
      float3 lit = e * (dotv * 1.3500 + 0.3) + float3(1.0000, 0.9800, 0.9400) * ledC.y * 0.6 * dotv;
      o = mix(o, o * 0.2800, ledA.x * aT) + lit * ledA.x * aT;
    }
    // Spill: a halo around the tower and its light on the haze and the facing buildings.
    float hwh = max(hwq, 0.0);
    float dEdge = max(abs(uvT.x - 427.5000) - hwh, 0.0) + max(293.0000 - uvT.y, 0.0) * 2.0;
    float3 sp = mix(float3(0.5000, 0.7200, 0.8500), float3(1.0000, 0.7800, 0.4500), step(-0.5, ledA.z) * 0.7);
    float halo = (0.35 * exp(-dEdge / 8.0) + 0.65 * exp(-dEdge / 55.0)) * 0.5500;
    float wash = exp(-dEdge / 170.0) * mix(0.35 + 0.9 * lum, 0.5, obj) * 0.35;
    o += sp * ledC.w * (halo + wash) * (1.0 - aT);
  }

  // 5. Fireworks: light in the burst's colour, falling off in 3D; smoke.
  if (f0.z + f1.z + f2.z > 0.0) {
    float dep = depthMap.eval(at).r * 5.2000;
    float dz = (dep - 1.3) * 70.0000;
    float sky = clamp(2.2 * (lum - 0.25), 0.0, 1.0) * 1.1000 * (1.0 - 0.6 * obj) + 0.05;
    float surf = mix(sky, 0.35 + 0.5 * lum + 0.9 * aT, obj);
    float R2 = sq(260.0000 * k);
    o += c0 * f0.z * surf * exp(-(sq(xy.x - f0.x) + sq(xy.y - f0.y) + dz * dz) / R2);
    o += c1 * f1.z * surf * exp(-(sq(xy.x - f1.x) + sq(xy.y - f1.y) + dz * dz) / R2);
    o += c2 * f2.z * surf * exp(-(sq(xy.x - f2.x) + sq(xy.y - f2.y) + dz * dz) / R2);
  }
  float t2 = sq(xy.x - flare.x) + sq(xy.y - flare.y);
  o += float3(1.0000, 0.9000, 0.7200) * flare.z * (exp(-t2 / sq(9.0000 * k)) + 0.35 * exp(-t2 / sq(60.0000 * k)));
  if (smoke.x > 0.0 || smoke.y > 0.0 || smoke.z > 0.0) {
    float3 tb = smoke;
    for (int i = 0; i < 3; i++) {
      float tt = i == 0 ? tb.x : (i == 1 ? tb.y : tb.z);
      if (tt > 0.0) {
        float2 ce = i == 0 ? f0.xy : (i == 1 ? f1.xy : f2.xy);
        ce.y -= 18.0000 * tt;
        float r = 70.0000 * (0.45 + 0.55 * (1.0 - exp(-tt / 0.6)));
        float d2 = (sq(xy.x - ce.x) + sq(xy.y - ce.y)) / (r * r);
        if (d2 < 2.5) {
          float2 q = (xy - ce) / 26.0 + float2(drift * 0.15 + float(i) * 5.3, tt * 0.4);
          float n = 0.65 * vnoise(q) + 0.35 * vnoise(q * 2.3 + 7.3);
          float dens = smoothstep(0.35, 0.75, n) * exp(-d2) * smoothstep(0.0, 0.2500, tt) * exp(-tt / 1.1000) * ledD.y;
          float3 lit = float3(0.3000, 0.3100, 0.3700) + (c0 * f0.z + c1 * f1.z + c2 * f2.z) * 0.35 + float3(0.3400, 0.1700, 0.0900) * 0.3;
          o = mix(o, lit, dens * 0.4200 * (1.0 - aT * 0.5));
        }
      }
    }
  }

  float3 res = toP3(clamp(o, 0.0, 1.0));
  if (mode > 0.5) {
    float a = max(aT, aN);
    return half4(half3(res * a), a);
  }
  return half4(half3(res), 1.0);
}

"""

private const val CityEventHeroMistAgsl = """

uniform float drift;
uniform float amt;
uniform float3 tint;
float h21(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + float2(1.0, 0.0)), u.x), mix(h21(i + float2(0.0, 1.0)), h21(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int k = 0; k < 4; k++) {
    v += a * vnoise(p);
    p = p * 2.03 + float2(17.1, 9.2);
    a *= 0.5;
  }
  return v;
}
half4 main(float2 xy) {
  float2 p = float2(xy.x + drift, xy.y) / float2(560.0, 230.0);
  float n = fbm(p + float2(0.0, drift * 0.0004));
  float m = smoothstep(0.46, 0.78, n);
  float sky = smoothstep(1180.0, 820.0, xy.y) * smoothstep(200.0, 460.0, xy.y);
  float a = amt * m * sky;
  return half4(half3(tint) * a, a);
}

"""
