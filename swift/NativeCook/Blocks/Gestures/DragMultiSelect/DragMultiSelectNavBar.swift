import SwiftUI

/// Browsing: back, the tray and "Someday ⌄", the round chevron, on an opaque bar that hides the first row.
/// Selecting (as in the source): the bar goes away, the first row shows through, and a blue Done sits on its line.
struct DragMultiSelectNavBar: View {
    let engine: DragMultiSelectEngine
    let topInset: Double

    private typealias Layout = DragMultiSelectLayout
    private typealias Palette = DragMultiSelectColor

    var body: some View {
        let selecting = engine.state.isSelecting
        let fade = engine.navFade.value
        ZStack(alignment: .topLeading) {
            browse
                .opacity(1 - fade)
                .allowsHitTesting(!selecting)
            select
                .opacity(fade)
                .allowsHitTesting(selecting)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .frame(height: topInset + Layout.navTop + Layout.nav + 2, alignment: .top)
        .modifier(DragMultiSelectTouchReport(engine: engine))
    }

    private var browse: some View {
        ZStack(alignment: .topLeading) {
            Palette.background
            HStack(spacing: 0) {
                Button {} label: {
                    DragMultiSelectIcon(kind: .chevronLeft, size: 28, color: Palette.nav)
                        .frame(width: 80, height: 44, alignment: .leading)
                        .contentShape(Rectangle().inset(by: -8))
                }
                .accessibilityLabel("Back")
                title
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(.isHeader)
                Button {} label: {
                    DragMultiSelectIcon(kind: .chevronDown, size: 20, color: Palette.nav)
                        .frame(width: Layout.more, height: Layout.more)
                        .overlay { Circle().strokeBorder(Palette.nav, lineWidth: 1.5) }
                        .frame(width: 80, height: 44, alignment: .trailing)
                        .contentShape(Rectangle().inset(by: -8))
                }
                .accessibilityLabel("More")
            }
            .buttonStyle(.plain)
            .padding(.horizontal, Layout.gutter - 12)
            .frame(height: Layout.nav)
            .padding(.top, topInset + Layout.navTop)
        }
    }

    private var title: some View {
        HStack(spacing: 6) {
            // The source's yellow archive tray, drawn flat as it is there.
            RoundedRectangle(cornerRadius: 3, style: .circular)
                .fill(Palette.tray)
                .frame(width: 18, height: 14)
                .overlay(alignment: .top) {
                    RoundedRectangle(cornerRadius: 1, style: .circular)
                        .fill(Palette.trayLine)
                        .frame(width: 8, height: 2)
                        .padding(.top, 4)
                }
                .padding(.trailing, 2)
            Text("Someday")
                .font(DragMultiSelectType.nav)
                .foregroundStyle(Palette.ink)
                .frame(height: DragMultiSelectType.navLineHeight)
            DragMultiSelectIcon(kind: .chevronDown, size: 15, color: Palette.nav)
        }
    }

    private var select: some View {
        HStack(spacing: 0) {
            Spacer(minLength: 0)
            Button {
                engine.leave()
            } label: {
                Text("Done")
                    .font(DragMultiSelectType.done)
                    .foregroundStyle(Palette.white)
                    .frame(width: Layout.doneWidth, height: Layout.doneHeight)
                    .background(RoundedRectangle(cornerRadius: Layout.doneRadius, style: .circular).fill(Palette.blue))
            }
            .buttonStyle(DragMultiSelectDimStyle(pressedOpacity: DragMultiSelectTokens.Component.pressedOpacity))
            .accessibilityLabel("Done selecting")
        }
        .padding(.trailing, Layout.gutter - 4)
        .frame(height: Layout.nav)
        .padding(.top, topInset + Layout.navTop)
    }
}
