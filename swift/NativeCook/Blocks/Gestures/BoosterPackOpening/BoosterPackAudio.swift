import AVFoundation
import UIKit

/// The block's sounds on separate players: the ambience loop, the looping tear, the opening
/// and a cue per card. The ambience follows the screen's lifecycle only; the others follow
/// the gesture, and nothing plays until `setEnabled(true)`.
final class BoosterPackAudio {
  private enum Phase { case idle, tearing, opened }

  private var enabled = false
  private var phase = Phase.idle
  private var moving = false
  private var freshSwipe = true
  private var lastAdvanceIndex = -1
  private var lastRevealIndex = -1

  private var tear: AVAudioPlayer?
  private var open: AVAudioPlayer?
  private var ambience: AVAudioPlayer?
  private var next: AVAudioPlayer?
  private var reveals: [BoosterPackCardRarity: AVAudioPlayer] = [:]
  private var prepared = false

  private func player(_ name: String, loops: Bool = false) -> AVAudioPlayer? {
    guard let url = Bundle.main.url(forResource: name, withExtension: "wav"),
      let player = try? AVAudioPlayer(contentsOf: url)
    else { return nil }
    player.numberOfLoops = loops ? -1 : 0
    player.prepareToPlay()
    return player
  }

  /// Loads the players the first time sound is wanted. Only the cues this pack uses are loaded.
  private func prepare() {
    if prepared { return }
    prepared = true
    // Play with the ring switch off, alongside whatever else is playing.
    try? AVAudioSession.sharedInstance().setCategory(.playback, options: .mixWithOthers)
    tear = player("booster-tear", loops: true)
    open = player("booster-open")
    ambience = player("booster-ambience-loop", loops: true)
    next = player("booster-card-next")
    for rarity in Set(BoosterPackCardRevealMotion.PACK_CARD_RARITIES) {
      reveals[rarity] = player("booster-reveal-\(rarity.rawValue)")
    }
  }

  private func start(_ player: AVAudioPlayer?, rewind: Bool) {
    guard enabled, let player else { return }
    if rewind { player.currentTime = 0 }
    player.play()
  }

  private func resetEffects() {
    phase = .idle
    moving = false
    freshSwipe = true
    tear?.pause()
    open?.pause()
    next?.pause()
    reveals.values.forEach { $0.pause() }
    lastAdvanceIndex = -1
    lastRevealIndex = -1
  }

  func beginTear() {
    if !enabled { return }
    resetEffects()
    phase = .tearing
  }

  /// The tear sounds only while the frontier moves; a hold pauses it where it is.
  func setTearing(_ nextMoving: Bool) {
    if !enabled || phase != .tearing || nextMoving == moving { return }
    moving = nextMoving
    if moving {
      start(tear, rewind: freshSwipe)
      freshSwipe = false
    } else {
      tear?.pause()
    }
  }

  func commitCut() {
    if !enabled || phase != .tearing { return }
    phase = .opened
    moving = false
    tear?.pause()
    start(open, rewind: true)
  }

  func beginCardAdvance(_ fromIndex: Int) {
    if !enabled || fromIndex < 0 || fromIndex <= lastAdvanceIndex || fromIndex < lastRevealIndex { return }
    lastAdvanceIndex = fromIndex
    // End the previous card's tail before accepting a new swipe.
    reveals.values.forEach { $0.pause() }
    start(next, rewind: true)
  }

  func revealCard(_ index: Int, _ rarity: BoosterPackCardRarity) {
    if !enabled || index < 0 || index <= lastRevealIndex || index <= lastAdvanceIndex { return }
    lastRevealIndex = index
    reveals.values.forEach { $0.pause() }
    start(reveals[rarity], rewind: true)
  }

  /// Stops the effects; the ambience plays on.
  func reset() {
    if enabled { resetEffects() }
  }

  /// On while the block is on screen in the foreground and may make sound.
  func setEnabled(_ nextEnabled: Bool) {
    if nextEnabled == enabled { return }
    enabled = nextEnabled
    if enabled {
      prepare()
      // Resume the existing playback head, including after a pause in the background.
      ambience?.play()
    } else {
      resetEffects()
      ambience?.pause()
    }
  }
}

/// The three taps of the block: the cut, a card leaving, an epic face landing.
final class BoosterPackHaptics {
  private let impact = UIImpactFeedbackGenerator(style: .light)
  private let select = UISelectionFeedbackGenerator()
  private let notify = UINotificationFeedbackGenerator()

  func cut() { impact.impactOccurred() }
  func selection() { select.selectionChanged() }
  func success() { notify.notificationOccurred(.success) }
}
