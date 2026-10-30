import SwiftUI
import UIKit

struct CabinClimateCarTouchView: UIViewRepresentable {
  var begin: (CGPoint) -> Void
  var move: (CGFloat) -> Void
  var end: () -> Void
  func makeUIView(context: Context) -> CabinClimateCarTouchSurface { CabinClimateCarTouchSurface() }
  func updateUIView(_ view: CabinClimateCarTouchSurface, context: Context) {
    view.begin = begin
    view.move = move
    view.end = end
  }
}
