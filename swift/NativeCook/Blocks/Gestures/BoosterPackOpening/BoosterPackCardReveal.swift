import CoreGraphics
import Foundation

/// A finger on the card stack, as the pan reports it.
struct BoosterPackDragEvent {
  var x: Double
  var y: Double
  var translationX = 0.0
  var translationY = 0.0
  var velocityX = 0.0
  var velocityY = 0.0
}

/// The five-card stack after the opening: which card is on top, how the finger tilts it,
/// the exit of a card and the clock of the card that arrives.
final class BoosterPackCardReveal {
  private typealias M = BoosterPackModel
  private typealias R = BoosterPackCardRevealMotion

  static let CARD_NAMES = ["Bulbasaur", "Squirtle", "Snorlax", "Wigglytuff ex", "Pikachu ex"]
  static let CARD_IMAGES = [
    "booster-card-bulbasaur", "booster-card-squirtle", "booster-card-snorlax", "booster-card-wigglytuff-ex",
    "booster-card-pikachu-ex",
  ]
  static let CARD_BACK_IMAGE = "booster-card-back"

  private(set) var index = 0
  private var announcedIndex = -1
  let yaw = BoosterPackAnimatedValue(0)
  let pitch = BoosterPackAnimatedValue(0)
  private var startYaw = 0.0
  private var startPitch = 0.0
  private(set) var dragging = false
  private(set) var exiting = false
  let exit = BoosterPackAnimatedValue(0)
  private(set) var exitDirection = 1.0
  let revealMs = BoosterPackAnimatedValue(R.EPIC_REVEAL_MS)

  var width = 0.0
  var height = 0.0
  var enabled = false
  var loaded = false
  var reducedMotion = false
  /// Milliseconds since the cut, read from the opening clock.
  var postCutMs: () -> Double = { 0 }
  var now: () -> Double = { 0 }
  var playNextCard: (Int) -> Void = { _ in }
  var playCardReveal: (Int, BoosterPackCardRarity) -> Void = { _, _ in }
  var cardHaptic: () -> Void = {}

  private var wasOpening = false

  private var cardWidth: Double { width * M.REFERENCE_LAYOUT.card.width }
  private var cardHeight: Double { height * M.REFERENCE_LAYOUT.card.height }

  var ready: Bool { enabled && loaded && postCutMs() >= M.OPENING_DURATION_MS - 1 }
  var busy: Bool {
    exiting || (R.isEpicCard(index) && !reducedMotion && revealMs.value < R.EPIC_INTERACTION_MS)
  }
  var opacity: Double { M.clamp((postCutMs() - 1490) / 320, 0, 1) }

  func step(_ now: Double) {
    exit.step(now)
    yaw.step(now)
    pitch.step(now)
    revealMs.step(now)
  }

  /// The per-frame watchers: a new session clears the stack, and each card is announced once,
  /// at the moment it becomes the top card.
  func react() {
    let opening = postCutMs() < M.OPENING_DURATION_MS - 1
    if opening && !wasOpening { reset() }
    wasOpening = opening

    // One event at the actual reveal boundary, including the first card after
    // opening. Keeping this marker through focus changes prevents stale replays.
    let next = ready && !exiting ? index : -1
    if next < 0 || next == announcedIndex { return }
    announcedIndex = next
    playCardReveal(next, R.PACK_CARD_RARITIES[next])
    // The first card has no exit before it; give it the same arrival beat.
    if next == 0 && !reducedMotion {
      revealMs.set(0)
      revealMs.timing(to: R.EPIC_REVEAL_MS, duration: R.EPIC_REVEAL_MS, now: now())
    }
  }

  func reset() {
    // Cancellation runs BEFORE resets. A stale exit callback cannot skip a card
    // or bring the previous pack back after replay, resize, or losing focus.
    exit.set(0)
    yaw.set(0)
    pitch.set(0)
    revealMs.set(R.EPIC_REVEAL_MS)
    index = 0
    announcedIndex = -1
    dragging = false
    exiting = false
    exitDirection = 1
  }

  /// Keep the currently displayed card when the block leaves the screen.
  func suspend() {
    exit.set(0)
    yaw.set(0)
    pitch.set(0)
    revealMs.set(R.EPIC_REVEAL_MS)
    dragging = false
    exiting = false
  }

  private func settle() {
    dragging = false
    yaw.spring(to: 0, duration: 400, dampingRatio: 0.8, now: now())
    pitch.spring(to: 0, duration: 400, dampingRatio: 0.8, now: now())
  }

  func advance(_ direction: Double = 1, _ velocity: Double = 0) {
    if !ready || busy || index >= R.CARD_COUNT - 1 { return }
    dragging = false
    yaw.cancel()
    pitch.cancel()
    revealMs.cancel()
    exitDirection = direction
    exiting = true
    exit.set(0)
    playNextCard(index)
    cardHaptic()
    let finish = { [weak self] in
      guard let self else { return }
      self.index += 1
      self.exit.set(0)
      self.exiting = false
      self.yaw.set(0)
      self.pitch.set(0)
      self.revealMs.set(0)
      self.revealMs.timing(to: R.EPIC_REVEAL_MS, duration: R.EPIC_REVEAL_MS, now: self.now())
    }
    if velocity != 0 && !reducedMotion {
      exit.spring(
        to: 1, duration: 280, dampingRatio: 1, velocity: min(8, abs(velocity) / width),
        overshootClamping: true, now: now(), completion: finish)
    } else {
      exit.timing(
        to: 1, duration: reducedMotion ? 160 : R.CARD_EXIT_MS, easing: .easeOut, now: now(),
        completion: finish)
    }
  }

  func begin(_ event: BoosterPackDragEvent) {
    if !ready || busy { return }
    let top = (height - cardHeight) / 2
    if abs(event.x - width / 2) > cardWidth / 2 + 18 || event.y < top - 18 || event.y > top + cardHeight + 18 {
      return
    }
    yaw.cancel()
    pitch.cancel()
    startYaw = yaw.value
    startPitch = pitch.value
    dragging = true
  }

  func update(_ event: BoosterPackDragEvent) {
    if !dragging || busy || !ready { return }
    let tilt = R.dragCardTilt(event.translationX, event.translationY, cardWidth)
    yaw.set(reducedMotion ? 0 : M.clamp(startYaw + tilt.yaw, -0.56, 0.56))
    pitch.set(reducedMotion ? 0 : M.clamp(startPitch + tilt.pitch, -0.16, 0.16))
  }

  func end(_ event: BoosterPackDragEvent, _ success: Bool) {
    if !dragging { return }
    if success && ready && index < R.CARD_COUNT - 1
      && R.shouldAdvanceCard(event.translationX, event.translationY, event.velocityX, cardWidth) {
      advance(event.translationX >= 0 ? 1 : -1, event.velocityX)
    } else {
      settle()
    }
  }

  func cancel() {
    if dragging { settle() }
  }
}
