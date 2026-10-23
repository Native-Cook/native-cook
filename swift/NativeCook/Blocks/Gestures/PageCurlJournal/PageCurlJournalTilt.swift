import CoreMotion

/// The phone's roll, which moves the foil's light band on the cover (Reanimated's ROTATION sensor).
@MainActor
final class PageCurlJournalTilt {
    private let manager = CMMotionManager()

    func start() {
        guard manager.isDeviceMotionAvailable, !manager.isDeviceMotionActive else { return }
        manager.deviceMotionUpdateInterval = 1.0 / 60.0
        manager.startDeviceMotionUpdates()
    }

    func stop() {
        guard manager.isDeviceMotionActive else { return }
        manager.stopDeviceMotionUpdates()
    }

    /// Roll in radians; 0 when the phone has no motion sensor (the simulator).
    var roll: Double {
        manager.deviceMotion?.attitude.roll ?? 0
    }
}
