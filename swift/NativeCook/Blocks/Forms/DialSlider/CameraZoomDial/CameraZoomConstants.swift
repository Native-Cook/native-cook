import SwiftUI

public enum CameraZoomDialGeometry {
    /// Measured from the supplied 1180 px-wide iPhone recording.
    public static let diameterToWidth: Double = 1.104
    /// (2556 - 1551.6) / 1180: visible circle from its top to screen bottom.
    public static let visibleHeightToWidth: Double = 0.852
    public static let degreesPerOctave: Double = 20
    public static let minorTickLength: Double = 15
    /// Major ticks use color and stroke width—not height—for emphasis.
    public static let majorTickLength: Double = 15
    public static let outerTickInset: Double = 5
    public static let labelInset: Double = 42
    public static let focalLabelInset: Double = 67
    /// Measured at roughly 12×27 px in the 3× iPhone capture.
    public static let pointerHalfWidth: Double = 2
    public static let pointerHeight: Double = 9
    /// Subtle rounding measured from the softened yellow marker in the capture.
    public static let pointerCornerRadius: Double = 0.45
    /// Extra clearance prevents anti-aliased tick edges bleeding through.
    public static let pointerOcclusionPadding: Double = 1
    /// The 90° cutout begins just below the yellow pointer's tip.
    public static let pointerOcclusionApexOffset: Double = 2
    /// A narrow stem keeps the selected stroke hidden below the V-shaped cutout.
    public static let pointerOcclusionTailHalfWidth: Double = 1.5
    /// The reference cutout is an isosceles triangle with a right-angle tip.
    public static let pointerOcclusionApexAngle: Double = 90
    /// Softens all three mask corners without moving its conceptual anchor.
    public static let pointerOcclusionCornerRadius: Double = 1.25
    /// Compact controls begin about 207 physical px below the circle top at 3x.
    public static let compactRowTop: Double = 69
    public static let longPressDuration: TimeInterval = 0.22
    public static let collapseDelay: TimeInterval = 1.2
}

public enum CameraZoomDialColors {
    public static let accent = Color(dialSliderHex: 0xFFD60A)
    public static let surface = Color(red: 0, green: 0, blue: 0, opacity: 0.5)
    public static let label = Color(dialSliderHex: 0xFFFFFF)
    public static let secondaryLabel = Color(red: 1, green: 1, blue: 1, opacity: 0.32)
    public static let minorTick = Color(red: 1, green: 1, blue: 1, opacity: 0.25)
    public static let majorTick = Color(red: 1, green: 1, blue: 1, opacity: 0.82)
    public static let compactSurface = Color(red: 28 / 255, green: 28 / 255, blue: 30 / 255, opacity: 0.68)
}
