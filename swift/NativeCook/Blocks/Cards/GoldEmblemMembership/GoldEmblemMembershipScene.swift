import SwiftUI

struct GoldEmblemMembershipScene: View {
    typealias H = GoldEmblemMembershipTheme
    typealias R = GoldEmblemMembershipResources
    let layout: H.Layout
    let frame: GoldEmblemMembershipMotion.Frame
    let time, scale: Double
    let reduced, pinned: Bool
    let clock: GoldEmblemMembershipClock
    var body: some View {
        let l = layout, card = l.card, tier = H.tiers[frame.shown]
        ZStack(alignment: .topLeading) {
            Color.white
            H.page.frame(width: l.w, height: l.y(483))
            RoundedRectangle(cornerRadius: 4).fill(H.color(frame.shown > 0 ? H.tiers[max(0, frame.shown-1)].sliver : 0x4A4B50))
                .frame(width: 9, height: card.height-34).offset(y: card.minY+17)
            RoundedRectangle(cornerRadius: 4).fill(H.color(frame.shown < 2 ? H.tiers[min(2, frame.shown+1)].sliver : 0x4A4B50))
                .frame(width: 9, height: card.height-34).offset(x: l.w-9, y: card.minY+17)
            ZStack(alignment: .topLeading) {
                GoldEmblemMembershipMetal(layout: l, frame: frame, time: time, scale: scale, reduced: reduced)
                    .frame(width: l.w, height: card.height+60).accessibilityHidden(true)
                ZStack(alignment: .topLeading) {
                    label(tier.name, "SemiBold", 23, tier.ink).offset(x: 33*l.sx, y: 37*l.k-16)
                    label(tier.range, "Regular", 12.5, tier.sub).offset(x: 33*l.sx, y: 66*l.k-9)
                    label(tier.next, "Regular", 11.5, tier.sub).offset(x: 33*l.sx, y: 151*l.k-8)
                }
                .frame(width: card.width, height: card.height, alignment: .topLeading)
                .opacity(frame.words).offset(x: card.minX, y: 10+frame.wordsY)
                GoldEmblemMembershipTouch { event, dx, dy, vx in
                    if !pinned { clock.touch(event, dx, dy, vx) }
                }.frame(width: l.w, height: card.height+60)
            }
            .frame(width: l.w, height: card.height+60, alignment: .topLeading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(tier.name), \(tier.range)")
            .accessibilityHint("Swipe to see the other tiers")
            .accessibilityAdjustableAction { direction in
                if !pinned { clock.adjust(direction == .increment ? 1 : -1) }
            }
            .offset(x: frame.drag, y: card.minY-10)
            ForEach(0..<3) { i in
                let stat = H.stats[i]
                VStack(spacing: 6) {
                    label(stat.0, "SemiBold", 15, 0xFFFFFF).monospacedDigit()
                    label(stat.1, "Regular", 11.5, 0xFFFFFF).opacity(0.82)
                }.frame(width: 140).offset(x: stat.2*l.sx-70, y: l.y(333)-11)
            }
            coupon(tier).offset(x: 17*l.sx, y: l.y(400))
            label("My privilege", "Bold", 17, 0x1D1D1F).accessibilityAddTraits(.isHeader)
                .offset(x: 18*l.sx, y: l.y(529)-12)
            label("View all ›", "Regular", 13, 0x9A9A9E)
                .frame(width: l.w-26*l.sx, alignment: .trailing).offset(y: l.y(529)-9)
            ForEach(0..<3) { i in
                let p = H.privileges[i]
                VStack(spacing: 0) {
                    GoldEmblemMembershipIcon(kind: i).frame(width: 34, height: 34)
                    label(p.0, "Regular", 13, 0x333336).padding(.top, l.y(618)-l.y(580)-26)
                    label(p.1, "Regular", 11.5, 0xD4A866).padding(.top, 6)
                }.frame(width: 120).offset(x: p.2*l.sx-60, y: l.y(580)-16)
            }
            label("Member benefits", "Bold", 17, 0x1D1D1F).accessibilityAddTraits(.isHeader)
                .offset(x: 17*l.sx, y: l.y(700)-12)
            benefit.offset(x: 17*l.sx, y: l.y(735))
            Circle().fill(Color.white.opacity(0.1))
                .overlay(Circle().strokeBorder(Color.white.opacity(0.6), lineWidth: 1.5))
                .frame(width: 44, height: 44).scaleEffect(1-0.12*frame.press).opacity(frame.finger)
                .offset(x: card.midX+frame.fingerX-22, y: card.midY-22)
                .allowsHitTesting(false).accessibilityHidden(true)
        }
        .frame(width: l.w, height: l.h, alignment: .topLeading).clipped()
        .allowsHitTesting(!pinned)
    }
    private func label(_ text: String, _ weight: String, _ size: Double, _ hex: UInt32) -> some View {
        Text(text).font(R.font(weight, size)).foregroundStyle(H.color(hex)).fixedSize()
    }
    private func coupon(_ tier: H.Tier) -> some View {
        let l = layout
        return ZStack(alignment: .topLeading) {
            H.strip
            GoldEmblemMembershipIcon(kind: 3).frame(width: 46, height: 32)
                .offset(x: 53*l.sx-23, y: l.y(445)-l.y(400)-16)
            (Text(tier.coupon).font(R.font("Bold", 30))+Text("%").font(R.font("SemiBold", 15)))
                .foregroundStyle(.white).fixedSize().offset(x: 95*l.sx, y: l.y(445)-l.y(400)-22)
            label("Gift coupon", "Regular", 13, 0xFFFFFF)
                .offset(x: 145*l.sx+Double(tier.coupon.count-1)*12, y: l.y(445)-l.y(400)-3)
            Button {} label: {
                label("Receive", "Medium", 14, 0x4A361C)
                    .frame(width: 85*l.sx, height: 33).background(H.color(0xE9C17F), in: Capsule())
            }.buttonStyle(GoldEmblemMembershipPressStyle(reduced: reduced))
                .accessibilityLabel("Receive the gift coupon")
                .offset(x: 265*l.sx, y: l.y(425)-l.y(400))
        }
        .frame(width: 367*l.sx, height: l.y(483)-l.y(400), alignment: .topLeading)
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 10, topTrailingRadius: 10))
    }
    private var benefit: some View {
        let l = layout
        return HStack(spacing: 0) {
            GoldEmblemMembershipIcon(kind: 4).frame(width: 36, height: 40)
                .frame(width: 62, height: 61).background(.white, in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 2) {
                (Text("$").font(R.font("Bold", 15))+Text("10").font(R.font("Bold", 26))).foregroundStyle(H.color(0x2A1E10))
                label("Member package", "Regular", 13, 0x3A2A14)
            }.padding(.leading, 16)
            Spacer(minLength: 0)
            Button {} label: {
                label("Receive", "Medium", 14, 0xD19A4A).padding(.horizontal, 22)
                    .frame(height: 33).background(.white, in: Capsule())
            }.buttonStyle(GoldEmblemMembershipPressStyle(reduced: reduced))
                .accessibilityLabel("Receive the member package")
        }.padding(.horizontal, 18)
        .frame(width: 367*l.sx, height: 105*l.k)
        .background(LinearGradient(colors: [H.color(0xF3D79F), H.color(0xDDB373)], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
