import Foundation
import Observation
import UIKit

typealias GlowknobChannel = Int // 0 = Brightness, 1 = Volume

// A full level range turns the knob 1.5π: at the specimen's scale that arc is
// close to the 160pt drag, so the grooves travel with the finger.
private let DIAL_TURN = Double.pi * 1.5
private let DIAL_DRAG = 160.0
// Visual give past 0/100 on the knob, in level units (≈21° at most).
private let DIAL_GIVE = 0.08
// Interrupting a replay or handoff morphs the last visible frame into the
// new source rather than cutting to it. Both ends are on screen, so this is
// ease-in-out: a strong ease-out spent most of the change in the first frame.
private let HANDOFF = 240.0
private let easeInOut = GlowknobEasing.bezier(0.77, 0, 0.175, 1)
// Mode switch: the light contracts into the end cap (ease-in-out, so it does
// not cut), the color transfers at the trough, then it blooms out to the new
// level with a soft start and a long tail. 380ms: long enough to read as one
// gesture, still under the platform push.
private let SWITCH = 380.0
private let TROUGH = 150.0 / SWITCH
private let contract = GlowknobEasing.bezier(0.65, 0, 0.35, 1)
private let bloom = GlowknobEasing.bezier(0.3, 0, 0.15, 1)
// Fill fraction at which the liquid fill is exactly the round end cap.
private let CAP = GlowknobReferenceGeometry.track.height / GlowknobReferenceGeometry.track.width
// Power-on: the light rises like liquid from 0 with a soft settle.
private let rise = GlowknobEasing.bezier(0.22, 1, 0.36, 1)
private let BOOT = 900.0
// Knob detents: 28 grooves per revolution.
private let GROOVE = (Double.pi * 2) / 28
// Fine scrub: finger this far below the track switches to ¼ speed.
private let FINE_DROP = 32.0

struct GlowknobHandoffFrame: Equatable {
  var fill: Double
  var light: Double
  var blur: Double
  var number: Double
  var label: Double
  var readout: Double
}

struct GlowknobFineAnchor: Equatable {
  var x: Double
  var level: Double
}

/// What the native readout shows (readout.tsx's `shown` state).
struct GlowknobReadoutShown: Equatable {
  var value: Int
  var roll: Bool
  var quick: Bool
}

// Skips the lerp when no handoff is running, so the live value is exact.
private func fade(_ live: Double, _ last: Double, _ amount: Double) -> Double {
  amount > 0 ? live + (last - live) * amount : live
}

private func clamp01(_ level: Double) -> Double { max(0, min(1, level)) }

/// The console's state machine (use-tactile-control.ts): levels, the channel
/// switch choreography, gestures, haptics and power-on. Shared values live on
/// the frame runtime; the `@Observable` fields are the RN component state.
@MainActor
@Observable
final class GlowknobTactileControl {
  // MARK: React state
  private(set) var mode: GlowknobChannel = 0
  private(set) var snapshot: [Int] = [52, 53]
  private(set) var playing = false
  private(set) var shown = GlowknobReadoutShown(value: 52, roll: false, quick: false)
  private(set) var reduced: Bool
  private(set) var enabled = true
  private(set) var fx: GlowknobEffects
  var haptics = true

  // MARK: Shared values
  @ObservationIgnored let brightness = GlowknobAnimatedValue(0.52)
  @ObservationIgnored let volume = GlowknobAnimatedValue(0.53)
  // Committed levels. The visible levels may still be gliding toward them.
  @ObservationIgnored let goal = GlowknobSharedValue<[Double]>([0.52, 0.53])
  @ObservationIgnored let channel = GlowknobSharedValue<GlowknobChannel>(0)
  @ObservationIgnored let mix = GlowknobAnimatedValue(0)
  @ObservationIgnored let reveal = GlowknobAnimatedValue(1)
  @ObservationIgnored let selector = GlowknobAnimatedValue(0)
  @ObservationIgnored let outgoingFill = GlowknobAnimatedValue(glowknobFillForLevel(0.52))
  @ObservationIgnored let outgoingNumber = GlowknobAnimatedValue(52)
  @ObservationIgnored let trackPress = GlowknobAnimatedValue(0)
  @ObservationIgnored let dialPress = GlowknobAnimatedValue(0)
  @ObservationIgnored let brightPress = GlowknobAnimatedValue(0)
  @ObservationIgnored let volumePress = GlowknobAnimatedValue(0)
  @ObservationIgnored let width = GlowknobAnimatedValue(1)
  @ObservationIgnored let dragStart = GlowknobAnimatedValue(0)
  @ObservationIgnored let dragOwner = GlowknobSharedValue<Int>(0)
  @ObservationIgnored let dialAngle = GlowknobAnimatedValue(0)
  @ObservationIgnored let dialGive = GlowknobAnimatedValue(0)
  @ObservationIgnored let available = GlowknobSharedValue(true)
  @ObservationIgnored let active = GlowknobAnimatedValue(1)
  @ObservationIgnored let replayTime = GlowknobAnimatedValue(-1)
  @ObservationIgnored let replayRunning = GlowknobSharedValue(false)
  @ObservationIgnored let replayId = GlowknobSharedValue(0)
  @ObservationIgnored let blend = GlowknobAnimatedValue(0)
  @ObservationIgnored let blendFill = GlowknobSharedValue(false)
  @ObservationIgnored let from = GlowknobSharedValue(GlowknobHandoffFrame(fill: glowknobFillForLevel(0.52), light: 1, blur: 0,
                                                          number: 1, label: 1, readout: 52))
  // Effect switches mirrored to the frame runtime for gestures and reactions.
  @ObservationIgnored let fxs: GlowknobSharedValue<GlowknobEffects>
  @ObservationIgnored let boot: GlowknobAnimatedValue
  @ObservationIgnored let spinning = GlowknobSharedValue(false)
  @ObservationIgnored let lastTick = GlowknobAnimatedValue(0)
  @ObservationIgnored let fine = GlowknobSharedValue(false)
  @ObservationIgnored let fineAnchor = GlowknobSharedValue(GlowknobFineAnchor(x: 0, level: 0))
  @ObservationIgnored let fineShown = GlowknobAnimatedValue(0)
  @ObservationIgnored let magnetHeld = GlowknobSharedValue(false)

  @ObservationIgnored private var meniscus: GlowknobMeniscus!
  @ObservationIgnored private var legibility: GlowknobReadoutLegibility!
  @ObservationIgnored private var runId = 0
  @ObservationIgnored private var mounted = true
  @ObservationIgnored private var booted = false
  @ObservationIgnored private var detent: GlowknobDetentPlayer?

  init(fx: GlowknobEffects = .all, reduced: Bool) {
    self.fx = fx
    self.reduced = reduced
    fxs = GlowknobSharedValue(fx)
    boot = GlowknobAnimatedValue(fx.powerOn && !reduced ? 0 : 1)
    meniscus = GlowknobMeniscus(fill: { [unowned self] in self.fill }, on: fx.meniscus && !reduced)
    legibility = GlowknobReadoutLegibility(readout: { [unowned self] in self.readout },
                                   fill: { [unowned self] in self.fill }, reduced: reduced)
    registerReactions()
    if fx.sound { detent = GlowknobDetentPlayer() }
  }

  // MARK: Derived values (recomputed whenever read)

  var pose: GlowknobReferencePose { glowknobReferencePose(replayTime.get(), reduced: reduced) }
  var value: Double { channel.get() == 0 ? brightness.get() : volume.get() }

  var sourceFill: Double {
    if replayTime.get() >= 0 { return pose.fill }
    let p = reveal.get()
    let target = glowknobFillForLevel(value)
    if p >= 1 { return target }
    // Trough is the round cap, never the thin liquid slice below it.
    let trough = min(outgoingFill.get(), CAP)
    if p < TROUGH {
      let k = contract(p / TROUGH)
      return outgoingFill.get() + (trough - outgoingFill.get()) * k
    }
    return trough + (target - trough) * bloom((p - TROUGH) / (1 - TROUGH))
  }

  // A finger on the light always wins over the fade: the endpoint stays under it.
  var fill: Double {
    let b = boot.get()
    let k = b >= 1 ? 1 : rise(b)
    let live = sourceFill
    if !blendFill.get() || dragOwner.get() != 0 { return live * k }
    return fade(live, from.get().fill, blend.get()) * k
  }

  var slosh: Double { meniscus.slosh.get() }

  var lightMix: Double { replayTime.get() >= 0 ? pose.mix : mix.get() }

  var lightAlpha: Double {
    let live = replayTime.get() >= 0 ? pose.light : 1
    return fade(live, from.get().light, blend.get()) * min(1, boot.get() * 5)
  }

  var lightBlur: Double {
    if reduced { return 0 }
    var live = 0.0
    if replayTime.get() >= 0 {
      live = pose.blur
    } else {
      // A light softening that peaks at the trough; kept low so the color
      // stays saturated while it blooms.
      let p = reveal.get()
      live = p < TROUGH ? (p / TROUGH) * 7 : max(0, 1 - (p - TROUGH) / 0.4) * 7
    }
    return fade(live, from.get().blur, blend.get())
  }

  var numberAlpha: Double {
    var live = 1.0
    if replayTime.get() >= 0 {
      live = pose.number
    } else if !reduced {
      // Out while contracting, swapped unseen at the trough, back in while
      // the new light blooms — the old channel's number never rides along.
      let p = reveal.get()
      live = p < TROUGH ? max(0, 1 - p / (TROUGH * 0.8)) : max(0, min(1, (p - TROUGH - 0.12) / 0.4))
    }
    return fade(live, from.get().number, blend.get()) * min(1, boot.get() * 4)
  }

  var readout: Double {
    // Power-on: show 0, then the number rolls up to the level in one move.
    if boot.get() < 0.12 { return 0 }
    if blend.get() > 0.5 { return from.get().readout }
    if replayTime.get() >= 0 { return pose.display }
    return reveal.get() < TROUGH ? outgoingNumber.get() : glowknobJsRound(clamp01(value) * 100)
  }

  // The mode title is one GlowknobNumericText that swaps its own glyphs on a switch,
  // so live it stays fully visible. The replay keeps its sampled visibility
  // (the reference fades the title out and back in around the transfer).
  var labelAlpha: Double {
    let live = replayTime.get() >= 0 ? max(pose.brightnessLabel, pose.volumeLabel) : 1
    return fade(live, from.get().label, blend.get())
  }

  // Selector glyphs/wells answer the press immediately; the light's color
  // transfer is choreographed separately.
  var selectorMix: Double { replayTime.get() >= 0 ? pose.mix : selector.get() }
  // The number rolls for every in-place change, including under a dragging
  // finger, but not for a channel swap (that one is hidden under the fade).
  var numberRolls: Bool { reveal.get() >= 1 }
  // A finger is moving the level continuously: rolls must be short enough to
  // settle between steps or the digits stay blurred for the whole drag.
  var numberQuick: Bool { dragOwner.get() == 1 || dragOwner.get() == 2 }
  var numberInk: Double { legibility.numberInk.get() }
  var numberRun: Double { legibility.numberRun }
  var dialRotation: Double { (dialAngle.get() + dialGive.get()) * DIAL_TURN }
  var glowPress: Double { max(trackPress.get(), dialPress.get()) }

  // MARK: JS-side callbacks

  private func publish(_ b: Double, _ v: Double) {
    if mounted { snapshot = [Int(glowknobJsRound(b * 100)), Int(glowknobJsRound(v * 100))] }
  }
  private func publishReplay(_ id: Int, _ next: GlowknobChannel, _ b: Double, _ v: Double) {
    if !mounted || id != runId { return }
    mode = next
    publish(b, v)
  }
  private func finishReplay(_ id: Int) {
    if mounted && id == runId { playing = false }
  }
  private func feedback() { if haptics { GlowknobHaptics.selection() } }
  private func edgeFeedback() { if haptics { GlowknobHaptics.impact(.light) } }
  // Detent click (opt-in): restarted per tick so rapid detents stay distinct.
  private func tickFeedback() {
    if haptics { GlowknobHaptics.selection() }
    if fx.sound { detent?.click() }
  }
  private func softFeedback() { if haptics { GlowknobHaptics.impact(.soft) } }
  private func stopPlayback() {
    runId += 1
    if mounted { playing = false }
  }

  private func setLevel(_ ch: GlowknobChannel, _ target: Double, _ duration: Double, _ easing: @escaping GlowknobEasingFunction) {
    let level = clamp01(target)
    let g = goal.get()
    goal.set(ch == 0 ? [level, g[1]] : [g[0], level])
    let sv = ch == 0 ? brightness : volume
    if duration > 0 { sv.set(glowknobWithTiming(level, duration: duration, easing: easing)) } else { sv.set(level) }
  }

  private func publishGoal() {
    let g = goal.get()
    publish(g[0], g[1])
  }

  // MARK: Reactions

  private func registerReactions() {
    let rt = GlowknobUIRuntime.shared
    // Endless encoder: grooves turn only when the level changes inside one
    // channel, so switching modes never spins the knob.
    struct ChannelValue: Equatable { var ch: GlowknobChannel; var v: Double }
    rt.reaction({ [unowned self] in ChannelValue(ch: self.channel.get(), v: self.value) }) { [unowned self] next, previous in
      if let previous, next.ch == previous.ch {
        self.dialAngle.set(self.dialAngle.get() + next.v - previous.v)
      }
    }
    // One light tick when a drag reaches 0 or 100; never at drag start.
    rt.reaction({ [unowned self] () -> Int in
      let owner = self.dragOwner.get()
      if owner != 1 && owner != 2 { return 9 }
      let v = self.value
      return v <= 0 ? -1 : v >= 1 ? 1 : 0
    }) { [unowned self] edge, previous in
      if edge != 0 && edge != 9 && previous == 0 { self.edgeFeedback() }
    }
    // Trough haptic: one soft tick in the frame the color transfer starts.
    rt.reaction({ [unowned self] in self.reveal.get() >= TROUGH }) { [unowned self] past, previous in
      if past && previous == false && self.reveal.get() < 0.9 && self.fxs.get().troughHaptic && !self.reduced {
        self.softFeedback()
      }
    }
    // Crown detents: a tick each time a groove passes under the finger or
    // during an inertial spin; at most ~33 ticks a second.
    rt.reaction({ [unowned self] in
      (((self.dialAngle.get() + self.dialGive.get()) * DIAL_TURN) / GROOVE).rounded(.down)
    }) { [unowned self] groove, previous in
      guard let previous, groove != previous, self.fxs.get().crown else { return }
      if self.dragOwner.get() != 2 && !self.spinning.get() { return }
      let now = Date().timeIntervalSince1970 * 1000
      if now - self.lastTick.get() < 30 { return }
      self.lastTick.set(now)
      self.tickFeedback()
    }
    rt.reaction({ [unowned self] in self.replayRunning.get() ? self.pose.phase : -1 }) { [unowned self] phase, previous in
      if phase < 0 || phase == previous { return }
      let frame = self.pose
      let next = frame.mode
      let switching = self.channel.get() != next
      self.channel.set(next)
      // The sampler draws the light; the level glides so the knob turns with
      // it instead of jumping at the phase boundary.
      self.setLevel(next, frame.level, switching || self.reduced ? 0 : GlowknobMotionTokens.hero, easeInOut)
      let g = self.goal.get()
      self.publishReplay(self.replayId.get(), next, g[0], g[1])
    }
    // readout.tsx: GlowknobNumericText only needs the integer, so the frame runtime
    // hands over a value only when it changes. Discrete jumps (tap, step,
    // replay) roll at 240ms; under a dragging finger each step is a short,
    // low slide with no scale or blur so the digits stay legible. A channel
    // swap does not roll: it happens unseen under the fade.
    rt.reaction({ [unowned self] in glowknobJsRound(self.readout) }) { [unowned self] next, previous in
      if next == previous { return }
      let quick = self.numberQuick
      self.shown = GlowknobReadoutShown(value: Int(next), roll: glowknobRollsOnStep(previous, next, quick, self.numberRolls),
                                quick: quick)
    }
  }

  // MARK: Handoff

  private func capture(_ withFill: Bool) {
    from.set(GlowknobHandoffFrame(fill: fill, light: lightAlpha, blur: lightBlur, number: numberAlpha,
                          label: labelAlpha, readout: readout))
    blendFill.set(withFill && !reduced)
    blend.set(1)
    // Opacity only under Reduce Motion; fill movement is excluded above.
    blend.set(glowknobWithTiming(0, duration: HANDOFF, easing: easeInOut, reduceMotion: .never))
  }

  private func cancelVisuals(_ withFill: Bool) {
    let interrupted = replayTime.get() >= 0 || reveal.get() < 1 || blend.get() > 0
    let shownMix = lightMix
    if interrupted { capture(withFill) }
    replayTime.cancel()
    replayRunning.set(false)
    replayTime.set(-1)
    reveal.cancel()
    mix.cancel()
    reveal.set(1)
    mix.set(interrupted ? shownMix : Double(channel.get()))
    if interrupted {
      mix.set(glowknobWithTiming(Double(channel.get()), duration: HANDOFF, easing: easeInOut, reduceMotion: .never))
    }
    selector.set(glowknobWithTiming(Double(channel.get()), duration: GlowknobMotionTokens.fast, easing: GlowknobMotionTokens.easeOut,
                            reduceMotion: .never))
    dragOwner.set(0)
  }

  private func quiet() {
    cancelVisuals(true)
    for sv in [trackPress, dialPress, dialGive] {
      sv.cancel()
      sv.set(0)
    }
  }

  // Feedback on touch-down, before the recognizer activates or a tap completes.
  private func pressIn(_ target: GlowknobAnimatedValue) {
    if !available.get() || reduced { return }
    target.set(glowknobWithTiming(1, duration: GlowknobMotionTokens.press, easing: GlowknobMotionTokens.easeOut))
  }

  private func pressOut(_ target: GlowknobAnimatedValue) {
    if reduced { target.set(0) } else { target.set(glowknobWithSpring(0, GlowknobTactile.release)) }
  }

  private func settle() {
    dragOwner.set(0)
    publishGoal()
  }

  private func begin(_ owner: Int) {
    cancelVisuals(true)
    spinning.set(false)
    fine.set(false)
    magnetHeld.set(false)
    dragOwner.set(owner)
    dragStart.set(value)
    stopPlayback()
    feedback()
  }

  private func update(_ next: Double) {
    if !available.get() { return }
    var level = next
    // Magnet at 50%: snaps within ±1.8% with one light haptic on entry.
    if fxs.get().magnet {
      let near = abs(level - 0.5) < 0.018
      if near { level = 0.5 }
      if near && !magnetHeld.get() { edgeFeedback() }
      magnetHeld.set(near)
    }
    setLevel(channel.get(), level, 0, GlowknobMotionTokens.easeOut)
  }

  // Track position → level, switching to ¼-speed relative mode once the
  // finger drops below the track (fine scrub). Stays fine until release.
  private func trackLevel(_ x: Double, _ dy: Double) -> Double {
    let absolute = glowknobLevelForFill(x / width.get())
    if !fxs.get().fineScrub { return absolute }
    if !fine.get() && dy > FINE_DROP {
      fine.set(true)
      fineAnchor.set(GlowknobFineAnchor(x: x, level: value))
      fineShown.set(glowknobWithTiming(1, duration: GlowknobMotionTokens.fast))
      tickFeedback()
    }
    if !fine.get() { return absolute }
    let anchor = fineAnchor.get()
    return anchor.level + ((x - anchor.x) / width.get()) * 0.25
  }

  // MARK: Track gesture: Race(Pan, Tap)

  func trackPanBegan() { pressIn(trackPress) }

  func trackPanStarted(x: Double, translationY: Double) {
    if available.get() {
      begin(1)
      update(trackLevel(x, translationY))
    }
  }

  func trackPanChanged(x: Double, translationY: Double) {
    if dragOwner.get() == 1 { update(trackLevel(x, translationY)) }
  }

  func trackPanFinalized() {
    pressOut(trackPress)
    if fine.get() {
      fine.set(false)
      fineShown.set(glowknobWithTiming(0, duration: GlowknobMotionTokens.base))
    }
    if dragOwner.get() == 1 { settle() }
  }

  func trackTapEnded(x: Double, success: Bool) {
    if success && available.get() {
      begin(3)
      // A jump to a new point glides; a drag follows the finger exactly.
      setLevel(channel.get(), glowknobLevelForFill(x / width.get()), reduced ? 0 : 180, GlowknobMotionTokens.easeOut)
      settle()
    }
  }

  // MARK: Dial gesture

  func dialPanBegan() { pressIn(dialPress) }

  func dialPanStarted() {
    if available.get() { begin(2) }
  }

  func dialPanChanged(translationX: Double) {
    if dragOwner.get() != 2 { return }
    let raw = dragStart.get() + translationX / DIAL_DRAG
    update(raw)
    dialGive.set(reduced ? 0 : glowknobRubberBand(raw - clamp01(raw), DIAL_GIVE))
  }

  func dialPanEnded(velocityX: Double) {
    // Crown flick: the level keeps turning with the finger's speed and
    // decays, rubber-banding at the ends; detent ticks thin out with it.
    if dragOwner.get() != 2 || !fxs.get().crown || reduced || abs(velocityX) < 250 { return }
    let ch = channel.get()
    spinning.set(true)
    let sv = ch == 0 ? brightness : volume
    sv.set(glowknobWithDecay(GlowknobDecayConfig(deceleration: 0.99, velocity: (velocityX / DIAL_DRAG) * 0.6,
                                 clamp: (0, 1), rubberBandEffect: true, rubberBandFactor: 0.6)) { [unowned self] _ in
      self.spinning.set(false)
      self.setLevel(ch, sv.get(), 0, GlowknobMotionTokens.easeOut)
      self.publishGoal()
    })
  }

  func dialPanFinalized() {
    pressOut(dialPress)
    if dialGive.get() != 0 {
      if reduced { dialGive.set(0) } else { dialGive.set(glowknobWithSpring(0, GlowknobTactile.release)) }
    }
    if dragOwner.get() == 2 { settle() }
  }

  // MARK: Commands

  private func handoff(_ next: GlowknobChannel) {
    let currentFill = fill, currentMix = lightMix, currentNumber = readout
    let changed = channel.get() != next
    // A mode change draws its own contraction; only fade the fill otherwise.
    cancelVisuals(!changed)
    channel.set(next)
    if !changed { return }
    outgoingFill.set(currentFill)
    outgoingNumber.set(currentNumber)
    mix.set(currentMix)
    selector.set(glowknobWithTiming(Double(next), duration: GlowknobMotionTokens.fast, easing: GlowknobMotionTokens.easeOut,
                            reduceMotion: .never))
    // Color feedback is meaningful under Reduce Motion; no movement is
    // retained. Otherwise the color transfers around the trough.
    let color = glowknobWithTiming(Double(next), duration: 200, easing: reduced ? GlowknobMotionTokens.easeOut : easeInOut,
                           reduceMotion: .never)
    mix.set(reduced ? color : glowknobWithDelay(90, color, .never))
    if !reduced {
      reveal.set(0)
      reveal.set(glowknobWithTiming(1, duration: SWITCH, easing: GlowknobEasing.linear))
    }
  }

  func select(_ next: GlowknobChannel) {
    if !enabled { return }
    stopPlayback()
    mode = next
    if !available.get() { return }
    handoff(next)
    publishGoal()
  }

  func adjust(_ delta: Int) {
    if !enabled { return }
    stopPlayback()
    if !available.get() { return }
    cancelVisuals(true)
    let ch = channel.get()
    // Step from the committed level so rapid presses never lose a step.
    let target = (glowknobJsRound(goal.get()[ch] * 100) + Double(delta)) / 100
    setLevel(ch, target, reduced ? 0 : GlowknobMotionTokens.fast, GlowknobMotionTokens.easeOut)
    publishGoal()
  }

  func reset() {
    if !enabled { return }
    stopPlayback()
    mode = 0
    handoff(0)
    setLevel(0, 0.52, reduced ? 0 : GlowknobMotionTokens.base, GlowknobMotionTokens.easeOut)
    setLevel(1, 0.53, 0, GlowknobMotionTokens.easeOut)
    publishGoal()
  }

  func stop() {
    stopPlayback()
    cancelVisuals(true)
    publishGoal()
  }

  func replay() {
    if !enabled { return }
    stopPlayback()
    let id = runId
    playing = true
    mode = 0
    cancelVisuals(true)
    // The live frame fades into the reference intro instead of cutting to it.
    capture(true)
    mix.cancel()
    mix.set(0)
    channel.set(0)
    setLevel(0, 0.52, reduced ? 0 : GlowknobMotionTokens.base, GlowknobMotionTokens.easeOut)
    setLevel(1, 0.53, 0, GlowknobMotionTokens.easeOut)
    replayId.set(id)
    replayTime.set(0)
    replayRunning.set(true)
    // The clock schedules semantic phases. referencePose independently removes
    // movement when reduced motion is requested; the clock must not skip phases.
    replayTime.set(glowknobWithTiming(glowknobReferenceDuration, duration: glowknobReferenceDuration, easing: GlowknobEasing.linear,
                              reduceMotion: .never) { [unowned self] finished in
      if finished {
        self.replayRunning.set(false)
        self.finishReplay(id)
      }
    })
  }

  // Mode selectors sink into their wells on press (90ms) and spring back on
  // release; under Reduce Motion only the darkening remains, instantly.
  func pressSelector(_ mode: GlowknobChannel, _ down: Bool) {
    let sv = mode == 0 ? brightPress : volumePress
    if reduced {
      sv.set(down ? 1 : 0)
    } else if down {
      sv.set(glowknobWithTiming(1, duration: 90, easing: GlowknobMotionTokens.easeOut))
    } else {
      sv.set(glowknobWithSpring(0, GlowknobTactile.release))
    }
  }

  // Power-on: light rises from 0, the number rolls up once, the knob homes
  // from -32° with a soft overshoot, and one soft haptic lands at the top.
  func powerOn() {
    if reduced { return }
    boot.set(0)
    boot.set(glowknobWithDelay(400, glowknobWithTiming(1, duration: BOOT, easing: GlowknobEasing.linear) { [unowned self] finished in
      if finished { self.softFeedback() }
    }))
    dialGive.set(-0.12)
    dialGive.set(glowknobWithDelay(550, glowknobWithSpring(0, .duration(900, dampingRatio: 0.55))))
  }

  /// Runs once per mount; `boot` was seeded at 0 so there is no flash.
  func mount() {
    mounted = true
    if booted { return }
    booted = true
    if fx.powerOn && !reduced { powerOn() }
  }

  /// `enabled = !disabled && focused && foreground` changed, or `reduced` did.
  func setEnvironment(enabled newEnabled: Bool, reduced newReduced: Bool) {
    let changed = newEnabled != enabled || newReduced != reduced
    enabled = newEnabled
    if newReduced != reduced {
      reduced = newReduced
      legibility.reduced = newReduced
      meniscus.setOn(fx.meniscus && !newReduced)
    }
    guard changed else { return }
    available.set(newEnabled)
    if !newEnabled || newReduced {
      stopPlayback()
      quiet()
    }
  }

  func setEffects(_ next: GlowknobEffects) {
    guard next != fx else { return }
    fx = next
    fxs.set(next)
    meniscus.setOn(next.meniscus && !reduced)
  }

  /// The specimen was laid out: the track's width in points.
  func setTrackWidth(_ points: Double) { width.set(points) }
}

/// The opt-in detent click (`fx.sound`, off by default): restarted per tick.
@MainActor
final class GlowknobDetentPlayer {
  private var player: GlowknobAVAudioPlayerShim?
  init() { player = GlowknobAVAudioPlayerShim(resource: "glowknob-detent", ext: "wav") }
  func click() { player?.restart() }
}
