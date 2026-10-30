import Foundation

// All clocks and analytic physics stay in Double, matching JavaScript numbers.
enum GoldTicketRewardMotion {
  typealias A = GoldTicketRewardTheme.A
  typealias B = GoldTicketRewardTheme.B
  typealias Rect = GoldTicketRewardTheme.Box
  static let LOOP = 9.0
  static let RESUME = 2.0
  static let PEAK = 3.25
  static let COIN_FRAMES = 24.0
  enum T {
    static let press = [1.45, 1.57]  // the ticket sinks under the finger (scale 0.97, ease-out)
    static let commit = 1.57  // ...and lets go: everything below starts here
    static let carsOut = [1.58, 2.12]  // the cars dissolve from the wheels up, a gold edge burning through
    static let glowOut = [1.6, 2.3]
    static let thanksOut = [1.6, 1.84]  // the copy above leaves upwards, 40 ms apart
    static let callbackOut = [1.64, 1.88]
    static let dividerOut = [1.68, 1.9]
    static let dim = [1.66, 2.36]  // the page behind darkens from violet slate to graphite
    static let ink = [1.66, 2.45]  // purple rises from the bottom edge like a dawn and eats the cream sheet
    static let morph = 1.6  // the ticket springs from its A box to its B box (response 0.55 s, damping 0.74)
    static let halo = [1.68, 1.95]  // the white cradle round the coin sinks into the ticket
    static let gold = [1.86, 2.42]  // gold pours across the ticket left to right
    static let coinUp = [1.72, 2.3]  // the coin lifts out of its slot to 78 pt above it, growing
    static let coinDrop = [2.3, 2.6]  // ...and falls onto the gold ticket (gravity)
    static let coinSpin = [1.72, 2.6]  // two full turns on the way
    static let land = 2.6  // impact: stamp, ring, flash, stars
    static let squash = [2.6, 2.95]
    static let ring = [2.6, 3.05]
    static let flash = [2.6, 2.95]
    static let burst = [2.6, 3.15]  // the stars fly out of the coin to where the pin shows them
    static let third = [1.76, 1.96]  // "Compare on the spot" makes room
    static let benefits = [1.8, 2.3]  // the two benefits grow, recentre and turn white
    static let button = [1.84, 2.32]  // the CTA turns from violet to white
    static let closeIn = [2.55, 2.85]
    static let sweep = [3.7, 4.5]  // one specular band crosses the foil
    static let glint = [4.35, 4.85]  // ...and catches the coin
    static let ctaGlow = [4.9, 5.7]  // then Check Now breathes once: the one thing to do next
    static let carsGlint = [0.35, 1.15]  // at rest a showroom light runs across the cars' paint
    static let anticipation = [1.6, 1.72]  // the coin sinks 2.6 pt into its cradle before it jumps
    static let relief = [1.72, 1.95]  // the ticket rises 3 pt when the coin's weight leaves it
    static let hold = [3.15, 6.7]  // the reward rests
    // back to rest
    static let closeOut = [6.7, 6.84]
    static let burstBack = [6.7, 6.98]
    static let coinLift = [6.78, 7.05]
    static let coinReturn = [7.05, 7.45]
    static let coinBackSpin = [6.8, 7.45]  // one turn
    static let goldOut = [6.9, 7.32]
    static let morphBack = [6.95, 7.55]
    static let inkBack = [6.92, 7.6]
    static let dimBack = [6.95, 7.6]
    static let buttonBack = [7.0, 7.45]
    static let benefitsBack = [7.0, 7.45]
    static let haloIn = [7.3, 7.55]
    static let glowIn = [7.15, 7.7]
    static let carsIn = [7.2, 7.72]
    static let thirdIn = [7.38, 7.6]
    static let thanksIn = [7.42, 7.7]
    static let callbackIn = [7.46, 7.74]
    static let dividerIn = [7.5, 7.76]
  }
  static let COIN_PEAK = 78.0
  static let COIN_PEAK_R = 1.35
  static let COIN_LIFT_BACK = 26.0
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
  static func easeOut(_ x: Double) -> Double {
    let u = 1 - clamp01(x)
    return 1 - u * u * u * u * u
  }
  static func easeInOut(_ x: Double) -> Double {
    let p = clamp01(x)
    return p < 0.5 ? 16 * p * p * p * p * p : 1 - pow(-2 * p + 2, 5) / 2
  }
  static func easeOutCubic(_ x: Double) -> Double {
    let u = 1 - clamp01(x)
    return 1 - u * u * u
  }
  static func easeOutBack(_ x: Double, _ k: Double = 1.4) -> Double {
    let p = clamp01(x) - 1
    return 1 + (k + 1) * p * p * p + k * p * p
  }
  static func spring(_ tau: Double, _ response: Double, _ damping: Double) -> Double {
    if tau <= 0 { return 0 }
    let w = 2 * Double.pi / response
    let zw = damping * w
    let wd = w * sqrt(1 - damping * damping)
    return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
  }
  static func wobble(_ tau: Double, _ period: Double, _ decay: Double) -> Double {
    tau <= 0 ? 0 : exp(-tau / decay) * cos(2 * Double.pi * tau / period)
  }
  struct Star {
    var x: Double
    var y: Double
    var s: Double
    var a: Double
    var rot: Double
  }
  struct Frame {
    var t, dim, glow, cars: Double
    var thanks, callback: (a: Double, dy: Double)
    var divider, ink, inkX, inkY: Double
    var ticket: Rect
    var notch, tScaleX, tScaleY, tDy, gold, sweep, lift, halo: Double
    var coin: (x: Double, y: Double, r: Double, frame: Double, shadow: Double)
    var glint: Double
    var ring: (r: Double, a: Double)
    var flash: Double
    var stars: [Star]
    var starsA, benefits, benefits2, third, button: Double
    var close: (a: Double, s: Double, rot: Double)
    var bAmount, aura, ctaGlow, carsGlint: Double
  }
  static func auraAt(_ t: Double, _ fwd: Bool, _ bAmount: Double) -> Double {
    let pour =
      fwd
      ? easeInOut(seg(t, T.gold[0], T.gold[1])) : 1 - easeInOut(seg(t, T.goldOut[0], T.goldOut[1]))
    let since = t - T.land
    let flare = fwd && since > 0 ? 0.32 * exp(-since / 0.28) * min(1, since / 0.04) : 0
    return pour * 0.2 + flare + bAmount * 0.04 * sin(2 * Double.pi * 3 * t / LOOP)
  }
  static func boxMix(_ a: Rect, _ b: Rect, _ p: Double) -> Rect {
    Rect(x: mix(a.x, b.x, p), y: mix(a.y, b.y, p), w: mix(a.w, b.w, p), h: mix(a.h, b.h, p))
  }
  static func frameAt(
    _ t: Double, _ extraSpin: Double = 0, _ hop: Double = 0, _ twinkle: Double = 0
  ) -> Frame {
    let fwd = t < 5  // first half of the loop goes A → B, second half B → A

    // the ticket's box: a spring out, an ease back
    let morphP =
      fwd ? spring(t - T.morph, 0.55, 0.74) : 1 - easeInOut(seg(t, T.morphBack[0], T.morphBack[1]))
    let press =
      1 - 0.03 * easeOut(seg(t, T.press[0], T.press[1])) * (1 - spring(t - T.commit, 0.35, 0.8))
    let land = t - T.land
    let squash = fwd && land > 0 ? 0.035 * wobble(land, 0.24, 0.12) : 0
    let ticket = boxMix(A.ticket, B.ticket, morphP)

    // coin
    let upP = easeOutCubic(seg(t, T.coinUp[0], T.coinUp[1]))
    let dropP = seg(t, T.coinDrop[0], T.coinDrop[1])
    var cx = A.coin.x
    var cy = A.coin.y
    var cr = A.coin.r
    var frame = 0.0
    if fwd {
      if t < T.coinDrop[0] {
        cx = mix(A.coin.x, B.coin.x, upP)
        cy = A.coin.y - COIN_PEAK * upP
        cr = A.coin.r * mix(1, COIN_PEAK_R, upP)
      } else {
        let g = dropP * dropP  // gravity
        cx = B.coin.x
        cy = mix(A.coin.y - COIN_PEAK, B.coin.y, g)
        cr = mix(A.coin.r * COIN_PEAK_R, B.coin.r, g)
      }
      cy += 2.6 * sin(Double.pi * seg(t, T.anticipation[0], T.anticipation[1]))
      if land > 0 { cy -= 3.2 * max(0, sin(min(land / 0.22, 1) * Double.pi)) * exp(-land * 4) }
      frame = COIN_FRAMES * 4 * easeInOut(seg(t, T.coinSpin[0], T.coinSpin[1]))
    } else {
      let liftP = easeOut(seg(t, T.coinLift[0], T.coinLift[1]))
      let retP = easeInOut(seg(t, T.coinReturn[0], T.coinReturn[1]))
      cx = mix(B.coin.x, A.coin.x, retP)
      cy = mix(B.coin.y - COIN_LIFT_BACK * liftP, A.coin.y, retP)
      cr = mix(B.coin.r, A.coin.r, retP)
      frame = COIN_FRAMES * 2 * easeInOut(seg(t, T.coinBackSpin[0], T.coinBackSpin[1]))
    }
    let bAmount = fwd ? seg(t, T.hold[0] - 0.3, T.hold[0]) : 1 - seg(t, T.hold[1], T.hold[1] + 0.3)
    cy += bAmount * 1.6 * sin((2 * Double.pi * 4 * t) / LOOP) - hop
    frame += extraSpin
    // how high the coin flies over the ticket, for its shadow (0 resting … 1 at the top)
    let air =
      fwd
      ? t < T.coinDrop[0] ? upP : 1 - dropP * dropP
      : easeOut(seg(t, T.coinLift[0], T.coinLift[1]))
        * (1 - seg(t, T.coinReturn[1] - 0.12, T.coinReturn[1]))

    // stars: out of the coin on the stamp, back into it on the way home
    var stars: [Star] = []
    for i in B.sparkles.indices {
      let s = B.sparkles[i]
      let tw = 0.8 + 0.2 * (0.5 + 0.5 * sin(2 * Double.pi * ((s.k * t) / LOOP + s.ph)))
      if fwd {
        let p = seg(t, T.burst[0] + Double(i) * 0.025, T.burst[1] + Double(i) * 0.025)
        let q = easeOutBack(p, 1.1)
        stars.append(
          Star(
            x: mix(B.coin.x, s.x, q),
            y: mix(B.coin.y, s.y, q),
            s: s.s * mix(0.25, 1, easeOut(p)) * tw
              * (1 + 0.35 * twinkle * sin(Double.pi * twinkle)),
            a: clamp01(p * 6),
            rot: (1 - easeOut(p)) * 1.6))
      } else {
        let p = easeInOut(seg(t, T.burstBack[0], T.burstBack[1]))
        stars.append(
          Star(
            x: mix(s.x, B.coin.x, p * 0.35), y: mix(s.y, B.coin.y, p * 0.35),
            s: s.s * tw * (1 - 0.7 * p), a: 1 - p, rot: p * 0.8))
      }
    }

    let outIn: ([Double], [Double]) -> Double = { o, i in
      fwd ? 1 - seg(t, o[0], o[1]) : seg(t, i[0], i[1])
    }

    let thanksA =
      fwd
      ? 1 - easeOutCubic(seg(t, T.thanksOut[0], T.thanksOut[1]))
      : easeOut(seg(t, T.thanksIn[0], T.thanksIn[1]))
    let callbackA =
      fwd
      ? 1 - easeOutCubic(seg(t, T.callbackOut[0], T.callbackOut[1]))
      : easeOut(seg(t, T.callbackIn[0], T.callbackIn[1]))

    let closeP =
      fwd ? easeOut(seg(t, T.closeIn[0], T.closeIn[1])) : 1 - seg(t, T.closeOut[0], T.closeOut[1])
    let ringP = seg(t, T.ring[0], T.ring[1])
    let flashP = seg(t, T.flash[0], T.flash[1])
    let sweepP = seg(t, T.sweep[0], T.sweep[1])

    return Frame(
      t: t,
      dim: fwd
        ? easeInOut(seg(t, T.dim[0], T.dim[1])) : 1 - easeInOut(seg(t, T.dimBack[0], T.dimBack[1])),
      glow: fwd
        ? 1 - easeInOut(seg(t, T.glowOut[0], T.glowOut[1]))
        : easeInOut(seg(t, T.glowIn[0], T.glowIn[1])),
      cars: fwd ? seg(t, T.carsOut[0], T.carsOut[1]) : 1 - seg(t, T.carsIn[0], T.carsIn[1]),
      thanks: (a: thanksA, dy: -10 * (1 - thanksA)),
      callback: (a: callbackA, dy: -10 * (1 - callbackA)),
      divider: outIn(T.dividerOut, T.dividerIn),
      ink: fwd
        ? easeInOut(seg(t, T.ink[0], T.ink[1])) : 1 - easeInOut(seg(t, T.inkBack[0], T.inkBack[1])),
      inkX: A.ticket.x + A.ticket.w / 2,
      inkY: A.ticket.y + A.ticket.h / 2,
      ticket: ticket,
      notch: mix(A.notchR, B.notchR, morphP),
      tScaleX: press * (1 + squash * 0.6),
      tScaleY: press * (1 - squash),
      tDy: squash * 40
        - (fwd
          ? 3 * easeOut(seg(t, T.relief[0], T.relief[1]))
            * (1 - seg(t, T.coinDrop[1] - 0.1, T.coinDrop[1])) : 0),
      gold: fwd
        ? easeInOut(seg(t, T.gold[0], T.gold[1]))
        : 1 - easeInOut(seg(t, T.goldOut[0], T.goldOut[1])),
      sweep: sweepP > 0 && sweepP < 1 ? easeInOut(sweepP) : -1,
      lift: fwd
        ? easeInOut(seg(t, T.gold[0], T.gold[1] + 0.2))
        : 1 - easeInOut(seg(t, T.morphBack[0], T.morphBack[1])),
      halo: fwd
        ? 1 - easeInOut(seg(t, T.halo[0], T.halo[1])) : easeOut(seg(t, T.haloIn[0], T.haloIn[1])),
      coin: (x: cx, y: cy, r: cr, frame: frame, shadow: air),
      glint: seg(t, T.glint[0], T.glint[1]),
      ring: (
        r: B.coin.r * mix(1.05, 3.4, easeOut(ringP)),
        a: ringP > 0 && ringP < 1 ? 0.85 * (1 - ringP) : 0
      ),
      flash: flashP > 0 && flashP < 1 ? sin(Double.pi * min(1, flashP * 2.4)) * (1 - flashP) : 0,
      stars: stars,
      starsA: fwd
        ? 1 - seg(t, T.glowOut[0], T.glowOut[0] + 0.25) : seg(t, T.glowIn[1] - 0.25, T.glowIn[1]),
      benefits: fwd
        ? easeInOut(seg(t, T.benefits[0], T.benefits[1]))
        : 1 - easeInOut(seg(t, T.benefitsBack[0], T.benefitsBack[1])),
      benefits2: fwd
        ? easeInOut(seg(t, T.benefits[0] + 0.04, T.benefits[1] + 0.04))
        : 1 - easeInOut(seg(t, T.benefitsBack[0] + 0.04, T.benefitsBack[1] + 0.04)),
      third: outIn(T.third, T.thirdIn),
      button: fwd
        ? easeInOut(seg(t, T.button[0], T.button[1]))
        : 1 - easeInOut(seg(t, T.buttonBack[0], T.buttonBack[1])),
      close: (a: closeP, s: mix(0.8, 1, closeP), rot: (1 - closeP) * -1.0),
      bAmount: bAmount,
      aura: auraAt(t, fwd, bAmount),
      ctaGlow: sin(Double.pi * seg(t, T.ctaGlow[0], T.ctaGlow[1])),
      carsGlint: t > T.carsGlint[0] && t < T.carsGlint[1]
        ? seg(t, T.carsGlint[0], T.carsGlint[1]) : -1)
  }
}
