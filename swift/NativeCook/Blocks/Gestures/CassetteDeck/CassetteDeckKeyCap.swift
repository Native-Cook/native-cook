import SwiftUI

/// One key of the row: the cap at rest and the cap pressed cross-fade with its depth, and the whole key
/// moves with the chassis dip. The key is drawn only; touches are taken by the UIKit surface above.
struct CassetteDeckKeyCap: View {
    let index: Int
    /// How far down the cap is, 0...1.
    let depth: Double
    /// The chassis dip in points before scaling.
    let dip: Double
    let reduced: Bool
    let metrics: CassetteDeckMetrics

    var body: some View {
        let s = metrics.scale
        let keys = CassetteDeckTheme.Layout.keys
        let red = index == 0
        let chassis = dip * CassetteDeckMotion.Chassis.dipTravel * s
        let upShift = reduced ? 0 : depth * keys.travel * s + chassis
        let downShift = reduced ? 0 : (depth - 1) * keys.travel * s + chassis
        ZStack(alignment: .topLeading) {
            Image(uiImage: red ? CassetteDeckArt.keyRed : CassetteDeckArt.keyDark)
                .resizable()
                .interpolation(.high)
                .frame(width: keys.w * s, height: keys.h * s)
                .opacity(1 - depth)
                .offset(y: upShift)
            Image(uiImage: red ? CassetteDeckArt.keyRedDown : CassetteDeckArt.keyDarkDown)
                .resizable()
                .interpolation(.high)
                .frame(width: keys.w * s, height: keys.h * s)
                .opacity(depth)
                .offset(y: downShift)
        }
        .frame(width: keys.w * s, height: keys.h * s, alignment: .topLeading)
        .offset(x: (keys.x0 + Double(index) * keys.pitch) * s, y: metrics.offsetY + keys.y * s)
    }
}
