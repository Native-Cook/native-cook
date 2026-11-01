import SwiftUI
import AVFoundation
import UIKit

final class RibbonRoadOnboardingClock: ObservableObject {
  @Published var running=false
  let art=RibbonRoadOnboardingArt()
  private var driver: RibbonRoadOnboardingDriver?
  private var pinnedDriver: RibbonRoadOnboardingDriver?
  private var pinnedTime: Double?
  private var dimensions=CGSize.zero
  private var date: Date?
  private var players: [String:AVAudioPlayer]=[:]
  private let selection=UISelectionFeedbackGenerator()
  private let light=UIImpactFeedbackGenerator(style:.light)
  private let medium=UIImpactFeedbackGenerator(style:.medium)
  func sample(_ at: Date,_ size: CGSize,_ auto: Bool,_ reduced: Bool,_ pinned: Double?) -> RibbonRoadOnboardingDriver {
    if driver==nil || dimensions != size {
      dimensions=size; driver=RibbonRoadOnboardingDriver(size); pinnedDriver=nil; date=nil
    }
    if let pinned {
      if pinnedDriver==nil || pinnedTime != pinned {
        pinnedTime=pinned; pinnedDriver=RibbonRoadOnboardingDriver.replay(max(0,pinned),size,reduced)
      }
      return pinnedDriver!
    }
    let d=driver!
    let dt=min(max(0,date.map { at.timeIntervalSince($0) } ?? 0.016),0.5)
    date=at
    d.step(dt,auto,reduced)
    // Same launch flag as DemoSound.inAutoPlay; this isolated port's Shared snapshot predates it.
    if auto && !(UserDefaults.standard.string(forKey:"recordingSound") ?? "").isEmpty {
      for name in d.cues { cue(name,reduced) }
    }
    if reduced && running && !d.on && d.clock-d.last>3 {
      DispatchQueue.main.async { [weak self] in self?.running=false; self?.date=nil }
    }
    return d
  }
  func active(_ active: Bool,_ reduced: Bool,_ pinned: Bool) {
    date=nil
    if active && !reduced && !pinned && players.isEmpty {
      try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
      for name in ["stretch","twang","zip"] {
        if let url=Bundle.main.url(forResource:"RibbonRoadOnboarding-rro-\(name)",withExtension:"wav"),let player=try? AVAudioPlayer(contentsOf:url) {
          player.volume=name=="stretch" ? 0.5 : 0.75; player.prepareToPlay(); players[name]=player
        }
      }
    }
    if !active || reduced || pinned { for player in players.values { player.stop() } }
  }
  func reset() { driver=nil; date=nil; pinnedDriver=nil; running=false }
  private func cue(_ name: String,_ reduced: Bool) {
    guard !reduced, let p=players[name] else { return }
    p.currentTime=0; p.play()
  }
  func touch(_ state: UIGestureRecognizer.State,_ point: CGPoint,_ reduced: Bool) {
    guard let d=driver else { return }
    if !running { running=true }
    switch state {
    case .began:
      if d.begin(point) { cue("stretch",reduced); selection.selectionChanged() }
    case .changed: d.move(point)
    case .ended,.cancelled,.failed:
      if d.end() { cue("twang",reduced); light.impactOccurred() }
    default: break
    }
  }
  func start(_ reduced: Bool) {
    guard let d=driver else { return }
    if !running { running=true }
    if d.start() { medium.impactOccurred(); cue("zip",reduced) }
  }
}
