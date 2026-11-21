import Foundation

struct PressedStampCardTouch {
    enum Mode { case holding, stamping, redeeming, cancelled }
    var mode: Mode
    var started: Date
    var point: CGPoint // scene coordinates
    var target: Int
    var charge: CGFloat = 0
    var redeemed = false
    var redeemHapticPlayed = false
    var magnet = false
    var eventOffset: Double = 0
    var pull: CGFloat = 0
    var velocityTilt: CGFloat = 0
}
