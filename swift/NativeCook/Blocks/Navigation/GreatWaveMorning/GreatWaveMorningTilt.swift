import CoreMotion
import Foundation

// The sea moves relative to a slowly learned resting orientation, matching the RN rotation sensor.
final class GreatWaveMorningTilt {
  private let manager = CMMotionManager()
  private var pitch = 0.0
  private var roll = 0.0
  private var baselinePitch = 0.0
  private var baselineRoll = 0.0
  private var x = 0.0
  private var y = 0.0
  private var initialized = false

  func start() {
    guard manager.isDeviceMotionAvailable, !manager.isDeviceMotionActive else { return }
    manager.deviceMotionUpdateInterval = 1.0 / 60.0
    manager.startDeviceMotionUpdates(to: .main) { [weak self] motion, _ in
      guard let attitude = motion?.attitude else { return }
      self?.pitch = attitude.pitch
      self?.roll = attitude.roll
    }
  }

  func stop() {
    manager.stopDeviceMotionUpdates()
    initialized = false
    x = 0
    y = 0
  }

  func sample(dt: Double) -> (x: Double, y: Double) {
    guard manager.isDeviceMotionActive else { return (0, 0) }
    if !initialized {
      initialized = true
      baselinePitch = pitch
      baselineRoll = roll
    }
    let baselineK = min(1, dt * 0.6)
    baselinePitch += (pitch - baselinePitch) * baselineK
    baselineRoll += (roll - baselineRoll) * baselineK
    let smoothingK = 1 - exp(-dt * 8)
    let targetX = max(-1, min(1, (roll - baselineRoll) / 0.35))
    let targetY = max(-1, min(1, (pitch - baselinePitch) / 0.35))
    x += (targetX - x) * smoothingK
    y += (targetY - y) * smoothingK
    return (x, y)
  }
}
