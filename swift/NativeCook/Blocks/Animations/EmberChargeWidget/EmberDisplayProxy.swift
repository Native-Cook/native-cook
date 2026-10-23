import UIKit

/// Weak display-link target so stopping the scene releases its motion owner.
@MainActor final class EmberDisplayProxy: NSObject {
  weak var owner: EmberMotion?

  init(_ owner: EmberMotion) { self.owner = owner }

  @objc func tick(_ link: CADisplayLink) { owner?.tick(link.timestamp) }
}
