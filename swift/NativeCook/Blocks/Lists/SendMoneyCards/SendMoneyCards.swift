import SwiftUI
import UIKit

struct SendMoneyCards: View {
    static let loop: Double = SendMoneyCardsSpec.loop

    var autoPlay = false
    @State private var model: SendMoneyCardsModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _model = State(initialValue: SendMoneyCardsModel(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { geometry in
            let windowInset = UIApplication.shared.connectedScenes
                .compactMap { ($0 as? UIWindowScene)?.keyWindow?.safeAreaInsets.top }
                .first ?? 0
            let safeTop = max(geometry.safeAreaInsets.top, windowInset)
            let offsetY = SendMoneyCardsSpec.islandOffset(safeTop: safeTop)
            let width = geometry.size.width
            let height = geometry.size.height
            let _ = model.revision

            TimelineView(.animation(paused: !model.needsTimeline)) { timeline in
                let pose = model.pose(at: timeline.date.timeIntervalSinceReferenceDate)
                SendMoneyCardsScreen(
                    width: width,
                    height: height,
                    offsetY: offsetY,
                    pose: pose,
                    reduced: reduceMotion,
                    model: model
                )
            }
            .onAppear {
                model.updateLayout(width: width, height: height, offsetY: offsetY, reduced: reduceMotion)
            }
            .onChange(of: width) { _, newValue in
                model.updateLayout(width: newValue, height: height, offsetY: offsetY, reduced: reduceMotion)
            }
            .onChange(of: height) { _, newValue in
                model.updateLayout(width: width, height: newValue, offsetY: offsetY, reduced: reduceMotion)
            }
            .onChange(of: reduceMotion) { _, newValue in
                model.updateLayout(width: width, height: height, offsetY: offsetY, reduced: newValue)
            }
        }
        .background(SendMoneyCardsSpec.Colour.screen)
        .onDisappear { model.stop() }
    }
}

private struct SendMoneyCardsScreen: View {
    let width: Double
    let height: Double
    let offsetY: Double
    let pose: SendMoneyCardsSpec.Pose
    let reduced: Bool
    let model: SendMoneyCardsModel

    var body: some View {
        let visibleSheet = reduced ? (pose.fade > 0.001 ? 1.0 : 0.0) : pose.sheet
        let pill = SendMoneyCardsSpec.pillBox(progress: reduced ? 0 : visibleSheet, width: width, offsetY: offsetY)
        let tab = SendMoneyCardsSpec.tabBox(arm: reduced ? 0 : pose.arm, progress: visibleSheet, pill: pill)
        let sheet = SendMoneyCardsSpec.sheetBox(progress: visibleSheet, width: width, height: height,
                                                offsetY: offsetY, closing: pose.closing)
        let openForAccessibility = model.isOpen || visibleSheet > 0.5
        ZStack(alignment: .topLeading) {
            SendMoneyCardsSpec.Colour.screen
            SendMoneyCardsHome(width: width, offsetY: offsetY, pose: pose, reduced: reduced,
                               onSend: model.accessibilitySend)
                .opacity(reduced ? 1 - pose.fade : 1)
                .accessibilityHidden(openForAccessibility)
            SendMoneyCardsGoo(width: width, height: height, pill: pill, tab: tab, sheet: sheet,
                               fade: pose.fade, reduced: reduced)
                .allowsHitTesting(false)
            SendMoneyCardsPill(width: width, offsetY: offsetY, pill: pill, bloom: sheet,
                               arm: pose.arm, sheet: pose.sheet, fade: pose.fade, reduced: reduced)
                .allowsHitTesting(false)
            SendMoneyCardsSheet(width: width, height: height, offsetY: offsetY, box: sheet,
                                progress: pose.sheet, fade: pose.fade, closing: pose.closing,
                                reduced: reduced, pressedTarget: model.pressedTarget,
                                onClose: model.closeButton)
                .accessibilityHidden(!openForAccessibility)
            SendMoneyCardsTouch(model: model, width: width, height: height, offsetY: offsetY)
                .accessibilityHidden(true)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipped()
    }
}

private struct SendMoneyCardsHome: View {
    let width: Double
    let offsetY: Double
    let pose: SendMoneyCardsSpec.Pose
    let reduced: Bool
    let onSend: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            headerButtons
                .opacity(1 - min(1, pose.sheet * 1.6))
            ZStack(alignment: .topLeading) {
                Text("Drag to send")
                    .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.regular,
                                                    size: SendMoneyCardsSpec.FontSize.hint))
                    .foregroundStyle(SendMoneyCardsSpec.Colour.inkSoft)
                    .frame(width: width, height: 22, alignment: .center)
                    .offset(y: offsetY + SendMoneyCardsSpec.hintY - 11 - SendMoneyCardsSpec.pressLift +
                            pose.hintLift - pose.lift)
                    .opacity(pose.arm)
                SendMoneyCardsPaymentCard(width: width - SendMoneyCardsSpec.Card.inset * 2,
                                          onSend: onSend)
                    .offset(x: SendMoneyCardsSpec.Card.inset, y: offsetY + SendMoneyCardsSpec.Card.top)
                transactionHeading
                ForEach(SendMoneyCardsSpec.payments) { payment in
                    SendMoneyCardsPaymentRow(payment: payment, width: width - SendMoneyCardsSpec.ListLayout.inset * 2)
                        .offset(x: SendMoneyCardsSpec.ListLayout.inset,
                                y: offsetY + SendMoneyCardsSpec.ListLayout.top +
                                Double(payment.id) * SendMoneyCardsSpec.ListLayout.pitch)
                }
            }
            .offset(y: reduced ? 0 : pose.lift)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var headerButtons: some View {
        let top = offsetY + SendMoneyCardsSpec.Header.buttonY - SendMoneyCardsSpec.Header.button / 2
        return ZStack(alignment: .topLeading) {
            Circle()
                .fill(SendMoneyCardsSpec.Colour.chip)
                .frame(width: SendMoneyCardsSpec.Header.button, height: SendMoneyCardsSpec.Header.button)
                .overlay {
                    SendMoneyCardsGlyph(kind: .grid, color: SendMoneyCardsSpec.Colour.ink,
                                        size: SendMoneyCardsSpec.Header.gridGlyph)
                }
                .offset(x: SendMoneyCardsSpec.Header.inset, y: top)
                .accessibilityElement()
                .accessibilityLabel("Menu")
                .accessibilityAddTraits(.isButton)
            Circle()
                .fill(SendMoneyCardsSpec.Colour.chip)
                .frame(width: SendMoneyCardsSpec.Header.button, height: SendMoneyCardsSpec.Header.button)
                .overlay {
                    SendMoneyCardsGlyph(kind: .plus, color: SendMoneyCardsSpec.Colour.ink,
                                        size: SendMoneyCardsSpec.Header.plusGlyph, stroke: 1.5)
                }
                .offset(x: width - SendMoneyCardsSpec.Header.inset - SendMoneyCardsSpec.Header.button, y: top)
                .accessibilityElement()
                .accessibilityLabel("Add card")
                .accessibilityAddTraits(.isButton)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var transactionHeading: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Last")
                    .font(.system(size: SendMoneyCardsSpec.FontSize.sectionLead, weight: .regular))
                    .foregroundStyle(SendMoneyCardsSpec.Colour.inkSoft)
                Text("Transactions")
                    .font(.system(size: SendMoneyCardsSpec.FontSize.sectionTitle, weight: .semibold))
                    .foregroundStyle(SendMoneyCardsSpec.Colour.ink)
            }
            .tracking(-0.2)
            .offset(x: SendMoneyCardsSpec.ListLayout.inset, y: offsetY + SendMoneyCardsSpec.ListLayout.titleY)
            Circle()
                .fill(SendMoneyCardsSpec.Colour.chip)
                .frame(width: SendMoneyCardsSpec.ListLayout.arrowButton,
                       height: SendMoneyCardsSpec.ListLayout.arrowButton)
                .overlay {
                    SendMoneyCardsGlyph(kind: .arrowUpRight, color: SendMoneyCardsSpec.Colour.ink,
                                        size: SendMoneyCardsSpec.ListLayout.arrowGlyph, stroke: 1.5)
                }
                .position(x: SendMoneyCardsSpec.ListLayout.arrowX,
                          y: offsetY + SendMoneyCardsSpec.ListLayout.arrowY)
                .accessibilityElement()
                .accessibilityLabel("All transactions")
                .accessibilityAddTraits(.isButton)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct SendMoneyCardsPaymentCard: View {
    let width: Double
    let onSend: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: SendMoneyCardsSpec.Card.radius, style: .continuous)
                .fill(SendMoneyCardsSpec.Colour.blue)
            Text(SendMoneyCardsSpec.network)
                .font(.system(size: SendMoneyCardsSpec.FontSize.brand, weight: .black).italic())
                .tracking(-3.4)
                .foregroundStyle(.white)
                .offset(x: SendMoneyCardsSpec.Card.padX, y: SendMoneyCardsSpec.Card.brandY - 13)
            Text(SendMoneyCardsSpec.balance)
                .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.semibold,
                                                size: SendMoneyCardsSpec.FontSize.balance))
                .tracking(-0.3)
                .foregroundStyle(.white)
                .frame(width: width - SendMoneyCardsSpec.Card.padRight, height: 44, alignment: .topTrailing)
                .offset(y: SendMoneyCardsSpec.Card.brandY - 19)
            RoundedRectangle(cornerRadius: SendMoneyCardsSpec.Card.chipRadius)
                .fill(SendMoneyCardsSpec.Colour.cardChip)
                .frame(width: SendMoneyCardsSpec.Card.chipWidth, height: SendMoneyCardsSpec.Card.chipHeight)
                .offset(x: SendMoneyCardsSpec.Card.padX, y: SendMoneyCardsSpec.Card.chipY)
            ForEach(0..<12, id: \.self) { index in
                let group = index / 4
                let dot = index % 4
                Circle()
                    .fill(.white)
                    .frame(width: SendMoneyCardsSpec.Card.dotSize, height: SendMoneyCardsSpec.Card.dotSize)
                    .offset(x: SendMoneyCardsSpec.Card.dotsX + Double(group) * SendMoneyCardsSpec.Card.groupPitch +
                            Double(dot) * SendMoneyCardsSpec.Card.dotPitch,
                            y: SendMoneyCardsSpec.Card.dotsCenterY - SendMoneyCardsSpec.Card.dotSize / 2)
            }
            Text(SendMoneyCardsSpec.last4)
                .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.semibold,
                                                size: SendMoneyCardsSpec.FontSize.number))
                .foregroundStyle(.white)
                .offset(x: SendMoneyCardsSpec.Card.last4X, y: SendMoneyCardsSpec.Card.numberY - 10)
            Text("VALID THRU")
                .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.medium,
                                                size: SendMoneyCardsSpec.FontSize.tiny))
                .tracking(0.2)
                .foregroundStyle(SendMoneyCardsSpec.Colour.blueText)
                .frame(width: width - SendMoneyCardsSpec.Card.padRight, height: 10, alignment: .topTrailing)
                .offset(y: SendMoneyCardsSpec.Card.validY - 5)
            Text(SendMoneyCardsSpec.valid)
                .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.semibold,
                                                size: SendMoneyCardsSpec.FontSize.valid))
                .foregroundStyle(.white)
                .frame(width: width - SendMoneyCardsSpec.Card.validRight, height: 20, alignment: .topTrailing)
                .offset(y: SendMoneyCardsSpec.Card.numberY - 9)
            Text(SendMoneyCardsSpec.holder)
                .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.medium,
                                                size: SendMoneyCardsSpec.FontSize.tiny))
                .tracking(0.2)
                .foregroundStyle(SendMoneyCardsSpec.Colour.blueText)
                .offset(x: SendMoneyCardsSpec.Card.padX, y: SendMoneyCardsSpec.Card.nameY - 5)
            HStack(spacing: 0) {
                SendMoneyCardsAction(label: "Send to", kind: .arrowUpLeft)
                Spacer(minLength: 0)
                SendMoneyCardsAction(label: "Receive", kind: .arrowDownLeft)
                Spacer(minLength: 0)
                SendMoneyCardsAction(label: "Add", kind: .plus)
            }
            .frame(width: width - 38)
            .offset(x: 19, y: SendMoneyCardsSpec.Card.actionY - SendMoneyCardsSpec.Card.action / 2)
        }
        .frame(width: width, height: SendMoneyCardsSpec.Card.height, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Card ending \(SendMoneyCardsSpec.last4), balance \(SendMoneyCardsSpec.balance.replacingOccurrences(of: " ", with: ""))")
        .accessibilityHint("Drag up to the top to send money")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: "Send money") { onSend() }
    }
}

private struct SendMoneyCardsAction: View {
    let label: String
    let kind: SendMoneyCardsGlyph.Kind

    var body: some View {
        VStack(spacing: SendMoneyCardsSpec.Card.labelGap) {
            Circle()
                .fill(SendMoneyCardsSpec.Colour.blueLift)
                .frame(width: SendMoneyCardsSpec.Card.action, height: SendMoneyCardsSpec.Card.action)
                .overlay {
                    SendMoneyCardsGlyph(kind: kind, color: .white,
                                        size: SendMoneyCardsSpec.Card.actionGlyph,
                                        stroke: label == "Add" ? 1.5 : 1.6)
                }
            Text(label)
                .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.regular,
                                                size: SendMoneyCardsSpec.FontSize.action))
                .foregroundStyle(.white)
                .frame(width: 80, height: 18)
        }
        .frame(width: SendMoneyCardsSpec.Card.action, alignment: .center)
    }
}

private struct SendMoneyCardsPaymentRow: View {
    let payment: SendMoneyCardsSpec.Payment
    let width: Double

    var body: some View {
        HStack(spacing: 0) {
            Circle()
                .fill(SendMoneyCardsSpec.Colour.rowIcon)
                .frame(width: SendMoneyCardsSpec.ListLayout.icon, height: SendMoneyCardsSpec.ListLayout.icon)
                .overlay {
                    SendMoneyCardsGlyph(kind: .ledger(incoming: payment.incoming),
                                        color: SendMoneyCardsSpec.Colour.inkSoft,
                                        size: SendMoneyCardsSpec.ListLayout.glyph, stroke: 1.5)
                }
            VStack(alignment: .leading, spacing: 0) {
                Text(payment.merchant)
                    .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.medium,
                                                    size: SendMoneyCardsSpec.FontSize.rowName))
                    .foregroundStyle(SendMoneyCardsSpec.Colour.ink)
                    .frame(height: 19)
                Text(payment.date)
                    .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.regular,
                                                    size: SendMoneyCardsSpec.FontSize.rowMeta))
                    .foregroundStyle(SendMoneyCardsSpec.Colour.muted)
                    .frame(height: 17)
            }
            .padding(.leading, SendMoneyCardsSpec.ListLayout.nameGap)
            Spacer(minLength: 0)
            Text(payment.amount)
                .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.regular,
                                                size: SendMoneyCardsSpec.FontSize.rowAmount))
                .monospacedDigit()
                .foregroundStyle(SendMoneyCardsSpec.Colour.inkSoft)
                .frame(height: 14)
        }
        .padding(.leading, SendMoneyCardsSpec.ListLayout.iconPad)
        .padding(.trailing, SendMoneyCardsSpec.ListLayout.padRight)
        .frame(width: width, height: SendMoneyCardsSpec.ListLayout.rowHeight)
        .background(SendMoneyCardsSpec.Colour.row,
                    in: Capsule())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(payment.merchant), \(payment.date), \(payment.amount.replacingOccurrences(of: " ", with: ""))")
    }
}

private struct SendMoneyCardsGoo: View {
    let width: Double
    let height: Double
    let pill: SendMoneyCardsSpec.Box
    let tab: SendMoneyCardsSpec.Box
    let sheet: SendMoneyCardsSpec.Box
    let fade: Double
    let reduced: Bool

    var body: some View {
        Canvas { context, _ in
            context.addFilter(.blur(radius: SendMoneyCardsSpec.gooBlur))
            context.addFilter(.alphaThreshold(
                min: SendMoneyCardsSpec.gooAlphaBias.magnitude / SendMoneyCardsSpec.gooAlphaMultiplier,
                color: .black
            ))
            context.drawLayer { layer in
                fill(pill, in: &layer)
                fill(tab, in: &layer)
                if !reduced { fill(sheet, in: &layer) }
            }
            if reduced {
                var layer = context
                layer.opacity = fade
                fill(sheet, in: &layer)
            }
        }
        .frame(width: width, height: height)
    }

    private func fill(_ box: SendMoneyCardsSpec.Box, in context: inout GraphicsContext) {
        let rect = CGRect(x: box.x, y: box.y, width: box.width, height: box.height)
        context.fill(Path(roundedRect: rect, cornerRadius: box.radius), with: .color(.black))
    }
}

private struct SendMoneyCardsPill: View {
    let width: Double
    let offsetY: Double
    let pill: SendMoneyCardsSpec.Box
    let bloom: SendMoneyCardsSpec.Box
    let arm: Double
    let sheet: Double
    let fade: Double
    let reduced: Bool

    var body: some View {
        let restLeft = (width - SendMoneyCardsSpec.Pill.width) / 2
        let shown = reduced ? 1 - fade : 1 - SendMoneyCardsSpec.clamp((sheet - 0.25) / 0.25)
        let open = reduced ? fade : SendMoneyCardsSpec.clamp((sheet - 0.35) / 0.2)
        let drop = reduced ? 0 : max(0, bloom.y + bloom.height - (offsetY + SendMoneyCardsSpec.Tab.bottom)) *
            SendMoneyCardsSpec.Tab.arrowFollow
        ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    Text("Send ")
                        .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.regular,
                                                        size: SendMoneyCardsSpec.FontSize.pill))
                        .foregroundStyle(SendMoneyCardsSpec.Colour.pillMuted)
                        .overlay(alignment: .leading) {
                            Text("to")
                                .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.medium,
                                                                size: SendMoneyCardsSpec.FontSize.pill))
                                .foregroundStyle(.white)
                                .offset(x: 42)
                        }
                        .offset(x: SendMoneyCardsSpec.Pill.padX, y: 10.5)
                    ZStack(alignment: .topLeading) {
                        ForEach(Array(SendMoneyCardsSpec.favourites.enumerated()), id: \.element.id) { index, person in
                            SendMoneyCardsAssets.image(person.image)
                                .resizable()
                                .scaledToFill()
                                .frame(width: SendMoneyCardsSpec.Pill.avatar, height: SendMoneyCardsSpec.Pill.avatar)
                                .clipShape(Circle())
                                .overlay(Circle().stroke(.black, lineWidth: 1.5))
                                .offset(x: Double(index) * SendMoneyCardsSpec.Pill.avatarStep)
                                .zIndex(Double(index))
                        }
                    }
                    .frame(width: SendMoneyCardsSpec.Pill.avatar + SendMoneyCardsSpec.Pill.avatarStep * 2,
                           height: SendMoneyCardsSpec.Pill.avatar, alignment: .topLeading)
                    .offset(x: SendMoneyCardsSpec.Pill.width - SendMoneyCardsSpec.Pill.padRight -
                            (SendMoneyCardsSpec.Pill.avatar + SendMoneyCardsSpec.Pill.avatarStep * 2), y: 8.5)
                }
                .frame(width: SendMoneyCardsSpec.Pill.width, height: SendMoneyCardsSpec.Pill.height,
                       alignment: .topLeading)
                .offset(x: restLeft - pill.x, y: offsetY + SendMoneyCardsSpec.Pill.top - pill.y)
                .opacity(shown)
            }
            .frame(width: pill.width, height: pill.height, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: max(0, pill.radius - 0.5)))
            .offset(x: pill.x, y: pill.y)
            SendMoneyCardsGlyph(kind: .arrowUpLeft, color: .white, size: 15, stroke: 1.5)
                .frame(width: 24, height: 24)
                .offset(x: width / 2 - 12, y: offsetY + SendMoneyCardsSpec.Tab.arrowY - 12 + drop)
                .opacity(arm * (1 - open))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct SendMoneyCardsSheet: View {
    let width: Double
    let height: Double
    let offsetY: Double
    let box: SendMoneyCardsSpec.Box
    let progress: Double
    let fade: Double
    let closing: Bool
    let reduced: Bool
    let pressedTarget: String?
    let onClose: () -> Void

    var body: some View {
        let transform = reduced
            ? (scale: 1.0, opacity: 1.0, translateY: 0.0)
            : SendMoneyCardsSpec.sheetContentTransform(progress: progress, closing: closing,
                                                       offsetY: offsetY, height: height)
        ZStack(alignment: .topLeading) {
            Color.black
            content
                .scaleEffect(transform.scale, anchor: .center)
                .offset(y: transform.translateY)
                .opacity(transform.opacity)
                .offset(x: -box.x, y: -box.y)
        }
        .frame(width: box.width, height: box.height, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: max(0, min(box.radius, min(box.width, box.height) / 2 - 1)),
                                    style: .continuous))
        .opacity(reduced ? fade : 1)
        .offset(x: box.x, y: box.y)
    }

    private var content: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Send")
                    .foregroundStyle(SendMoneyCardsSpec.Colour.sheetMuted)
                    .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.medium,
                                                    size: SendMoneyCardsSpec.FontSize.sheetTitle))
                    .frame(height: SendMoneyCardsSpec.FontSize.sheetTitleLine, alignment: .topLeading)
                Text("Money To")
                    .foregroundStyle(.white)
                    .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.semibold,
                                                    size: SendMoneyCardsSpec.FontSize.sheetTitle))
                    .frame(height: SendMoneyCardsSpec.FontSize.sheetTitleLine, alignment: .topLeading)
            }
            .tracking(-0.6)
            .offset(x: SendMoneyCardsSpec.Sheet.titleX, y: offsetY + SendMoneyCardsSpec.Sheet.titleY)
            Button(action: onClose) {
                SendMoneyCardsGlyph(kind: .close, color: .white,
                                    size: SendMoneyCardsSpec.Sheet.closeGlyph, stroke: 1.7)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(SendMoneyCardsPressStyle())
            .scaleEffect(pressedTarget == "close" ? SendMoneyCardsSpec.pressScale : 1)
            .accessibilityLabel("Close")
            .offset(x: width - SendMoneyCardsSpec.Sheet.closeX - 22,
                    y: offsetY + SendMoneyCardsSpec.Sheet.closeY - 22)
            ForEach(Array(SendMoneyCardsSpec.favourites.enumerated()), id: \.element.id) { index, person in
                SendMoneyCardsFavourite(person: person)
                    .offset(x: SendMoneyCardsSpec.Sheet.avatarX - 40 +
                            Double(index) * SendMoneyCardsSpec.Sheet.avatarStep,
                            y: offsetY + SendMoneyCardsSpec.Sheet.avatarsY - SendMoneyCardsSpec.Sheet.avatar / 2)
            }
            SendMoneyCardsAddNew(pressed: pressedTarget == "add")
                .offset(x: SendMoneyCardsSpec.Sheet.addNewX - 40,
                        y: offsetY + SendMoneyCardsSpec.Sheet.avatarsY - SendMoneyCardsSpec.Sheet.avatar / 2)
            VStack(alignment: .leading, spacing: 0) {
                Text("Your")
                    .foregroundStyle(SendMoneyCardsSpec.Colour.sheetMuted)
                    .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.medium,
                                                    size: SendMoneyCardsSpec.FontSize.contactsTitle))
                    .frame(height: SendMoneyCardsSpec.FontSize.contactsLine, alignment: .topLeading)
                Text("Contacts")
                    .foregroundStyle(.white)
                    .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.semibold,
                                                    size: SendMoneyCardsSpec.FontSize.contactsTitle))
                    .frame(height: SendMoneyCardsSpec.FontSize.contactsLine, alignment: .topLeading)
            }
            .offset(x: SendMoneyCardsSpec.Sheet.titleX, y: offsetY + SendMoneyCardsSpec.Sheet.contactsTitleY)
            ForEach(Array(SendMoneyCardsSpec.contacts.enumerated()), id: \.element.id) { index, person in
                SendMoneyCardsContact(person: person, pressed: pressedTarget == "contact-\(index)")
                    .offset(x: SendMoneyCardsSpec.Sheet.contactX - SendMoneyCardsSpec.Sheet.avatar / 2,
                            y: offsetY + SendMoneyCardsSpec.Sheet.contactsTop +
                            Double(index) * SendMoneyCardsSpec.Sheet.contactPitch - 22)
            }
            Button("Next") {}
                .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.medium, size: 14))
                .foregroundStyle(.white)
                .frame(width: SendMoneyCardsSpec.Sheet.buttonWidth, height: SendMoneyCardsSpec.Sheet.buttonHeight)
                .background(SendMoneyCardsSpec.Colour.blue, in: Capsule())
                .buttonStyle(SendMoneyCardsPressStyle())
                .scaleEffect(pressedTarget == "next" ? SendMoneyCardsSpec.pressScale : 1)
                .offset(x: SendMoneyCardsSpec.Sheet.buttonX,
                        y: offsetY + SendMoneyCardsSpec.Sheet.buttonTop)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }
}

private struct SendMoneyCardsFavourite: View {
    let person: SendMoneyCardsSpec.Person

    var body: some View {
        Button {} label: {
            VStack(spacing: 0) {
                SendMoneyCardsAssets.image(person.image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: SendMoneyCardsSpec.Sheet.avatar, height: SendMoneyCardsSpec.Sheet.avatar)
                    .clipShape(Circle())
                Text(person.name)
                    .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.regular,
                                                    size: SendMoneyCardsSpec.FontSize.label))
                    .foregroundStyle(.white)
                    .frame(height: 17)
            }
            .frame(width: 80)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Send to \(person.name)")
    }
}

private struct SendMoneyCardsAddNew: View {
    let pressed: Bool

    var body: some View {
        Button {} label: {
            VStack(spacing: 0) {
                Circle()
                    .fill(SendMoneyCardsSpec.Colour.addNew)
                    .frame(width: SendMoneyCardsSpec.Sheet.avatar, height: SendMoneyCardsSpec.Sheet.avatar)
                    .overlay {
                        SendMoneyCardsGlyph(kind: .plus, color: SendMoneyCardsSpec.Colour.inkSoft,
                                            size: 18, stroke: 1.3)
                    }
                Text("Add New")
                    .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.regular,
                                                    size: SendMoneyCardsSpec.FontSize.label))
                    .foregroundStyle(.white)
                    .frame(height: 17)
            }
            .frame(width: 80)
        }
        .buttonStyle(SendMoneyCardsPressStyle())
        .scaleEffect(pressed ? SendMoneyCardsSpec.pressScale : 1)
        .accessibilityLabel("Add new recipient")
    }
}

private struct SendMoneyCardsContact: View {
    let person: SendMoneyCardsSpec.Person
    let pressed: Bool

    var body: some View {
        Button {} label: {
            HStack(spacing: SendMoneyCardsSpec.Sheet.nameGap) {
                SendMoneyCardsAssets.image(person.image)
                    .resizable()
                    .scaledToFit()
                    .frame(width: SendMoneyCardsSpec.Sheet.avatar, height: SendMoneyCardsSpec.Sheet.avatar)
                Text(person.name)
                    .font(SendMoneyCardsAssets.font(SendMoneyCardsSpec.FontName.medium,
                                                    size: SendMoneyCardsSpec.FontSize.contact))
                    .foregroundStyle(.white)
                    .frame(height: 20)
            }
            .padding(.trailing, 24)
            .frame(height: 44)
        }
        .buttonStyle(SendMoneyCardsPressStyle())
        .scaleEffect(pressed ? SendMoneyCardsSpec.pressScale : 1)
        .accessibilityLabel("Send to \(person.name)")
    }
}

private struct SendMoneyCardsPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? SendMoneyCardsSpec.pressScale : 1)
    }
}

#Preview {
    SendMoneyCards(autoPlay: true)
        .ignoresSafeArea()
        .statusBarHidden()
}
