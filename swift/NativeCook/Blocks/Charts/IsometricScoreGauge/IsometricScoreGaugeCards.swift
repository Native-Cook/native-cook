import SwiftUI
import UIKit

struct IsometricScoreGaugeCards: UIViewRepresentable {
    var ambient: Double
    var frame: IsometricScoreGaugeMotion.Frame
    func makeUIView(context: Context) -> IsometricScoreGaugeCardSurface { IsometricScoreGaugeCardSurface() }
    func updateUIView(_ view: IsometricScoreGaugeCardSurface, context: Context) { view.draw(frame, ambient: ambient) }
}
