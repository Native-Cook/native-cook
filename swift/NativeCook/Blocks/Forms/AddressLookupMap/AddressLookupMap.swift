import SwiftUI
import UIKit

/*
 Address Lookup Map — the delivery step of Parcelo's checkout.

 Typing an address shows suggestions that rise in with the matched letters in bold and re-rank as
 the query grows. Picking one folds the list into the field, the drawn map
 (AddressLookupMapStreetMap, no map tiles) flies to the address, the pin drops with a bounce and a
 soft ring, and unit, postcode and city fill in one after the other. Dragging the map slides the
 town under the pin to fine-tune the drop-off point; pinching zooms.

 The loop (AddressLookupMapMotion) and a person (AddressLookupMapModel) drive the same values; the
 loop types into the field's value without focusing it, so no keyboard shows.
*/
struct AddressLookupMap: View {
    static let loop: Double = AddressLookupMapMotion.loop

    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var model = AddressLookupMapModel()
    @FocusState private var focused: AddressLookupMapModel.Field?

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    var body: some View {
        GeometryReader { outer in
            let top = outer.safeAreaInsets.top
            let bottom = outer.safeAreaInsets.bottom
            GeometryReader { inner in
                let g = AddressLookupMapGeo(W: inner.size.width, H: inner.size.height, top: top, bottom: bottom)
                screen(g)
                    .onAppear { model.configure(g, autoPlay: autoPlay, reduced: reduceMotion) }
            }
            .ignoresSafeArea()
        }
        .ignoresSafeArea(.keyboard)
        .background(AddressLookupMapGeo.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onChange(of: focused) { _, f in model.focusChanged(f) }
        .onDisappear { model.stop() }
    }

    private func screen(_ g: AddressLookupMapGeo) -> some View {
        TimelineView(.animation(minimumInterval: nil, paused: model.isPaused)) { tl in
            let now = tl.date.timeIntervalSinceReferenceDate
            let p = model.pose(now)
            let c = model.content(now)
            ZStack(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    AddressLookupMapGeo.page
                        .contentShape(Rectangle())
                        .onTapGesture { focused = nil }
                    AddressLookupMapHeader(g: g).equatable()
                    Text("Where should we deliver?")
                        .font(.system(size: g.titleSize, weight: .bold))
                        .kerning(-0.6)
                        .foregroundStyle(AddressLookupMapGeo.ink)
                        .lineLimit(1)
                        .frame(width: g.width, height: g.titleSize + 6, alignment: .leading)
                        .offset(x: g.margin, y: g.titleTop)
                        .accessibilityAddTraits(.isHeader)
                    map(g, p, now)
                    details(g, p, c)
                    continueButton(g, p)
                    suggestions(g, p, c)
                    field(g, p, c)
                }
                .offset(y: -model.raiseValue(now))
                finger(g, model.dot(now))
            }
            .frame(width: g.W, height: g.H, alignment: .topLeading)
        }
        .frame(width: g.W, height: g.H, alignment: .topLeading)
        .clipped()
    }

    // MARK: Field

    private func field(_ g: AddressLookupMapGeo, _ p: AddressLookupMapMotion.Pose, _ c: AddressLookupMapModel.Content) -> some View {
        let showClear = model.user && !model.uQuery.isEmpty
        let border = AddressLookupMapGeo.mix(AddressLookupMapGeo.hairlineRGB, AddressLookupMapGeo.accentRGB, p.focus)
        let caretX = AddressLookupMapGeo.iconX + AddressLookupMapGeo.textWidth(c.query) + 1
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.rgba(224, 86, 46, 0.12))
                .frame(width: g.width + 8, height: g.fieldH + 8)
                .offset(x: -4, y: -4)
                .opacity(p.focus)
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(AddressLookupMapGeo.card)
                    .shadow(color: Color.rgba(40, 30, 18, 0.08), radius: 1, y: 1)
                    .shadow(color: Color.rgba(40, 30, 18, 0.07), radius: 11, y: 8)
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(AddressLookupMapGeo.muted)
                    .frame(width: AddressLookupMapGeo.iconX, height: g.fieldH)
                    .opacity(1 - p.pin)
                Image(systemName: "mappin.circle.fill")
                    .font(.system(size: 19))
                    .foregroundStyle(AddressLookupMapGeo.accent)
                    .frame(width: AddressLookupMapGeo.iconX, height: g.fieldH)
                    .scaleEffect(0.6 + 0.4 * p.pin)
                    .opacity(p.pin)
                if c.query.isEmpty {
                    Text("Street, building or postcode")
                        .font(.system(size: 17))
                        .kerning(-0.2)
                        .foregroundStyle(Color(hex: 0xA9A498))
                        .lineLimit(1)
                        .frame(width: g.width - AddressLookupMapGeo.iconX - 14, height: g.fieldH, alignment: .leading)
                        .offset(x: AddressLookupMapGeo.iconX)
                        .opacity(p.placeholder)
                        .allowsHitTesting(false)
                }
                TextField("", text: Binding(get: { c.query }, set: { model.setQuery($0) }))
                    .font(.system(size: 17))
                    .kerning(-0.2)
                    .foregroundStyle(AddressLookupMapGeo.ink)
                    .tint(AddressLookupMapGeo.accent)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.words)
                    .textContentType(.fullStreetAddress)
                    .submitLabel(.search)
                    .onSubmit { model.submit() }
                    .focused($focused, equals: .query)
                    .accessibilityLabel("Delivery address")
                    .padding(.leading, AddressLookupMapGeo.iconX)
                    .padding(.trailing, showClear ? 40 : 14)
                    .frame(width: g.width, height: g.fieldH)
                    .opacity(p.text)
                RoundedRectangle(cornerRadius: 1)
                    .fill(AddressLookupMapGeo.accent)
                    .frame(width: 2, height: 22)
                    .offset(x: caretX, y: (g.fieldH - 22) / 2)
                    .opacity(p.caret)
                    .allowsHitTesting(false)
                if showClear {
                    Button { model.clearQuery(); focused = .query } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 16))
                            .foregroundStyle(Color(hex: 0xB3AEA3))
                            .frame(width: 32, height: g.fieldH)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear address")
                    .offset(x: g.width - 6 - 32)
                }
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(border.color, lineWidth: 1)
                    .allowsHitTesting(false)
            }
            .frame(width: g.width, height: g.fieldH, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous).inset(by: -30))
        }
        .frame(width: g.width, height: g.fieldH, alignment: .topLeading)
        .offset(x: g.margin, y: g.fieldTop)
    }

    // MARK: Suggestions

    private func suggestions(_ g: AddressLookupMapGeo, _ p: AddressLookupMapMotion.Pose, _ c: AddressLookupMapModel.Content) -> some View {
        let M = AddressLookupMapMotion.self
        let ch = p.choose
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(AddressLookupMapGeo.card)
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(AddressLookupMapGeo.hairline, lineWidth: 0.5))
                .shadow(color: Color.rgba(40, 30, 18, 0.08), radius: 2, y: 2)
                .shadow(color: Color.rgba(40, 30, 18, 0.16), radius: 20, y: 18)
                .frame(width: g.width, height: max(0, (8 + p.count * M.rowH) * (1 - ch * ch)))
                .offset(x: g.margin, y: g.listTop)
                .opacity(p.list * (1 - M.seg(ch, 0.5, 1)))
                .allowsHitTesting(false)
            ForEach(0 ..< AddressLookupMapData.addresses.count, id: \.self) { i in
                row(g, p, c, i)
            }
        }
    }

    private func row(_ g: AddressLookupMapGeo, _ p: AddressLookupMapMotion.Pose, _ c: AddressLookupMapModel.Content, _ i: Int) -> some View {
        let M = AddressLookupMapMotion.self
        let a = AddressLookupMapData.addresses[i]
        let yy = p.rowY.indices.contains(i) ? p.rowY[i] : 0
        let aa = M.clamp01(p.rowA.indices.contains(i) ? p.rowA[i] : 0)
        let mine = p.chosen == i
        let ch = p.choose
        let fade = mine ? 1 - M.seg(ch, 0.55, 0.9) : 1 - M.seg(ch, 0, 0.45)
        let rowTop = Double(g.listTop) + 4 + yy * M.rowH
        let fly = mine ? -ch * (rowTop - Double(g.fieldTop) + (M.rowH - Double(g.fieldH)) / 2) : 0
        let opacity = aa * fade * p.list
        let query = c.query
        return Button { model.choose(i); focused = nil } label: {
            AddressLookupMapRow(address: a, query: query)
        }
        .buttonStyle(AddressLookupMapRowStyle())
        .accessibilityLabel("\(a.street), \(a.area)")
        .frame(width: g.width - 8, height: M.rowH)
        .scaleEffect(mine ? 1 - 0.04 * ch : 1)
        .offset(x: g.margin + 4, y: g.listTop + 4 + yy * M.rowH + 10 * (1 - aa) + fly)
        .opacity(opacity)
        .allowsHitTesting(model.tappable(i))
        .accessibilityHidden(!model.rowShown(i, c))
    }

    // MARK: Map

    private func map(_ g: AddressLookupMapGeo, _ p: AddressLookupMapMotion.Pose, _ now: Double) -> some View {
        let M = AddressLookupMapMotion.self
        let cam = AddressLookupMapCamera(x: p.camX, y: p.camY, s: p.camS)
        let pin = AddressLookupMapPinPose(y: M.dropY(p.drop), squash: M.dropSquash(p.drop), alpha: p.pin, lift: p.lift, ring1: p.ring1, ring2: p.ring2)
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        return ZStack(alignment: .topLeading) {
            AddressLookupMapStreetMap(width: g.width, height: g.mapH, camera: cam, pin: pin, rotation: M.mapRot)
            callout(g, title: "Front door", sub: "Drop-off point", v: p.callA, shown: model.calloutShown(false, now))
            callout(g, title: "Side gate", sub: "Moved 9 m", v: p.callB, shown: model.calloutShown(true, now))
            Button { model.recenter() } label: {
                Image(systemName: "location.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(AddressLookupMapGeo.ink)
                    .frame(width: 36, height: 36)
                    .background(
                        Circle().fill(AddressLookupMapGeo.card)
                            .shadow(color: Color.rgba(40, 30, 18, 0.16), radius: 1, y: 1)
                            .shadow(color: Color.rgba(40, 30, 18, 0.12), radius: 7, y: 6)
                    )
                    .contentShape(Circle().inset(by: -8))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Recenter map")
            .offset(x: g.width - 12 - 36, y: g.mapH - 12 - 36)
            shape.strokeBorder(Color.white.opacity(0.5), lineWidth: 1).allowsHitTesting(false)
        }
        .frame(width: g.width, height: g.mapH, alignment: .topLeading)
        .clipShape(shape)
        .contentShape(shape)
        .background(
            shape.fill(AddressLookupMapColors.land)
                .shadow(color: Color.rgba(40, 30, 18, 0.12), radius: 1.5, y: 1)
                .shadow(color: Color.rgba(40, 30, 18, 0.12), radius: 16, y: 14)
        )
        .gesture(
            DragGesture(minimumDistance: 4)
                .onChanged { model.dragChanged($0.translation) }
                .onEnded { model.dragEnded(velocity: $0.velocity) }
                .simultaneously(with: MagnifyGesture()
                    .onChanged { model.pinchChanged($0.magnification) }
                    .onEnded { _ in model.pinchEnded() })
        )
        .offset(x: g.margin, y: g.mapTop)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Map")
    }

    private func callout(_ g: AddressLookupMapGeo, title: String, sub: String, v: Double, shown: Bool) -> some View {
        let w: CGFloat = 112, h: CGFloat = 44
        return ZStack {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(AddressLookupMapGeo.card)
                .shadow(color: Color.rgba(40, 30, 18, 0.14), radius: 1, y: 1)
                .shadow(color: Color.rgba(40, 30, 18, 0.14), radius: 9, y: 8)
            RoundedRectangle(cornerRadius: 2)
                .fill(AddressLookupMapGeo.card)
                .frame(width: 12, height: 12)
                .rotationEffect(.degrees(45))
                .offset(y: h / 2 - 1)
            VStack(spacing: 0) {
                Text(title).font(.system(size: 14, weight: .semibold)).kerning(-0.2).foregroundStyle(AddressLookupMapGeo.ink)
                Text(sub).font(.system(size: 11.5)).monospacedDigit().foregroundStyle(AddressLookupMapGeo.muted)
            }
        }
        .frame(width: w, height: h)
        .scaleEffect(0.94 + 0.06 * v)
        .offset(x: g.width / 2 - w / 2, y: g.mapH / 2 - 50 - h - 10 + 6 * (1 - v))
        .opacity(v)
        .allowsHitTesting(false)
        .accessibilityHidden(!shown)
    }

    // MARK: Details

    private func details(_ g: AddressLookupMapGeo, _ p: AddressLookupMapMotion.Pose, _ c: AddressLookupMapModel.Content) -> some View {
        let w1 = (g.width - 10) * 1.25 / 2.25
        let w2 = g.width - 10 - w1
        return ZStack(alignment: .topLeading) {
            detailBox(g, label: "Unit or flat", value: c.unit, placeholder: "Optional", fill: p.fill[0], placeholderAlpha: p.placeholder,
                      field: .unit, index: 0, tabular: false)
                .frame(width: w1, height: g.boxH)
                .offset(x: g.margin, y: g.row1)
            detailBox(g, label: "Postcode", value: c.postcode, placeholder: "0000", fill: p.fill[1], placeholderAlpha: p.placeholder,
                      field: .postcode, index: 1, tabular: true)
                .frame(width: w2, height: g.boxH)
                .offset(x: g.margin + w1 + 10, y: g.row1)
            detailBox(g, label: "City", value: c.city, placeholder: "Town or city", fill: p.fill[2], placeholderAlpha: p.placeholder,
                      field: .city, index: 2, tabular: false)
                .frame(width: w1, height: g.boxH)
                .offset(x: g.margin, y: g.row2)
            HStack(spacing: 4) {
                Text("Leave at door")
                    .font(.system(size: 15, weight: .medium))
                    .kerning(-0.2)
                    .foregroundStyle(AddressLookupMapGeo.ink)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityHidden(true)
                Toggle("Leave at door", isOn: Binding(get: { model.leaveAtDoor }, set: { model.setLeaveAtDoor($0) }))
                    .labelsHidden()
                    .tint(AddressLookupMapGeo.accent)
            }
            .padding(.horizontal, 12)
            .frame(width: w2, height: g.boxH)
            .background(AddressLookupMapBoxBackground())
            .offset(x: g.margin + w1 + 10, y: g.row2)
        }
    }

    private func detailBox(_ g: AddressLookupMapGeo, label: String, value: String, placeholder: String, fill f: Double,
                           placeholderAlpha: Double, field: AddressLookupMapModel.Field, index: Int, tabular: Bool) -> some View {
        let empty = value.isEmpty
        let alpha = empty ? placeholderAlpha * (1 - f) : f
        let rise = empty ? 0 : 7 * (1 - f)
        return ZStack(alignment: .leading) {
            AddressLookupMapBoxBackground()
            Rectangle()
                .fill(Color.rgba(224, 86, 46, 0.08))
                .opacity(empty ? 0 : 0.9 * sin(Double.pi * f))
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .allowsHitTesting(false)
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(AddressLookupMapGeo.muted)
                TextField("", text: Binding(get: { value }, set: { model.setDetail(index, $0) }),
                          prompt: Text(placeholder).foregroundStyle(Color(hex: 0xB3AEA3)))
                    .font(.system(size: 16))
                    .monospacedDigit()
                    .kerning(-0.2)
                    .foregroundStyle(AddressLookupMapGeo.ink)
                    .tint(AddressLookupMapGeo.accent)
                    .keyboardType(tabular ? .numberPad : .default)
                    .autocorrectionDisabled()
                    .focused($focused, equals: field)
                    .accessibilityLabel(label)
                    .frame(height: 22)
                    .offset(y: rise)
                    .opacity(alpha)
            }
            .padding(.horizontal, 12)
        }
    }

    // MARK: Button

    private func continueButton(_ g: AddressLookupMapGeo, _ p: AddressLookupMapMotion.Pose) -> some View {
        let label = AddressLookupMapGeo.mix(AddressLookupMapRGB(0xA29C90), AddressLookupMapRGB(0xFFF7F1), p.ready)
        return Button { model.continuePressed() } label: {
            ZStack {
                Capsule().fill(AddressLookupMapGeo.accent)
                    .shadow(color: Color.rgba(150, 50, 20, 0.35), radius: 1, y: 1)
                    .shadow(color: Color.rgba(150, 50, 20, 0.26), radius: 12, y: 10)
                    .opacity(p.ready)
                Capsule().fill(Color(hex: 0xE4DFD5))
                ZStack(alignment: .top) {
                    LinearGradient(stops: [
                        .init(color: Color(hex: 0xEC6A40), location: 0),
                        .init(color: AddressLookupMapGeo.accent, location: 0.5),
                        .init(color: Color(hex: 0xC9461F), location: 1),
                    ], startPoint: .top, endPoint: .bottom)
                    Rectangle().fill(Color.white.opacity(0.28)).frame(height: 1)
                }
                .clipShape(Capsule())
                .opacity(p.ready)
                Text("Continue to payment")
                    .font(.system(size: 17, weight: .semibold))
                    .kerning(-0.2)
                    .foregroundStyle(label.color)
            }
            .frame(width: g.width, height: g.buttonH)
        }
        .buttonStyle(AddressLookupMapPressStyle())
        .accessibilityLabel("Continue to payment")
        .offset(x: g.margin, y: g.buttonTop)
    }

    // MARK: Ghost finger

    private func finger(_ g: AddressLookupMapGeo, _ d: AddressLookupMapMotion.Dot) -> some View {
        var x = g.margin + g.width * 0.62
        var y = g.fieldTop + g.fieldH / 2
        if d.target == 1 {
            x = g.margin + g.width * 0.45
            y = g.listTop + 4 + 29
        } else if d.target == 2 {
            x = g.margin + g.width * 0.66 + d.dx
            y = g.mapTop + g.mapH * 0.66 + d.dy
        }
        let ink = Color.rgba(30, 26, 20, 1)
        return Circle()
            .fill(ink.opacity(0.16))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.75), lineWidth: 1))
            .shadow(color: ink.opacity(0.14), radius: 6, y: 4)
            .frame(width: 44, height: 44)
            .scaleEffect(d.scale)
            .offset(x: x - 22, y: y - 22 + (1 - d.alpha) * 8)
            .opacity(d.alpha)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

// MARK: - Pieces

struct AddressLookupMapHeader: View, Equatable {
    let g: AddressLookupMapGeo

    var body: some View {
        HStack(spacing: 0) {
            Image(systemName: "chevron.left")
                .font(.system(size: 19, weight: .regular))
                .foregroundStyle(AddressLookupMapGeo.ink)
                .padding(.leading, 6)
                .frame(width: 72, height: 44, alignment: .leading)
                .accessibilityLabel("Back")
            VStack(spacing: 6) {
                Text("Checkout")
                    .font(.system(size: 17, weight: .semibold))
                    .kerning(-0.3)
                    .foregroundStyle(AddressLookupMapGeo.ink)
                HStack(spacing: 4) {
                    Capsule().fill(AddressLookupMapGeo.ink).frame(width: 22, height: 3)
                    Capsule().fill(AddressLookupMapGeo.accent).frame(width: 22, height: 3)
                    Capsule().fill(Color.rgba(40, 34, 24, 0.14)).frame(width: 22, height: 3)
                }
                .accessibilityLabel("Step 2 of 3")
            }
            .frame(maxWidth: .infinity)
            Text("2 of 3")
                .font(.system(size: 13, weight: .medium))
                .monospacedDigit()
                .foregroundStyle(AddressLookupMapGeo.muted)
                .frame(width: 72, height: 44, alignment: .trailing)
        }
        .frame(width: g.W - (g.margin - 6) - g.margin, height: 44)
        .offset(x: g.margin - 6, y: g.headerTop)
    }
}

/// One suggestion: matched letters in bold.
struct AddressLookupMapRow: View {
    let address: AddressLookupMapAddress
    let query: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "mappin")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(AddressLookupMapGeo.muted)
                .frame(width: 32, height: 32)
                .background(Circle().fill(Color(hex: 0xF1EEE7)))
            VStack(alignment: .leading, spacing: 1) {
                street
                    .font(.system(size: 16))
                    .kerning(-0.2)
                    .lineLimit(1)
                Text("\(address.area) \(address.postcode)")
                    .font(.system(size: 13))
                    .foregroundStyle(AddressLookupMapGeo.muted)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(AddressLookupMapGeo.distanceKm(address))
                .font(.system(size: 13))
                .monospacedDigit()
                .foregroundStyle(AddressLookupMapGeo.muted)
        }
        .padding(.horizontal, 10)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
    }

    private var street: Text {
        let s = address.street
        let base = Color(hex: 0x4E4A42)
        guard let r = AddressLookupMapData.matchRange(s, query) else { return Text(s).foregroundStyle(base) }
        let chars = Array(s)
        let pre = String(chars[0 ..< r.lowerBound])
        let mid = String(chars[r.lowerBound ..< r.upperBound])
        let post = String(chars[r.upperBound...])
        return Text(pre).foregroundStyle(base)
            + Text(mid).fontWeight(.bold).foregroundStyle(AddressLookupMapGeo.ink)
            + Text(post).foregroundStyle(base)
    }
}

struct AddressLookupMapRowStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.rgba(40, 34, 24, configuration.isPressed ? 0.05 : 0))
            )
    }
}

struct AddressLookupMapPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: configuration.isPressed ? 0.1 : 0.16), value: configuration.isPressed)
    }
}

struct AddressLookupMapBoxBackground: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(AddressLookupMapGeo.card)
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(AddressLookupMapGeo.hairline, lineWidth: 0.5))
            .shadow(color: Color.rgba(40, 30, 18, 0.06), radius: 1, y: 1)
    }
}

struct AddressLookupMapRGB {
    var r: Double, g: Double, b: Double, a: Double

    init(_ hex: UInt32, _ a: Double = 1) {
        r = Double((hex >> 16) & 0xff) / 255
        g = Double((hex >> 8) & 0xff) / 255
        b = Double(hex & 0xff) / 255
        self.a = a
    }

    init(r: Double, g: Double, b: Double, a: Double) {
        self.r = r
        self.g = g
        self.b = b
        self.a = a
    }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }
}

// MARK: - Geometry

struct AddressLookupMapGeo: Equatable {
    static let accent = Color(hex: 0xE0562E)
    static let ink = Color(hex: 0x1E1D1A)
    static let muted = Color(hex: 0x77736A)
    static let page = Color(hex: 0xF6F4EF)
    static let card = Color(hex: 0xFFFEFB)
    static let hairline = Color.rgba(40, 34, 24, 0.12)
    static let accentRGB = AddressLookupMapRGB(0xE0562E)
    static let hairlineRGB = AddressLookupMapRGB(r: 40 / 255, g: 34 / 255, b: 24 / 255, a: 0.12)
    static let iconX: CGFloat = 44

    private static let fieldFont = UIFont.systemFont(ofSize: 17)

    static func textWidth(_ s: String) -> CGFloat {
        guard !s.isEmpty else { return 0 }
        return ceil((s as NSString).size(withAttributes: [.font: fieldFont, .kern: -0.2]).width)
    }

    static func mix(_ a: AddressLookupMapRGB, _ b: AddressLookupMapRGB, _ t: Double) -> AddressLookupMapRGB {
        let k = min(max(t, 0), 1)
        return AddressLookupMapRGB(r: a.r + (b.r - a.r) * k, g: a.g + (b.g - a.g) * k, b: a.b + (b.b - a.b) * k, a: a.a + (b.a - a.a) * k)
    }

    static func distanceKm(_ a: AddressLookupMapAddress) -> String {
        let d = hypot(a.x - AddressLookupMapMotion.overviewX, a.y - AddressLookupMapMotion.overviewY) * 0.0042 + 0.6
        return String(format: "%.1f km", d)
    }

    let W: CGFloat, H: CGFloat, compact: Bool, margin: CGFloat, width: CGFloat
    let headerTop: CGFloat, titleTop: CGFloat, titleSize: CGFloat, fieldTop: CGFloat, fieldH: CGFloat
    let mapTop: CGFloat, mapH: CGFloat, boxH: CGFloat, row1: CGFloat, row2: CGFloat
    let buttonTop: CGFloat, buttonH: CGFloat, listTop: CGFloat

    init(W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat) {
        self.W = W
        self.H = H
        compact = H < 740
        margin = max(20, (W - 440) / 2)
        width = W - margin * 2
        headerTop = top + 2
        titleTop = headerTop + (compact ? 52 : 62)
        titleSize = compact ? 23 : 27
        fieldTop = titleTop + (compact ? 38 : 46)
        fieldH = compact ? 50 : 54
        mapTop = fieldTop + fieldH + 12
        buttonH = compact ? 50 : 54
        buttonTop = H - max(bottom, 14) - 8 - buttonH
        boxH = compact ? 54 : 58
        row2 = buttonTop - 14 - boxH
        row1 = row2 - 10 - boxH
        mapH = row1 - 14 - mapTop
        listTop = fieldTop + fieldH + 6
    }
}
