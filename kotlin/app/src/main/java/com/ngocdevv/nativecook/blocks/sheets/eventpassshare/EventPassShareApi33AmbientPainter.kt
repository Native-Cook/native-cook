package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/** API 33+ AGSL translation of the RN page and sheet SkSL (Android's native sRGB output path). */
@RequiresApi(33)
internal class EventPassShareApi33AmbientPainter {
    private val pageShader = RuntimeShader(PageSource)
    private val sheetShader = RuntimeShader(SheetSource)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawPage(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        referenceY: Float,
        sheetOffset: Float,
        ambient: EventPassShareAmbientFrame,
    ) {
        pageShader.setFloatUniform("res", width, height)
        pageShader.setFloatUniform("geo", scale, referenceY, sheetOffset)
        pageShader.setFloatUniform("amb", ambient.angle, ambient.tiltX, ambient.tiltY, ambient.glow)
        paint.shader = pageShader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
        paint.alpha = 255
    }

    fun drawSheet(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        referenceY: Float,
        sheetOffset: Float,
        ambient: EventPassShareAmbientFrame,
        cardSpill: FloatArray,
    ) {
        sheetShader.setFloatUniform("res", width, height)
        sheetShader.setFloatUniform("geo", scale, referenceY, sheetOffset)
        sheetShader.setFloatUniform("amb", ambient.angle, ambient.tiltX, ambient.tiltY, ambient.glow)
        sheetShader.setFloatUniform("card", cardSpill[0], cardSpill[1], cardSpill[2], 0f)
        paint.shader = sheetShader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
        paint.alpha = 255
    }

    private companion object {
        // Shared RN SkSL, retained coefficient-for-coefficient; encodeOut omits only the iOS P3 branch.
        private const val Common = """
const float TAU = 6.2831853;
const float DEG = 0.01745329;

float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vn(float2 q) {
  float2 i = floor(q);
  float2 g = fract(q);
  g = g * g * (3.0 - 2.0 * g);
  float a = hs(i);
  float b = hs(i + float2(1.0, 0.0));
  float c = hs(i + float2(0.0, 1.0));
  float d = hs(i + float2(1.0, 1.0));
  return mix(mix(a, b, g.x), mix(c, d, g.x), g.y);
}

float3 labToLinear(float3 c) {
  float l = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
  float m = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
  float s = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
  l = l * l * l; m = m * m * m; s = s * s * s;
  return float3(
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
}

float enc1(float x) { return x <= 0.0031308 ? 12.92 * x : 1.055 * pow(x, 1.0 / 2.4) - 0.055; }

// Linear sRGB in, encoded sRGB out. Android does not need the iOS Display P3 conversion.
half4 encodeOut(float3 lin, float grain) {
  lin = clamp(lin, 0.0, 1.0);
  float3 e = float3(enc1(lin.r), enc1(lin.g), enc1(lin.b)) + grain;
  return half4(half3(clamp(e, 0.0, 1.0)), 1.0);
}

float3 field(float2 q, float4 amb) {
  float th = amb.x;
  q -= float2(amb.z, -amb.y) * 2.2000;
  float2 o = 0.8500 * float2(cos(th), sin(th));
  float2 n = q * 0.0075;
  q += 16.0000 * float2(vn(n + o) - 0.5, vn(n - o + float2(17.0, 9.0)) - 0.5) * 2.0;
  float3 lab = float3(0.1900, -0.0215, -0.0276);
  {
    float2 c = float2(112.0000, 214.0000) + 26.0000 * float2(cos(th + 0.0000), 0.7 * sin(th + 0.0000));
    float2 d = q - c;
    float w = 0.9500 * exp(-dot(d, d) / 22500.0000);
    lab = mix(lab, float3(0.6300, -0.1427, 0.0464), w);
  }
  {
    float2 c = float2(338.0000, 352.0000) + 22.0000 * float2(cos(th + 2.1000), 0.7 * sin(th + 2.1000));
    float2 d = q - c;
    float w = 0.8500 * exp(-dot(d, d) / 16384.0000);
    lab = mix(lab, float3(0.6600, -0.1059, -0.0449), w);
  }
  {
    float2 c = float2(372.0000, 226.0000) + 14.0000 * float2(cos(th + 4.0000), 0.7 * sin(th + 4.0000));
    float2 d = q - c;
    float w = 0.6200 * exp(-dot(d, d) / 3600.0000);
    lab = mix(lab, float3(0.7900, 0.0529, 0.1188), w);
  }
  {
    float2 c = float2(52.0000, 572.0000) + 24.0000 * float2(cos(th + 1.0000), 0.7 * sin(th + 1.0000));
    float2 d = q - c;
    float w = 0.8500 * exp(-dot(d, d) / 22500.0000);
    lab = mix(lab, float3(0.5000, 0.0581, -0.1597), w);
  }
  {
    float2 c = float2(356.0000, 652.0000) + 18.0000 * float2(cos(th + 3.3000), 0.7 * sin(th + 3.3000));
    float2 d = q - c;
    float w = 0.6000 * exp(-dot(d, d) / 10816.0000);
    lab = mix(lab, float3(0.5800, 0.1545, -0.0414), w);
  }
  float hr = amb.z * 0.0200 + amb.y * 0.0150;
  float cr = cos(hr);
  float sr = sin(hr);
  lab.yz = float2(lab.y * cr - lab.z * sr, lab.y * sr + lab.z * cr);
  lab.x *= 1.0 + 0.10 * amb.w;
  return max(labToLinear(lab), 0.0);
}

float sdBox(float2 p, float2 b, float r) {
  float2 d = abs(p) - b + r;
  return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}
"""

        private const val PageSource = """
uniform float2 res;
uniform float3 geo;
uniform float4 amb;
""" + Common + """
half4 main(float2 p) {
  float2 r = float2(p.x / geo.x, geo.y + p.y / geo.x);
  float3 lin = float3(0.0159, 0.0152, 0.0159);
  if (r.y < 105.0000 + geo.z) {
    float3 fld = field(r, amb);
    lin = fld * mix(0.7, 0.95, smoothstep(10.0, 70.0, r.y));
  }
  return encodeOut(lin, (hs(floor((p / geo.x) * 3.0)) - 0.5) * 0.012);
}
"""

        private const val SheetSource = """
uniform float2 res;
uniform float3 geo;
uniform float4 amb;
uniform float4 card;
""" + Common + """
float3 thinfilm(float opd) {
  return 0.5 + 0.5 * cos(TAU * opd / float3(650.0, 532.0, 450.0));
}

float3 foilAt(float2 o) {
  float tx = card.x * DEG;
  float ty = card.y * DEG;
  float3 nrm = normalize(float3(sin(ty), -sin(tx), 1.0));
  float3 eye = normalize(float3(-o, 650.0));
  float3 lgt = normalize(float3(-0.30, -0.70, 1.0));
  float3 hv = normalize(lgt + eye);
  float2 slope = nrm.xy - hv.xy;
  float flow = 0.85 * vn(o * 0.016 + float2(3.1, 7.7)) + 0.15;
  float phase = dot(slope, float2(0.55, 0.84)) * 7.5 + flow;
  return thinfilm(400.0 + 470.0 * phase);
}

float rim(float2 r, float2 c, float2 h, float rad, float px) {
  float d = sdBox(r - c, h, rad) * geo.x;
  return exp(-(d - 0.9) * (d - 0.9) / 0.8) * px;
}

half4 main(float2 p) {
  float S = geo.x;
  float2 r = float2(p.x / S, geo.y + p.y / S);
  float2 q = r + float2(0.0, geo.z);
  float3 fld = field(q, amb);
  float bleed = exp(-(r.y - 61.0000) / 38.0000);
  float3 charcoal = float3(0.0159, 0.0152, 0.0159);
  float3 lin = mix(charcoal, fld * 0.9, 0.2000 + 0.2200 * bleed);

  float below = r.y - 734.0000;
  if (below > 0.0) {
    float3 mirrored = field(float2(r.x, 734.0000 - below * 1.6) + float2(0.0, geo.z), amb);
    lin += mirrored * 0.3000 * exp(-below / 70.0000) * smoothstep(0.0, 6.0, below);
  }

  float topEdge = (r.y - 61.0000) * S;
  lin += (float3(0.10) + fld * 0.6) * exp(-topEdge * topEdge / 0.9) * (0.55 + 0.45 * exp(-abs(r.x - 201.0) / 120.0));

  float2 shift = float2(card.y, -card.x) * 1.6000;
  float2 cc = float2(201.5000, 464.3000) + shift;
  float dc = sdBox(r - cc, float2(135.1500, 191.0000), 23.0000);
  float reach = dc > 0.0 ? exp(-dc / 20.0000) : exp(dc / 5.0);
  float2 dir = r - cc;
  float face = 0.65 + 0.35 * dot(normalize(dir + 0.0001), normalize(float2(card.y, -card.x) + 0.0001)) * min(1.0, length(card.xy) / 4.0);
  float spill = (0.1200 + (1.0 - 0.1200) * card.z) * reach * face;
  float3 spillCol = fld * 1.6 + 0.04;
  if (spill > 0.003) {
    float2 o = clamp(dir, -float2(135.1500, 191.0000), float2(135.1500, 191.0000)) * 0.9249;
    spillCol = mix(spillCol, foilAt(o) * 0.9, 0.65);
  }

  float ds = sdBox(r - float2(201.0000, 464.0000), float2(180.5000, 270.0000), 14.0000) * S;
  float inStage = clamp(0.5 - ds * 3.0, 0.0, 1.0);
  float gain = r.y < 330.0000
    ? mix(1.2500, 0.7000, smoothstep(236.0000, 330.0000, r.y))
    : mix(0.7000, 0.3800, smoothstep(330.0000, 690.0000, r.y));
  float3 win = fld * gain + spillCol * spill * 0.5500;
  float top = smoothstep(234.0000, 194.0000, r.y);
  win += float3(0.9, 0.95, 1.0) * exp(-(ds + 0.6) * (ds + 0.6) / 0.35) * (0.05 + 0.16 * top);
  lin = mix(lin + spillCol * spill * 0.18, win, inStage);

  float3 disp = float3(0.0);
  if (r.y > 767.5000) {
    disp.r += rim(r + 0.35, float2(201.0000, 801.0000), float2(180.5000, 25.5000), 25.5000, 1.0);
    disp.g += rim(r, float2(201.0000, 801.0000), float2(180.5000, 25.5000), 25.5000, 1.0);
    disp.b += rim(r - 0.35, float2(201.0000, 801.0000), float2(180.5000, 25.5000), 25.5000, 1.0);
  } else if (r.y < 173.0000) {
    disp.r += rim(r + 0.3, float2(201.0000, 148.0000), float2(79.0000, 17.0000), 17.0000, 1.0);
    disp.g += rim(r, float2(201.0000, 148.0000), float2(79.0000, 17.0000), 17.0000, 1.0);
    disp.b += rim(r - 0.3, float2(201.0000, 148.0000), float2(79.0000, 17.0000), 17.0000, 1.0);
    float2 cr = r - float2(363.0000, 100.0000);
    float dr = (length(cr + 0.3) - 22.0000) * S;
    float dg = (length(cr) - 22.0000) * S;
    float db = (length(cr - 0.3) - 22.0000) * S;
    disp += float3(exp(-(dr - 0.9) * (dr - 0.9) / 0.8), exp(-(dg - 0.9) * (dg - 0.9) / 0.8), exp(-(db - 0.9) * (db - 0.9) / 0.8));
  }
  lin += disp * (fld * 1.4 + 0.05) * 0.5;

  float grain = (hs(floor((p / geo.x) * 3.0)) - 0.5) * mix(0.010, 0.018, inStage);
  return encodeOut(lin, grain);
}
"""
    }
}
