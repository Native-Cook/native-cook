import SwiftUI

struct WalletFirstDepositText: View {
    let frame: WalletFirstDepositFrame
    let scale: Double
    let y0: Double
    var bannerPress: Double = 0

    var body: some View {
        ZStack(alignment: .topLeading) {
            label("Total balance", x: 45, baseline: 474, size: 13.3, weight: .medium, tracking: -0.2)
            balance
            bannerText
            pillText("Receive", pill: 0)
            pillText("Send", pill: 1)
            transactionText
            recentText
            analyticsText
        }
        .frame(width: 1206 * scale, height: 2780 * scale, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private var balance: some View {
        WalletFirstDepositOdometer(kind: .balance, progress: frame.balance, scale: pointScale)
            .offset(x: 50 * scale, y: y0 + 635 * scale - 0.953 * 49 * pointScale)
    }

    private var bannerText: some View {
        let width = 1159.25
        let height = 313.8
        let oldLineTwo = Text("your verification is completed.")
            .font(.system(size: 12.7 * pointScale, weight: .medium))
            .foregroundColor(.black)
            + Text(" This usually")
            .font(.system(size: 12.7 * pointScale, weight: .regular))
            .foregroundColor(WalletFirstDepositMetrics.secondary)
        let pressScale = (1 - 0.03 * frame.banner) * (1 - 0.03 * bannerPress)
        return ZStack(alignment: .topLeading) {
            Group {
                bannerLabel(
                    Text("You’ll be able to set up your debit card once")
                        .font(.system(size: 12.7 * pointScale, weight: .medium))
                        .foregroundColor(.black),
                    x: 57,
                    baseline: 842,
                    size: 12.7
                )
                bannerLabel(oldLineTwo, x: 56, baseline: 898, size: 12.7)
                bannerLabel(
                    Text("takes a few hours. Please wait…")
                        .font(.system(size: 12.7 * pointScale, weight: .regular))
                        .foregroundColor(WalletFirstDepositMetrics.secondary),
                    x: 55,
                    baseline: 953,
                    size: 12.7
                )
            }
            .opacity(1 - frame.copy)
            bannerLabel(
                Text("Verified. Your card is ready.")
                    .font(.system(size: 12.7 * pointScale, weight: .medium))
                    .foregroundColor(.black),
                x: 57,
                baseline: 842,
                size: 12.7
            )
                .opacity(frame.copy)
                .offset(y: 12 * (1 - frame.copy) * scale)
        }
        .frame(width: width * scale, height: height * scale, alignment: .topLeading)
        .scaleEffect(pressScale)
        .opacity(1 - frame.banner)
        .position(
            x: (22.25 + width / 2) * scale,
            y: y0 + (745 + height / 2 - 30 * frame.banner) * scale
        )
    }

    private func bannerLabel(
        _ content: Text,
        x: Double,
        baseline: Double,
        size: Double
    ) -> some View {
        content
            .frame(width: 1159.25 * scale, height: size * pointScale * 1.2, alignment: .leading)
            .position(
                x: (x - 22.25 + 1159.25 / 2) * scale,
                y: (baseline - 745) * scale - 0.953 * size * pointScale + size * pointScale * 0.6
            )
    }

    private func pillText(_ value: String, pill index: Int) -> some View {
        let spec = index == 0
            ? (progress: frame.pill0, x: 46.3, y: 722.4, width: 330.9, textX: 172.0)
            : (progress: frame.pill1, x: 394.2, y: 722.4, width: 330.5, textX: 546.0)
        let height = 127.2
        return ZStack(alignment: .topLeading) {
            Text(value)
                .font(.system(size: 13.3 * pointScale, weight: .medium))
                .foregroundStyle(.white)
                .frame(width: spec.width * scale, height: 13.3 * pointScale * 1.2, alignment: .leading)
                .position(
                    x: (spec.textX - spec.x + spec.width / 2) * scale,
                    y: (800 - spec.y) * scale - 0.953 * 13.3 * pointScale + 13.3 * pointScale * 0.6
                )
        }
        .frame(width: spec.width * scale, height: height * scale, alignment: .topLeading)
        .scaleEffect(0.9 + 0.1 * spec.progress)
        .opacity(min(1, spec.progress * 2))
        .position(x: (spec.x + spec.width / 2) * scale, y: y0 + (spec.y + height / 2) * scale)
    }

    private var transactionText: some View {
        Group {
            Group {
                label("Transactions", x: 45, baseline: 1002, size: 13.3, weight: .medium, tracking: -0.2)
                label("See all", x: 1015, baseline: 1000, size: 13.3, weight: .medium, color: Color(hex: 0x6d6d72))
            }
            .opacity(frame.txHead)
            .offset(y: 48 * (1 - frame.txHead) * scale)
            Group {
                label("Hearth Coffee", x: 237, baseline: 1150, size: 16.4, weight: .medium)
                label("Purchase. Yesterday 8:10 am", x: 239, baseline: 1207, size: 13.3, color: WalletFirstDepositMetrics.secondary, tracking: -0.35)
                rightLabel("$4.85", right: 1132, baseline: 1180, size: 17.4, weight: .medium, tracking: -1.2)
            }
            .opacity(frame.txRow0)
            .offset(y: 48 * (1 - frame.txRow0) * scale)
            Group {
                label("Dieter E.", x: 239, baseline: 1369, size: 16.4, weight: .medium)
                label("Received. 12 Nov 9:20 am", x: 239, baseline: 1427, size: 13.3, color: WalletFirstDepositMetrics.secondary, tracking: -0.35)
                rightLabel(
                    "+ $109.25",
                    right: 1132,
                    baseline: 1400,
                    size: 17.4,
                    weight: .medium,
                    color: Color(
                        red: interpolate(0, 31.0 / 255, frame.green),
                        green: interpolate(0, 159.0 / 255, frame.green),
                        blue: interpolate(0, 59.0 / 255, frame.green)
                    ),
                    tracking: -1.2
                )
            }
            .opacity(frame.txRow1)
            .offset(y: 48 * (1 - frame.txRow1) * scale)
        }
    }

    private var recentText: some View {
        let recentX = interpolate(51, 46, frame.reflow)
        let recentY = interpolate(1173, 1615, frame.reflow)
        return Group {
            label("Recent payees", x: recentX, baseline: recentY, size: 13.3, weight: .medium, tracking: -0.2)
            Group {
                actionCopy("Invite friends", "Share the app", x: 78.75)
                actionCopy("Send money", "Start transfer", x: 467.6)
                actionCopy("Receive funds", "Share details", x: 856.45)
            }
            .opacity(1 - frame.actionsOut)
            .offset(y: -10 * frame.actionsOut * scale)
            Group {
                ForEach(Array(["Konstantin", "Vasya", "Aleksa", "Joshua"].enumerated()), id: \.offset) { index, name in
                    Text(name)
                        .font(.system(size: 13.3 * pointScale, weight: .medium))
                        .tracking(-0.2 * pointScale)
                        .frame(width: 285.3 * scale, height: 13.3 * pointScale * 1.2)
                        .position(
                            x: (164.9 + Double(index) * 291.3) * scale,
                            y: y0 + 1901 * scale - 0.953 * 13.3 * pointScale + 13.3 * pointScale * 0.6
                        )
                }
                label("See all", x: 1014, baseline: 1612, size: 13.3, weight: .medium, color: Color(hex: 0x6d6d72))
            }
            .opacity(frame.names)
            .offset(y: 18 * (1 - frame.names) * scale)
        }
    }

    private func actionCopy(_ title: String, _ subtitle: String, x: Double) -> some View {
        Group {
            label(title, x: x, baseline: 1519, size: 13.3, weight: .medium, tracking: -0.2)
            label(subtitle, x: x, baseline: 1573, size: 13.3, color: WalletFirstDepositMetrics.secondary)
        }
    }

    private var analyticsText: some View {
        let cardTop = interpolate(1823.2, 2147.5, frame.reflow2)
        let spentY = cardTop + interpolate(88.8, 87.5, frame.plot)
        let valueY = cardTop + interpolate(164.8, 156.5, frame.plot)
        let labelY = cardTop + interpolate(256.8, 157.5, frame.plot)
        let axisXs = [81.0, 251, 425.5, 599, 774.5, 947.5, 1120]
        return Group {
            label("Analytics", x: 45, baseline: interpolate(1773, 2097, frame.reflow2), size: 13.3, weight: .medium, tracking: -0.2)
            label("More", x: 1038, baseline: interpolate(1771, 2096, frame.reflow2), size: 13.3, weight: .medium, color: Color(hex: 0x6d6d72))
            label("Spent this week", x: interpolate(76, 69, frame.plot), baseline: spentY, size: 13.8, color: WalletFirstDepositMetrics.secondary)
            WalletFirstDepositOdometer(kind: .spent, progress: frame.spentValue, scale: pointScale)
                .offset(x: interpolate(74, 68, frame.plot) * scale, y: y0 + valueY * scale - 0.953 * 19.4 * pointScale)
            rightLabel(
                "$312",
                right: 1123,
                baseline: labelY - 16 * frame.label,
                size: 13.1,
                color: Color(hex: 0x9c9ca0),
                tracking: -1.2
            )
            .opacity(1 - frame.label)
            rightLabel(
                "$1,92k",
                right: 1128,
                baseline: labelY + 16 * (1 - frame.label),
                size: 10.5,
                color: Color(hex: 0x9c9ca0),
                tracking: -0.4
            )
            .opacity(frame.label)
            ForEach(0..<7, id: \.self) { index in
                centeredLabel(
                    "\(index + 1)",
                    centerX: axisXs[index],
                    baseline: cardTop + interpolate(519.8, 470.5, frame.plot),
                    size: 11.8,
                    color: Color(hex: 0xc5c5c9)
                )
            }
        }
    }

    private func label(
        _ value: String,
        x: Double,
        baseline: Double,
        size: Double,
        weight: Font.Weight = .regular,
        color: Color = .black,
        tracking: Double = 0
    ) -> some View {
        Text(value)
            .font(.system(size: size * pointScale, weight: weight))
            .foregroundStyle(color)
            .tracking(tracking * pointScale)
            .frame(width: 1180 * scale, height: size * pointScale * 1.2, alignment: .leading)
            .position(
                x: (x + 590) * scale,
                y: y0 + baseline * scale - 0.953 * size * pointScale + size * pointScale * 0.6
            )
    }

    private func rightLabel(
        _ value: String,
        right: Double,
        baseline: Double,
        size: Double,
        weight: Font.Weight = .regular,
        color: Color = .black,
        tracking: Double = 0
    ) -> some View {
        Text(value)
            .font(.system(size: size * pointScale, weight: weight))
            .foregroundStyle(color)
            .tracking(tracking * pointScale)
            .frame(width: 600 * scale, height: size * pointScale * 1.2, alignment: .trailing)
            .position(
                x: (right - 300) * scale,
                y: y0 + baseline * scale - 0.953 * size * pointScale + size * pointScale * 0.6
            )
    }

    private func centeredLabel(
        _ value: String,
        centerX: Double,
        baseline: Double,
        size: Double,
        color: Color
    ) -> some View {
        Text(value)
            .font(.system(size: size * pointScale, weight: .regular))
            .foregroundStyle(color)
            .frame(width: 40, height: size * pointScale * 1.2, alignment: .center)
            .position(
                x: centerX * scale,
                y: y0 + baseline * scale - 0.953 * size * pointScale + size * pointScale * 0.6
            )
    }

    private var pointScale: Double { scale * 3 }
}

private func interpolate(_ from: Double, _ to: Double, _ amount: Double) -> Double {
    from + (to - from) * amount
}
