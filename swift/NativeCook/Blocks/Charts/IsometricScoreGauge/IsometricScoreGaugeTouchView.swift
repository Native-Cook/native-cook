import SwiftUI
import UIKit

struct IsometricScoreGaugeTouchView: UIViewRepresentable {
    var scale: Double
    var dy: Double
    var began: (CGPoint) -> Void
    var changed: (Double, Double) -> Void
    var ended: (Double) -> Void
    func makeUIView(context: Context) -> IsometricScoreGaugeTouchSurface { IsometricScoreGaugeTouchSurface() }
    func updateUIView(_ view: IsometricScoreGaugeTouchSurface, context: Context) {
        view.scale = scale; view.dy = dy
        view.began = began; view.changed = changed; view.ended = ended
    }
}
