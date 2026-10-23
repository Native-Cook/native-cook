import CoreText
import SwiftUI
import UIKit

struct BeamCheckoutStepperDemo: View {
    var body: some View {
        BeamCheckoutStepper(autoPlay: true)
            .background(.black)
            .ignoresSafeArea()
            .statusBarHidden()
    }
}

struct BeamCheckoutStepper: View {
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var controller: BeamCheckoutStepperController
    @FocusState private var focusedField: Int?
    @State private var keyboardTop = CGFloat.greatestFiniteMagnitude
    @State private var fontsRegistered = false
    @State private var deliveryDragging = false

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _controller = StateObject(wrappedValue: BeamCheckoutStepperController(autoPlay: autoPlay, still: UIAccessibility.isReduceMotionEnabled))
    }

    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width / 402, geometry.size.height / 874)
            let stageTop = (geometry.size.height - 874 * scale) / 2
            let bottom = stageTop + (446 + controller.motion.frame[BeamCheckoutStepperF.panelHeight]) * scale + 12
            let lift = max(0, bottom - keyboardTop)
            ZStack {
                Color.black
                stage
                    .frame(width: 402, height: 874)
                    .scaleEffect(scale, anchor: .topLeading)
                    .frame(width: 402 * scale, height: 874 * scale, alignment: .topLeading)
                    .offset(y: -lift)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            .overlay(alignment: .top) {
                if lift > 0 {
                    LinearGradient(colors: [.black, .black, .clear], startPoint: .top, endPoint: .bottom)
                        .frame(height: 136)
                        .allowsHitTesting(false)
                }
            }
        }
        .background(.black)
        .ignoresSafeArea()
        .onAppear {
            registerFonts()
            controller.setReducedMotion(reduceMotion)
            controller.start()
        }
        .onDisappear { controller.stop() }
        .onChange(of: reduceMotion) { _, value in controller.setReducedMotion(value) }
        .onChange(of: focusedField) { _, value in controller.focus(value ?? -1) }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { note in
            if let rectangle = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect {
                keyboardTop = rectangle.minY
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            keyboardTop = .greatestFiniteMagnitude
        }
    }

    private var stage: some View {
        let frame = controller.motion.frame
        let f = BeamCheckoutStepperF.self
        return ZStack(alignment: .topLeading) {
            BeamCheckoutStepperMetalView(frame: frame, clock: controller.motion.time, still: reduceMotion)
                .frame(width: 402, height: 874)
                .accessibilityHidden(true)
            ForEach(0..<5, id: \.self) { index in row(index, frame: frame) }
            ForEach(0..<5, id: \.self) { index in panel(index, frame: frame) }
            railTouch
            if frame[f.ctaSplit] > 0.02 && frame[f.ctaLens] < 0.95 {
                pressTarget(index: f.backPress, label: "Back", action: {
                    focusedField = nil
                    controller.go(max(0, controller.form.step - 1))
                }) {
                    Image(uiImage: UIImage(named: "BeamCheckoutStepper-icon-back") ?? UIImage())
                        .resizable()
                        .frame(width: 20, height: 20)
                        .opacity(frame[f.ctaSplit])
                }
                .frame(width: 56, height: 56)
                .position(x: 68, y: 446 + frame[f.panelHeight] - 44)
                .disabled(controller.form.step == 0 || controller.form.step == 4)
            }
            let split = frame[f.ctaSplit]
            let lens = frame[f.ctaLens]
            let left = 16 + 64 * split
            let ctaX = 24 + left + (149 - left) * lens
            let ctaWidth = (322 - 64 * split) + (56 - (322 - 64 * split)) * lens
            let labelTime = controller.motion.labelMix * 0.26
            let oldLabelOpacity = 1 - min(1, labelTime / 0.1)
            let newLabelOpacity = max(0, min(1, (labelTime - 0.1) / 0.16))
            pressTarget(index: f.ctaPress, label: controller.currentLabel, action: {
                focusedField = nil
                controller.next()
            }) {
                ZStack {
                    Text(controller.previousLabel)
                        .opacity(oldLabelOpacity)
                    Text(controller.currentLabel)
                        .opacity(newLabelOpacity)
                }
                .font(beamFont(bold: true, size: 17))
                .lineLimit(1)
                .foregroundStyle(frame[f.ctaDone] > 0.5 ? Color(red: 0.024, green: 0.086, blue: 0.227) : .white)
                .opacity(max(0, 1 - lens / 0.3))
            }
            .frame(width: max(56, ctaWidth), height: 56)
            .position(x: ctaX + ctaWidth / 2, y: 446 + frame[f.panelHeight] - 44)
        }
        .frame(width: 402, height: 874)
        .opacity(frame[f.present])
    }

    private func row(_ index: Int, frame: [Double]) -> some View {
        let f = BeamCheckoutStepperF.self
        let labels = ["Contact", "Shipping", "Payment", "Review", "Confirmed"]
        let names = ["mail", "box", "card", "receipt", "seal"]
        let summary: String
        switch index {
        case 0: summary = controller.form.values[0]
        case 1: summary = "\(controller.form.values[2]) · \(controller.form.express ? "Express" : "Standard")"
        case 2: summary = "•••• \(controller.form.values[4].filter(\.isNumber).suffix(4))"
        case 3: summary = controller.form.total
        default: summary = "#NC-20481"
        }
        let current = index == controller.form.step && !controller.form.paid
        let done = frame[f.rowDone + index] > 0.5
        let y = 128.0 + Double(index) * 68
        let scale = (0.965 + 0.035 * frame[f.rowRise + index]) * (1 - 0.03 * frame[f.rowPress + index])
        return pressTarget(index: f.rowPress + index, label: "Step \(index + 1) of 5, \(labels[index])", action: {
            focusedField = nil
            controller.go(index)
        }) {
            ZStack(alignment: .leading) {
                if frame[f.nodeDone + index] < 0.99 {
                    Text("\(index + 1)")
                        .font(beamFont(bold: true, size: 17))
                        .foregroundStyle(.white.opacity((1 - frame[f.nodeDone + index]) * (0.42 + 0.58 * min(1, frame[f.nodeOn + index]))))
                        .frame(width: 36)
                        .position(x: 56 + frame[f.nodeShake + index], y: 22)
                }
                Image(uiImage: UIImage(named: "BeamCheckoutStepper-icon-\(names[index])") ?? UIImage())
                    .resizable()
                    .frame(width: 20, height: 20)
                    .opacity(0.38 + 0.22 * frame[f.rowDone + index] + 0.57 * frame[f.rowActive + index])
                    .position(x: 102, y: 22)
                Text(labels[index])
                    .font(beamFont(bold: current, size: 16))
                    .foregroundStyle(.white.opacity(current ? 1 : done ? 0.58 : 0.38))
                    .frame(width: 144, alignment: .leading)
                    .position(x: 194, y: 22)
                if done {
                    Text(summary)
                        .font(beamFont(bold: false, size: 13))
                        .foregroundStyle(.white.opacity(0.56 * frame[f.rowDone + index]))
                        .lineLimit(1)
                        .frame(width: 145, alignment: .trailing)
                        .position(x: 281, y: 22)
                }
            }
            .frame(width: 402, height: 44)
            .scaleEffect(scale, anchor: UnitPoint(x: 56 / 402, y: 0.5))
        }
        .frame(width: 402, height: 44)
        .position(x: 201, y: y)
        .disabled(index > controller.form.furthest || current || controller.form.paid)
        .accessibilityValue(current ? "Current" : done ? "Completed" : "Not started")
    }

    private func panel(_ index: Int, frame: [Double]) -> some View {
        let f = BeamCheckoutStepperF.self
        let from = Int(frame[f.panelFrom].rounded())
        let to = Int(frame[f.panelTo].rounded())
        let mix = frame[f.panelMix]
        let incoming = to == index
        let opacity: Double
        if from == to { opacity = incoming ? 1 : 0 }
        else if incoming {
            let u = min(1, max(0, (mix - 0.4) / 0.6))
            opacity = u * u * (3 - 2 * u)
        } else { opacity = from == index ? 1 - min(1, max(0, mix / 0.3)) : 0 }
        let shift = incoming && from != to ? 10 * (1 - opacity) : 0
        return ZStack(alignment: .topLeading) {
            Text(["Your email", "Delivery address", "Card details", "Review order", "Order confirmed"][index])
                .font(beamFont(bold: true, size: 17))
                .foregroundStyle(Color(red: 0.96, green: 0.97, blue: 1))
                .frame(width: 280, height: 24, alignment: .leading)
                .position(x: 156, y: 28)
            Text(index == 4 ? "#NC-20481" : "\(index + 1) of 5")
                .font(beamFont(bold: false, size: 13))
                .foregroundStyle(.white.opacity(0.56))
                .position(x: 305, y: 29)
            if index == 0 { field(0, x: 16, y: 52, width: 322, frame: frame) }
            if index == 1 {
                field(1, x: 16, y: 52, width: 322, frame: frame)
                field(2, x: 16, y: 116, width: 198, frame: frame)
                field(3, x: 222, y: 116, width: 116, frame: frame)
                deliveryControl(frame: frame)
            }
            if index == 2 {
                field(4, x: 16, y: 52, width: 322, frame: frame)
                field(5, x: 16, y: 116, width: 157, frame: frame)
                field(6, x: 181, y: 116, width: 157, frame: frame)
            }
            if index == 3 { reviewLines }
            if index == 4 {
                Text("Receipt sent to \(controller.form.values[0])")
                    .font(beamFont(bold: false, size: 14))
                    .foregroundStyle(.white.opacity(0.56))
                    .lineLimit(1)
                    .frame(width: 322, alignment: .leading)
                    .position(x: 177, y: 58)
            }
            if index < 3 && !controller.form.errorMessage.isEmpty {
                Text(controller.form.errorMessage)
                    .font(beamFont(bold: false, size: 13))
                    .foregroundStyle(Color(red: 1, green: 0.54, blue: 0.58))
                    .frame(width: 322, alignment: .leading)
                    .position(x: 177, y: [108.0, 232, 172][index] + 16)
                    .opacity(frame[f.error])
            }
        }
        .frame(width: 354, height: frame[f.panelHeight], alignment: .topLeading)
        .opacity(opacity)
        .offset(x: 24, y: 446 + shift)
        .allowsHitTesting(controller.form.step == index)
        .accessibilityHidden(controller.form.step != index)
    }

    private func field(_ index: Int, x: Double, y: Double, width: Double, frame: [Double]) -> some View {
        let names = ["Email", "Street address", "City", "ZIP", "Card number", "MM / YY", "CVC"]
        let value = controller.form.values[index]
        let floating = frame[BeamCheckoutStepperF.fieldFloat + index]
        let isCurrent = controller.form.step == (index == 0 ? 0 : index < 4 ? 1 : 2)
        let binding = Binding<String>(get: { controller.form.values[index] }, set: { controller.edit(index, $0) })
        return ZStack(alignment: .topLeading) {
            Text(names[index])
                .font(beamFont(bold: false, size: 16))
                .foregroundStyle(.white.opacity(0.56))
                .scaleEffect(1 - 0.28 * floating, anchor: .topLeading)
                .offset(x: 16, y: 18 - 11 * floating)
                .allowsHitTesting(false)
            TextField("", text: binding)
                .textInputAutocapitalization(index == 0 || index >= 3 ? .never : .words)
                .autocorrectionDisabled()
                .keyboardType(index == 0 ? .emailAddress : index >= 3 ? .numberPad : .default)
                .textContentType(index == 0 ? .emailAddress : index == 1 ? .streetAddressLine1 : index == 3 ? .postalCode : nil)
                .submitLabel([0, 3, 6].contains(index) ? .done : .next)
                .onSubmit {
                    if [0, 3, 6].contains(index) {
                        controller.next()
                        focusedField = nil
                    } else { focusedField = index + 1 }
                }
                .focused($focusedField, equals: index)
                .font(beamFont(bold: false, size: 16))
                .foregroundStyle(.white)
                .tint(Color(red: 0.37, green: 0.66, blue: 1))
                .padding(.horizontal, 16)
                .padding(.top, 22)
                .frame(width: width, height: 56)
                .disabled(!isCurrent || controller.form.paid)
                .accessibilityLabel(names[index] + (controller.form.errorField == index ? ", error: \(controller.form.errorMessage)" : ""))
        }
        .frame(width: width, height: 56, alignment: .topLeading)
        .position(x: x + width / 2, y: y + 28)
        .accessibilityValue(value)
    }

    private func deliveryControl(frame: [Double]) -> some View {
        let segment = HStack(spacing: 0) {
            ForEach(0..<2, id: \.self) { choice in
                pressTarget(index: BeamCheckoutStepperF.segPress, label: choice == 0 ? "Standard, free" : "Express, $12", action: {
                    controller.delivery(choice == 1)
                }) {
                    Text(choice == 0 ? "Standard · Free" : "Express · $12")
                        .font(beamFont(bold: true, size: 14))
                        .foregroundStyle(.white.opacity(controller.form.express == (choice == 1) ? 1 : 0.56))
                }
                .frame(width: 161, height: 48)
                .accessibilityAddTraits(controller.form.express == (choice == 1) ? .isSelected : [])
            }
        }
        return segment
            .frame(width: 322, height: 48)
            .position(x: 177, y: 208)
            .simultaneousGesture(DragGesture(minimumDistance: 5)
                .onChanged { gesture in
                    if !deliveryDragging {
                        deliveryDragging = true
                        controller.beginDeliveryDrag()
                    }
                    controller.updateDeliveryDrag(gesture.translation.width)
                }
                .onEnded { gesture in
                    let projectedSpeed = (gesture.predictedEndTranslation.width - gesture.translation.width) / 0.25
                    controller.endDeliveryDrag(projectedSpeed)
                    deliveryDragging = false
                })
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Delivery speed")
    }

    private var reviewLines: some View {
        let values = controller.form.values
        let lines = [
            ("Contact", values[0]),
            ("Ship to", "\(values[1]), \(values[2])"),
            ("Delivery", controller.form.express ? "Express · 2–3 days" : "Standard · 5–7 days"),
            ("Card", "•••• \(values[4].filter(\.isNumber).suffix(4))")
        ]
        return ForEach(0..<4, id: \.self) { index in
            HStack {
                Text(lines[index].0).foregroundStyle(.white.opacity(0.56))
                Spacer()
                Text(lines[index].1).foregroundStyle(.white).lineLimit(1)
            }
            .font(beamFont(bold: false, size: 14))
            .frame(width: 322, height: 32)
            .position(x: 177, y: 68 + Double(index) * 32)
        }
    }

    private var railTouch: some View {
        BeamCheckoutStepperRailTouch(controller: controller) { target in
            focusedField = nil
            controller.go(target)
        }
        .frame(width: 60, height: 328)
        .position(x: 56, y: 264)
        .allowsHitTesting(!controller.form.paid)
        .accessibilityHidden(true)
    }

    private func pressTarget<Label: View>(index: Int, label: String, action: @escaping () -> Void, @ViewBuilder content: () -> Label) -> some View {
        Button(action: action) { content().frame(maxWidth: .infinity, maxHeight: .infinity) }
            .buttonStyle(BeamCheckoutStepperButtonStyle(controller: controller, index: index))
            .accessibilityLabel(label)
            .accessibilityAddTraits(.isButton)
    }

    private func beamFont(bold: Bool, size: CGFloat) -> Font {
        .custom(bold ? "Inter-Bold" : "Inter-Regular", size: size)
    }

    private func registerFonts() {
        guard !fontsRegistered else { return }
        fontsRegistered = true
        for name in ["BeamCheckoutStepper-Inter-Regular", "BeamCheckoutStepper-Inter-Bold"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }

}

private struct BeamCheckoutStepperButtonStyle: ButtonStyle {
    let controller: BeamCheckoutStepperController
    let index: Int

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, down in
                controller.press(index, down: down)
            }
    }
}

private struct BeamCheckoutStepperRailTouch: UIViewRepresentable {
    let controller: BeamCheckoutStepperController
    let tap: (Int) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(controller: controller, tap: tap) }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        let gesture = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handle(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = 1000
        gesture.cancelsTouchesInView = false
        gesture.delegate = context.coordinator
        view.addGestureRecognizer(gesture)
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {}

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        let controller: BeamCheckoutStepperController
        let tap: (Int) -> Void
        private var start = CGPoint.zero
        private var previous = CGPoint.zero
        private var previousTime = 0.0
        private var dragged = false

        init(controller: BeamCheckoutStepperController, tap: @escaping (Int) -> Void) {
            self.controller = controller
            self.tap = tap
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

        @objc func handle(_ gesture: UILongPressGestureRecognizer) {
            guard let view = gesture.view else { return }
            let point = gesture.location(in: view)
            let stamp = CACurrentMediaTime()
            switch gesture.state {
            case .began:
                start = point
                previous = point
                previousTime = stamp
                dragged = false
                controller.beginRail()
            case .changed:
                let displacement = point.y - start.y
                let speed = (point.y - previous.y) / max(0.001, stamp - previousTime)
                if abs(displacement) >= 5 { dragged = true }
                controller.updateRail(displacement, speed)
                previous = point
                previousTime = stamp
            case .ended, .cancelled, .failed:
                let speed = (point.y - previous.y) / max(0.001, stamp - previousTime)
                controller.endRail(speed)
                if !dragged && gesture.state == .ended {
                    let target = Int(((point.y + 100 - 128) / 68).rounded())
                    if target >= 0 && target <= controller.form.furthest && target != controller.form.step { tap(target) }
                }
            default: break
            }
        }
    }
}

#Preview { BeamCheckoutStepperDemo() }
