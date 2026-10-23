import SwiftUI
import UIKit

struct SignInScreen: View {
    @State private var replayID = 0
    @State private var speed = 1.0
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topLeading) {
                Color.white
                AnimationSurface(replayID: replayID, speed: speed, isPlaying: scenePhase == .active)
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Ngọc Lê. Signing in...")

                Button { replayID += 1 } label: {
                    ReplayChevron()
                        .stroke(Color(white: 17 / 255), style: StrokeStyle(lineWidth: 30 * 48 / 512, lineCap: .round, lineJoin: .round))
                        .frame(width: 30, height: 30)
                        .frame(width: 48, height: 48)
                        .background(.white, in: Circle())
                }
                .buttonStyle(ReplayButtonStyle())
                .shadow(color: .black.opacity(0.08), radius: 18, x: 0, y: 8)
                .accessibilityLabel("Replay sign-in animation")
                .accessibilityIdentifier("replay")
                .position(x: 44, y: 92)

                HStack(spacing: 8) {
                    speedButton(0.5, "0.5×")
                    speedButton(1, "1×")
                    speedButton(2, "2×")
                }
                .padding(4)
                .background(.white, in: Capsule())
                .shadow(color: .black.opacity(0.08), radius: 18, x: 0, y: 8)
                .position(x: geometry.size.width - 20 - speedBarWidth / 2, y: geometry.size.height - 70)
            }
        }
        .ignoresSafeArea()
    }

    private func speedButton(_ value: Double, _ label: String) -> some View {
        Button { speed = value } label: {
            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .tracking(-0.2)
                .foregroundStyle(speed == value ? .white : Color(white: 17 / 255))
                .frame(width: chipWidth(label), height: 36)
                .background(speed == value ? Color(white: 17 / 255) : .clear, in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(SpeedButtonStyle())
        .accessibilityLabel("Animation speed \(label)")
        .accessibilityIdentifier("speed-\(value)")
        .accessibilityAddTraits(speed == value ? .isSelected : [])
    }

    private func chipWidth(_ label: String) -> CGFloat {
        let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 15, weight: .semibold), .kern: -0.2]
        return max(52, (label as NSString).size(withAttributes: attributes).width + 24)
    }

    private var speedBarWidth: CGFloat {
        chipWidth("0.5×") + chipWidth("1×") + chipWidth("2×") + 16 + 8
    }
}

/// Ionicons' chevron-back geometry, drawn natively at the original 30-point size.
private struct ReplayChevron: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.width * 328 / 512, y: rect.height * 112 / 512))
        path.addLine(to: CGPoint(x: rect.width * 184 / 512, y: rect.height * 256 / 512))
        path.addLine(to: CGPoint(x: rect.width * 328 / 512, y: rect.height * 400 / 512))
        return path
    }
}

private struct ReplayButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.65 : 1)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
    }
}

private struct SpeedButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.7 : 1)
    }
}
