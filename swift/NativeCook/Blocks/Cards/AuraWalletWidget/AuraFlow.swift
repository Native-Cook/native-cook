// The score: pure functions shared by the glass, portraits and state machine.
// Design points and milliseconds, matching aura-flow.ts term for term.
import Foundation

enum AuraFlow {
  enum drop {
    static let halo: Double = 6
    static let haloFull: Double = 9.5
    enum weigh {
      static let duration: Double = 220
      static let dampingRatio: Double = 0.6
    }
    static let sag: Double = 6
    static let othersScale: Double = 0.92
    static let othersOpacity: Double = 0.55
    static let aside: Double = 3
    enum form {
      static let duration: Double = 400
      static let dampingRatio: Double = 0.7
    }
  }
  enum pull {
    static let dead: Double = 12
    static let perStep: Double = 6.5
    static let amountStep: Double = 10
    static let amountMax: Double = 100
    static let amountDefault: Double = 60
    static let sideways: Double = 28
    static let rubber: Double = 0.3
  }
  enum neck {
    static let root: Double = 13
    static let rootLong: Double = 19
    static let waistShort: Double = 9
    static let waistLong: Double = 4
    static let blend: Double = 12
  }
  enum send {
    static let snap: Double = 140
    static let hang: Double = 90
    static let gravity: Double = 1700
    static let fallMin: Double = 140
    static let stretch: Double = 0.12
    static let flatten: Double = 0.22
    static let squash: Double = 50
    static let ringDecay: Double = 110
    static let ringPeriod: Double = 300
    static let absorbAfter: Double = 60
    static let absorb: Double = 220
    static let sinkBy: Double = 3
    static let shrinkTo: Double = 0.86
    static let rippleSpeed: Double = 460
    static let rippleLife: Double = 700
    static let rimFlash: Double = 2.2
    static let lipKick: Double = 3
    enum lipRing {
      static let duration: Double = 500
      static let dampingRatio: Double = 0.45
    }
    static let cushionDip: Double = 0.06
    static let cushionRecover: Double = 600
    static let waveSpeed: Double = 330
    static let waveLife: Double = 520
    static let budDelay: Double = 380
    static let budFrom: Double = 0.5
    enum bud {
      static let duration: Double = 520
      static let dampingRatio: Double = 0.55
    }
    static let unlock: Double = 300
    static let sink: Double = 300
  }
  enum pills {
    static let sendWide: Double = 164
    static let receiveRound: Double = 52
    static let kiss: Double = 8
    enum morph {
      static let duration: Double = 400
      static let dampingRatio: Double = 0.85
    }
  }
  enum nudge {
    static let depth: Double = 4
    static let stagger: Double = 45
    static let each: Double = 320
  }
  enum enter {
    static let hold: Double = 260
    static let smooth: Double = 50
    static let reveal: Double = 140
    static let delay: Double = 60
    static let stagger: Double = 55
  }
  enum cue {
    static let first: Double = 2400
    static let every: Double = 6000
  }
  enum views {
    static let pullSlop: Double = 6
    enum route {
      static let travel: Double = 14
      static let duration: Double = 220
    }
    enum chip {
      static let fade: Double = 180
      static let blur: Double = 2
    }
    enum hint {
      static let fade: Double = 160
      static let sentHold: Double = 2000
    }
    enum arrow {
      static let exit: Double = 12
      static let exitTime: Double = 160
      static let enter: Double = 8
      static let enterTime: Double = 200
      static let enterDelay: Double = 60
    }
    static let receiptFade: Double = 180
    static let departureFade: Double = 150
  }
}

let auraLandY = AuraDesign.pane.y + AuraDesign.pane.h / 2

func auraClamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
  min(max(value, low), high)
}

private func auraEase(_ value: Double) -> Double {
  let t = auraClamp(value)
  return t * t * (3 - 2 * t)
}

func auraSlotX(_ index: Double) -> Double {
  181 + (index - 2) * AuraDesign.lip.pitch
}

/// A spring sampled at an absolute time; this is distinct from withSpring's integrator.
func auraSpring(_ elapsed: Double, _ duration: Double, _ dampingRatio: Double) -> Double {
  if elapsed <= 0 { return 0 }
  let t = elapsed / duration
  let omega = 2 * Double.pi
  let zeta = auraClamp(dampingRatio, 0.05, 0.999)
  let damped = omega * sqrt(1 - zeta * zeta)
  let envelope = exp(-zeta * omega * t)
  return 1 - envelope * (cos(damped * t) + ((zeta * omega) / damped) * sin(damped * t))
}

func auraRubber(_ travel: Double, _ limit: Double) -> Double {
  if travel <= limit { return travel }
  return limit + (travel - limit) * AuraFlow.pull.rubber
}

func auraPullLimit() -> Double {
  AuraFlow.pull.dead + (AuraFlow.pull.amountMax / AuraFlow.pull.amountStep - 0.5)
    * AuraFlow.pull.perStep
}

func auraAmountAt(_ depth: Double) -> Double {
  if depth < AuraFlow.pull.dead { return 0 }
  let steps = floor((depth - AuraFlow.pull.dead) / AuraFlow.pull.perStep) + 1
  return auraClamp(
    steps * AuraFlow.pull.amountStep, AuraFlow.pull.amountStep, AuraFlow.pull.amountMax)
}

func auraDepthFor(_ amount: Double) -> Double {
  AuraFlow.pull.dead
    + (auraClamp(amount, AuraFlow.pull.amountStep, AuraFlow.pull.amountMax)
      / AuraFlow.pull.amountStep - 0.5) * AuraFlow.pull.perStep
}

func auraDropHalo(_ weight: Double) -> Double {
  AuraFlow.drop.halo + (AuraFlow.drop.haloFull - AuraFlow.drop.halo) * auraClamp(weight, 0, 1.15)
}

func auraNeckRoot(_ depth: Double) -> Double {
  AuraFlow.neck.root + (AuraFlow.neck.rootLong - AuraFlow.neck.root)
    * auraEase(depth / auraPullLimit())
}

func auraNeckWaist(_ depth: Double) -> Double {
  let stretched = auraClamp(depth / auraPullLimit())
  return AuraFlow.neck.waistShort + (AuraFlow.neck.waistLong - AuraFlow.neck.waistShort)
    * auraEase(stretched)
}

func auraSinkDepth(_ elapsed: Double, _ depth: Double) -> Double {
  let t = auraClamp(elapsed / AuraFlow.send.sink)
  let answer = 1 - (1 - t) * (1 - t) * (1 - t)
  return depth * (0.2 * answer + 0.8 * pow(t, 2.4))
}

func auraEnterLength() -> Double {
  AuraFlow.enter.delay + AuraFlow.enter.stagger * (AuraDesign.lip.count - 1)
    + AuraFlow.send.bud.duration
}

func auraEnterScale(_ index: Double, _ elapsed: Double) -> Double {
  let since = elapsed - AuraFlow.enter.delay - index * AuraFlow.enter.stagger
  if since <= 0 { return 0 }
  return AuraFlow.send.budFrom + (1 - AuraFlow.send.budFrom)
    * auraSpring(since, AuraFlow.send.bud.duration, AuraFlow.send.bud.dampingRatio)
}

func auraEnterOpacity(_ index: Double, _ elapsed: Double) -> Double {
  auraClamp((elapsed - AuraFlow.enter.delay - index * AuraFlow.enter.stagger) / 120)
}

func auraFriendPose(_ index: Double, _ chosen: Double, _ formed: Double) -> AuraFriendPose {
  if chosen < 0 || formed == 0 { return .init() }
  if index == chosen {
    return .init(
      dy: AuraFlow.drop.sag * formed, halo: AuraFlow.drop.halo * auraClamp(formed, 0, 1.2))
  }
  let away = index - chosen
  let back = auraClamp(formed)
  return .init(
    dx: (away < 0 ? -1 : 1) * AuraFlow.drop.aside * formed / abs(away),
    scale: 1 + (AuraFlow.drop.othersScale - 1) * back,
    opacity: 1 + (AuraFlow.drop.othersOpacity - 1) * back)
}

func auraLipPose(
  _ index: Double, _ chosen: Double, _ formed: Double,
  _ leaving: Double, _ unformed: Double
) -> AuraFriendPose {
  if index == leaving && index != chosen {
    return .init(
      dy: AuraFlow.drop.sag * unformed, halo: AuraFlow.drop.halo * auraClamp(unformed, 0, 1.2))
  }
  var pose = auraFriendPose(index, chosen, formed)
  if index == chosen || leaving < 0 { return pose }
  let back = auraClamp(max(formed, unformed))
  pose.scale = 1 + (AuraFlow.drop.othersScale - 1) * back
  pose.opacity = 1 + (AuraFlow.drop.othersOpacity - 1) * back
  return pose
}

func auraNudgeLength() -> Double {
  AuraFlow.nudge.stagger * (AuraDesign.lip.count - 1) + AuraFlow.nudge.each
}

func auraNudgeDip(_ index: Double, _ elapsed: Double) -> Double {
  let local = (elapsed - index * AuraFlow.nudge.stagger) / AuraFlow.nudge.each
  if elapsed < 0 || local <= 0 || local >= 1 { return 0 }
  return AuraFlow.nudge.depth * sin(Double.pi * local)
}

func auraSendMoments(_ fromY: Double) -> AuraSendMoments {
  let fall = max(auraLandY - fromY, 0)
  let fallTime = max(sqrt((2 * fall) / AuraFlow.send.gravity) * 1000, AuraFlow.send.fallMin)
  let impact = AuraFlow.send.hang + fallTime
  let bud = impact + AuraFlow.send.budDelay
  return .init(
    fallStart: AuraFlow.send.hang, fallTime: fallTime, impact: impact,
    unlock: impact + AuraFlow.send.unlock, bud: bud,
    end: max(
      bud + AuraFlow.send.bud.duration, impact + AuraFlow.send.rippleLife,
      AuraFlow.send.snap + AuraFlow.send.cushionRecover, AuraFlow.send.snap + AuraFlow.send.waveLife
    ))
}

/// One clock supplies the neck, gravity, landing, absorption, ripple and returning bud.
func auraSendPose(_ elapsed: Double, _ fromY: Double) -> AuraSendPose {
  let moments = auraSendMoments(fromY)
  let t = max(elapsed, 0)
  let falling = auraClamp((t - moments.fallStart) / moments.fallTime)
  let sinceImpact = t - moments.impact
  let landed = sinceImpact >= 0
  let y = fromY + (auraLandY - fromY) * falling * falling
  let stretch = AuraFlow.send.stretch * sin(Double.pi * falling)
  let ringing = max(sinceImpact - AuraFlow.send.squash, 0)
  let flat =
    landed
    ? AuraFlow.send.flatten * auraEase(sinceImpact / AuraFlow.send.squash)
      * exp(-ringing / AuraFlow.send.ringDecay)
      * cos((2 * Double.pi * ringing) / AuraFlow.send.ringPeriod) : 0
  let taken =
    landed ? auraEase((sinceImpact - AuraFlow.send.absorbAfter) / AuraFlow.send.absorb) : 0
  let ripple = landed ? auraClamp(sinceImpact / AuraFlow.send.rippleLife) : 0
  let sinceSnap = t - AuraFlow.send.snap
  let wave = auraClamp(sinceSnap / AuraFlow.send.waveLife)
  let sinceBud = t - moments.bud
  return .init(
    neck: 1 - auraEase(t / AuraFlow.send.snap),
    lipKick: sinceSnap > 0
      ? -AuraFlow.send.lipKick
        * (1
          - auraSpring(
            sinceSnap, AuraFlow.send.lipRing.duration, AuraFlow.send.lipRing.dampingRatio)) : 0,
    y: y + AuraFlow.send.sinkBy * taken,
    tall: (1 + stretch - flat) * (1 + (AuraFlow.send.shrinkTo - 1) * taken),
    wide: (1 - stretch * 0.7 + flat * 0.9) * (1 + (AuraFlow.send.shrinkTo - 1) * taken),
    presence: 1 - taken, taken: taken,
    rippleRadius: landed ? (AuraFlow.send.rippleSpeed * sinceImpact) / 1000 : 0,
    rippleStrength: landed ? (1 - ripple) * (1 - ripple) : 0,
    rim: landed ? 1 + (AuraFlow.send.rimFlash - 1) * (1 - ripple) * (1 - ripple) : 1,
    cushion: sinceSnap > 0
      ? 1 - AuraFlow.send.cushionDip * (1 - auraEase(sinceSnap / AuraFlow.send.cushionRecover))
      : 1 - AuraFlow.send.cushionDip * auraEase(t / AuraFlow.send.snap),
    waveRadius: sinceSnap > 0 ? (AuraFlow.send.waveSpeed * sinceSnap) / 1000 : 0,
    waveStrength: sinceSnap > 0 ? (1 - wave) * (1 - wave) : 0,
    bud: sinceBud > 0
      ? AuraFlow.send.budFrom + (1 - AuraFlow.send.budFrom)
        * auraSpring(sinceBud, AuraFlow.send.bud.duration, AuraFlow.send.bud.dampingRatio) : 0,
    budOpacity: auraClamp(sinceBud / 120))
}

func auraActionFrames(_ wide: Double) -> AuraActionFrames {
  let receive =
    AuraDesign.actions.widths[2] + (AuraFlow.pills.receiveRound - AuraDesign.actions.widths[2])
    * wide
  let send =
    AuraDesign.actions.widths[3] + (AuraFlow.pills.sendWide - AuraDesign.actions.widths[3]) * wide
  let settingsX = AuraDesign.inset
  let swapX = settingsX + AuraDesign.actions.widths[0] + AuraDesign.actions.gap
  let receiveX = swapX + AuraDesign.actions.widths[1] + AuraDesign.actions.gap
  let sendX = receiveX + receive + AuraDesign.actions.gap
  return .init(
    xs: .init(settingsX, swapX, receiveX, sendX),
    widths: .init(AuraDesign.actions.widths[0], AuraDesign.actions.widths[1], receive, send),
    kiss: AuraFlow.pills.kiss * sin(Double.pi * auraClamp(wide)))
}
