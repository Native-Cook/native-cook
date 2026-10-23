import SwiftUI

struct DragCardToSendHome: View {
    typealias T = DragCardToSendTokens
    @ObservedObject var motion: DragCardToSendMotion

    var body: some View {
        let pull = motion.phase == .pulling ? max(0, motion.cardOffset.height) : 0
        let sink = 1 - T.pull.listSink * min(pull / T.replica.pullFrame, 1)
        let open = min(max((motion.g - 1) / T.replica.sheetMorphSpan, 0), 1)
        let spread = T.layout.headerSpread * smooth(min(motion.g / T.replica.spreadProgress, 1))

        ZStack(alignment: .topLeading) {
            HStack {
                DragCardToSendGridIcon()
                    .frame(width: T.layout.headerButton, height: T.layout.headerButton)
                    .background(T.ColorToken.headerButton, in: Circle())
                    .offset(x: -spread)
                Spacer()
                Image(systemName: "plus")
                    .font(.system(size: T.replica.icon, weight: .regular))
                    .frame(width: T.layout.headerButton, height: T.layout.headerButton)
                    .background(T.ColorToken.headerButton, in: Circle())
                    .offset(x: spread)
            }
            .padding(.horizontal, T.layout.side)
            .offset(y: T.layout.headerCenter - T.layout.headerButton / 2)

            ZStack(alignment: .topLeading) {
                VStack(spacing: T.layout.rowGap) {
                    ForEach(motion.transactions.prefix(T.replica.maxRows)) { transaction in
                        DragCardToSendTransactionRow(transaction: transaction)
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
                .padding(.horizontal, T.layout.side)
                .offset(y: T.layout.rowTop)

                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Last").font(.system(size: T.type.title, weight: .regular))
                        Text("Transactions").font(.system(size: T.type.title, weight: .bold)).tracking(-0.3)
                    }
                    Spacer()
                    Image(systemName: "arrow.up.right")
                        .font(.system(size: T.replica.icon, weight: .regular))
                        .frame(width: T.home.titleButton, height: T.home.titleButton)
                        .background(T.ColorToken.headerButton, in: Circle())
                }
                .foregroundStyle(T.ColorToken.ink)
                .padding(.leading, T.layout.side + T.home.listTitleInset)
                .padding(.trailing, T.layout.side)
                .offset(y: T.layout.listTitleTop)
            }
            .offset(y: pull)
            .scaleEffect(sink)
            .animation(.spring(response: T.replica.listSpringResponse, dampingFraction: T.replica.listSpringDamping), value: motion.transactions.count)
        }
        .opacity(1 - (1 - T.fade.homeDim) * open)
        .allowsHitTesting(false)
    }

    private func smooth(_ value: Double) -> Double { value * value * (3 - 2 * value) }
}

struct DragCardToSendGridIcon: View {
    typealias T = DragCardToSendTokens
    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.fixed(T.home.dot), spacing: T.home.dotGap), count: 3), spacing: T.home.dotGap) {
            ForEach(0..<9, id: \.self) { _ in Circle().fill(T.ColorToken.ink).frame(width: T.home.dot, height: T.home.dot) }
        }
        .frame(width: T.home.gridWidth)
    }
}

struct DragCardToSendTransactionRow: View {
    typealias T = DragCardToSendTokens
    let transaction: T.Transaction

    var body: some View {
        HStack(spacing: 0) {
            DragCardToSendTransactionGlyph(incoming: transaction.incoming)
                .stroke(T.ColorToken.ink, style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                .frame(width: T.home.rowGlyph, height: T.home.rowGlyph)
                .frame(width: T.layout.rowIcon, height: T.layout.rowIcon)
                .background(T.ColorToken.rowIcon, in: Circle())
            VStack(alignment: .leading, spacing: T.home.rowDateGap) {
                Text(transaction.name).font(.system(size: T.type.rowName - 1, weight: .semibold)).lineLimit(1)
                Text(transaction.date).font(.system(size: T.type.rowMeta)).foregroundStyle(T.ColorToken.mute)
            }
            .padding(.leading, T.home.rowTextGap)
            Spacer(minLength: T.replica.rowMinGap)
            Text((transaction.amount > 0 ? "+" : "") + DragCardToSendFormat.money(transaction.amount))
                .font(.system(size: T.home.rowAmount, weight: .semibold).monospacedDigit())
                .foregroundStyle(transaction.amount > 0 ? T.ColorToken.positive : T.ColorToken.ink)
        }
        .padding(.horizontal, T.home.rowInset)
        .frame(height: T.layout.rowHeight)
        .background(T.ColorToken.rowFill, in: Capsule())
        .foregroundStyle(T.ColorToken.ink)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(transaction.name), \(transaction.date), \(DragCardToSendFormat.money(transaction.amount))")
    }
}

private struct DragCardToSendTransactionGlyph: Shape {
    let incoming: Bool

    func path(in rect: CGRect) -> Path {
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + rect.width * x / 20, y: rect.minY + rect.height * y / 20)
        }

        var path = Path()
        if incoming {
            path.move(to: point(14, 3))
            path.addLine(to: point(5, 12))
            path.addLine(to: point(5, 5.5))
            path.move(to: point(5, 12))
            path.addLine(to: point(11.5, 12))
        } else {
            path.move(to: point(14, 12))
            path.addLine(to: point(5, 3))
            path.addLine(to: point(5, 9.5))
            path.move(to: point(5, 3))
            path.addLine(to: point(11.5, 3))
        }
        path.move(to: point(4, 16.5))
        path.addLine(to: point(16, 16.5))
        return path
    }
}
