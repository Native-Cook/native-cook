import CoreText
import UIKit

final class DollhouseFloorsResources {
    static let shared = DollhouseFloorsResources()
    private init() {
        for name in ["Inter-Regular", "Inter-SemiBold", "Inter-Bold", "Ionicons"] {
            if let url = Bundle.main.url(forResource: "DollhouseFloors-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }
    private var fonts: [String: UIFont] = [:]
    func font(_ weight: String, _ size: Double) -> UIFont {
        let key = "\(weight)-\(size)"
        if let face = fonts[key] { return face }
        let face = UIFont(name: weight == "Icon" ? "Ionicons" : weight == "SemiBold" ? "Inter-SemiBold" : "Inter24pt-\(weight)", size: size) ?? .systemFont(ofSize: size)
        fonts[key] = face
        return face
    }
}

final class DollhouseFloorsButton: UIButton {
    var scalesWhenPressed = true
    override func layoutSubviews() {
        super.layoutSubviews()
        guard let label = titleLabel, let text = title(for: .normal), let font = label.font else { return }
        let scale = UIScreen.main.scale
        let measured = (text as NSString).size(withAttributes: [.font: font])
        let w = ceil(measured.width * scale) / scale
        let h = ceil(font.lineHeight * scale) / scale
        // Yoga floors text origins and ceils their measured extents.
        func snap(_ x: Double) -> Double { floor(x * scale + 0.0000001) / scale }
        label.transform = .identity
        label.textAlignment = .left
        label.frame = CGRect(x: snap((bounds.width - w) / 2), y: snap((bounds.height - h) / 2), width: w, height: h)
    }
    override var isHighlighted: Bool {
        didSet { transform = isHighlighted && scalesWhenPressed ? CGAffineTransform(scaleX: 0.96, y: 0.96) : .identity }
    }
}

final class DollhouseFloorsInterfaceView: UIView, UIGestureRecognizerDelegate {
    let clock: DollhouseFloorsClock
    let resources = DollhouseFloorsResources.shared
    let ink = DollhouseFloorsArt.uiColor(0x2A2420)
    let muted = DollhouseFloorsArt.uiColor(0x7A706A)
    private var top = 0.0, bottom = 0.0
    private var current = DollhouseFloorsMotion.Frame()
    private var frozen = false
    private var labels: [String: UILabel] = [:]
    private var chips: [DollhouseFloorsButton] = []
    private let model = UIView(), info = UIView()
    private let back = DollhouseFloorsButton(), heart = DollhouseFloorsButton(), cta = DollhouseFloorsButton()
    private var start = CGPoint.zero
    private var velocity = 0.0, startTime = 0.0
    private let panVelocity = UIPanGestureRecognizer()
    private var dragging = false
    private let selection = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    init(clock: DollhouseFloorsClock) {
        self.clock = clock
        super.init(frame: .zero)
        backgroundColor = .clear
        func add(_ key: String, _ text: String, _ weight: String, _ size: Double, _ color: UIColor, _ parent: UIView? = nil, _ tracking: Double = 0) {
            let label = UILabel(); label.font = resources.font(weight, size); label.textColor = color
            if tracking != 0 { label.attributedText = NSAttributedString(string: text, attributes: [.font: label.font!, .foregroundColor: color, .kern: tracking]) }
            else { label.text = text }
            label.isAccessibilityElement = true
            labels[key] = label; (parent ?? self).addSubview(label)
        }
        add("kicker", "FOR SALE · OPEN HOUSE SAT", "SemiBold", 12, DollhouseFloorsArt.uiColor(0xB07A45), nil, 1.1)
        add("title", "Maple Row Townhouse", "Bold", 26, ink)
        add("meta", "3 bd · 2 ba · 122 m² · Built 2019", "Regular", 15, muted)
        addSubview(back); addSubview(heart)
        for b in [back, heart] { b.backgroundColor = .white; b.layer.cornerRadius = 22; b.scalesWhenPressed = false }
        setIcon(back, 61993, 22); setIcon(heart, 62327, 21)
        back.accessibilityLabel = "Back"; heart.accessibilityLabel = "Save home"
        addSubview(model)
        model.isAccessibilityElement = true; model.accessibilityTraits = .adjustable
        let press = UILongPressGestureRecognizer(target: self, action: #selector(touch(_:)))
        press.minimumPressDuration = 0; press.allowableMovement = .greatestFiniteMagnitude
        press.delegate = self; model.addGestureRecognizer(press)
        panVelocity.delegate = self; panVelocity.cancelsTouchesInView = false; model.addGestureRecognizer(panVelocity)
        addSubview(info)
        add("infoTitle", "Tap a floor to look inside", "SemiBold", 16, ink, info)
        add("infoText", "3 levels · 140 m² with the attic", "Regular", 14, muted, info)
        info.isUserInteractionEnabled = false
        for (k,name) in ["Roof", "First", "Ground", ""].enumerated() {
            let button = DollhouseFloorsButton(); button.tag = k == 3 ? -1 : 2 - k
            button.backgroundColor = .white; button.layer.cornerRadius = 20; button.layer.borderWidth = 1
            button.layer.borderColor = DollhouseFloorsArt.uiColor(0xEADFCF).cgColor
            if k < 3 {
                button.setTitle(name, for: .normal); button.titleLabel?.font = resources.font("SemiBold", 14)
                button.setTitleColor(ink, for: .normal); button.accessibilityLabel = "\(name) floor"
            }
            button.addTarget(self, action: #selector(chip(_:)), for: .touchUpInside)
            chips.append(button); addSubview(button)
        }
        add("price", "$684,000", "Bold", 22, ink)
        add("est", "Est. $3,210/mo", "Regular", 13, muted)
        cta.backgroundColor = ink; cta.layer.cornerRadius = 28
        cta.setTitle("Book a viewing", for: .normal); cta.titleLabel?.font = resources.font("SemiBold", 16)
        cta.setTitleColor(.white, for: .normal); cta.accessibilityLabel = "Book a viewing"; addSubview(cta)
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
    private func setIcon(_ button: UIButton, _ scalar: Int, _ size: Double) {
        button.setTitle(String(UnicodeScalar(scalar)!), for: .normal)
        button.titleLabel?.font = resources.font("Icon", size)
        button.setTitleColor(ink, for: .normal)
    }
    func update(_ f: DollhouseFloorsMotion.Frame, top: Double, bottom: Double, pinned: Bool) {
        let old = current; current = f; self.top = top; self.bottom = bottom; frozen = pinned
        isUserInteractionEnabled = !pinned
        model.isUserInteractionEnabled = !pinned
        for b in chips { b.isUserInteractionEnabled = !pinned }
        if f.sel != old.sel || labels["infoTitle"]?.text == "Tap a floor to look inside" {
            let names = ["Ground floor · 68 m²", "First floor · 54 m²", "Roof · 18 m²"]
            let rooms = ["Living room, kitchen, half bath", "2 bedrooms, bath, study nook", "Slate roof, attic storage, solar ready"]
            labels["infoTitle"]?.text = f.sel >= 0 ? names[f.sel] : "Tap a floor to look inside"
            labels["infoText"]?.text = f.sel >= 0 ? rooms[f.sel] : "3 levels · 140 m² with the attic"
            for b in chips where b.tag >= 0 {
                let on = b.tag == f.sel
                b.backgroundColor = on ? ink : .white; b.layer.borderColor = (on ? ink : DollhouseFloorsArt.uiColor(0xEADFCF)).cgColor
                b.setTitleColor(on ? .white : ink, for: .normal)
                b.accessibilityTraits = on ? [.button, .selected] : .button
            }
        }
        let open = f.e > 0.5
        setIcon(chips[3], open ? 62090 : 62384, 18)
        chips[3].accessibilityLabel = open ? "Stack the floors" : "Pull the floors apart"
        model.accessibilityLabel = open ? "Floors pulled apart. Tap a floor to focus it, drag down to close" : "Cut-away model of the house. Drag up to see each floor"
        setNeedsLayout()
    }
    private func pixelCeil(_ x: Double) -> Double { ceil(x * UIScreen.main.scale) / UIScreen.main.scale }
    private func labelHeight(_ key: String) -> Double { pixelCeil(labels[key]!.font.lineHeight) }
    private func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGRect {
        let s = UIScreen.main.scale
        let left = floor(x * s + 0.5 + 0.0000001) / s, top = floor(y * s + 0.5 + 0.0000001) / s
        return CGRect(x: left, y: top, width: floor((x + w) * s + 0.5 + 0.0000001) / s - left, height: floor((y + h) * s + 0.5 + 0.0000001) / s - top)
    }
    private func place(_ key: String, _ x: Double, _ y: Double, _ width: Double, center: Bool = false) {
        let label = labels[key]!; label.textAlignment = .left
        let measured = pixelCeil(((label.text ?? "") as NSString).size(withAttributes: [.font: label.font!]).width)
        let centeredX = floor((x + (width - measured) / 2) * UIScreen.main.scale + 0.0000001) / UIScreen.main.scale
        label.frame = center ? rect(centeredX, y, measured, labelHeight(key)) : rect(x, y, width, labelHeight(key))
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width, h = bounds.height, base = (h * 0.63).rounded()
        back.frame = CGRect(x: 16, y: top + 6, width: 44, height: 44)
        heart.frame = CGRect(x: w - 60, y: top + 6, width: 44, height: 44)
        place("kicker", 20, top + 62, w - 40)
        let titleY = top + 62 + labelHeight("kicker") + 4
        place("title", 20, titleY, w - 40)
        place("meta", 20, titleY + labelHeight("title") + 4, w - 40)
        model.frame = CGRect(x: 0, y: top + 150, width: w, height: base - top - 130)
        let v = DollhouseFloorsMotion.clamp((current.e - 0.6) / 0.4)
        info.transform = .identity
        info.frame = CGRect(x: 20, y: base + 40 + (1 - v) * 10, width: w - 40, height: labelHeight("infoTitle") + 3 + labelHeight("infoText"))
        info.alpha = v
        place("infoTitle", 0, 0, w - 40, center: true)
        place("infoText", 0, labelHeight("infoTitle") + 3, w - 40, center: true)
        let widths = chips.map { $0.tag < 0 ? 44.0 : pixelCeil(($0.title(for: .normal)! as NSString).size(withAttributes: [.font: resources.font("SemiBold", 14)]).width) + 38 }
        var x = (w - widths.reduce(0, +) - 24) / 2
        for (i,b) in chips.enumerated() { b.frame = rect(x, h - bottom - 96 - 40, widths[i], 40); x += widths[i] + 8 }
        let by = h - bottom - 16 - 56
        let priceH = labelHeight("price") + 2 + labelHeight("est")
        place("price", 20, by + (56 - priceH) / 2, 160)
        place("est", 20, by + (56 - priceH) / 2 + labelHeight("price") + 2, 160)
        let ctaWidth = pixelCeil(("Book a viewing" as NSString).size(withAttributes: [.font: resources.font("SemiBold", 16)]).width) + 52
        cta.frame = rect(w - 20 - ctaWidth, by, ctaWidth, 56)
    }
    @objc private func chip(_ button: UIButton) {
        guard !frozen else { return }
        selection.selectionChanged(); clock.driver.pick(button.tag); clock.wake()
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    @objc private func touch(_ gesture: UILongPressGestureRecognizer) {
        guard !frozen else { return }
        let p = gesture.location(in: self), time = ProcessInfo.processInfo.systemUptime
        if gesture.state == .began { start = p; velocity = 0; dragging = false; startTime = time }
        else if gesture.state == .changed {
            velocity = panVelocity.velocity(in: self).y
            if !dragging && abs(p.y - start.y) > 8 { dragging = true; clock.driver.panStart() }
            if dragging { clock.driver.pan(p.y - start.y); clock.wake() }
        } else if gesture.state == .ended || gesture.state == .cancelled || gesture.state == .failed {
            if dragging { clock.driver.panEnd(velocity); light.impactOccurred() }
            else if gesture.state == .ended && time - startTime <= 0.5 {
                if clock.driver.tap(p.y, width: bounds.width, height: bounds.height) { selection.selectionChanged() }
            }
            clock.wake()
        }
    }
}
