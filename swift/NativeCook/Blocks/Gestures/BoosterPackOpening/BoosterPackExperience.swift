import CoreGraphics
import Foundation
import Observation

/// What the SwiftUI controls over the scene read. It changes at state boundaries and while
/// the controls fade, not on every frame of the opening.
@Observable final class BoosterPackInterface {
  var labelIndex = 0
  var interactive = false
  var opacity = 0.0
  var arrowsVisible = false
  /// Bumped when the vector overlay has something new to draw.
  var overlayFrame = 0
}

/// The scene's state and its one clock. A swipe owns the cut, the geometry owns the
/// material's response, and the time since the cut owns the automatic opening; the renderer,
/// the vector overlay and the sounds all read what `step` leaves here for the frame.
final class BoosterPackExperience {
  private typealias M = BoosterPackModel
  private typealias G = BoosterPackGeometry
  private typealias E = BoosterPackOpeningEffectsMotion
  private typealias R = BoosterPackCardRevealMotion

  /// Seconds in one loop of `autoPlay`.
  static let loop = 15.0

  private static let BODY_TIMES = M.BODY_KEYFRAMES.map(\.ms)
  private static let BODY_SEAM_Y = M.BODY_KEYFRAMES.map(\.seamY)
  private static let BODY_SCALE = M.BODY_KEYFRAMES.map(\.scale)
  private static let CARD_TIMES = M.CARD_KEYFRAMES.map(\.ms)
  private static let CARD_OPACITY = M.CARD_KEYFRAMES.map(\.opacity)

  static let TRACE_THICKNESS_SCALE = 0.85
  private static let DIM_PROGRESS: [Double] = [0, 0.1175, 0.44, 0.52, 0.72, 0.915, 1]
  private static let DIM_OPACITY: [Double] = [0, 0.21, 0.374, 0.456, 0.62, 0.7, 0.7]
  private static let BACKDROP_REVEAL_LEAD_MS = 160.0
  private static let BACKDROP_FADE_DURATION_MS = 320.0
  private static let BACKDROP_ZOOM_DURATION_MS = 2_600.0

  let interface = BoosterPackInterface()
  let cards = BoosterPackCardReveal()
  let audio = BoosterPackAudio()
  private let haptics = BoosterPackHaptics()

  // Layout.
  private(set) var width = 0.0
  private(set) var height = 0.0
  private(set) var pack = BoosterPackRect.zero
  private var settledBodyTexture = BoosterPackRect.zero
  private(set) var seamY = 0.0

  // Configuration.
  private(set) var autoPlay = false
  private(set) var muted = false
  private(set) var reduceMotion = false
  private(set) var focused = false

  // The cut.
  private(set) var points: [CGPoint] = []
  private var previewBottomMask = G.makeEmptyPath()
  private var previewMesh = BoosterPackMesh(vertices: [], textures: [])
  private var peelVisible = 0.0
  private var foilMesh = BoosterPackMesh(vertices: [], textures: [])
  private(set) var split = 0.0
  private(set) var committed = false
  private(set) var active = false
  private(set) var direction = 1.0
  private let cutProgress = BoosterPackAnimatedValue(0)
  private let traceOpacity = BoosterPackAnimatedValue(0)
  private let dimOpacity = BoosterPackAnimatedValue(0)
  let postCutMs = BoosterPackAnimatedValue(0)
  private var audioMovementUntil = -Double.infinity
  private let backdropOpacity = BoosterPackAnimatedValue(0)
  private let backdropZoom = BoosterPackAnimatedValue(0)

  // The idle loop of an untouched pack.
  private var idleStart = 0.0
  private var idlePhase = 0.0
  private let idleMix = BoosterPackAnimatedValue(0)

  // Watchers remember what they last saw, so each transition is handled once.
  private var backdropVisible: Bool?
  private var tearAudioMoving = false
  private var idleRunning: Bool?
  private var epicFaceLanded: Bool?
  private var drawnMarks: [Double] = []

  // Sparks thrown from the tear.
  private var sparks: [BoosterPackTearSpark] = []
  private var emitter = (x: 0.0, y: 0.0, tracking: false, budget: 0.0, seed: 0.0)

  // The backdrop behind the revealed cards.
  private var backdropRotation = 0.0
  private var particlePhase = 0.0

  // The clock.
  private(set) var now = 0.0
  private var previousNow: Double?

  // The scripted loop.
  private var loopStart: Double?
  private var scriptRunning = false
  private var scriptStage = 0
  private var touching = false
  private var lastTouch = -Double.infinity

  /// Everything the frame's drawing needs, written once per `step`.
  private(set) var scene = BoosterPackScene()

  private var liveEffects: Bool { !reduceMotion }
  private var soundEnabled: Bool { focused && !muted && !autoPlay }

  init() {
    cards.postCutMs = { [unowned self] in self.postCutMs.value }
    cards.now = { [unowned self] in self.now }
    cards.playNextCard = { [unowned self] index in self.audio.beginCardAdvance(index) }
    cards.playCardReveal = { [unowned self] index, rarity in self.audio.revealCard(index, rarity) }
    cards.cardHaptic = { [unowned self] in if self.touchDriven { self.haptics.selection() } }
  }

  /// Sounds and haptics answer a finger; the scripted loop is silent and still.
  private var touchDriven: Bool { !scriptRunning }

  // MARK: Configuration

  func configure(autoPlay: Bool, muted: Bool, reduceMotion: Bool) {
    let restart = autoPlay != self.autoPlay
    self.autoPlay = autoPlay
    self.muted = muted
    self.reduceMotion = reduceMotion
    cards.reducedMotion = reduceMotion
    audio.setEnabled(soundEnabled)
    if restart { restartLoop() }
  }

  /// The block is on screen with the app in the foreground.
  func setFocused(_ next: Bool) {
    if next == focused { return }
    focused = next
    cards.enabled = next
    if !next {
      cards.suspend()
      previousNow = nil
    } else if autoPlay && width > 0 {
      // The loop is timed from its start, so after an absence it begins again.
      reset()
      restartLoop()
    }
    audio.setEnabled(soundEnabled)
  }

  func setLoaded(_ loaded: Bool) { cards.loaded = loaded }

  func layout(width: Double, height: Double) {
    if width == self.width && height == self.height { return }
    self.width = width
    self.height = height
    cards.width = width
    cards.height = height
    let packWidth = width * M.REFERENCE_LAYOUT.pack.width
    // The reference video applies a small non-uniform viewport scale to the pack.
    // Preserve that measured silhouette instead of stretching the artwork.
    let packHeight = packWidth * M.REFERENCE_LAYOUT.pack.sourceAspect * M.REFERENCE_LAYOUT.pack.viewportHeightScale
    pack = BoosterPackRect(
      x: width * M.REFERENCE_LAYOUT.pack.centerX - packWidth / 2,
      y: height * M.REFERENCE_LAYOUT.pack.top, width: packWidth, height: packHeight)
    // The moving body in the reference settles back toward the package's native
    // aspect ratio after the first separation beat.
    let settledWidth = width * M.REFERENCE_LAYOUT.pack.settledBodyWidth
    settledBodyTexture = BoosterPackRect(
      x: width * M.REFERENCE_LAYOUT.pack.centerX - settledWidth / 2,
      y: height * M.REFERENCE_LAYOUT.pack.top,
      width: settledWidth, height: settledWidth * M.REFERENCE_LAYOUT.pack.sourceAspect)
    seamY = height * M.REFERENCE_LAYOUT.seamY
    reset()
    restartLoop()
  }

  // MARK: The cut

  private func updatePreviewGeometry(_ sourcePoints: [CGPoint], _ swipeDirection: Double) {
    let completed = G.completePreviewCutPoints(sourcePoints, pack, swipeDirection, seamY)
    previewBottomMask = G.makeBottomMask(completed, pack)
    previewMesh = G.makePreviewFoilMesh(completed, pack)
  }

  /// Back to the sealed pack. The ambience keeps playing.
  func reset() {
    cards.reset()
    backdropOpacity.set(0)
    backdropZoom.set(0)
    audioMovementUntil = -.infinity
    audio.reset()
    points = []
    previewBottomMask = G.makeEmptyPath()
    previewMesh = G.makeCollapsedPreviewFoilMesh(pack)
    peelVisible = 0
    foilMesh = G.makeCollapsedFoilMesh(pack)
    split = 0
    committed = false
    active = false
    direction = 1
    cutProgress.set(0)
    traceOpacity.set(0)
    dimOpacity.set(0)
    postCutMs.set(0)
  }

  private func commitCut(_ sourcePoints: [CGPoint], _ swipeDirection: Double) {
    audioMovementUntil = -.infinity
    let completed = G.completeCutPoints(sourcePoints, pack, swipeDirection)

    points = completed
    previewBottomMask = G.makeBottomMask(completed, pack)
    previewMesh = G.makePreviewFoilMesh(completed, pack)
    foilMesh = G.makeFoilMesh(completed, pack)
    active = false
    cutProgress.set(1)
    committed = true
    // The release is a single pose boundary hidden by the optical burst.
    split = 1
    peelVisible = 0
    // Keep the last folded pose under the first flash frame. Flattening the
    // cap at this boundary exposes a rectangular corner absent in the video.
    dimOpacity.set(0)
    postCutMs.set(0)
    // This clock also gates replay. Motion is reduced by each visual layer,
    // without skipping the session's elapsed time.
    postCutMs.timing(to: M.OPENING_DURATION_MS, duration: M.OPENING_DURATION_MS, now: now)
    traceOpacity.timing(to: 0, duration: 180, easing: .easeOut, delay: 100, now: now)
    if touchDriven { haptics.cut() }
    audio.commitCut()
  }

  func updateCut(_ position: CGPoint) {
    if !active { return }

    guard let next = BoosterPackTearGesture.advanceTearPoints(points, position, pack, direction) else { return }

    let head = next[next.count - 1]
    let progress = M.clamp(
      direction == 1 ? (head.x - pack.x) / pack.width : (pack.x + pack.width - head.x) / pack.width, 0, 1)

    // Finger position drives the whole cut, including the last few millimetres.
    // No timing animation or velocity prediction may advance an unfinished tear.
    let advanced = progress > cutProgress.value
    points = next
    cutProgress.set(progress)
    let reachedEdge = direction == 1 ? head.x >= pack.x + pack.width : head.x <= pack.x
    if reachedEdge {
      commitCut(next, direction)
      return
    }

    // The tear sounds for 100 ms after the frontier last moved.
    if advanced { audioMovementUntil = now + 100 }

    updatePreviewGeometry(next, direction)
    dimOpacity.set(M.interpolateKeyframes(progress, Self.DIM_PROGRESS, Self.DIM_OPACITY))
  }

  /// Shared entry point for the real gesture and the scripted loop.
  func beginCut(_ event: CGPoint) {
    let corridorTop = pack.y + pack.height * 0.08
    let corridorBottom = pack.y + pack.height * 0.27
    let edgeSlop = pack.width * 0.13
    let nearLeft = event.x >= pack.x - edgeSlop && event.x <= pack.x + edgeSlop
    let nearRight = event.x >= pack.x + pack.width - edgeSlop && event.x <= pack.x + pack.width + edgeSlop
    let insideCorridor = event.y >= corridorTop && event.y <= corridorBottom

    if (!nearLeft && !nearRight) || !insideCorridor {
      active = false
      return
    }

    let nextDirection: Double = nearLeft ? 1 : -1
    let y = M.clamp(event.y, corridorTop, corridorBottom)
    let boundaryX = nextDirection == 1 ? pack.x : pack.x + pack.width
    let currentX = M.clamp(event.x, pack.x, pack.x + pack.width)
    let initialPoints = [CGPoint(x: boundaryX, y: y), CGPoint(x: currentX, y: y)]

    audioMovementUntil = -.infinity
    audio.beginTear()
    // A second stroke may begin before the cancelled stroke has faded.
    // Cancel its cleanup before it can erase the new cut path.
    direction = nextDirection
    active = true
    peelVisible = 1
    points = initialPoints
    updatePreviewGeometry(initialPoints, nextDirection)
    let initialProgress = abs(currentX - boundaryX) / pack.width
    cutProgress.set(initialProgress)
    traceOpacity.set(1)
    dimOpacity.set(M.interpolateKeyframes(initialProgress, Self.DIM_PROGRESS, Self.DIM_OPACITY))
  }

  /// An unfinished tear springs back and its trace fades.
  private func cancelCut(velocityX: Double) {
    if !active { return }
    active = false
    audioMovementUntil = -.infinity
    audio.reset()
    let returnVelocity = -abs(velocityX / pack.width)
    cutProgress.spring(to: 0, duration: 400, dampingRatio: 0.8, velocity: returnVelocity, now: now) { [weak self] in
      guard let self else { return }
      self.peelVisible = 0
      self.previewBottomMask = G.makeEmptyPath()
      self.previewMesh = G.makeCollapsedPreviewFoilMesh(self.pack)
    }
    dimOpacity.timing(to: 0, duration: 180, easing: .easeOut, now: now)
    traceOpacity.timing(to: 0, duration: 150, now: now) { [weak self] in self?.points = [] }
  }

  // MARK: Touches

  /// Touches arrive between frames; animations they start are timed from when they happened.
  func touchTime(_ timestamp: Double) {
    now = max(now, timestamp)
  }

  private func noteTouch() {
    scriptRunning = false
    lastTouch = now
  }

  /// A finger came down: on a sealed pack it may start a tear, on the stack a tilt.
  func panBegan(_ event: BoosterPackDragEvent) {
    touching = true
    noteTouch()
    if split > 0.5 {
      cards.begin(event)
      return
    }
    beginCut(CGPoint(x: event.x, y: event.y))
  }

  func panChanged(_ event: BoosterPackDragEvent) {
    noteTouch()
    if split > 0.5 {
      cards.update(event)
      return
    }
    updateCut(CGPoint(x: event.x, y: event.y))
  }

  /// The finger lifted. `moved` says the touch travelled far enough to count as a pan,
  /// `cancelled` that the system took it away.
  func panEnded(_ event: BoosterPackDragEvent, moved: Bool, cancelled: Bool) {
    touching = false
    noteTouch()
    if moved {
      if cards.dragging {
        cards.end(event, !cancelled)
      } else if !cancelled {
        // A fast swipe can deliver the edge crossing with its final event.
        // Cancellation must never turn that final coordinate into a cut.
        updateCut(CGPoint(x: event.x, y: event.y))
      }
    }
    cards.cancel()
    cancelCut(velocityX: event.velocityX)
  }

  /// A tap on the top card reveals the next one.
  func tapped(_ point: CGPoint) {
    noteTouch()
    let halfWidth = width * M.REFERENCE_LAYOUT.card.width / 2
    let halfHeight = height * M.REFERENCE_LAYOUT.card.height / 2
    if split > 0.5 && abs(point.x - width / 2) <= halfWidth && abs(point.y - height / 2) <= halfHeight {
      cards.advance()
    }
  }

  /// One of the two arrows beside the stack.
  func advanceCard(_ direction: Double) {
    noteTouch()
    cards.advance(direction)
  }

  /// "Open another pack".
  func replay() {
    noteTouch()
    reset()
  }

  // MARK: The scripted loop

  private func restartLoop() {
    scriptRunning = autoPlay
    scriptStage = 0
    loopStart = nil
  }

  /// Where the scripted finger is, `progress` of the way along the reference trace. The trace
  /// is stretched over the pack so it enters at the left edge and leaves just past the right.
  private func scriptedFinger(_ progress: Double) -> CGPoint {
    let trace = M.REFERENCE_LAYOUT.trace.self
    let point = G.referenceTracePoint(progress, width, height)
    let along = (point.x / width - trace.startX) / (trace.endX - trace.startX)
    return CGPoint(x: pack.x + (pack.width + 2) * along, y: point.y)
  }

  /// Plays the loop through the same entry points a finger uses, timed from the start of the
  /// loop so that each period is exactly `loop` seconds.
  private func stepScript() {
    guard autoPlay, width > 0 else { return }
    if !scriptRunning {
      // 2.5 s after the last touch the block goes back to the sealed pack and plays again.
      if touching || now - lastTouch < 2500 { return }
      reset()
      restartLoop()
    }
    if loopStart == nil {
      loopStart = now
      // An untouched pack is partway through its idle cycle when a loop begins, as it is
      // 1.4 s after the reset that ends the loop before.
      idleStart = now - (Self.loop - 13.6) * 1000
      idleMix.set(liveEffects ? 1 : 0)
    }
    guard var start = loopStart else { return }
    if now - start >= Self.loop * 2000 {
      // The clock stood still for a while: begin again rather than catch up.
      reset()
      restartLoop()
      return
    }
    if now - start >= Self.loop * 1000 {
      start += Self.loop * 1000
      loopStart = start
      scriptStage = 0
    }
    let t = (now - start) / 1000

    let tearStart = 1.0
    let tearEnd = 2.1
    if scriptStage == 0 && t >= tearStart {
      scriptStage = 1
      beginCut(scriptedFinger(0))
    }
    if scriptStage == 1 {
      // The finger keeps the pace of the reference trace: a slow start, then a steady pull.
      let keys = M.TRACE_PROGRESS_KEYFRAMES
      let frame = keys[0].0 + (keys[keys.count - 2].0 - keys[0].0) * M.clamp((t - tearStart) / (tearEnd - tearStart), 0, 1)
      updateCut(scriptedFinger(t >= tearEnd ? 1 : M.traceProgressAtFrame(frame)))
      if t >= tearEnd { scriptStage = 2 }
    }
    for (stage, time) in [5.2, 6.3, 7.4, 10.4].enumerated() where scriptStage == stage + 2 && t >= time {
      scriptStage += 1
      cards.advance()
    }
    if scriptStage == 6 && t >= 13.6 {
      scriptStage = 7
      reset()
    }
  }

  // MARK: The frame

  /// Advances the clock to `timestamp` (milliseconds) and prepares the frame.
  func step(_ timestamp: Double) {
    let delta = min(timestamp - (previousNow ?? timestamp), 50)
    previousNow = timestamp
    now = timestamp
    guard width > 0, height > 0 else { return }

    stepScript()

    cutProgress.step(now)
    traceOpacity.step(now)
    dimOpacity.step(now)
    postCutMs.step(now)
    backdropOpacity.step(now)
    backdropZoom.step(now)
    idleMix.step(now)
    cards.step(now)

    watch()
    stepSparks(delta / 1000)
    stepBackdrop(delta)
    scene = makeScene()
    publishInterface()
  }

  /// Rebuilds the scene without advancing the clock.
  func refreshScene() {
    guard width > 0, height > 0 else { return }
    scene = makeScene()
  }

  private func watch() {
    let backdropRevealTime = reduceMotion
      ? M.POST_CUT_DURATION_MS
      : M.POST_CUT_DURATION_MS + BoosterPackCardMotion.CARD_CENTERING_DURATION_MS - Self.BACKDROP_REVEAL_LEAD_MS
    let visible = committed && postCutMs.value >= backdropRevealTime
    if visible != backdropVisible {
      backdropVisible = visible
      if !visible {
        backdropOpacity.set(0)
        backdropZoom.set(0)
      } else {
        backdropOpacity.timing(
          to: 1, duration: reduceMotion ? 180 : Self.BACKDROP_FADE_DURATION_MS, easing: .easeOut, now: now)
        if reduceMotion {
          backdropZoom.set(1)
        } else {
          backdropZoom.timing(to: 1, duration: Self.BACKDROP_ZOOM_DURATION_MS, easing: .easeOut, now: now)
        }
      }
    }

    // Only movement START and STOP reach the player; a long hold stays silent.
    let moving = focused && active && now < audioMovementUntil
    if moving != tearAudioMoving {
      tearAudioMoving = moving
      audio.setTearing(moving)
    }

    // The idle loop runs only while an untouched pack is on screen.
    let idle = !active && !committed && peelVisible == 0
    let running = liveEffects && focused && idle
    if running != idleRunning {
      idleRunning = running
      if !running {
        // Hold the phase while fading, so a sheen never jumps as a tear begins.
        idleMix.timing(to: 0, duration: 160, easing: .outQuad, now: now)
      } else if !(scriptRunning && idleMix.value == 1 && !idleMix.isAnimating) {
        idleStart = now
        idleMix.timing(to: 1, duration: 420, easing: .outQuad, now: now)
      }
    }
    if running {
      idlePhase = ((now - idleStart) / E.IDLE_CYCLE_MS).truncatingRemainder(dividingBy: 1)
    }

    cards.react()

    // The epic face lands with its foil flash.
    let landed = cards.ready && R.isEpicCard(cards.index)
      && cards.revealMs.value >= 1250 && cards.revealMs.value < R.EPIC_REVEAL_MS
    if landed && epicFaceLanded == false && touchDriven { haptics.success() }
    epicFaceLanded = landed
  }

  private func stepSparks(_ seconds: Double) {
    let enabled = liveEffects && focused
    guard enabled, active || !sparks.isEmpty else {
      if !enabled { sparks = [] }
      emitter.tracking = false
      emitter.budget = 0
      return
    }
    let spacing = width * 0.017
    var next: [BoosterPackTearSpark] = []
    for spark in sparks {
      if let stepped = E.stepTearSpark(spark, seconds, width) { next.append(stepped) }
    }

    if active, points.count > 1, let head = points.last {
      if emitter.tracking {
        // Emission follows distance. Holding the finger still emits nothing.
        emitter.budget = min(emitter.budget + hypot(head.x - emitter.x, head.y - emitter.y) / spacing, 6)
        let count = Int(emitter.budget)
        for index in 0..<count {
          emitter.seed += 1
          if next.count >= E.MAX_TEAR_SPARKS { continue }
          let along = Double(index + 1) / Double(count)
          next.append(E.spawnTearSpark(
            CGPoint(x: emitter.x + (head.x - emitter.x) * along, y: emitter.y + (head.y - emitter.y) * along),
            emitter.seed, direction, width))
        }
        emitter.budget -= Double(count)
      }
      emitter.x = head.x
      emitter.y = head.y
      emitter.tracking = true
    } else {
      emitter.tracking = false
      emitter.budget = 0
    }
    sparks = next
  }

  private func stepBackdrop(_ delta: Double) {
    // The plane turns and the particles rise only while the backdrop shows.
    guard focused, !reduceMotion, backdropOpacity.value > 0 else { return }
    backdropRotation -= 360 * delta / BoosterPackBackdrop.ROTATION_DURATION
    particlePhase += delta
  }

  private func publishInterface() {
    let last = cards.index == R.CARD_COUNT - 1
    let interactive = cards.ready && !cards.busy
    let opacity = cards.opacity
    let arrows = !(cards.dragging || cards.busy || last)
    if interface.labelIndex != cards.index { interface.labelIndex = cards.index }
    if interface.interactive != interactive { interface.interactive = interactive }
    if interface.opacity != opacity { interface.opacity = opacity }
    if interface.arrowsVisible != arrows { interface.arrowsVisible = arrows }
    // The overlay redraws while something on it moves, and once more to clear it.
    let marks = scene.marks.map { $0.epic ? -$0.opacity : $0.opacity }
    if scene.overlayActive || scene.overlayWasActive || marks != drawnMarks {
      drawnMarks = marks
      interface.overlayFrame &+= 1
    }
  }

  // MARK: The scene

  private func makeScene() -> BoosterPackScene {
    var s = BoosterPackScene()
    let t = postCutMs.value
    let progress = cutProgress.value
    s.width = width
    s.height = height
    s.pack = pack
    s.seamY = seamY
    s.direction = direction
    s.reducedMotion = reduceMotion
    s.committed = committed
    s.postCutMs = t
    s.punch = liveEffects && committed ? E.commitPunchScale(t) : 1

    // Pack geometry reaches its final pose at 1000 ms. The card and the session
    // clock continue to 2000.
    let geometryMs = min(t, M.POST_CUT_DURATION_MS)

    s.backgroundMorph = M.clamp(t / 133.333, 0, 1)
    s.dimOpacity = dimOpacity.value
    let previewMix = peelVisible * M.clamp((progress - 0.14) / 0.08, 0, 1)
    s.previewOpacity = (1 - split) * previewMix
    s.intactOpacity = (1 - split) * (1 - previewMix)
    s.flatFoilOpacity = split * (1 - M.clamp(t / 33.333, 0, 1))
    s.meshOpacity = split * M.clamp(t / 33.333, 0, 1) * (1 - M.clamp((t - 920) / 80, 0, 1))

    let bodyMix = M.clamp((geometryMs - 66.667) / 83.333, 0, 1)
    // Interpolate geometry of one opaque image. Crossfading two images
    // would let the background through at the halfway point.
    s.bodyTextureRect = BoosterPackRect(
      x: pack.x + (settledBodyTexture.x - pack.x) * bodyMix,
      y: pack.y,
      width: pack.width + (settledBodyTexture.width - pack.width) * bodyMix,
      height: pack.height + (settledBodyTexture.height - pack.height) * bodyMix)
    if reduceMotion {
      s.bodyOpacity = split * (1 - M.clamp((t - 650) / 200, 0, 1))
    } else {
      // The video ends with a little of the package lip still at the bottom.
      // Clear that remnant while the card moves into its final presentation.
      s.bodyOpacity = split * (1 - M.clamp((t - M.POST_CUT_DURATION_MS) / 180, 0, 1))
    }
    s.bodyDeglareStrength = split * M.clamp((t - 16.667) / 100, 0, 1)
    s.visibleTraceOpacity = committed ? 0 : traceOpacity.value
    s.edgeOpacity = split * (1 - M.clamp((t - 900) / 200, 0, 1))
    let mix = idleMix.value
    s.packAuraOpacity = 0.36 * (1 + 0.3 * mix * E.idleAuraBreath(idlePhase))
      * (1 - split * M.clamp(t / 350, 0, 1))
    let sheen = E.idleSheen(idlePhase, pack)
    s.sheenStart = sheen.start
    s.sheenEnd = sheen.end
    s.sheenOpacity = mix * sheen.opacity
    let hint = E.idleSeamHint(idlePhase, pack)
    s.seamHintOpacity = mix * hint.opacity
    s.seamHintHead = hint.head
    s.seamHintTail = hint.tail

    let mouthDepth = width * M.interpolateKeyframes(geometryMs,
      [0, 66.667, 100, 150, 300, 433.333, 633.333, 766.667, 833.333, 900, 1000],
      [0.0065, 0.009, 0.015, 0.025, 0.042, 0.049, 0.052, 0.059, 0.061, 0.063, 0.067])
    let mouthDepthExponent = M.interpolateKeyframes(geometryMs,
      [0, 150, 300, 433.333, 550, 633.333, 766.667, 1000],
      [1.3, 1.3, 1.7, 2, 1.55, 1.3, 1.3, 1.3])
    let mouthEntryOffset = width * M.interpolateKeyframes(geometryMs,
      [0, 150, 300, 433.333, 633.333, 766.667, 833.333, 900, 1000],
      [0, 0, 0.002, 0.01, 0.0073, -0.006, 0.002, 0.016, 0.020])
    let mouthExitOffset = width * M.interpolateKeyframes(geometryMs,
      [0, 150, 300, 433.333, 633.333, 766.667, 833.333, 900, 1000],
      [0, 0.035, 0.037, 0.061, 0.061, 0.051, 0.061, 0.084, 0.090])
    let mouthRelaxationExponent = M.interpolateKeyframes(geometryMs,
      [0, 300, 433.333, 633.333, 766.667, 833.333, 1000],
      [1.4, 1.65, 1.7, 1.5, 1.3, 1.2, 1.2])
    let mouthCenterLift = width * M.interpolateKeyframes(geometryMs,
      [0, 300, 433.333, 633.333, 766.667, 833.333, 900, 1000],
      [0, 0, -0.008, -0.008, -0.014, -0.010, -0.016, -0.012])
    s.cavityStrength = M.interpolateKeyframes(geometryMs, [0, 66.667, 100, 150], [0.12, 0.38, 0.82, 1])
    s.cavityOpacity = s.bodyOpacity * s.cavityStrength

    // The renderer and reflections consume the same pose. Changing motion or
    // pausing it cannot leave an independent lighting animation running.
    if reduceMotion {
      s.bodyPose = BoosterPackLightingPose(
        originX: width * 0.5, originY: seamY, translateX: 0,
        translateY: height * 0.015 * M.clamp(geometryMs / 220, 0, 1), scale: 1, rotation: 0)
    } else {
      let desiredSeam = M.interpolateKeyframes(geometryMs, Self.BODY_TIMES, Self.BODY_SEAM_Y) * height
      let scale = M.interpolateKeyframes(geometryMs, Self.BODY_TIMES, Self.BODY_SCALE)
      s.bodyPose = BoosterPackLightingPose(
        originX: width * 0.5, originY: seamY, translateX: 0,
        translateY: desiredSeam - seamY, scale: scale, rotation: 0)
    }

    s.cardTop = BoosterPackCardMotion.cardTopAtTime(t, reduceMotion) * height
    s.cardOpacity = reduceMotion
      ? M.clamp((t - 700) / 200, 0, 1)
      : M.interpolateKeyframes(t, Self.CARD_TIMES, Self.CARD_OPACITY)
    s.cardContactShadowOpacity = s.bodyOpacity * s.cardOpacity * s.cavityStrength * 0.36
    s.cardShadowOpacity = s.cardOpacity * 0.46
    s.riseLightOpacity = liveEffects ? E.cardRiseLightAt(t) : 0

    // The fold: the measured profile projected onto the user's own cut.
    s.previewBottomMask = previewBottomMask
    s.previewFoilVertices = BoosterPackFoldMotion.deformPreviewFoil(previewMesh.vertices, pack, progress, direction)
    s.previewMeshTextures = previewMesh.textures
    if s.previewOpacity > 0 || s.flatFoilOpacity > 0 {
      s.fold = BoosterPackMaterials.foldUniforms(
        s.previewFoilVertices, previewMesh.textures, pack, direction, reduceMotion)
    }
    let rows = G.PREVIEW_MESH_ROWS
    s.flashPacketMask = G.makeBottomMask(
      stride(from: 0, to: s.previewFoilVertices.count, by: rows).map { s.previewFoilVertices[$0] }, pack)

    // The strip that flies off keeps the torn edge it was cut with.
    s.meshTextures = foilMesh.textures
    s.foilVertices = reduceMotion
      ? foilMesh.vertices
      : BoosterPackFoilMotion.deformFlyingFoil(foilMesh.vertices, pack, width, height, geometryMs)
    if s.meshOpacity > 0 {
      s.foil = BoosterPackMaterials.foilUniforms(
        s.foilVertices, foilMesh.textures, pack, geometryMs, width, height, reduceMotion)
      s.foilEdge = stride(from: 1, to: s.foilVertices.count, by: 2).map { s.foilVertices[$0] }
    }

    if committed && s.bodyOpacity > 0 {
      // The mouth and body are invisible during tracing, so they are built from the
      // committed cut only.
      let mouthRestPoints = G.relaxMouthPoints(
        points, pack, mouthEntryOffset, mouthExitOffset, mouthRelaxationExponent, mouthCenterLift, direction)
      let curl = width * M.interpolateKeyframes(geometryMs,
        [0, 433, 633, 767, 833, 900, 1000], [0, 0, 0.005, 0.015, 0.02, 0.032, 0.035])
      let relaxedMouthPoints = G.curlMouthPoints(mouthRestPoints, pack, curl, direction)
      // Registered print landmarks sit 2–4 px lower at f150/f162/f178. Adjust
      // the substrate UVs without moving the already aligned torn silhouette.
      let offset = height * M.interpolateKeyframes(geometryMs,
        [0, 150, 433.333, 633.333, 766.667, 850, 900, 1000],
        [0, 0.0041, 0.005, 0.0049, 0.0113, 0.0113, 0.003, 0])
      s.body = G.makeBodyMaterialMesh(
        relaxedMouthPoints, pack, seamY + width * 0.014, M.clamp((geometryMs - 16.667) / 100, 0, 1), offset)
      s.postCutBottomMask = G.makeCavityBottomMask(relaxedMouthPoints, pack)
      s.entryBodyWedgePath = G.makeEntryBodyWedge(relaxedMouthPoints, pack, direction)
      s.mouthFrontEdgePath = G.makeCavityFrontEdge(relaxedMouthPoints)
      let lipThickness = width * M.interpolateKeyframes(geometryMs,
        [0, 150, 433, 767, 900, 1000], [0.011, 0.014, 0.019, 0.021, 0.021, 0.020])
      let mouthSurface = G.makeMouthWallMesh(
        relaxedMouthPoints, mouthRestPoints, pack, mouthDepth, direction, mouthDepthExponent)
      s.mouthLighting = BoosterPackMouthLighting.deriveMouthLighting(.init(
        vertices: mouthSurface.vertices, textures: mouthSurface.textures, pose: s.bodyPose,
        opening: mouthDepth, width: width, height: height, reducedMotion: reduceMotion))
      s.cavitySpecularOpacity = s.mouthLighting.liningLight * 0.94
      s.mouthSurfaceVertices = G.expandMouthMesh(mouthSurface.vertices, G.MOUTH_WALL_ROWS, width * 0.0015)
      s.mouthSurfaceOutline = G.makeMouthMeshOutline(mouthSurface.vertices, G.MOUTH_WALL_ROWS)
      s.mouthSurfaceTextures = mouthSurface.textures
      let rimSurface = G.makeMouthMaterialMesh(
        relaxedMouthPoints, pack, lipThickness, direction, 1, true, width * 0.0015)
      s.rimSurfaceVertices = G.expandMouthMesh(rimSurface.vertices, 2, width * 0.003)
      s.rimSurfaceOutline = G.makeMouthMeshOutline(rimSurface.vertices, 2, width * 0.0005)
      s.rimSurfaceTextures = rimSurface.textures

      let u = s.mouthLighting.liningCenter
      let x = pack.x + pack.width * (direction == 1 ? u : 1 - u)
      var y = seamY
      for i in 1..<relaxedMouthPoints.count where x <= relaxedMouthPoints[i].x {
        let a = relaxedMouthPoints[i - 1]
        let b = relaxedMouthPoints[i]
        let f = M.clamp((x - a.x) / max(b.x - a.x, 0.001), 0, 1)
        y = a.y + (b.y - a.y) * f
        break
      }
      s.mouthGlowCenter = CGPoint(x: x, y: y)
    }

    // The trace under the finger.
    let traceSize = width * Self.TRACE_THICKNESS_SCALE
    s.traceSize = traceSize
    if s.visibleTraceOpacity > 0 {
      let traceBodyWidth = traceSize * 0.072
      let traceCoreWidth = traceSize * 0.046
      s.traceBodyPath = G.makeSmoothPath(G.trimTraceEnd(points, traceBodyWidth * 0.5 - traceSize * 0.003))
      s.traceCorePath = G.makeSmoothPath(G.trimTraceEnd(points, traceCoreWidth * 0.5))
      // Keep the material transition fixed to the pack.
      s.traceGradientStart = CGPoint(x: direction == 1 ? pack.x : pack.x + pack.width, y: seamY)
      s.traceGradientEnd = CGPoint(x: direction == 1 ? pack.x + pack.width : pack.x, y: seamY)
      let extension_ = pack.width * M.interpolateKeyframes(progress, [0, 0.72, 1], [0, 0.014, 0.06])
      s.traceCoreGradientEnd = CGPoint(
        x: direction == 1 ? pack.x + pack.width + extension_ : pack.x - extension_, y: seamY)
    }
    s.sparks = liveEffects && focused ? sparks : []
    s.burstLive = liveEffects && committed

    // The backdrop and the stack of revealed cards.
    s.backdropOpacity = backdropOpacity.value
    s.backdropZoom = backdropZoom.value
    s.backdropRotation = backdropRotation
    s.particlePhase = particlePhase
    makeCards(&s)

    s.overlayActive = s.visibleTraceOpacity > 0.001 || s.seamHintOpacity > 0.001 || !s.sparks.isEmpty
      || (committed && t < E.BURST_DURATION_MS + 20)
    s.overlayWasActive = scene.overlayActive
    return s
  }

  private func makeCards(_ s: inout BoosterPackScene) {
    s.cardsOpacity = backdropOpacity.value > 0.001 ? 1 : 0
    guard s.cardsOpacity > 0 else { return }
    let cardWidth = width * M.REFERENCE_LAYOUT.card.width
    let cardHeight = height * M.REFERENCE_LAYOUT.card.height
    let index = cards.index
    let exit = cards.exit.value
    let revealMs = cards.revealMs.value
    let still = R.epicCardPose(2600, true)

    s.auraOpacity = R.isEpicCard(index) ? R.epicCardPose(revealMs, reduceMotion).light * (1 - exit) : 0
    s.sparkleOpacity = (0..<18).map { id in
      if reduceMotion || !R.isEpicCard(index) { return 0 }
      return R.epicCardPose(revealMs).light * pow(max(0, sin(revealMs / 170 + Double(id) * 2.1)), 4) * 0.9
    }

    s.cards = (0..<R.CARD_COUNT).map { cardIndex in
      var card = BoosterPackSceneCard()
      let relative = cardIndex - index
      let leaving = relative == 0 && cards.exiting
      let epic = relative == 0 && R.isEpicCard(cardIndex) && !leaving
        ? R.epicCardPose(revealMs, reduceMotion) : still
      let arrival = relative == 0 && !leaving && !R.isEpicCard(cardIndex)
        ? R.commonCardArrival(revealMs, reduceMotion).pop : 0
      let tiltMix = relative > 0 ? 1 - exit : 1
      let yaw = cards.yaw.value * tiltMix
      let pitch = cards.pitch.value * tiltMix
      let depth = Double(max(0, relative)) * cardWidth * R.CARD_STACK_DEPTH
      let pose = BoosterPackCardPose(
        x: width / 2 - sin(yaw) * depth
          + (leaving && !reduceMotion ? cards.exitDirection * width * 1.22 * exit : 0),
        y: s.cardTop + cardHeight / 2 + sin(pitch) * depth
          - (leaving && !reduceMotion ? sin(exit * Double.pi) * cardWidth * 0.025 : 0),
        yaw: reduceMotion ? 0 : yaw + epic.yaw + (leaving ? cards.exitDirection * 0.22 * exit : 0),
        pitch: reduceMotion ? 0 : pitch + epic.pitch,
        roll: reduceMotion ? 0 : epic.roll,
        scale: epic.scale * (1 + arrival * 0.032) * (1 - Double(max(0, relative)) * 0.003 * (1 - exit)))
      card.frontMatrix = R.cardProjection(pose, cardWidth)
      card.backMatrix = R.cardProjection(pose, cardWidth, true)

      var opacity: Double
      if relative < 0 || relative >= R.CARD_COUNT {
        opacity = 0
      } else if relative == 0 {
        opacity = reduceMotion ? 1 - exit : 1
      } else {
        opacity = 1 - (R.isEpicCard(index) ? R.epicCardPose(revealMs, reduceMotion).light : 0)
      }
      card.frontOpacity = cos(pose.yaw) >= 0.002 ? opacity : 0
      card.backOpacity = cos(pose.yaw) < -0.002 ? opacity : 0
      // The reference shows clean paper edges on unrevealed layers. Bring in the
      // next face as soon as the outgoing card starts uncovering it.
      card.artworkOpacity = relative == 0 || (relative == 1 && cards.exiting) ? 1 : 0

      // Epic cards keep their measured sweep; plain cards get a softer arrival glint.
      let isEpic = R.isEpicCard(cardIndex)
      var sweep = still.sweep
      var flash = still.flash
      if relative == 0 {
        if isEpic {
          let light = R.epicCardPose(revealMs, reduceMotion)
          sweep = light.sweep
          flash = light.flash
        } else {
          let light = R.commonCardArrival(revealMs, reduceMotion)
          sweep = light.sweep
          flash = light.flash
        }
      }
      card.tilt = CGPoint(x: sin(pose.yaw), y: pose.pitch)
      card.holo = R.CARD_HOLO[cardIndex]
      card.shine = isEpic ? 1 : 0.7
      card.sweep = sweep
      card.flash = flash
      card.borderGlow = cardIndex == index && isEpic ? R.epicCardPose(revealMs, reduceMotion).flash * 0.82 : 0
      return card
    }

    s.marks = (0..<3).map { slot in
      var mark = BoosterPackSceneMark()
      mark.epic = R.isEpicCard(index)
      if slot >= R.CARD_RARITIES[index] { return mark }
      // The first card's reveal clock starts when the stack becomes interactive.
      if index == 0 && !cards.ready { return mark }
      let delay: Double = mark.epic ? 2100 : 120
      mark.opacity = cards.opacity * (1 - exit) * M.clamp((revealMs - delay - Double(slot) * 90) / 180, 0, 1)
      return mark
    }
  }

  /// When "Open another pack" starts to fade in: it waits for the last card's turn.
  var footerDelay: Double { reduceMotion ? 0 : R.EPIC_INTERACTION_MS }
}
