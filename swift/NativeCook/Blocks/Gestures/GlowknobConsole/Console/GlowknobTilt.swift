import CoreMotion
import UIKit

/// Reanimated's `useAnimatedSensor(SensorType.ROTATION, { interval: 16 })` on
/// iOS (ReanimatedSensor.m): device motion at 16 ms in the
/// `XArbitraryCorrectedZVertical` frame (Auto, with a magnetometer; else
/// `XArbitraryZVertical`), reporting attitude roll and pitch; portrait needs
/// no interface-orientation adjustment. Then tilt.tsx: a normalized tilt
/// (-1…1) relative to a slowly following baseline, so highlights answer a
/// movement of the phone, then drift back to rest for however it is held.
/// Running only while the tilt effect is on and the page is focused.
@MainActor
final class GlowknobTiltSource {
  private let manager = CMMotionManager()
  private let tilt: GlowknobSharedValue<GlowknobTilt>
  private var base = (roll: 0.0, pitch: 0.0, ready: false)
  private(set) var running = false

  init(tilt: GlowknobSharedValue<GlowknobTilt>) { self.tilt = tilt }

  func start() {
    guard !running, manager.isDeviceMotionAvailable else { return }
    running = true
    base = (0, 0, false)
    manager.deviceMotionUpdateInterval = 16.0 / 1000
    manager.showsDeviceMovementDisplay = true
    let frame: CMAttitudeReferenceFrame = CMMotionManager.availableAttitudeReferenceFrames()
      .contains(.xArbitraryCorrectedZVertical) && manager.isMagnetometerAvailable
      ? .xArbitraryCorrectedZVertical : .xArbitraryZVertical
    manager.startDeviceMotionUpdates(using: frame, to: .main) { [weak self] motion, _ in
      guard let motion else { return }
      MainActor.assumeIsolated { self?.receive(roll: motion.attitude.roll, pitch: motion.attitude.pitch) }
    }
  }

  func stop() {
    guard running else { return }
    running = false
    manager.stopDeviceMotionUpdates()
  }

  private func receive(roll r: Double, pitch p: Double) {
    let roll = base.ready ? base.roll + (r - base.roll) * 0.02 : r
    let pitch = base.ready ? base.pitch + (p - base.pitch) * 0.02 : p
    base = (roll, pitch, true)
    tilt.set(GlowknobTilt(max(-1, min(1, (r - roll) / 0.35)), max(-1, min(1, (p - pitch) / 0.35))))
  }
}
