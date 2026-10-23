import SwiftUI

struct TiltSwipeActionsTabPanel: View {
    let scene: TiltSwipeActionsScene
    let reduceMotion: Bool
    @Binding var menuOpen: Bool

    private let tabs: [(TiltSwipeActionsGlyphName, String)] = [(.home, "Home"), (.chart, "Insights"), (.card, "Cards")]
    private let menu: [(String, TiltSwipeActionsGlyphName)] = [("Income", .plus), ("Expense", .bag), ("Transfer", .undo)]

    var body: some View {
        ZStack(alignment: .topLeading) {
            if menuOpen {
                TiltSwipeActionsColor.ink.opacity(0.04)
                    .contentShape(Rectangle())
                    .onTapGesture { close() }
            }
            Rectangle()
                .fill(TiltSwipeActionsColor.tabBar)
                .overlay(alignment: .top) { Rectangle().fill(TiltSwipeActionsColor.rule).frame(height: 1) }
                .frame(width: scene.width, height: scene.height - scene.barTop)
                .offset(y: scene.barTop)
            ForEach(Array(tabs.enumerated()), id: \.offset) { index, item in
                Button(action: {}) {
                    ZStack {
                        TiltSwipeActionsGlyph(name: item.0, color: index == 0 ? TiltSwipeActionsColor.ink : TiltSwipeActionsColor.ink3, size: 24)
                        if index == 0 {
                            Circle().fill(TiltSwipeActionsColor.brassDot).frame(width: 4, height: 4).offset(y: 24)
                        }
                    }
                    .frame(width: TiltSwipeActionsLayout.tab, height: TiltSwipeActionsLayout.tabHeight)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(item.1)
                .offset(x: scene.tabCenter(index) - TiltSwipeActionsLayout.tab / 2, y: scene.barTop + 8)
            }
            if menuOpen { menuCard }
            Button(action: toggle) {
                TiltSwipeActionsGlyph(name: .plus, color: TiltSwipeActionsColor.ivory, size: 22, stroke: 1.75)
                    .rotationEffect(.degrees(menuOpen && !reduceMotion ? 45 : 0))
                    .frame(width: TiltSwipeActionsLayout.fab, height: TiltSwipeActionsLayout.fab)
                    .background(TiltSwipeActionsColor.ink, in: Circle())
                    .shadow(color: TiltSwipeActionsColor.ink.opacity(0.24), radius: 10, y: 5)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(menuOpen ? "Close menu" : "Add transaction")
            .offset(x: scene.fabLeft, y: scene.fabTop + 1)
        }
        .frame(width: scene.width, height: scene.height, alignment: .topLeading)
    }

    private var menuCard: some View {
        VStack(spacing: 0) {
            ForEach(Array(menu.enumerated()), id: \.offset) { index, item in
                Button(action: close) {
                    HStack {
                        Text(item.0)
                            .font(.custom(TiltSwipeActionsFont.sansMedium, fixedSize: 16))
                            .foregroundStyle(TiltSwipeActionsColor.ink)
                        Spacer()
                        TiltSwipeActionsGlyph(name: item.1, color: TiltSwipeActionsColor.ink2, size: 20)
                    }
                    .padding(.horizontal, 20)
                    .frame(height: TiltSwipeActionsLayout.menuItem)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(item.0)
            }
        }
        .padding(.vertical, TiltSwipeActionsLayout.menuPadY)
        .frame(width: TiltSwipeActionsLayout.menuWidth)
        .background(TiltSwipeActionsColor.stock, in: RoundedRectangle(cornerRadius: TiltSwipeActionsLayout.menuRadius, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: TiltSwipeActionsLayout.menuRadius).stroke(TiltSwipeActionsColor.pieceEdge, lineWidth: 1))
        .shadow(color: TiltSwipeActionsColor.ink.opacity(0.20), radius: 18, y: 8)
        .offset(x: scene.width - TiltSwipeActionsLayout.gutter - TiltSwipeActionsLayout.menuWidth,
                y: scene.barTop - 8 - (TiltSwipeActionsLayout.menuPadY * 2 + TiltSwipeActionsLayout.menuItem * 3))
        .transition(.scale(scale: reduceMotion ? 1 : 0.96, anchor: .bottomTrailing).combined(with: .opacity))
    }

    private func toggle() {
        withAnimation(reduceMotion ? .linear(duration: TiltSwipeActionsTokens.Timing.reduced) : TiltSwipeActionsCurve.out.speed(1 / TiltSwipeActionsTokens.Timing.menuIn)) {
            menuOpen.toggle()
        }
    }

    private func close() {
        withAnimation(.easeOut(duration: reduceMotion ? TiltSwipeActionsTokens.Timing.reduced : TiltSwipeActionsTokens.Timing.menuOut)) {
            menuOpen = false
        }
    }
}
