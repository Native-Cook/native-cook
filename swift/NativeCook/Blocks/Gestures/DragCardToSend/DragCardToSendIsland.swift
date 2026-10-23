import SwiftUI

struct DragCardToSendLiquid: View {
    let frame: DragCardToSendFrame
    let layout: DragCardToSendLayout
    let onUndo: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in drawLiquid(context: &context) }
                .frame(width: layout.width, height: layout.avatarY + 80)
                .allowsHitTesting(false)

            if frame.dropRadius > 9, let center = frame.dropCenter, !frame.sentCommitted {
                Text("$\(frame.amount ?? frame.receiptAmount)")
                    .font(.system(size: 26, weight: .bold))
                    .tracking(-0.5)
                    .monospacedDigit()
                    .foregroundStyle(.white)
                    .frame(width: 120, height: 32)
                    .scaleEffect(min(1, frame.dropRadius / 29))
                    .position(center)
            }

            if frame.receiptProgress > 0.82 {
                DragCardToSendReceipt(frame: frame, layout: layout, onUndo: onUndo)
                    .opacity((frame.receiptProgress - 0.82) / 0.18)
                    .scaleEffect(0.92 + 0.08 * frame.receiptProgress)
            }
        }
    }

    private func drawLiquid(context: inout GraphicsContext) {
        let island = CGRect(x: layout.width / 2 - 47, y: 14.35, width: 94, height: 36)
        let receipt = CGRect(x: 16, y: 10, width: layout.width - 32, height: 64)
        let progress = frame.receiptProgress
        let box = CGRect(
            x: island.minX + (receipt.minX - island.minX) * progress,
            y: island.minY + (receipt.minY - island.minY) * progress,
            width: island.width + (receipt.width - island.width) * progress,
            height: island.height + (receipt.height - island.height) * progress
        )
        context.fill(Path(roundedRect: box, cornerRadius: box.height / 2), with: .color(.black))

        guard let center = frame.dropCenter, frame.dropRadius > 0.5 else { return }
        let radius = frame.dropRadius
        if center.y < 135 {
            var neck = Path()
            neck.move(to: CGPoint(x: layout.width / 2 - min(18, radius * 0.55), y: box.maxY - 4))
            neck.addCurve(to: CGPoint(x: center.x - radius * 0.72, y: center.y),
                          control1: CGPoint(x: layout.width / 2 - 13, y: box.maxY + radius * 0.65),
                          control2: CGPoint(x: center.x - radius * 0.78, y: center.y - radius * 0.55))
            neck.addLine(to: CGPoint(x: center.x + radius * 0.72, y: center.y))
            neck.addCurve(to: CGPoint(x: layout.width / 2 + min(18, radius * 0.55), y: box.maxY - 4),
                          control1: CGPoint(x: center.x + radius * 0.78, y: center.y - radius * 0.55),
                          control2: CGPoint(x: layout.width / 2 + 13, y: box.maxY + radius * 0.65))
            neck.closeSubpath()
            context.fill(neck, with: .color(.black))
        }

        let dropRect = CGRect(x: center.x - radius * (1 - frame.dropStretch * 0.25),
                              y: center.y - radius * (1 + frame.dropStretch),
                              width: radius * 2 * (1 - frame.dropStretch * 0.25),
                              height: radius * 2 * (1 + frame.dropStretch))
        context.fill(Path(ellipseIn: dropRect), with: .radialGradient(
            Gradient(colors: [Color(white: 0.28), Color(white: 0.08), .black]),
            center: CGPoint(x: dropRect.minX + dropRect.width * 0.36, y: dropRect.minY + dropRect.height * 0.28),
            startRadius: 1,
            endRadius: radius * 1.35
        ))
        let highlight = CGRect(x: center.x - radius * 0.48, y: center.y - radius * 0.55,
                               width: radius * 0.26, height: radius * 0.42)
        context.fill(Path(ellipseIn: highlight), with: .color(.white.opacity(0.72)))
    }
}

struct DragCardToSendReceipt: View {
    let frame: DragCardToSendFrame
    let layout: DragCardToSendLayout
    let onUndo: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            ZStack(alignment: .bottomTrailing) {
                DragCardToSendAvatar(name: DragCardToSendData.people[frame.receiptPerson].id, size: 40)
                Circle()
                    .fill(DragCardToSendPalette.accent)
                    .frame(width: 18, height: 18)
                    .overlay(Image(systemName: "checkmark").font(.system(size: 9, weight: .bold)).foregroundStyle(.white))
                    .overlay(Circle().stroke(.black, lineWidth: 2))
                    .offset(x: 3, y: 3)
            }
            VStack(alignment: .leading, spacing: 0) {
                Text("To \(DragCardToSendData.people[frame.receiptPerson].name)")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white.opacity(0.64))
                Text("$\(frame.receiptAmount)")
                    .font(.system(size: 22, weight: .bold))
                    .tracking(-0.5)
                    .foregroundStyle(.white)
            }
            Spacer()
            Button(action: onUndo) {
                HStack(spacing: 7) {
                    ZStack {
                        Circle().stroke(.white.opacity(0.22), lineWidth: 2)
                        Circle()
                            .trim(from: 0, to: frame.undoProgress)
                            .stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                    }
                    .frame(width: 16, height: 16)
                    Text("Undo")
                        .font(.system(size: 15, weight: .semibold))
                        .tracking(-0.2)
                }
                .foregroundStyle(.white)
                .frame(width: 92, height: 36)
                .background(.white.opacity(0.16), in: Capsule())
            }
            .buttonStyle(.plain)
        }
        .frame(width: layout.width - 56, height: 40)
        .position(x: layout.width / 2, y: 42)
    }
}
