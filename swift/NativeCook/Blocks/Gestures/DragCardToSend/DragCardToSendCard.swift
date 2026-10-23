import SwiftUI

struct DragCardToSendCard: View {
    typealias T = DragCardToSendTokens
    @ObservedObject var motion: DragCardToSendMotion
    let width: Double

    var body: some View {
        let cardWidth = width - T.layout.side * 2
        let lift = min(max((1 - motion.cardScale) / (1 - T.tokenScale), 0), 1)
        let tilt = min(max(motion.cardOffset.height / T.pull.full, 0), 1)
        let sheen = sin(2 * .pi * motion.clock / T.ambient.sheen) * T.cardStyle.sheenAmplitude + tilt * T.cardStyle.sheenPull + motion.sway / T.cardStyle.sheenSway

        ZStack {
            LinearGradient(colors: [T.ColorToken.cardHigh, T.ColorToken.card, T.ColorToken.card], startPoint: .topLeading, endPoint: .bottomTrailing)
            VStack(spacing: 0) {
                HStack {
                    Text("AERO")
                        .font(.system(size: T.type.brand, weight: .black, design: .default))
                        .italic()
                        .tracking(T.cardStyle.brandTracking)
                        .foregroundStyle(T.ColorToken.cardInk)
                    Spacer()
                    DragCardToSendOdometer(cents: motion.balance)
                        .offset(y: T.cardStyle.balanceYOffset)
                }
                HStack {
                    RoundedRectangle(cornerRadius: T.card.chipRadius)
                        .fill(T.ColorToken.chip)
                        .frame(width: T.card.chipWidth, height: T.card.chipHeight)
                    Spacer()
                    Text("VALID THRU").font(.system(size: T.cardStyle.validFont, weight: .semibold)).tracking(T.cardStyle.validTracking).foregroundStyle(T.ColorToken.cardFaint)
                }
                .padding(.top, T.card.chipTop)
                HStack {
                    Text("••••  ••••  ••••  1234")
                    Spacer()
                    Text("12/35")
                }
                .font(.system(size: T.type.cardSmall + 1, weight: .semibold).monospacedDigit())
                .padding(.top, T.card.numberTop)
                HStack {
                    Text("JULES MORROW").font(.system(size: T.cardStyle.holderFont, weight: .semibold)).tracking(T.cardStyle.holderTracking).foregroundStyle(T.ColorToken.cardFaint)
                    Spacer()
                }
                .padding(.top, T.card.holderTop)
                Spacer()
            }
            .padding(T.layout.cardPad)

            VStack(spacing: 0) {
                Spacer()
                HStack {
                    DragCardToSendCardAction(symbol: "arrow.up.left", label: "Send to")
                    Spacer()
                    DragCardToSendCardAction(symbol: "arrow.down.right", label: "Receive")
                    Spacer()
                    DragCardToSendCardAction(symbol: "plus", label: "Add")
                }
                .padding(.horizontal, T.layout.cardPad - T.space.sm)
                .padding(.bottom, T.card.actionsBottom)
            }
            .frame(width: cardWidth, height: T.layout.cardHeight, alignment: .bottom)

            T.ColorToken.cardDeep.opacity(T.pull.deep * min(max(motion.cardOffset.height / T.replica.pullFrame, 0), 1))
            LinearGradient(colors: [.clear, .white.opacity(T.cardStyle.sheenAlpha), .clear], startPoint: .top, endPoint: .bottom)
                .frame(width: cardWidth * T.card.sheenWidth, height: T.layout.cardHeight * T.cardStyle.sheenHeight)
                .rotationEffect(.degrees(T.card.sheenAngle))
                .offset(x: sheen * cardWidth)
        }
        .foregroundStyle(T.ColorToken.cardInk)
        .frame(width: cardWidth, height: T.layout.cardHeight, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: T.layout.cardRadius, style: .continuous))
        .shadow(color: T.ColorToken.shadow.opacity(T.cardStyle.shadowBase + T.cardStyle.shadowLift * lift), radius: T.cardStyle.radiusBase + T.cardStyle.radiusLift * lift, y: T.cardStyle.shadowY)
        .scaleEffect(motion.cardScale * motion.cardPress * (1 - T.pull.shrink * tilt))
        .rotationEffect(.degrees(motion.sway))
        .opacity(motion.cardOpacity)
        .position(x: width / 2 + motion.cardOffset.width, y: T.layout.cardTop + T.layout.cardHeight / 2 + motion.cardOffset.height)
        .allowsHitTesting(false)
    }
}

private struct DragCardToSendOdometer: View {
    typealias T = DragCardToSendTokens
    let cents: Int

    var body: some View {
        let text = DragCardToSendFormat.money(cents)
        let characters = Array(text)

        HStack(spacing: 0) {
            ForEach(characters.indices, id: \.self) { characterIndex in
                let character = characters[characterIndex]
                if let digit = character.wholeNumberValue {
                    DragCardToSendOdometerDigit(
                        value: digit,
                        index: characters[..<characterIndex].filter { $0.wholeNumberValue != nil }.count
                    )
                } else {
                    Text(character == "$" ? "$ " : String(character))
                        .font(balanceFont)
                        .tracking(T.cardStyle.balanceTracking)
                        .foregroundStyle(T.ColorToken.cardInk)
                }
            }
        }
        .frame(height: T.card.digitHeight, alignment: .top)
        .accessibilityLabel("Balance \(text)")
    }

    private var balanceFont: Font {
        .system(size: T.type.balance, weight: .bold).monospacedDigit()
    }
}

private struct DragCardToSendOdometerDigit: View {
    typealias T = DragCardToSendTokens
    let value: Int
    let index: Int
    @State private var displayedValue: Int

    init(value: Int, index: Int) {
        self.value = value
        self.index = index
        _displayedValue = State(initialValue: value)
    }

    var body: some View {
        VStack(spacing: 0) {
            ForEach(0 ..< 10, id: \.self) { digit in
                Text("\(digit)")
                    .font(balanceFont)
                    .tracking(T.cardStyle.balanceTracking)
                    .foregroundStyle(T.ColorToken.cardInk)
                    .frame(height: T.card.digitHeight, alignment: .top)
            }
        }
        .offset(y: -Double(displayedValue) * T.card.digitHeight)
        .frame(height: T.card.digitHeight, alignment: .top)
        .clipped()
        .onChange(of: value) { _, nextValue in
            withAnimation(.timingCurve(0.33, 1, 0.68, 1, duration: T.send.roll).delay(Double(index) * 0.04)) {
                displayedValue = nextValue
            }
        }
    }

    private var balanceFont: Font {
        .system(size: T.type.balance, weight: .bold).monospacedDigit()
    }
}

struct DragCardToSendCardAction: View {
    typealias T = DragCardToSendTokens
    let symbol: String
    let label: String

    var body: some View {
        VStack(spacing: T.space.sm) {
            Image(systemName: symbol)
                .font(.system(size: T.replica.icon, weight: .regular))
                .frame(width: T.layout.actionSize, height: T.layout.actionSize)
                .background(T.ColorToken.cardButton, in: Circle())
            Text(label).font(.system(size: T.type.cardLabel, weight: .semibold))
        }
        .frame(width: T.card.actionWidth)
    }
}
