import CoreMotion
import UIKit

/// Haptics for the moments the finger earns: a rigid tap when the seal cracks, light at the fold, medium on commit,
/// success with the petals. The generators are made on first use, so a screen that is only measured makes none.
@MainActor
final class EnvelopeGiftRevealHaptics {
    private lazy var light = UIImpactFeedbackGenerator(style: .light)
    private lazy var rigid = UIImpactFeedbackGenerator(style: .rigid)
    private lazy var medium = UIImpactFeedbackGenerator(style: .medium)
    private lazy var soft = UIImpactFeedbackGenerator(style: .soft)
    private lazy var notification = UINotificationFeedbackGenerator()

    func play(_ kind: EnvelopeGiftRevealEngine.Haptic) {
        switch kind {
        case .flap: light.impactOccurred()
        case .crack: rigid.impactOccurred()
        case .commit: medium.impactOccurred()
        case .light: soft.impactOccurred()
        case .success: notification.notificationOccurred(.success)
        }
    }
}

/// The phone's roll, which moves the light band on the card (Reanimated's ROTATION sensor).
@MainActor
final class EnvelopeGiftRevealTilt {
    private var manager: CMMotionManager?

    func start() {
        let motion = manager ?? CMMotionManager()
        manager = motion
        guard motion.isDeviceMotionAvailable, !motion.isDeviceMotionActive else { return }
        // `useAnimatedSensor(..., { interval: 16 })` in the approved RN implementation.
        motion.deviceMotionUpdateInterval = EnvelopeGiftRevealTokens.Assets.motionIntervalSeconds
        motion.startDeviceMotionUpdates()
    }

    func stop() {
        guard let motion = manager, motion.isDeviceMotionActive else { return }
        motion.stopDeviceMotionUpdates()
    }

    /// Roll in radians; 0 when the phone has no motion sensor (the simulator).
    var roll: Double {
        manager?.deviceMotion?.attitude.roll ?? 0
    }
}
