import SwiftUI

struct GoldEmblemMembershipIcon: View, Equatable {
    typealias A = GoldEmblemMembershipArt
    typealias H = GoldEmblemMembershipTheme
    let kind: Int
    private static let fills: [Path] = [
        Path(CGRect(x: 10, y: 13, width: 20, height: 17)),
        Path(ellipseIn: CGRect(x: 10, y: 12, width: 18, height: 18)),
        Path(ellipseIn: CGRect(x: 10, y: 6, width: 18, height: 18)),
        Path(ellipseIn: CGRect(x: 24, y: 11, width: 10, height: 10)),
        Path(ellipseIn: CGRect(x: 6, y: 4, width: 24, height: 24)),
    ]
    var body: some View {
        Canvas { c, _ in
            if kind < 3 {
                c.fill(Self.fills[kind], with: .color(H.color(0xF1C987)))
                c.stroke(kind == 0 ? A.giftLines : kind == 1 ? A.welfareLines : A.redeemLines,
                         with: .color(H.color(0x2A2A2C)), style: StrokeStyle(lineWidth: 1.8, lineCap: kind == 1 ? .round : .butt, lineJoin: kind == 0 ? .miter : .round))
                if kind == 2 { c.stroke(A.starSmall, with: .color(H.color(0x2A2A2C)), style: StrokeStyle(lineWidth: 1.4, lineJoin: .round)) }
            } else if kind == 3 {
                c.fill(A.ticket, with: .color(H.color(0xE9C27E)))
                c.stroke(A.ticketDash, with: .color(H.color(0xC99A55)), style: StrokeStyle(lineWidth: 1.2, dash: [2, 2]))
                c.stroke(Self.fills[3], with: .color(H.color(0xC99A55)), lineWidth: 1.4)
            } else {
                c.fill(A.ribbonLeft, with: .color(H.color(0xF07A34))); c.fill(A.ribbonRight, with: .color(H.color(0xF07A34)))
                c.fill(Self.fills[4], with: .color(H.color(0xF7A341))); c.fill(A.starBadge, with: .color(.white))
            }
        }.accessibilityHidden(true)
    }
}
