import Foundation
// CADisplayLink retains its target; this weak proxy leaves ownership with the page.
import QuartzCore

@MainActor final class AuraDisplayProxy: NSObject {
  weak var machine: AuraMachine?
  @objc func cue() { machine?.cueFired() }
  @objc func confirmation() { machine?.sentExpired() }
  @objc func step(_ display: CADisplayLink) { machine?.step(display) }
}
