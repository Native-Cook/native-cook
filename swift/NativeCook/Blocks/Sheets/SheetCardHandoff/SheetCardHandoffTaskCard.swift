import SwiftUI

struct SheetCardHandoffTask {
    enum Status { case running, later }

    var name: String
    var caption: String
    var time: String
    var color: Color
    /// Running (green dot) or later today (white ring).
    var status = Status.running
    /// How far through today's session it is, 0 to 1: the dashes under the time fill up to it.
    var progress = 0.35
}

enum SheetCardHandoffMetrics {
    static let cardHeight = 112.0
}

/// The task as a card: what the list shows, and what the flying card turns into.
struct SheetCardHandoffTaskCard: View {
    let task: SheetCardHandoffTask
    let width: Double

    private static let running = Color(hex: 0x3ddc6c)
    private static let dashes = 14

    var body: some View {
        let isRunning = task.status == .running
        let filled = Int((task.progress * Double(Self.dashes)).rounded())
        VStack(alignment: .leading, spacing: 0) {
            Text(task.name)
                .font(.system(size: 19, weight: .heavy))
                .tracking(-0.4)
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(height: 24, alignment: .leading)
            Text(task.caption)
                .font(.system(size: 12.5, weight: .medium))
                .lineSpacing(1)
                .foregroundStyle(.white.opacity(0.86))
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 3)
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(task.time)
                        .font(.system(size: 12, weight: .heavy))
                        .tracking(0.2)
                        .foregroundStyle(.white)
                    HStack(spacing: 3) {
                        ForEach(0..<Self.dashes, id: \.self) { index in
                            RoundedRectangle(cornerRadius: 1)
                                .fill(.white.opacity(index < filled ? 1 : 0.38))
                                .frame(width: 6, height: 2)
                        }
                    }
                }
                Spacer(minLength: 0)
                HStack(spacing: 6) {
                    Circle()
                        .fill(isRunning ? Self.running : .clear)
                        .overlay { if !isRunning { Circle().strokeBorder(.white, lineWidth: 1.5) } }
                        .frame(width: 7, height: 7)
                    Text(isRunning ? "RUNNING" : "LATER")
                        .font(.system(size: 10, weight: .heavy))
                        .tracking(0.6)
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 9)
                .frame(height: 22)
                .background(.black.opacity(0.12), in: Capsule())
            }
            .frame(maxHeight: .infinity, alignment: .bottom)
        }
        .padding(.horizontal, 18)
        .padding(.top, 16)
        .padding(.bottom, 14)
        .frame(width: width, height: SheetCardHandoffMetrics.cardHeight, alignment: .topLeading)
        .background {
            // A light from the top left, as on a printed card.
            LinearGradient(
                stops: [
                    .init(color: .white.opacity(0.22), location: 0),
                    .init(color: .white.opacity(0), location: 0.55),
                    .init(color: .black.opacity(0.06), location: 1),
                ],
                startPoint: .topLeading,
                endPoint: UnitPoint(x: 0.6, y: 1)
            )
            .background(task.color)
        }
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}
