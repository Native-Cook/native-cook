import SwiftUI

struct DragCardToSend: View {
    static let loop: Double = DragCardToSendTokens.loop

    let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var motion: DragCardToSendMotion

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _motion = StateObject(wrappedValue: DragCardToSendMotion(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                DragCardToSendTokens.ColorToken.page
                DragCardToSendHome(motion: motion)
                Text("Drag to send")
                    .font(.system(size: DragCardToSendTokens.type.hint, weight: .medium))
                    .foregroundStyle(DragCardToSendTokens.ColorToken.mute)
                    .frame(width: proxy.size.width)
                    .position(x: proxy.size.width / 2, y: DragCardToSendTokens.layout.dragLabelTop)
                    .opacity(motion.phase == .pulling ? min(motion.cardOffset.height / DragCardToSendTokens.magnet.radius, 1) : 0)
                DragCardToSendIsland(motion: motion, size: proxy.size)
                DragCardToSendTouchView(motion: motion)
                    .accessibilityLabel("AERO card, balance \(DragCardToSendFormat.money(motion.balance))")
                    .accessibilityHint("Pull down to choose who to send money to")
                    .accessibilityAction(named: "Send money") { motion.openSheet() }
                DragCardToSendSheet(motion: motion, size: proxy.size)
                DragCardToSendPill(motion: motion, width: proxy.size.width)
                DragCardToSendCard(motion: motion, width: proxy.size.width)
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
            .clipped()
            .onAppear { motion.start(size: proxy.size, reduceMotion: reduceMotion) }
            .onDisappear { motion.stop() }
            .onChange(of: proxy.size) { _, size in motion.start(size: size, reduceMotion: reduceMotion) }
            .onChange(of: reduceMotion) { _, reduced in motion.start(size: proxy.size, reduceMotion: reduced) }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .statusBarHidden()
    }
}

enum DragCardToSendFormat {
    static func money(_ cents: Int) -> String {
        let sign = cents < 0 ? "−" : ""
        let amount = Double(abs(cents)) / 100
        return sign + amount.formatted(.currency(code: "USD").locale(Locale(identifier: "en_US")))
    }
}
