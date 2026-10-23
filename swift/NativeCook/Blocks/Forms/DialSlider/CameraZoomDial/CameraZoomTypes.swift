import Foundation

/// Anything with a zoom factor, used by the stop-normalization helpers.
public protocol CameraZoomStopValue {
    var value: Double { get set }
}

public struct CameraZoomStop: Equatable, Hashable, CameraZoomStopValue {
    /// Positive zoom factor; normalized to the nearest configured step.
    public var value: Double
    /// Circular wheel label. Defaults to the numeric zoom factor without `x`.
    public var label: String?
    /// Compact inactive label. Defaults to `label`, or camera-style numeric text.
    public var compactLabel: String?
    /// Optional equivalent focal length, for example `13MM` or `26MM`.
    public var focalLength: String?

    public init(
        value: Double,
        label: String? = nil,
        compactLabel: String? = nil,
        focalLength: String? = nil
    ) {
        self.value = value
        self.label = label
        self.compactLabel = compactLabel
        self.focalLength = focalLength
    }
}
