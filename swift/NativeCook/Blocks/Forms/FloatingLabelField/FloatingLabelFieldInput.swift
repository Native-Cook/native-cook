import SwiftUI
import UIKit

/// A real text field. The screen writes its text directly, so no keystroke rebuilds the form.
final class FloatingLabelFieldTextField: UITextField {
    private let inset = UIEdgeInsets(top: 0, left: 18, bottom: 0, right: 48)
    override func textRect(forBounds bounds: CGRect) -> CGRect { bounds.inset(by: inset) }
    override func editingRect(forBounds bounds: CGRect) -> CGRect { bounds.inset(by: inset) }
    override func placeholderRect(forBounds bounds: CGRect) -> CGRect { bounds.inset(by: inset) }
}

struct FloatingLabelFieldInput: UIViewRepresentable {
    let index: Int
    let model: FloatingLabelFieldModel
    /// False while the loop draws the value itself.
    let live: Bool
    let alpha: Double

    private static let ink = UIColor(red: 0x1E / 255, green: 0x2A / 255, blue: 0x24 / 255, alpha: 1)
    private static let accent = UIColor(red: 0x1F / 255, green: 0x5C / 255, blue: 0x45 / 255, alpha: 1)

    func makeCoordinator() -> Coordinator { Coordinator(index: index, model: model) }

    func makeUIView(context: Context) -> FloatingLabelFieldTextField {
        let field = FloatingLabelFieldTextField()
        field.font = .systemFont(ofSize: 17)
        field.backgroundColor = .clear
        field.autocorrectionType = .no
        field.spellCheckingType = .no
        field.delegate = context.coordinator
        field.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .editingChanged)
        field.setContentHuggingPriority(.defaultLow, for: .horizontal)
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        switch index {
        case 0:
            field.autocapitalizationType = .words
            field.textContentType = .name
            field.returnKeyType = .next
            field.accessibilityLabel = "Full name"
        case 1:
            field.keyboardType = .emailAddress
            field.autocapitalizationType = .none
            field.textContentType = .emailAddress
            field.returnKeyType = .next
            field.accessibilityLabel = "Email"
        default:
            field.autocapitalizationType = .none
            // Not .newPassword: iOS would lay its strong-password highlight over the drawn dots.
            field.textContentType = UITextContentType(rawValue: "")
            field.isSecureTextEntry = true
            field.returnKeyType = .done
            field.accessibilityLabel = "Password"
        }
        model.inputs[index] = field
        return field
    }

    func updateUIView(_ field: FloatingLabelFieldTextField, context: Context) {
        let secure = index == 2
        let color: UIColor = live && !secure ? Self.ink : .clear
        if field.textColor != color { field.textColor = color }
        let tint: UIColor = secure ? .clear : Self.accent
        if field.tintColor != tint { field.tintColor = tint }
        if abs(Double(field.alpha) - alpha) > 0.001 { field.alpha = CGFloat(alpha) }
    }

    @MainActor
    final class Coordinator: NSObject, UITextFieldDelegate {
        let index: Int
        let model: FloatingLabelFieldModel

        init(index: Int, model: FloatingLabelFieldModel) {
            self.index = index
            self.model = model
        }

        @objc func changed(_ field: UITextField) {
            model.onChange(index, field.text ?? "")
        }

        func textFieldDidBeginEditing(_ textField: UITextField) { model.onFocus(index) }
        func textFieldDidEndEditing(_ textField: UITextField) { model.onBlur(index) }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            model.onSubmit(index)
            return false
        }

        func textField(_ field: UITextField, shouldChangeCharactersIn range: NSRange, replacementString string: String) -> Bool {
            let old = (field.text ?? "") as NSString
            let next = old.replacingCharacters(in: range, with: string)
            let limit = index == 1 ? 40 : (index == 2 ? 24 : 28)
            if next.utf16.count > limit { return false }
            guard index == 2 else { return true }
            // A secure field empties itself on the first key after refocus; write the text here instead.
            field.text = next
            model.onChange(index, next)
            return false
        }
    }
}

/// The outlined field: border, notch, drawn outline, value, label, badge, helper and strength bar.
struct FloatingLabelFieldCell: View {
    typealias M = FloatingLabelFieldMotion
    typealias RGB = FloatingLabelFieldRGB

    let index: Int
    let label: String
    let pose: M.FieldPose
    let width: CGFloat
    let height: CGFloat
    let helper: String
    let live: Bool
    let model: FloatingLabelFieldModel

    static let hairline = RGB(0xD8D1C2)
    static let ink = RGB(0x1E2A24)
    static let muted = RGB(0x7C8279)
    static let accent = RGB(0x1F5C45)
    static let accentTint = RGB(0xDFEBE2)
    static let error = RGB(0xB4442C)
    static let errorTint = RGB(0xF6E1DA)
    static let track = RGB(0xE4DECF)
    static let strengthColors = [RGB(0xB4442C), RGB(0xC98A1B), RGB(0x6E8B3D), RGB(0x2F7D55)]
    private static let words = ["Weak", "Fair", "Good", "Strong"]

    private let padX: CGFloat = 18
    private let floatScale: CGFloat = 0.78
    private let floatDX: CGFloat = 6
    private let arc: CGFloat = 7
    private let notchPad: CGFloat = 6
    private let inset: CGFloat = 1
    private let dotPitch: CGFloat = 13
    private let caretH: CGFloat = 22

    private var secure: Bool { index == 2 }

    private var labelWidth: CGFloat {
        (label as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 17, weight: .medium)]).width
    }

    private func outline(_ notchLeft: CGFloat, _ notchRight: CGFloat) -> Path {
        let r: CGFloat = 16 - inset
        let k = r * 0.5523
        let x0 = inset, y0 = inset, x1 = width - inset, y1 = height - inset
        var p = Path()
        p.move(to: CGPoint(x: notchRight, y: y0))
        p.addLine(to: CGPoint(x: x1 - r, y: y0))
        p.addCurve(to: CGPoint(x: x1, y: y0 + r), control1: CGPoint(x: x1 - r + k, y: y0), control2: CGPoint(x: x1, y: y0 + r - k))
        p.addLine(to: CGPoint(x: x1, y: y1 - r))
        p.addCurve(to: CGPoint(x: x1 - r, y: y1), control1: CGPoint(x: x1, y: y1 - r + k), control2: CGPoint(x: x1 - r + k, y: y1))
        p.addLine(to: CGPoint(x: x0 + r, y: y1))
        p.addCurve(to: CGPoint(x: x0, y: y1 - r), control1: CGPoint(x: x0 + r - k, y: y1), control2: CGPoint(x: x0, y: y1 - r + k))
        p.addLine(to: CGPoint(x: x0, y: y0 + r))
        p.addCurve(to: CGPoint(x: x0 + r, y: y0), control1: CGPoint(x: x0, y: y0 + r - k), control2: CGPoint(x: x0 + r - k, y: y0))
        p.addLine(to: CGPoint(x: notchLeft, y: y0))
        return p
    }

    private func border(_ p: M.FieldPose) -> some View {
        let notchLeft = padX
        let notchRight = min(width - 56, padX + floatDX + labelWidth * floatScale + notchPad)
        let badge = CGPoint(x: width - 27, y: height / 2)
        return Canvas { ctx, _ in
            let path = outline(notchLeft, notchRight)
            ctx.stroke(path, with: .color(Self.hairline.color), lineWidth: 1)

            let open = M.clamp01((p.float - 0.2) / 0.45)
            var notch = Path()
            if open < 0.999 {
                let middle = (notchLeft + notchRight) / 2
                let half = (notchRight - notchLeft) / 2 * open
                notch.move(to: CGPoint(x: notchLeft, y: inset))
                notch.addLine(to: CGPoint(x: middle - half, y: inset))
                notch.move(to: CGPoint(x: middle + half, y: inset))
                notch.addLine(to: CGPoint(x: notchRight, y: inset))
            }
            ctx.stroke(notch, with: .color(Self.hairline.color), lineWidth: 1)

            let e = p.error
            let lineColor = Color(.sRGB,
                                  red: Self.accent.r + (Self.error.r - Self.accent.r) * e,
                                  green: Self.accent.g + (Self.error.g - Self.accent.g) * e,
                                  blue: Self.accent.b + (Self.error.b - Self.accent.b) * e)
            if p.lineEnd - p.lineStart > 0.002 {
                let trimmed = path.trimmedPath(from: min(p.lineStart, p.lineEnd), to: p.lineEnd)
                ctx.stroke(trimmed, with: .color(lineColor), style: StrokeStyle(lineWidth: 2, lineCap: .round))
            }
            let drawn = M.clamp01(p.lineEnd - p.lineStart)
            let stub = drawn * drawn * drawn * drawn
            if stub > 0.001 {
                var c = ctx
                c.opacity = stub
                c.stroke(notch, with: .color(lineColor), lineWidth: 2)
            }

            let okOpacity = M.clamp01(p.valid * 2.5) * (1 - p.error)
            if okOpacity > 0.001 {
                var c = ctx
                c.opacity = okOpacity
                let s = 0.6 + 0.4 * M.easeOutBack(M.clamp01(p.valid / 0.6))
                c.translateBy(x: badge.x, y: badge.y)
                c.scaleBy(x: s, y: s)
                c.fill(Path(ellipseIn: CGRect(x: -11, y: -11, width: 22, height: 22)), with: .color(Self.accentTint.color))
                var check = Path()
                check.move(to: CGPoint(x: -4.6, y: 0.4))
                check.addLine(to: CGPoint(x: -1.4, y: 3.5))
                check.addLine(to: CGPoint(x: 4.8, y: -3.4))
                let end = M.clamp01((p.valid - 0.25) / 0.75)
                if end > 0 {
                    c.stroke(check.trimmedPath(from: 0, to: end), with: .color(Self.accent.color),
                             style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
            }

            let badOpacity = M.clamp01(p.error * 2)
            if badOpacity > 0.001 {
                var c = ctx
                c.opacity = badOpacity
                let s = 0.6 + 0.4 * M.easeOutBack(M.clamp01(p.error / 0.7))
                c.translateBy(x: badge.x, y: badge.y)
                c.scaleBy(x: s, y: s)
                c.fill(Path(ellipseIn: CGRect(x: -11, y: -11, width: 22, height: 22)), with: .color(Self.errorTint.color))
                var bar = Path()
                bar.move(to: CGPoint(x: 0, y: -5))
                bar.addLine(to: CGPoint(x: 0, y: 1.2))
                c.stroke(bar, with: .color(Self.error.color), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                c.fill(Path(ellipseIn: CGRect(x: -1.15, y: 3.65, width: 2.3, height: 2.3)), with: .color(Self.error.color))
            }
        }
        .frame(width: width, height: height)
        .allowsHitTesting(false)
    }

    private func dots(_ p: M.FieldPose) -> some View {
        Canvas { ctx, _ in
            for i in 0..<24 {
                let c = M.clamp01(p.count - Double(i))
                if c <= 0 { continue }
                let s = c >= 1 ? 1 : 0.4 + 0.6 * M.easeOutBack(c)
                let d = 8 * s
                let cx = CGFloat(i) * dotPitch + 4
                var layer = ctx
                layer.opacity = M.clamp01(c * 2)
                layer.fill(Path(ellipseIn: CGRect(x: cx - d / 2, y: 4 - d / 2, width: d, height: d)), with: .color(Self.ink.color))
            }
        }
        .frame(width: 24 * dotPitch, height: 8)
        .offset(x: padX, y: (height - 8) / 2)
        .opacity(p.text)
        .allowsHitTesting(false)
    }

    private func strengthColor(_ s: Double) -> RGB {
        let v = min(4, max(1, s))
        let k = min(2, Int(v - 1))
        return Self.strengthColors[k].mix(Self.strengthColors[k + 1], v - 1 - Double(k))
    }

    private func strength(_ p: M.FieldPose) -> some View {
        let color = strengthColor(p.strength).color
        return HStack(spacing: 12) {
            HStack(spacing: 5) {
                ForEach(0..<4, id: \.self) { i in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Self.track.color)
                        Capsule().fill(color)
                            .scaleEffect(x: M.clamp01(p.strength - Double(i)), y: 1, anchor: .leading)
                    }
                    .frame(height: 4)
                    .clipShape(Capsule())
                }
            }
            .frame(minWidth: 0, maxWidth: .infinity)
            ZStack(alignment: .trailing) {
                ForEach(0..<4, id: \.self) { i in
                    let d = p.strength - Double(i + 1)
                    Text(Self.words[i])
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(color)
                        .opacity(M.clamp01(1 - abs(d) * 2))
                        .offset(y: -5 * d)
                }
            }
            .frame(width: 46, height: 16, alignment: .trailing)
        }
        .frame(width: width - padX * 2, height: 16)
        .offset(x: padX, y: height + 11)
        .opacity(p.float * (1 - p.error))
        .allowsHitTesting(false)
    }

    var body: some View {
        let p = pose
        let f = CGFloat(p.float)
        let resting = Self.muted.mix(Self.accent, p.focus)
        let labelColor = resting.mix(Self.error, p.error)
        let value = (!live && !secure && p.frame >= 0) ? M.values[index][p.frame] : ""
        let caretX: CGFloat = {
            if secure { return CGFloat(p.count) * dotPitch + 1 }
            if live || p.frame < 0 { return -1 }
            return FloatingLabelFieldModel.caretStop(index, p.frame)
        }()

        ZStack(alignment: .topLeading) {
            FloatingLabelFieldPlate(width: width, height: height).equatable()
            border(p)

            FloatingLabelFieldInput(index: index, model: model, live: live, alpha: live ? p.text : 1)
                .frame(width: width, height: height)

            if !value.isEmpty {
                Text(value)
                    .font(.system(size: 17))
                    .foregroundStyle(Self.ink.color)
                    .lineLimit(1)
                    .fixedSize()
                    .frame(height: height)
                    .offset(x: padX)
                    .opacity(p.text)
                    .allowsHitTesting(false)
            }

            if secure { dots(p) }

            if caretX >= 0 {
                RoundedRectangle(cornerRadius: 1)
                    .fill(Self.accent.color)
                    .frame(width: 2, height: caretH)
                    .offset(x: padX + caretX, y: (height - caretH) / 2)
                    .opacity(p.caret * p.text)
                    .allowsHitTesting(false)
            }

            Text(label)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(labelColor.color)
                .lineLimit(1)
                .fixedSize()
                .frame(height: 22)
                .scaleEffect(1 - (1 - floatScale) * f, anchor: .leading)
                .offset(x: padX + floatDX * f + arc * CGFloat(sin(.pi * p.float)),
                        y: (height - 22) / 2 - height / 2 * f)
                .allowsHitTesting(false)
                .accessibilityHidden(true)

            Text(helper)
                .font(.system(size: 12.5, weight: .medium))
                .foregroundStyle(Self.error.color)
                .lineLimit(1)
                .frame(width: width - padX * 2, height: 16, alignment: .leading)
                .offset(x: padX, y: height + 6 - 7 * (1 - CGFloat(p.error)))
                .opacity(p.error)
                .allowsHitTesting(false)

            if secure { strength(p) }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .offset(x: M.shakeX(p.shake))
    }
}

/// The field's fill, soft shadows and inset top highlight; never redrawn while the pose moves.
struct FloatingLabelFieldPlate: View, Equatable {
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .circular)
        let shadow = Color(.sRGB, red: 31 / 255, green: 45 / 255, blue: 36 / 255, opacity: 1)
        shape
            .fill(Color(.sRGB, red: 0xFB / 255, green: 0xF9 / 255, blue: 0xF4 / 255))
            .shadow(color: shadow.opacity(0.06), radius: 11, y: 8)
            .shadow(color: shadow.opacity(0.07), radius: 1, y: 1)
            .overlay {
                Canvas { ctx, size in
                    let rect = CGRect(origin: .zero, size: size)
                    var crescent = Path(roundedRect: rect, cornerRadius: 16)
                    crescent.addPath(Path(roundedRect: rect.offsetBy(dx: 0, dy: 1), cornerRadius: 16))
                    ctx.clip(to: Path(roundedRect: rect, cornerRadius: 16))
                    ctx.fill(crescent, with: .color(.white.opacity(0.9)), style: FillStyle(eoFill: true))
                }
            }
            .frame(width: width, height: height)
            .allowsHitTesting(false)
    }
}
