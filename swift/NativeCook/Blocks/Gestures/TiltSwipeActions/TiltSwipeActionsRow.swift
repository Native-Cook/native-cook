import SwiftUI

struct TiltSwipeActionsRowView: View {
    let item: TiltSwipeActionsTransaction
    let width: CGFloat
    let splitCount: Int
    let splitCommitted: Bool
    let pull: CGFloat
    let offset: CGFloat
    let dim: CGFloat
    let flagged: Bool
    let reduceMotion: Bool
    let gesture: AnyGesture<DragGesture.Value>
    let onFlag: () -> Void
    let onDelete: () -> Void

    private var reveal: CGFloat { min(max(-offset, 0), width) }
    private var armed: CGFloat { TiltSwipeActionsCurve.interpolate(reveal, from: width * 0.55, to: width * 0.70) }

    var body: some View {
        ZStack(alignment: .trailing) {
            if offset < 0 { actions }
            content
                .offset(x: offset)
                .shadow(color: TiltSwipeActionsColor.ink.opacity(offset == 0 ? 0 : 0.12), radius: 12, y: 5)
        }
        .frame(width: width, height: TiltSwipeActionsLayout.row)
        .contentShape(Rectangle())
        .gesture(gesture)
        .opacity(1 - dim)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(item.title), \(item.category), \(item.day), \(TiltSwipeActionsData.money(item.amount).whole)\(TiltSwipeActionsData.money(item.amount).cents)\(flagged ? ", flagged" : "")")
        .accessibilityHint("Swipe right to split or left for actions")
        .accessibilityAction(named: flagged ? "Unflag" : "Flag", onFlag)
        .accessibilityAction(named: "Delete", onDelete)
    }

    @ViewBuilder
    private var content: some View {
        if pull > 0.5 && item.key == "saigon" {
            TiltSwipeActionsSplitBar(item: item, width: width, pull: pull, count: max(splitCount, 1), committed: false)
        } else {
            rowLine
        }
    }

    private var rowLine: some View {
        HStack(spacing: 0) {
            ZStack {
                Circle().stroke(TiltSwipeActionsColor.ring, lineWidth: 1)
                TiltSwipeActionsGlyph(name: item.icon, color: TiltSwipeActionsColor.ink2, size: 20)
            }
            .frame(width: TiltSwipeActionsLayout.circle, height: TiltSwipeActionsLayout.circle)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Text(item.title)
                        .font(.custom(TiltSwipeActionsFont.sansMedium, fixedSize: 16))
                        .tracking(-0.1)
                        .foregroundStyle(TiltSwipeActionsColor.ink)
                        .lineLimit(1)
                    if flagged { Circle().fill(TiltSwipeActionsColor.brassPaper).frame(width: 5, height: 5) }
                }
                Text(splitCommitted ? "Your share" : "\(item.category) · \(item.day)")
                    .font(.custom(TiltSwipeActionsFont.sans, fixedSize: 13))
                    .foregroundStyle(TiltSwipeActionsColor.ink2)
                    .lineLimit(1)
            }
            .padding(.leading, 12)
            Spacer(minLength: 4)
            VStack(alignment: .trailing, spacing: -3) {
                TiltSwipeActionsMoney(value: splitCommitted ? -TiltSwipeActionsData.shares(item.amount, count: max(splitCount, 2))[0] : item.amount)
                if splitCommitted {
                    Text("of $96.60")
                        .font(.custom(TiltSwipeActionsFont.sans, fixedSize: 12))
                        .foregroundStyle(TiltSwipeActionsColor.ink2)
                }
            }
        }
        .frame(width: width, height: TiltSwipeActionsLayout.row)
        .background(TiltSwipeActionsColor.paper)
        .overlay(alignment: .bottom) { Rectangle().fill(TiltSwipeActionsColor.rule).frame(height: 1) }
    }

    private var actions: some View {
        HStack(spacing: 0) {
            Button(action: onFlag) {
                VStack(spacing: 4) {
                    TiltSwipeActionsGlyph(name: .flag, color: TiltSwipeActionsColor.ivory, size: 20)
                    Text(flagged ? "UNFLAG" : "FLAG")
                        .font(.custom(TiltSwipeActionsFont.sansMedium, fixedSize: 11)).tracking(1.32)
                }
                .foregroundStyle(TiltSwipeActionsColor.ivory)
                .frame(width: TiltSwipeActionsLayout.tile, height: TiltSwipeActionsLayout.row)
                .background(TiltSwipeActionsColor.flag)
            }
            Button(action: onDelete) {
                VStack(spacing: 4) {
                    TiltSwipeActionsGlyph(name: .trash, color: TiltSwipeActionsColor.ivory, size: 20)
                    Text("DELETE")
                        .font(.custom(TiltSwipeActionsFont.sansMedium, fixedSize: 11)).tracking(1.32)
                }
                .foregroundStyle(TiltSwipeActionsColor.ivory)
                .frame(width: max(TiltSwipeActionsLayout.tile, reveal - TiltSwipeActionsLayout.tile), height: TiltSwipeActionsLayout.row)
                .background(TiltSwipeActionsColor.delete)
            }
        }
        .frame(width: max(144, reveal), alignment: .trailing)
        .scaleEffect(x: reduceMotion ? 1 : 0.94 + armed * 0.06, anchor: .trailing)
        .clipped()
    }
}

struct TiltSwipeActionsMoney: View {
    let value: Double
    var signed = true
    var large = false

    var body: some View {
        let money = TiltSwipeActionsData.money(value, signed: signed)
        HStack(alignment: .firstTextBaseline, spacing: 0) {
            Text(money.whole)
                .font(.custom(large ? TiltSwipeActionsFont.display : TiltSwipeActionsFont.money, fixedSize: large ? 48 : 20))
                .tracking(large ? -0.5 : -0.2)
            Text(money.cents)
                .font(.custom(large ? TiltSwipeActionsFont.money22 : TiltSwipeActionsFont.moneySmall, fixedSize: large ? 22 : 14))
        }
        .foregroundStyle(TiltSwipeActionsColor.ink)
        .fixedSize()
    }
}

struct TiltSwipeActionsSplitBar: View {
    let item: TiltSwipeActionsTransaction
    let width: CGFloat
    let pull: CGFloat
    let count: Int
    let committed: Bool

    private var visible: CGFloat {
        TiltSwipeActionsCurve.interpolate(pull, from: TiltSwipeActionsTokens.Split.piecesIn.lowerBound, to: TiltSwipeActionsTokens.Split.piecesIn.upperBound)
    }
    private var gap: CGFloat {
        let base = count >= 2 ? TiltSwipeActionsTokens.Split.gap : 0
        let detent = TiltSwipeActionsTokens.Split.detentStart + CGFloat(max(count - 2, 0)) * TiltSwipeActionsTokens.Split.detentStep
        return min(TiltSwipeActionsTokens.Split.gapMax, base + max(pull - detent, 0) * 0.07)
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            HStack(spacing: count >= 2 ? gap : 0) {
                ForEach(0..<max(count, 1), id: \.self) { index in piece(index: index) }
            }
            .frame(width: width, height: TiltSwipeActionsLayout.bar)
            .offset(y: TiltSwipeActionsLayout.inset)
            if count >= 2 { hud }
        }
        .frame(width: width, height: TiltSwipeActionsLayout.row, alignment: .topLeading)
    }

    private func piece(index: Int) -> some View {
        let pieceWidth = (width - gap * CGFloat(max(count - 1, 0))) / CGFloat(max(count, 1))
        let person = TiltSwipeActionsData.people[index]
        let value = TiltSwipeActionsData.shares(item.amount, count: max(count, 1))[index]
        let compact = count >= 4
        let inked = index == 0 && count >= 2
        let shape = TiltSwipeActionsPieceShape(first: index == 0, last: index == count - 1)
        return ZStack(alignment: .topLeading) {
            shape
                .fill(inked ? TiltSwipeActionsColor.ink : TiltSwipeActionsColor.stock)
                .overlay(shape.stroke(inked ? TiltSwipeActionsColor.ink : TiltSwipeActionsColor.pieceEdge, lineWidth: 1))
                .shadow(color: TiltSwipeActionsColor.ink.opacity(0.12), radius: 10, y: 5)
            ZStack(alignment: .topLeading) {
                TiltSwipeActionsMonogram(person: person, inked: inked)
                    .scaleEffect(compact ? 0.64 : 1)
                    .offset(x: compact ? 2 : -1, y: compact ? 2 : -1)
                if !compact {
                    Text(person.name.uppercased())
                        .font(.custom(TiltSwipeActionsFont.sansMedium, fixedSize: 10)).tracking(1.4)
                        .foregroundStyle(inked ? TiltSwipeActionsColor.brass : TiltSwipeActionsColor.brassPaper)
                        .offset(x: 45, y: 13)
                }
                HStack(alignment: .firstTextBaseline, spacing: 0) {
                    let money = TiltSwipeActionsData.money(value, signed: false)
                    Text(money.whole).font(.custom(TiltSwipeActionsFont.money, fixedSize: compact ? 15 : 18))
                    Text(money.cents).font(.custom(TiltSwipeActionsFont.moneySmall, fixedSize: compact ? 11 : 13))
                        .foregroundStyle(inked ? TiltSwipeActionsColor.ivory.opacity(0.6) : TiltSwipeActionsColor.ink2)
                }
                .foregroundStyle(inked ? TiltSwipeActionsColor.ivory : TiltSwipeActionsColor.ink)
                .fixedSize()
                .position(x: compact ? pieceWidth / 2 : 78, y: compact ? 49 : 43)
            }
            .frame(width: pieceWidth, height: TiltSwipeActionsLayout.bar, alignment: .topLeading)
            .clipShape(shape)
        }
        .frame(width: pieceWidth, height: TiltSwipeActionsLayout.bar)
        .opacity(max(visible, committed ? 1 : 0))
    }

    private var hud: some View {
        HStack(spacing: 8) {
            let amount = TiltSwipeActionsData.money(item.amount, signed: false)
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text(amount.whole).font(.custom(TiltSwipeActionsFont.money, fixedSize: 18))
                Text(amount.cents).font(.custom(TiltSwipeActionsFont.moneySmall, fixedSize: 13)).foregroundStyle(TiltSwipeActionsColor.ivory.opacity(0.6))
            }
            .foregroundStyle(TiltSwipeActionsColor.ivory)
            Text("÷").font(.custom(TiltSwipeActionsFont.sans, fixedSize: 16)).foregroundStyle(TiltSwipeActionsColor.brass)
            HStack(spacing: 2) {
                ForEach(2...5, id: \.self) { step in
                    Text("\(step)")
                        .font(.custom(step == count ? TiltSwipeActionsFont.sansSemi : TiltSwipeActionsFont.sansMedium, fixedSize: 13))
                        .foregroundStyle(step == count ? TiltSwipeActionsColor.ink : TiltSwipeActionsColor.ivory.opacity(0.42))
                        .frame(width: 28, height: 28)
                        .background(step == count ? TiltSwipeActionsColor.brass : Color.clear, in: Circle())
                }
            }
        }
        .padding(.leading, 16).padding(.trailing, 6)
        .frame(height: TiltSwipeActionsLayout.hud)
        .background(TiltSwipeActionsColor.ink, in: Capsule())
        .shadow(color: TiltSwipeActionsColor.ink.opacity(0.25), radius: 12, y: 5)
        .offset(y: -TiltSwipeActionsLayout.hudOver)
        .opacity(visible)
    }
}

struct TiltSwipeActionsPieceShape: Shape {
    let first: Bool
    let last: Bool

    func path(in rect: CGRect) -> Path {
        let outer = TiltSwipeActionsLayout.outer
        let broken: CGFloat = 4
        let topLeft = first ? outer : broken
        let bottomLeft = first ? outer : broken
        let topRight = last ? outer : broken
        let bottomRight = last ? outer : broken
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + topLeft, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - topRight, y: rect.minY))
        path.addQuadCurve(to: CGPoint(x: rect.maxX, y: rect.minY + topRight), control: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - bottomRight))
        path.addQuadCurve(to: CGPoint(x: rect.maxX - bottomRight, y: rect.maxY), control: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX + bottomLeft, y: rect.maxY))
        path.addQuadCurve(to: CGPoint(x: rect.minX, y: rect.maxY - bottomLeft), control: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + topLeft))
        path.addQuadCurve(to: CGPoint(x: rect.minX + topLeft, y: rect.minY), control: CGPoint(x: rect.minX, y: rect.minY))
        path.closeSubpath()
        return path
    }
}

struct TiltSwipeActionsMonogram: View {
    let person: TiltSwipeActionsPerson
    var inked = false
    var size: CGFloat = 28

    var body: some View {
        Circle()
            .fill(inked ? TiltSwipeActionsColor.brass : TiltSwipeActionsColor.ink)
            .overlay {
                Text(person.initials)
                    .font(.custom(TiltSwipeActionsFont.monogram, fixedSize: size == 28 ? 11 : 9))
                    .tracking(size == 28 ? 0.44 : 0)
                    .foregroundStyle(inked ? TiltSwipeActionsColor.ink : TiltSwipeActionsColor.brass)
            }
            .frame(width: size, height: size)
    }
}
