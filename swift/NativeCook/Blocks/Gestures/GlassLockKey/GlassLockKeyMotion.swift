import Foundation

/// A pure snapshot of the active six-second replica timeline.
///
/// The renderer applies `keyScaleX` around the source key's horizontal pivot
/// (`603 / 1206 * width`) to match the React Native replica.
/// Source: `react-native/src/blocks/gestures/glass-lock-key/GlassReplica.tsx:9-20,37-53`
/// and `react-native/src/blocks/gestures/glass-lock-key/motion.ts:48-57`.
struct GlassLockKeyReplicaFrame {
    static let period = 6.0

    let keyScaleX: Double
    let keyOpacity: Double
    let lockOpacity: Double
    let openOpacity: Double
    let closedOpacity: Double
    let closedX: Double
    let closedY: Double
    let openX: Double
    let openY: Double

    init(time: Double, width: Double, height: Double) {
        let remainder = time.truncatingRemainder(dividingBy: Self.period)
        let t = remainder >= 0 ? remainder : remainder + Self.period

        let keySpin = Self.phase(t, from: 0, to: 1.0)
        keyScaleX = max(0.06, abs(cos(.pi * keySpin)))

        lockOpacity = Self.phase(t, from: 1.0, to: 1.35)
            * (1 - Self.phase(t, from: 5.4, to: 5.75))
        keyOpacity = 1 - lockOpacity

        let up: Double
        if t < 4.2 {
            up = Self.easeOutBack(Self.phase(t, from: 1.9, to: 2.15))
        } else {
            up = 1 - Self.phase(t, from: 4.2, to: 4.45)
        }

        openOpacity = Self.clamp(up)
        closedOpacity = 1 - Self.clamp(up)
        closedX = (23 / 1206) * width * up
        closedY = (-90 / 2622) * height * up
        openX = (-23 / 1206) * width * (1 - up)
        openY = (90 / 2622) * height * (1 - up)
    }

    func interpolated(toward other: Self, progress: Double) -> Self {
        let progress = Self.clamp(progress)
        if progress == 0 { return self }
        if progress == 1 { return other }

        return Self(
            keyScaleX: keyScaleX + (other.keyScaleX - keyScaleX) * progress,
            keyOpacity: keyOpacity + (other.keyOpacity - keyOpacity) * progress,
            lockOpacity: lockOpacity + (other.lockOpacity - lockOpacity) * progress,
            openOpacity: openOpacity + (other.openOpacity - openOpacity) * progress,
            closedOpacity: closedOpacity + (other.closedOpacity - closedOpacity) * progress,
            closedX: closedX + (other.closedX - closedX) * progress,
            closedY: closedY + (other.closedY - closedY) * progress,
            openX: openX + (other.openX - openX) * progress,
            openY: openY + (other.openY - openY) * progress
        )
    }

    private init(
        keyScaleX: Double,
        keyOpacity: Double,
        lockOpacity: Double,
        openOpacity: Double,
        closedOpacity: Double,
        closedX: Double,
        closedY: Double,
        openX: Double,
        openY: Double
    ) {
        self.keyScaleX = keyScaleX
        self.keyOpacity = keyOpacity
        self.lockOpacity = lockOpacity
        self.openOpacity = openOpacity
        self.closedOpacity = closedOpacity
        self.closedX = closedX
        self.closedY = closedY
        self.openX = openX
        self.openY = openY
    }

    private static func clamp(_ value: Double) -> Double {
        min(max(value, 0), 1)
    }

    private static func phase(_ time: Double, from start: Double, to end: Double) -> Double {
        clamp((time - start) / (end - start))
    }

    private static func easeOutBack(_ progress: Double) -> Double {
        let c = 1.7
        return 1 + (c + 1) * pow(progress - 1, 3) + c * pow(progress - 1, 2)
    }
}
