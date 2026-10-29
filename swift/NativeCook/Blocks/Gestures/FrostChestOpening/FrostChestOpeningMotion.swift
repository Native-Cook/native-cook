import Foundation

// Value-for-value port of fco-motion.ts. All geometry remains in 402 × 874 design points.
enum FrostChestOpeningMotion {
  static let LOOP = 9.6
  static let DRAIN = 4.0
  static let RESUME = 2.0
  enum T {
    static let press = 0.62  // the finger lands on the pill
    static let pop = [0.645, 0.665]  // the glass orb snaps out of the bead to 80 % of its size...
    static let popPeak = 0.82  // ...swells on to 106.5 %...
    static let popSettle = 0.95  // ...and settles (radii fitted to the meniscus)
    static let meniscus = 0.66  // the first sliver of liquid
    static let fillKnee = 0.81  // 15 % full; from here it rises at a steady rate
    static let fillEnd = 2.26  // full
    static let drift = [0.88, 2.35]  // while it fills the orb grows 55 → 67 pt and drifts up-left
    static let hintOut = [0.62, 0.8]  // the hint gives way before the orb reaches it
    static let charge = [0.8, 1.9]  // the chest lights up from inside
    static let shaft = [1.98, 2.43]  // a diagonal light shaft sweeps the chest left to right
    static let headerOut = [2.35, 2.62]  // header and title fade while the orb flies
    static let pillOut = [2.3, 2.54]
    static let flyStart = 2.38
    static let impact = 2.64  // the orb is swallowed by the chest
    static let flashIn = [2.632, 2.74]  // a white burst grows out of the swallow point to the whole screen
    static let flashOut = [2.88, 3.02]  // held white longer than the recording (0.25 s), then clears
    static let swap = 2.715  // closed → open, hidden by the flash
    static let glareChest = [2.74, 2.86]  // the open chest's overexposure fades
    static let pale = [3.8, 4.2]  // the open chest pales a little as the light settles
    static let sparkle = [2.74, 3.4]  // stars and ice chips stream out of the chest
    static let frostIn = [3.02, 3.22]  // the rays give way to the frost backdrop
    static let card = 3.03  // the card appears in the blaze above the chest
    static let cardFull = 3.27  // it has grown to 108 %, rising from y 314 to its place
    static let cardShrink = [3.45, 3.64]  // and eases down to 100 %
    static let cardSharp = [3.26, 3.38]  // the motion blur clears
    static let panel = [3.98, 4.12]  // the panel's glass fades in...
    static let others = [4.08, 4.38]  // ...then "Other Rewards"
    static let tileStart = [4.13, 4.2, 4.28]  // each tile fades in (exponential, τ below)
    static let tileTau = [0.065, 0.06, 0.05]
    static let claim = 4.245  // Claim rises from 60 pt below (τ 0.1 s); it shows pale from 4.17 s and saturates (τ 0.09 s)
    static let claimShow = [4.17, 4.27]
    static let settled = 4.7  // everything has arrived: a touch-driven reveal waits here
    static let leak = [2.4, 2.632]
    static let burstAt = 2.632
    static let surge = [3.0, 3.1]  // the second surge: the card is born white-hot in it
    static let cardCool = [3.2, 3.45]  // the card cools from white to its colours
    static let motes = [2.74, 4.8]  // light dust rising in the beam
    static let claimPress = [7.7, 7.86]
    static let exit = [7.86, 8.14]  // rewards and button leave (faster than they came), the card sinks
    static let sink = [7.92, 8.36]
    static let flash2In = [8.24, 8.38]
    static let flash2Out = [8.42, 8.86]
    static let swapBack = 8.4
    static let restIn = [8.46, 9.06]
  }
  enum ORB {
    static let bead = (x: 201.5, y: 651.0, r: 6.5)
    static let start = (x: 201.5, y: 648.0, r: 55.0)
    static let end = (x: 201.5, y: 643.0, r: 66.8)
    static let swallow = (x: 203.0, dy: 205.0, dr: 36.8, dur: 0.26)
  }
  struct Frame {
    var t: Double
    var idle: Double
    var orb: Double
    var orbX: Double
    var orbY: Double
    var orbR: Double
    var fill: Double
    var charge: Double
    var sky: Double
    var shaft: Double
    var fly: Double
    var header: Double
    var pill: Double
    var hint: Double
    var press: Double
    var seam: Double
    var flash: Double
    var reveal: Double
    var frost: Double
    var glare: Double
    var pale: Double
    var mouth: Double
    var sparkle: Double
    var openDY: Double
    var cardX: Double
    var cardY: Double
    var cardS: Double
    var cardA: Double
    var spin: Double
    var cardBlur: Double
    var sink: Double
    var panel: Double
    var others: Double
    var claim: Double
    var claimInk: Double
    var claimY: Double
    var claimDown: Double
    var lCore: Double
    var lRays: Double
    var lReach: Double
    var lStreak: Double
    var lRingR: Double
    var lRingA: Double
    var lPillar: Double
    var lX: Double
    var lY: Double
    var lBelow: Double
    var lBehind: Double
    var exposure: Double
    var vignette: Double
    var dim: Double
    var shakeX: Double
    var shakeY: Double
    var punch: Double
    var cardHot: Double
    var cardHalo: Double
    var motes: Double
    var tiles: [Double]
  }
  static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
  static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
  static func easeOut(_ x: Double) -> Double {
    let u = 1 - x
    return 1 - u * u * u
  }
  static func easeIn(_ x: Double) -> Double { x * x * x }
  static func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
  }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
  static let L_CARD_Y = 226.0
  static let L_CARD_X = 201.5
  static let SPIN_T = [3.03, 3.13, 3.17, 3.23, 3.31, 3.42, 3.565, 3.68, 3.8]
  static let SPIN_A: [Double] = [180, 200, 270, 360, 450, 540, 630, 705, 720]
  static func spinAt(_ t: Double) -> Double {
    if t <= SPIN_T[0] { return SPIN_A[0] }
    for i in 1..<SPIN_T.count {
      if t < SPIN_T[i] {
        let k = (t - SPIN_T[i - 1]) / (SPIN_T[i] - SPIN_T[i - 1])
        let e = i == SPIN_T.count - 1 ? 1 - (1 - k) * (1 - k) : k
        return mix(SPIN_A[i - 1], SPIN_A[i], e)
      }
    }
    return SPIN_A.last!
  }
  static func fillAt(_ t: Double) -> Double {
    if t < T.meniscus { return 0 }
    if t < T.fillKnee { return mix(0.03, 0.15, seg(t, T.meniscus, T.fillKnee)) }
    return mix(0.15, 1, seg(t, T.fillKnee, T.fillEnd))
  }
  static func frameAt(_ input: Double, _ idle: Double) -> Frame {
    let t = input.truncatingRemainder(dividingBy: LOOP)

    let charging = t >= T.press && t < T.swap
    // Orb: pop (ease-out with a small overshoot that settles), slow growth and drift, then the flight.
    let popK = seg(t, T.pop[0], T.pop[1])
    let pop =
      t < T.pop[1]
      ? 0.8 * easeOut(popK)
      : t < T.popPeak
        ? mix(0.8, 1.065, easeOut(seg(t, T.pop[1], T.popPeak)))
        : mix(1.065, 1, easeInOut(seg(t, T.popPeak, T.popSettle)))
    let d = seg(t, T.drift[0], T.drift[1])
    var orbX = mix(ORB.start.x, ORB.end.x, d)
    var orbY = mix(ORB.start.y, ORB.end.y, d)
    var orbR = mix(ORB.start.r, ORB.end.r, d)
    if t < T.popSettle {
      let k = seg(t, T.pop[0], T.popSettle)
      orbX = mix(ORB.bead.x, ORB.start.x, k)
      orbY = mix(ORB.bead.y, ORB.start.y, k)
      orbR = mix(ORB.bead.r, ORB.start.r, pop)
    }
    let flyK = seg(t, T.flyStart, T.impact)
    if t > T.flyStart {
      // Measured path: slow off the pill, then ever faster up into the chest, shrinking all the way.
      let u = min(t, T.impact) - T.flyStart
      let k = u / ORB.swallow.dur
      orbX = mix(ORB.end.x, ORB.swallow.x, min(1, k))
      orbY = ORB.end.y - ORB.swallow.dy * (0.25 * k + 0.75 * pow(k, 2.4))
      orbR = ORB.end.r - ORB.swallow.dr * (0.3 * k + 0.7 * pow(k, 1.8))
    }
    let orb: Double = charging && t < T.impact ? 1 : 0
    let fill = charging ? fillAt(t) : 0
    let fly = charging ? flyK : 0
    let charge = t < T.swap ? easeInOut(seg(t, T.charge[0], T.charge[1])) : 0
    let sky = t < T.swap ? easeInOut(seg(t, T.charge[0], T.impact)) : 0
    let shaft = t > T.shaft[0] && t < T.shaft[1] ? seg(t, T.shaft[0], T.shaft[1]) : 0
    let restBack = easeOut(seg(t, T.restIn[0], T.restIn[1]))
    let header = t < T.swapBack ? 1 - seg(t, T.headerOut[0], T.headerOut[1]) : restBack
    // The orb takes the pill's place: the pill fades out as the orb pops, and comes back after Claim.
    let pill = t < T.swapBack ? 1 - easeOut(seg(t, T.press, T.popSettle)) : restBack
    let hint = t < T.swapBack ? 1 - easeOut(seg(t, T.hintOut[0], T.hintOut[1])) : restBack
    let press =
      t < T.press
      ? 0
      : t < T.fillEnd
        ? easeOut(seg(t, T.press, T.press + 0.12)) : 1 - seg(t, T.fillEnd, T.fillEnd + 0.2)
    let seam = t < T.swap ? seg(t, T.impact - 0.04, T.swap) : 0
    // The burst is opaque from its start; it covers the screen by growing (see drawFlash).
    let flash1 =
      t < T.flashOut[0]
      ? seg(t, T.flashIn[0], T.flashIn[0] + 0.014)
      : 1 - easeOut(seg(t, T.flashOut[0], T.flashOut[1]))
    let flash2 =
      t < T.flash2Out[0]
      ? easeIn(seg(t, T.flash2In[0], T.flash2In[1]))
      : 1 - easeOut(seg(t, T.flash2Out[0], T.flash2Out[1]))
    let flash = t < 6 ? flash1 : flash2 * 0.92
    let reveal: Double = t >= T.swap && t < T.swapBack ? 1 : 0
    let frost = t < T.swapBack ? easeInOut(seg(t, T.frostIn[0], T.frostIn[1])) : 0
    let glare = reveal * (1 - seg(t, T.glareChest[0], T.glareChest[1]))
    let pale = easeInOut(seg(t, T.pale[0], T.pale[1]))
    let mouth = reveal * mix(1, 0.55, seg(t, 3.0, 3.85))
    let sparkle = t >= T.sparkle[0] && t < T.sparkle[1] ? t - T.sparkle[0] : -1
    let openDY = reveal * (5 * (1 - seg(t, 3.0, 5.5)) + 3 * (1 - easeOut(seg(t, T.swap, 2.85))))
    // Card (measured): appears small in the blaze at y 314, rises and grows to 108 % by 3.27 s while
    // it turns, then eases down to its size; blurred until 3.26 s, sharp by 3.38 s.
    let sink = easeIn(seg(t, T.sink[0], T.sink[1]))
    let cu = max(0, t - T.card) / 0.17
    let cardY = L_CARD_Y + 88 * exp(-cu * cu)
    let cardX = L_CARD_X - 3 * (1 - seg(t, 3.4, 3.7))
    let sk = seg(t, T.card, T.cardFull)
    let cardS =
      t < T.cardShrink[0]
      ? 0.1 + 0.98 * sk * sk * (3 - 2 * sk)
      : mix(1.08, 1, easeInOut(seg(t, T.cardShrink[0], T.cardShrink[1])))
    let cardA = reveal * seg(t, T.card, T.card + 0.07)
    let spin = spinAt(t)
    let cardBlur = 4.5 * (1 - seg(t, T.cardSharp[0], T.cardSharp[1])) * (t >= T.card ? 1 : 0)
    let exit = easeOut(seg(t, T.exit[0], T.exit[1]))
    let panel = seg(t, T.panel[0], T.panel[1]) * (1 - exit)
    let others = seg(t, T.others[0], T.others[1]) * (1 - exit)
    let tile = { (k: Int) -> Double in
      (t < T.tileStart[k] ? 0 : 1 - exp(-(t - T.tileStart[k]) / T.tileTau[k])) * (1 - exit)
    }
    let ct = max(0, t - T.claim)
    let claimY = (t < T.claim ? 60 : 60 * exp(-ct / 0.1)) + 40 * exit
    let claim = seg(t, T.claimShow[0], T.claimShow[1]) * (1 - exit)
    let claimInk = t < T.claim ? 0 : 1 - exp(-ct / 0.09)
    let cd = seg(t, T.claimPress[0], T.claimPress[1])
    let claimDown = cd <= 0 || cd >= 1 ? 0 : sin(cd * Double.pi)
    // ── The light ──
    let bt = t - T.burstAt
    let lit = t >= T.burstAt && t < T.swapBack
    let leak = t < T.burstAt && t >= T.leak[0] ? easeIn(seg(t, T.leak[0], T.leak[1])) : 0
    let sinkLight = 0.8 * sin(Double.pi * seg(t, T.sink[0], T.sink[1] + 0.05))
    var lCore = 0.18 * leak
    if lit {
      if bt < 0.03 {
        lCore = mix(0.2, 3.2, bt / 0.03)
      } else if t < 2.9 {
        lCore = mix(3.2, 2.6, seg(t, T.burstAt + 0.03, 2.9))
      } else if t < T.surge[0] {
        lCore = mix(2.6, 1.3, seg(t, 2.9, T.surge[0]))
      } else if t < T.surge[1] {
        lCore = mix(1.3, 2.6, easeOut(seg(t, T.surge[0], T.surge[1])))
      } else if t < 3.45 {
        // The light withdraws into the card, which shows through it from about 3.3 s.
        let k = seg(t, T.surge[1] + 0.08, 3.45)
        lCore = mix(2.6, 0.35, 1 - (1 - k) * (1 - k))
      } else {
        lCore = mix(0.35, 0.1, seg(t, 3.4, 4.4))
      }
      lCore = lCore * (1 - seg(t, T.sink[0], T.sink[1])) + sinkLight
    }
    let lRays =
      lit
      ? (bt < 0.6 ? 1.1 : mix(1.05, 0.18, seg(t, 3.3, 3.95)))
        * (1 - 0.8 * seg(t, T.sink[0], T.sink[1])) : 0.7 * leak
    let lReach = lit ? 0.2 + 0.9 * easeOut(seg(t, T.burstAt, 2.95)) : 0.18 + 0.1 * leak
    var lStreak = 0.15 * leak
    if lit {
      if bt < 0.04 {
        lStreak = mix(0.2, 1.2, bt / 0.04)
      } else if t < T.surge[0] {
        lStreak = mix(1.2, 0.25, easeOut(seg(t, T.burstAt + 0.04, T.surge[0])))
      } else if t < T.surge[1] {
        lStreak = mix(0.25, 0.8, seg(t, T.surge[0], T.surge[1]))
      } else {
        lStreak = mix(0.8, 0, easeOut(seg(t, T.surge[1], 3.6)))
      }
    }
    var lRingR = 0.0
    var lRingA = 0.0
    if lit && t < 3.05 {
      lRingR = 740 * easeOut(seg(t, T.burstAt, 3.05))
      lRingA = 0.9 * (1 - seg(t, T.burstAt, 3.05))
    } else if t >= T.surge[0] + 0.04 && t < 3.45 {
      // A smaller pulse ring from the card's birth.
      lRingR = 420 * easeOut(seg(t, T.surge[0] + 0.04, 3.45))
      lRingA = 0.45 * (1 - seg(t, T.surge[0] + 0.04, 3.45))
    }
    // The pillar out of the mouth gives way once the card is born: from then on the light sits behind it.
    let onCard = seg(t, T.surge[0], T.surge[0] + 0.08) * (1 - seg(t, 3.5, 4.0))
    let lPillar = lit ? seg(t, 2.85, 2.98) * (1 - onCard) * (1 - seg(t, 3.3, 3.6)) : 0
    let toMouth = easeInOut(seg(t, T.burstAt, 2.8))
    let lX = lit ? mix(mix(200, 198.5, toMouth), cardX, onCard) : 200
    let lY = lit ? mix(mix(430, 400, toMouth), cardY, onCard) : 425
    let lBelow = t < 2.88 ? 1 : mix(1, 0.1, seg(t, 2.88, 3.02))
    let lBehind: Double = lit ? 0 : 1
    let hump1 = seg(t, T.burstAt, 2.68) * (1 - seg(t, 2.9, 3.05))
    let hump2 = sin(Double.pi * seg(t, T.surge[0], 3.4))
    let exposure = 1 + (lit ? 0.6 * hump1 + 0.35 * hump2 : 0)
    // The world dims to a deep blue as the orb flies in (the inhale before the burst) and stays dim
    // while the light pours out, so the light reads against it; the lights come up as the card lands.
    let dimIn = easeInOut(seg(t, 2.3, 2.6))
    let dimOut = easeInOut(seg(t, 3.25, 3.8))
    let dim = t < T.swapBack ? 0.72 * dimIn * (1 - dimOut) : 0
    let vignette = 0.38 * dimIn * (1 - dimOut) * (t < T.swapBack ? 1 : 0)
    var shakeX = 0.0
    var shakeY = 0.0
    if bt >= 0 && bt < 0.35 {
      let amp = 5 * exp(-bt / 0.07)
      shakeX = amp * sin(bt * 95)
      shakeY = amp * cos(bt * 78 + 0.7)
    } else if t >= T.surge[0] + 0.02 && t < T.surge[0] + 0.25 {
      let amp = 2 * exp(-(t - T.surge[0] - 0.02) / 0.06)
      shakeX = amp * sin(t * 90)
      shakeY = amp * cos(t * 70)
    }
    let pushIn =
      t < T.burstAt
      ? 0.012 * easeIn(seg(t, 1.9, T.burstAt))
      : 0.012 * (1 - easeOut(seg(t, T.burstAt, T.burstAt + 0.5)))
    let punch = 1 + pushIn + (bt >= 0 && t < T.swapBack ? 0.03 * exp(-bt / 0.09) : 0)
    let cardHot = t < T.cardCool[0] ? 1 : 1 - easeOut(seg(t, T.cardCool[0], T.cardCool[1]))
    let cardHalo = seg(t, T.card, T.card + 0.07) * (1 - seg(t, 3.35, 3.9))
    let motes = t >= T.motes[0] && t < T.motes[1] ? t - T.motes[0] : -1

    return Frame(
      t: t,
      idle: idle,
      orb: orb,
      orbX: orbX,
      orbY: orbY,
      orbR: orbR,
      fill: fill,
      charge: charge,
      sky: sky,
      shaft: shaft,
      fly: fly,
      header: header,
      pill: pill,
      hint: hint,
      press: press,
      seam: seam,
      flash: flash,
      reveal: reveal,
      frost: frost,
      glare: glare,
      pale: pale,
      mouth: mouth,
      sparkle: sparkle,
      openDY: openDY,
      cardX: cardX,
      cardY: cardY,
      cardS: cardS,
      cardA: cardA,
      spin: spin,
      cardBlur: cardBlur,
      sink: sink,
      panel: panel,
      others: others,
      claim: claim,
      claimInk: claimInk,
      claimY: claimY,
      claimDown: claimDown,
      lCore: lCore,
      lRays: lRays,
      lReach: lReach,
      lStreak: lStreak,
      lRingR: lRingR,
      lRingA: lRingA,
      lPillar: lPillar,
      lX: lX,
      lY: lY,
      lBelow: lBelow,
      lBehind: lBehind,
      exposure: exposure,
      vignette: vignette,
      dim: dim,
      shakeX: shakeX,
      shakeY: shakeY,
      punch: punch,
      cardHot: cardHot,
      cardHalo: cardHalo,
      motes: motes,
      tiles: [tile(0), tile(1), tile(2)])
  }
  static func rand(_ i: Double, _ salt: Double) -> Double {
    let x = sin(i * 127.1 + salt * 311.7) * 43758.5453
    return x - floor(x)
  }
}
