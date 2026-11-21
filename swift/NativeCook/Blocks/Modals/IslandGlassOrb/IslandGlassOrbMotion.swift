// Island Glass Orb: the React Native timeline, ported as a pure function of seconds.
// Geometry uses the same 402 x 874 canonical canvas. Every frame contains every key.

import Foundation
import CoreGraphics

enum IslandGlassOrbMotion {
    typealias Frame = [String: Double]

    static let loop = 10.0
    static let glide = (d: 22.1, k: 157.9)
    static let pop = (d: 19.0, k: 195.0)
    static let snap = (d: 30.5, k: 322.3)
    static let honey = (d: 10.0, k: 220.0)

    static let keys = [
        "m", "pill", "ban", "antic", "dx", "dy", "e", "amp", "ampLag", "lineLen", "lineX", "lineY",
        "mic", "spinA", "spinT", "spinBlur", "pillWob", "clock", "textT", "textOut", "arcA", "arcW",
        "arcHue", "spillY", "spillA", "rimL", "rimR", "row", "pluckX", "pluckD", "causticAmp", "bandY", "bandA", "floorY", "floorA", "bandHue", "floorHue", "free", "cap", "neck", "kJoin", "iw", "ih"
    ]
    static let blendKeys = [
        "m", "pill", "ban", "dx", "dy", "e", "amp", "ampLag", "lineLen", "lineX", "lineY", "mic", "causticAmp", "free", "cap", "iw", "ih"
    ]

    private static let pullDX = 234.0 - 200.0
    private static let pullDY = 110.0 - 58.0

    static func clamp(_ v: Double) -> Double { min(max(v, 0), 1) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func out(_ k: Double) -> Double { 1 - (1 - k) * (1 - k) * (1 - k) }
    static func easeIn(_ k: Double) -> Double { k * k * k }
    static func inOut(_ k: Double) -> Double { k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2 }
    private static func emph(_ k: Double) -> Double { 1 - pow(1 - k, 4) }

    static func smooth(_ a: Double, _ b: Double, _ v: Double) -> Double {
        let k = clamp((v - a) / (b - a))
        return k * k * (3 - 2 * k)
    }

    /// Closed-form mass-1 spring released at time zero with velocity in units/second.
    static func spring(_ t: Double, _ from: Double, _ to: Double, _ v: Double, _ d: Double, _ k: Double) -> Double {
        if t <= 0 { return from }
        let x0 = from - to
        let w0 = sqrt(k)
        let z = d / (2 * w0)
        if z < 1 {
            let wd = w0 * sqrt(1 - z * z)
            let e = exp(-z * w0 * t)
            return to + e * (x0 * cos(wd * t) + ((v + z * w0 * x0) / wd) * sin(wd * t))
        }
        let s = sqrt(max(z * z - 1, 1e-6))
        let r1 = -w0 * (z - s)
        let r2 = -w0 * (z + s)
        let c2 = (v - r1 * x0) / (r2 - r1)
        let c1 = x0 - c2
        return to + c1 * exp(r1 * t) + c2 * exp(r2 * t)
    }

    static func blend(_ a: Frame, _ b: Frame, _ k: Double) -> Frame {
        var result = a
        for key in keys {
            let av = a[key]!
            result[key] = av + (b[key]! - av) * k
        }
        return result
    }

    /// Catmull-Rom through the four voice bursts, with the loudest at 2.2 seconds.
    private static func ampAt(_ t: Double) -> Double {
        let kt = [1.5, 1.7, 1.88, 2.2, 2.45, 2.7, 3.0, 3.2, 3.42, 3.7, 3.95, 4.2, 4.5, 4.9]
        let kv = [0.15, 0.9, 0.4, 1.0, 0.35, 0.8, 0.45, 0.6, 0.9, 0.45, 0.3, 0.9, 0.4, 0]
        if t < 1.1 { return 0 }
        if t < 1.5 { return 0.15 * inOut(seg(t, 1.1, 1.5)) }
        if t < 4.9 {
            var i = 0
            while i < kt.count - 2 && t >= kt[i + 1] { i += 1 }
            let p0 = kv[max(i - 1, 0)]
            let p1 = kv[i]
            let p2 = kv[i + 1]
            let p3 = kv[min(i + 2, kv.count - 1)]
            let u = (t - kt[i]) / (kt[i + 1] - kt[i])
            let v = 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u)
            return min(max(v, 0), 1.05)
        }
        return 0
    }

    private static func pullAt(_ t: Double) -> Double {
        if t < 3.25 { return 0 }
        if t < 3.7 { return inOut(seg(t, 3.25, 3.7)) }
        if t < 3.85 { return 1 }
        if t < 5.2 { return spring(t - 3.85, 1, 0, 0, honey.d, honey.k) * (1 - seg(t, 4.9, 5.2)) }
        return 0
    }

    private static func spillAt(_ t: Double) -> (y: Double, env: Double) {
        let u = seg(t, 7.0, 7.7)
        let y = mix(10 + 93, 250, emph(u))
        let env = t < 7.0 || t > 7.7 ? 0 : smooth(0, 0.18, u) * (1 - smooth(0.55, 1, u))
        return (y, env)
    }

    private static func rimAt(_ t: Double) -> Double { exp(-pow((t - 7.38) / 0.2, 2)) }

    static func fly(_ t: Double) -> Double { spring(t - 4.5, 0, 1, 0, 16, 210) }
    static func offset(_ t: Double) -> (dx: Double, dy: Double) {

        if t < 3 { return (0, 0) }
        if t < 3.5 { let u = inOut(seg(t, 3, 3.5)); return (u, 250 * u) }
        if t < 4.5 {
            let y = spring(t - 3.5, 308, 440, 0, 22.1, 157.9), u = inOut(seg(t, 3.95, 4.5))
            return (1 - 51 * u, y - 58 - 68 * u)
        }
        let f = fly(t); return (-50 * (1 - f), 314 * (1 - f))
    }
    static func band(_ t: Double) -> (y: Double, a: Double, hue: Double) {
        let u = seg(t, 7.25, 7.95), inside = t >= 7.25 && t <= 7.95
        return (inside ? mix(300, 660, inOut(u)) : 300, inside ? smooth(0, 0.12, u) * (1 - smooth(0.8, 1, u)) : 0, 0.76 + 0.42 * u)
    }
    static func dropShape(_ travel: Double, _ broken: Double, _ ts: Double, _ antic: Double) -> Frame {


  let u = clamp(travel / 250.0);
  var free = smooth(40, 230, travel);
  // the black stays with the island: the drop's cap is gone by 60 pt, so the clear lens shows as soon as it clears the island
  // (the spec had 0.46 → 0 between 80 and 230 pt; that left a wide black dome with shoulders under the narrowing island)
  var cap = 0.46 * (1 - smooth(0, 60, travel));
  var neck = travel <= 120 ? mix(28, 20, travel / 120) : mix(20, 6, clamp((travel - 120) / 120)); // Ø 20 at 120 pt, Ø 6 at 240
  // a wide meniscus while the funnel is drawn out (smooth, no shoulders), tightening to a hairline thread by 240 pt
  var kJoin = travel < 80 ? mix(28.0, 44, travel / 80) : mix(44, 12, smooth(80, 240, travel));
  let base = mix(126.0, 120.0, antic);
  var iw = mix(base, 58, smooth(0.25, 1, u)); // the island narrows to 58 pt
  if (broken > 0.5) {
    iw = spring(ts, 58, 126.0, 0, 12, 260); // it recoils (d 12, k 260)
    if (ts < 0.08) {
      let r = 1 - ts / 0.08; // the thread pinches in 80 ms
      neck *= r;
      kJoin *= r;
    } else {
      let r = smooth(160, 90, travel); // and re-forms under 150 pt on the way home
      neck *= r;
      kJoin *= r;
    }
    // a torn-off bead stays a clear circle until it is close enough to be pulled back in
    let near = smooth(160, 90, travel);
    free = mix(1, free, near);
    cap *= near;
  }
  let ih = mix(37.0, 40.0, antic) + 0.05 * (126.0 - iw);
  return ["free": free, "cap": cap, "neck": neck, "kJoin": kJoin, "iw": iw, "ih": ih]

    }
    static func frame(_ tIn: Double) -> Frame {


  let t = (tIn.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop);
  let arrive = 4.678;

  // shapes
  let antic = t < 0.5 ? 0 : t < 0.62 ? out(seg(t, 0.5, 0.62)) : 1 - inOut(seg(t, 0.62, 0.9));
  var m = 0.0;
  if t >= 0.62 && t < arrive { m = spring(t - 0.62, 0, 1, 0, 19, 195) }
  else if t >= arrive { m = max(0, spring(t - arrive, 1, 0, 0, 30.5, 322.3)) } // the bead is swallowed
  if t > 5.6 { m = 0 }
  let pill = t < 4.95 || t >= 8.8 + 0.25 ? 0 : max(0, spring(t - 4.95, 0, 1, 0, 30.5, 322.3));
  var ban = 0.0;
  if t >= 5.7 + 0.25 && t < 8.8 + 0.25 { ban = spring(t - 5.7 - 0.25, 0, 1, 0, 22.1, 157.9) }
  else if t >= 8.8 + 0.25 && t < 9.9 { ban = max(0, spring(t - 8.8 - 0.25, 1, 0, 0, 22.1, 157.9)) }

  // the drop: intro squash, then the tear-off, the bead's squash on the ring, the flight home
  let offsetValue = offset(t); let dx = offsetValue.dx; let dy = offsetValue.dy;
  let travel = hypot(dx, dy);
  let broken: Double = t >= 3.5 && t < arrive ? 1 : 0;
  let ts = t - 3.5;
  var e = 0.0;
  if (t >= 0.78 && t < 1.4) {
    let k = 1 - seg(t, 0.78, 1.4);
    e = 0.06 * sin(2 * .pi * 5 * (t - 0.78)) * k * k;
  } else if (t >= 3.0 && t < 3.5) {
    e = -0.07 * clamp(travel / 250.0);
  } else if (t >= 3.5 && t < 4.5) {
    e = -0.07 * exp(-ts / 0.04) + 0.06 * sin(2 * .pi * 3.5 * ts) * exp(-ts / 0.2); // ±6 % squash
  } else if (t >= 4.5 && t < arrive) {
    let v = abs(fly(t + 0.004) - fly(t - 0.004)) / 0.008;
    e = -0.22 * clamp((v * hypot(-50.0,314.0)) / 2400); // stretch ≤ 1.22 along the flight
  }
  let shape = dropShape(travel, broken, ts, antic);
  let bump = t >= arrive - 0.03 && t < arrive + 0.27 ? sin(.pi * seg(t, arrive - 0.03, arrive + 0.27)) : 0; // the merge: h 37 → 47 → 37

  // the voice and its line
  let amp = ampAt(t);
  let ampLag = ampAt(t - 0.06);
  let mc = clamp(m);
  var lineLen = 0.0;
  var lineX = 200.0 + dx * mc;
  var lineY = mix(30.0, 58.0, mc) + dy * mc;
  if t >= 1.1 && t < 1.5 { lineLen = 152.0 * out(seg(t, 1.1, 1.5)) }
  else if t >= 1.5 && t < 4.8 { lineLen = 152.0 }
  else if (t >= 4.8 && t < 5.3) {
    let k = inOut(seg(t, 4.8, 5.3));
    lineLen = mix(152.0, 0, k);
    lineX = mix(lineX, 112.0 + 10.5, k);
    lineY = mix(lineY, 40.0, k);
  }
  let mic = t < 0.7 ? 0 : t < 4.95 ? out(seg(t, 0.7, 0.85)) : 1 - out(seg(t, 4.95, 4.95 + 0.15));

  // thinking: the pill 4950–5250, the spinner to 5950
  let spinA = t < 5.2 || t >= 6.1 ? 0 : out(seg(t, 5.2, 5.4)) * (1 - seg(t, 5.95, 6.1));
  let spinT = t - 5.2;
  let spinBlur = 6 * seg(t, 5.95, 6.1);
  let pillWob = t >= 5.35 && t < 5.95 ? 3 * sin(2 * .pi * (t - 5.35)) : 0;

  // reply (v2 timing + 250 ms)
  let clock = t < 5.7 + 0.25 ? 1 : t < 9.0 + 0.25 ? 1 - out(seg(t, 5.7 + 0.25, 5.9 + 0.25)) : out(seg(t, 9.0 + 0.25, 9.2 + 0.25));
  let textT = t - 6.0 - 0.25;
  let textOut = easeIn(seg(t, 8.7 + 0.25, 8.9 + 0.25));
  var arcA = out(seg(t, 6.2 + 0.25, 6.8 + 0.25)) * (1 - seg(t, 8.95 + 0.25, 9.2 + 0.25));
  if t < 6.2 + 0.25 || t >= 9.2 + 0.25 { arcA = 0 }
  let arcW = mix(140, 220, out(seg(t, 6.2 + 0.25, 6.8 + 0.25))) * (1 - easeIn(seg(t, 8.8 + 0.25, 9.2 + 0.25)));
  let arcHue = (t - 6.2 - 0.25) / 2;

  // payoff: the band of arc-coloured light over the ring (the floor 120 ms behind), the widget rims and the calendar row
  let band = Self.band(t);
  let floor = Self.band(t - 0.12);
  var row = 0.0;
  if t >= 7.4 + 0.25 && t < 7.7 + 0.25 { row = spring(t - 7.4 - 0.25, 0, 1, 0, 19, 195) }
  else if (t >= 7.7 + 0.25 && t < 8.6 + 0.25) {
    let r0 = spring(0.3, 0, 1, 0, 19, 195);
    row = spring(t - 7.7 - 0.25, r0, 0, 0, 22.1, 157.9) * (1 - seg(t, 8.3 + 0.25, 8.6 + 0.25));
  }

  return [
"m": m,
"pill": pill,
"ban": ban,
"antic": antic,
"dx": dx,
"dy": dy,
"e": e,
"amp": amp,
"ampLag": ampLag,
"causticAmp": ampAt(t - 0.08),
"lineLen": lineLen,
"lineX": lineX,
"lineY": lineY,
"mic": mic,
"spinA": spinA,
"spinT": spinT,
"spinBlur": spinBlur,
"pillWob": pillWob,
"clock": clock,
"textT": textT,
"textOut": textOut,
"arcA": arcA,
"arcW": arcW,
"arcHue": arcHue,
"bandY": band.y,
"bandA": band.a,
"floorY": floor.y,
"floorA": floor.a,
"bandHue": band.hue,
"floorHue": floor.hue,
"rimL": rimAt(t),
"rimR": rimAt(t - 0.06),
"row": row,
"pluckX": 0,
"pluckD": 0,
"free": shape["free"]!,
"cap": shape["cap"]!,
"neck": shape["neck"]!,
"kJoin": shape["kJoin"]!,
"iw": shape["iw"]!,
"ih": shape["ih"]! + 10 * bump,
"spillY": 103,
"spillA": 0]
    }

    static func live(_ l: Frame, clock: Double) -> Frame {
        var f = frame(0)
        let m = l["m"]!
        let mc = clamp(m)
        let ban = l["ban"]!
        let dx = l["dx"]!
        let dy = l["dy"]!
        f["m"] = m
        f["pill"] = l["pill"]!
        f["ban"] = ban
        f["antic"] = 0
        f["dx"] = dx
        f["dy"] = dy
        f["e"] = -0.05 * (dy / pullDY) * mc - 0.02 * (abs(dx) / abs(pullDX)) * mc
        f["amp"] = l["amp"]!
        f["ampLag"] = l["amp"]!
        f["lineLen"] = 152 * out(seg(m, 0.55, 1.0))
        f["lineX"] = 200 + dx * mc
        f["lineY"] = mix(30, 58, mc) + dy * mc
        f["mic"] = l["mic"]!
        f["spinA"] = l["spin"]!
        f["spinT"] = clock
        f["spinBlur"] = 0
        f["clock"] = 1 - smooth(0.15, 0.6, ban)
        f["textT"] = 10
        f["textOut"] = 1 - smooth(0.55, 0.95, ban)
        f["arcA"] = smooth(0.45, 1, ban)
        f["arcW"] = 220 * smooth(0.2, 1, ban)
        f["arcHue"] = clock / 2
        f["pluckX"] = l["pluckX"]!
        f["pluckD"] = l["pluckD"]!
        let shape = dropShape(hypot(dx, dy), l["broken"] ?? 0, l["ts"] ?? 10, 0)
        for (key, value) in shape { f[key] = value }
        f["causticAmp"] = l["causticAmp"] ?? l["amp"]!
        f["ampLag"] = l["ampLag"] ?? l["amp"]!
        f["e"] = l["e"] ?? 0
        return f
    }

    static func thinkRest() -> Frame {
        var f = frame(5.6)
        f["pillWob"] = 0
        f["spinT"] = 0.25
        return f
    }

    static func replyRest() -> Frame {
        var f = frame(8.75)
        f["row"] = 0
        f["spillA"] = 0; f["bandA"] = 0; f["floorA"] = 0
        f["rimL"] = 0
        f["rimR"] = 0
        f["arcHue"] = 0.15
        return f
    }

    /// The drop CGRect stores its bounds; its midpoint and half-size match the source cx/cy/a/b.
    static func shapes(_ f: Frame, oy: Double) -> (box: CGRect, drop: CGRect) {
        let iw = f["iw"]!
        let ih = f["ih"]!
        let ix = 201 - iw / 2
        let pw = 237 + f["pillWob"]!
        let px = 82 - f["pillWob"]! / 2
        let x1 = mix(ix, px, f["pill"]!)
        let y1 = mix(11, 11, f["pill"]!)
        let w1 = mix(iw, pw, f["pill"]!)
        let h1 = mix(ih, 58, f["pill"]!)
        let boxX = mix(x1, 11, f["ban"]!)
        let boxY = mix(y1, 10, f["ban"]!) + oy
        let boxW = mix(w1, 380, f["ban"]!)
        let boxH = mix(h1, 126, f["ban"]!)
        let box = CGRect(x: CGFloat(boxX), y: CGFloat(boxY), width: CGFloat(boxW), height: CGFloat(boxH))

        let m = f["m"]!
        let mc = clamp(m)
        let grow = 1 + 0.08 * f["amp"]!
        let cx = 200 + f["dx"]! * mc
        let cy = mix(30, 58, min(max(m, 0), 1.15)) + f["dy"]! * mc + oy
        let a = (mix(96, mix(196, 182, f["free"]!), m) / 2) * grow * (1 + f["e"]!)
        let b = (mix(24, mix(152, 182, f["free"]!), m) / 2) * grow * (1 - f["e"]!)
        let drop = CGRect(x: CGFloat(cx - a), y: CGFloat(cy - b), width: CGFloat(a * 2), height: CGFloat(b * 2))
        return (box, drop)
    }
}
