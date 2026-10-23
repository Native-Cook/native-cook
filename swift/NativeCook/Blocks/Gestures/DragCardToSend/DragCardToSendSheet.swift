import SwiftUI
import UIKit

struct DragCardToSendSheet: View {
    typealias T = DragCardToSendTokens
    @ObservedObject var motion: DragCardToSendMotion
    let size: CGSize

    var body: some View {
        let clip = DragCardToSendIslandGeometry.clip(g: motion.g, size: size)
        let body = DragCardToSendIslandGeometry.body(g: motion.g, size: size)
        let sheetWidth = size.width - T.sheet.inset * 2
        let sheetHeight = size.height - T.sheet.top - T.sheet.bottomGap
        let transition = min(max((motion.g - 1) / T.replica.sheetMorphSpan, 0), 1)
        let scale = min(1, body.width / sheetWidth) * (1 - transition) + transition
        let dropX = size.width / 2 - sheetWidth * scale / 2
        let dropY = body.minY + 12 - (T.sheet.favTop - 8) * scale
        let originX = mix(dropX, T.sheet.inset, transition)
        let originY = mix(dropY, T.sheet.top, transition)
        let opacity = motion.isReduced ? (motion.g >= 2 ? 1 : 0) : min(max((motion.g - T.fade.contentFrom) / (T.fade.contentTo - T.fade.contentFrom), 0), 1)

        ZStack(alignment: .topLeading) {
            DragCardToSendSheetContent(motion: motion, size: CGSize(width: sheetWidth, height: sheetHeight))
                .frame(width: sheetWidth, height: sheetHeight, alignment: .topLeading)
                .scaleEffect(scale)
                .position(x: originX - clip.minX + sheetWidth * scale / 2, y: originY - clip.minY + sheetHeight * scale / 2)
        }
        .frame(width: clip.width, height: clip.height, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: min(max(body.width, body.height) / 2, T.sheet.radius), style: .continuous))
        .position(x: clip.midX, y: clip.midY)
        .opacity(opacity)
        .allowsHitTesting(motion.isSheetInteractive)
        .accessibilityHidden(!motion.isSheetInteractive)
    }

    private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
}

struct DragCardToSendSheetContent: View {
    typealias T = DragCardToSendTokens
    @ObservedObject var motion: DragCardToSendMotion
    let size: CGSize

    var body: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Send").foregroundStyle(T.ColorToken.sheetMute)
                Text("Money To")
            }
            .font(.system(size: T.sheet.titleSize, weight: .bold))
            .tracking(-0.8)
            .fixedSize()
            .offset(x: T.sheet.pad, y: T.sheet.titleTop)
            .accessibilityAddTraits(.isHeader)

            Button { motion.closeSheet() } label: {
                Image(systemName: "xmark").font(.system(size: T.space.lg, weight: .regular))
                    .frame(width: T.sheet.close, height: T.sheet.close)
            }
            .buttonStyle(DragCardToSendPressStyle())
            .position(x: size.width - T.sheet.closeRight - T.sheet.close / 2, y: T.sheet.titleTop + T.sheet.close / 2)
            .accessibilityLabel("Close")

            HStack(spacing: 0) {
                ForEach(T.favourites) { person in
                    let index = T.favourites.firstIndex(where: { $0.id == person.id }) ?? 0
                    Button { motion.select(index) } label: {
                        VStack(spacing: T.sheetStyle.favNameTop) {
                            DragCardToSendAvatar(person: person, size: T.sheet.favSize)
                                .scaleEffect(targetScale(index))
                            Text(person.name).font(.system(size: T.type.favName, weight: .medium))
                        }
                        .frame(maxWidth: .infinity)
                        .offset(x: T.sheet.favNudge[index])
                    }
                    .buttonStyle(DragCardToSendPressStyle())
                    .accessibilityLabel("Send to \(person.name)")
                }
                VStack(spacing: T.sheetStyle.favNameTop) {
                    Image(systemName: "plus")
                        .font(.system(size: T.replica.icon, weight: .regular))
                        .frame(width: T.sheet.favSize, height: T.sheet.favSize)
                        .overlay(Circle().stroke(Color.white.opacity(0.7), lineWidth: T.sheetStyle.addBorder))
                    Text("Add New").font(.system(size: T.type.favName, weight: .medium))
                }
                .frame(maxWidth: .infinity)
                .offset(x: T.sheet.favNudge[3])
            }
            .padding(.horizontal, T.sheet.favPad)
            .offset(y: T.sheet.favTop)

            VStack(alignment: .leading, spacing: 0) {
                Text("Your").foregroundStyle(T.ColorToken.sheetMute).fontWeight(.medium)
                Text("Contacts").fontWeight(.bold)
            }
            .font(.system(size: T.type.sheetSection))
            .tracking(-0.3)
            .offset(x: T.sheet.pad, y: T.sheet.sectionTop)
            .accessibilityAddTraits(.isHeader)

            VStack(spacing: 0) {
                ForEach(T.contacts) { person in
                    let row = T.contacts.firstIndex(where: { $0.id == person.id }) ?? 0
                    let index = T.favourites.count + row
                    Button { motion.select(index) } label: {
                        HStack(spacing: T.sheetStyle.contactGap) {
                            DragCardToSendAvatar(person: person, size: T.sheet.contactSize)
                                .scaleEffect(targetScale(index))
                            Text(person.name).font(.system(size: T.type.contact - 1, weight: .semibold))
                            Spacer()
                        }
                        .padding(.horizontal, T.sheetStyle.rowHorizontal)
                        .frame(height: T.sheet.rowHeight)
                        .background(T.ColorToken.sheetHover.opacity(motion.hoverIndex == index ? 1 : 0), in: Capsule())
                    }
                    .buttonStyle(DragCardToSendPressStyle())
                    .accessibilityLabel("Send to \(person.name)")
                }
            }
            .padding(.horizontal, T.space.lg)
            .offset(y: T.sheet.rowsTop)

            Button("Next") { motion.sendSelected() }
                .buttonStyle(.plain)
                .font(.system(size: T.sheetStyle.nextFont, weight: .semibold))
                .frame(width: T.sheet.nextWidth, height: T.sheet.nextHeight)
                .background(T.ColorToken.next, in: Capsule())
                .position(x: size.width / 2, y: T.sheet.nextTop + T.sheet.nextHeight / 2)
                .opacity(min(max((motion.g - T.effects.nextStart) / T.effects.nextSpan, 0), 1))
                .accessibilityLabel("Next, send to the selected person")

            if motion.sendIndex >= 0 {
                DragCardToSendSendPayoff(motion: motion, index: motion.sendIndex)
            }
        }
        .foregroundStyle(T.ColorToken.sheetInk)
    }

    private func targetScale(_ index: Int) -> Double {
        guard motion.hoverIndex == index else { return 1 }
        return 1 + (T.magnet.targetScale - 1) * motion.hoverAmount
    }
}

struct DragCardToSendSendPayoff: View {
    typealias T = DragCardToSendTokens
    @ObservedObject var motion: DragCardToSendMotion
    let index: Int

    var body: some View {
        let target = motion.targetPoint(index)
        let local = CGPoint(x: target.x - T.sheet.inset, y: target.y - T.sheet.top)
        let avatar = index < T.favourites.count ? T.sheet.favSize : T.sheet.contactSize
        let radius = avatar / 2 * T.magnet.targetScale + T.sheetStyle.spark
        ZStack {
            ForEach(0..<T.ambient.sparks, id: \.self) { spark in
                let angle = Double(spark) / Double(T.ambient.sparks) * .pi * 2
                let distance = radius + T.ambient.sparkReach * sqrt(max(motion.ring, 0))
                Circle().fill(T.ColorToken.ring)
                    .frame(width: T.sheetStyle.spark, height: T.sheetStyle.spark)
                    .scaleEffect(1 - T.effects.sparkShrink * motion.ring)
                    .offset(x: cos(angle) * distance, y: sin(angle) * distance)
                    .opacity(motion.isReduced ? 0 : 1 - motion.ring)
            }
            Circle()
                .trim(from: 0, to: motion.ring)
                .stroke(T.ColorToken.ring, style: StrokeStyle(lineWidth: T.sheetStyle.ringLine, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .frame(width: radius * 2, height: radius * 2)
            Image(systemName: "checkmark")
                .font(.system(size: T.cardStyle.holderFont, weight: .bold))
                .foregroundStyle(T.ColorToken.island)
                .frame(width: T.sheetStyle.badge, height: T.sheetStyle.badge)
                .background(T.ColorToken.success, in: Circle())
                .overlay(Circle().stroke(T.ColorToken.island, lineWidth: T.sheetStyle.badgeBorder))
                .scaleEffect(motion.check)
                .opacity(min(motion.check * T.effects.checkOpacity, 1))
                .offset(x: avatar * T.effects.badgeOffset, y: avatar * T.effects.badgeOffset)
        }
        .position(x: local.x, y: local.y)
        .allowsHitTesting(false)
    }
}

struct DragCardToSendAvatar: View {
    typealias T = DragCardToSendTokens
    let person: T.Person
    let size: Double

    var body: some View {
        Group {
            if let image = image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Circle().fill(T.ColorToken.ring)
                    .overlay(Text(person.initials).font(.system(size: size * T.effects.initialsScale, weight: .bold)).foregroundStyle(.white))
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var image: UIImage? {
        guard let asset = person.asset else { return nil }
        let value = UIImage(named: asset)
        #if DEBUG
        assert(value != nil, "Missing DragCardToSend asset: \(asset)")
        #endif
        return value
    }
}

struct DragCardToSendPressStyle: ButtonStyle {
    typealias T = DragCardToSendTokens
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? T.press.scale : 1)
            .animation(.easeOut(duration: configuration.isPressed ? T.press.inDuration : T.press.outDuration), value: configuration.isPressed)
    }
}
