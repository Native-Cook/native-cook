import SwiftUI
import UIKit

/// The single-line field of "Add yours" (line 568): a dashed blue box that takes focus as soon as it appears.
struct FolioFeatureLibraryTopicField: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame
    let width: Double
    @FocusState private var focused: Bool

    var body: some View {
        let points = 15 * f.type.factor(maxScale: nil, scales: true)
        let binding = Binding<String>(
            get: { e.customTopic },
            set: { e.setCustomTopic($0) }
        )
        return TextField("", text: binding, prompt: Text("Your topic").foregroundColor(Color(folio: 0x7DA3E8)))
            .font(Font(UIFont.systemFont(ofSize: CGFloat(points), weight: .regular) as CTFont))
            .foregroundColor(Color(folio: 0x1E6FEF))
            .tint(Color(folio: 0x1E6FEF))
            .focused($focused)
            .submitLabel(.done)
            .onSubmit { e.addTopic() }
            .disabled(e.generating)
            .accessibilityLabel("Add your own topic")
            .padding(.horizontal, 15)
            .frame(width: CGFloat(width), height: 40)
            .overlay(FolioFeatureLibraryDashedBorder())
            .onAppear { focused = true }
            .onChange(of: focused) { _, now in
                if now {
                    e.topicFieldFocused()
                } else {
                    e.topicFieldBlurred()
                }
            }
    }
}

/// `borderWidth: 1.5, borderColor: '#1E6FEF', borderStyle: 'dashed'` on a 40 pt high pill.
struct FolioFeatureLibraryDashedBorder: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 20)
            .strokeBorder(Color(folio: 0x1E6FEF), style: StrokeStyle(lineWidth: 1.5, dash: [4.5, 4.5]))
            .allowsHitTesting(false)
    }
}

/// The topic sheet's scrolling part (lines 563-570): its title, its copy and the chips.
struct FolioFeatureLibraryTopicsContent: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            FolioFeatureLibraryLabel(text: "Select Topics", style: FolioFeatureLibraryStyles.sheetTitle, type: f.type)
                .accessibilityAddTraits(.isHeader)
            FolioFeatureLibraryLabel(
                text: "Choose topics for your book summary, or select none for a random book",
                style: FolioFeatureLibraryStyles.sheetCopy,
                type: f.type
            )
            .padding(.top, 6)
            .padding(.bottom, 30)
            chips
        }
        .padding(.horizontal, 18)
        .padding(.top, 14)
        .padding(.bottom, 24)
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }

    /// `topicWrap` (line 566): the chips sit where Yoga would put them, moved by the 240 ms layout transition.
    private var chips: some View {
        let time = f.time
        return ZStack(alignment: .topLeading) {
            ForEach(e.topicItems, id: \.self) { topic in
                topicChip(topic, time: time)
            }
            addElement(time: time)
        }
        .frame(width: CGFloat(f.width - 36), height: CGFloat(e.chipTarget.height), alignment: .topLeading)
    }

    @ViewBuilder
    private func topicChip(_ topic: String, time: Double) -> some View {
        if let frame = e.chipFrame(topic, at: time) {
            let isSelected = e.selected.contains(topic)
            let moreIndex = FolioFeatureLibraryData.moreTopics.firstIndex(of: topic)
            let fade = moreIndex.map { e.entering("more", duration: 0.26, delay: Double($0) * 0.04, at: time) } ?? 1.0
            let pop = e.zoom("check:\(topic)", spring: FolioFeatureLibrarySpring(damping: 14, stiffness: 320), at: time)
            Button {
                e.toggleTopic(topic)
            } label: {
                HStack(spacing: 6) {
                    FolioFeatureLibraryLabel(text: topic, style: FolioFeatureLibraryStyles.topicText(selected: isSelected), type: f.type)
                    if isSelected {
                        FolioFeatureLibraryIcon(name: "checkmark-circle-outline", size: 17, color: Color.white, type: f.type)
                            .scaleEffect(CGFloat(pop))
                    }
                }
                .frame(width: CGFloat(frame.width), height: 40)
                .background(chipBackground(selected: isSelected))
                .overlay(chipRing(selected: isSelected))
            }
            .buttonStyle(.plain)
            .disabled(e.generating)
            .opacity(fade)
            .offset(x: CGFloat(frame.x), y: CGFloat(frame.y))
            .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        }
    }

    /// `topic` and `topicSelected` (lines 681-682).
    private func chipBackground(selected: Bool) -> some View {
        if selected {
            return FolioFeatureLibrarySurface(
                radius: 20,
                fill: Color(folio: 0x1E6FEF),
                shadows: [FolioFeatureLibraryShadow(x: 0, y: 4, blur: 12, spread: -4, color: Color(folio: 0x1E6FEF, alpha: 0.7))]
            )
        }
        return FolioFeatureLibrarySurface(
            radius: 20,
            fill: Color(folio: 0xF2F2F4),
            shadows: FolioFeatureLibraryShadows.single(y: 2, blur: 6, opacity: 0.08)
        )
    }

    /// The `inset` half of the unselected chip's shadow: a white 1 pt ring inside its edge.
    @ViewBuilder
    private func chipRing(selected: Bool) -> some View {
        if !selected {
            RoundedRectangle(cornerRadius: 20)
                .strokeBorder(Color.white.opacity(0.9), lineWidth: 1)
                .allowsHitTesting(false)
        }
    }

    @ViewBuilder
    private func addElement(time: Double) -> some View {
        if let frame = e.chipFrame(FolioFeatureLibraryEngine.chipKeyAdd, at: time) {
            Group {
                if e.addingTopic {
                    FolioFeatureLibraryTopicField(e: e, f: f, width: frame.width)
                } else {
                    Button {
                        e.beginAddingTopic()
                    } label: {
                        HStack(spacing: 6) {
                            FolioFeatureLibraryIcon(name: "add", size: 17, color: Color(folio: 0x1E6FEF), type: f.type)
                            FolioFeatureLibraryLabel(text: "Add yours", style: FolioFeatureLibraryStyles.addTopicText, type: f.type)
                        }
                        .frame(width: CGFloat(frame.width), height: 40)
                        .overlay(FolioFeatureLibraryDashedBorder())
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(e.generating)
                    .accessibilityLabel("Add your own topic")
                }
            }
            .offset(x: CGFloat(frame.x), y: CGFloat(frame.y))
        }
    }
}

/// The `Modal` of the topic sheet (lines 558-576): a dimmed backdrop and a sheet that rises from the bottom.
struct FolioFeatureLibraryTopicsSheet: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame

    var body: some View {
        let sheetHeight = f.height - max(f.insetTop + 3, 40)
        let slide = f.reduced ? 0 : (1 - f.sheetProgress) * f.height
        let fade = f.reduced ? f.sheetProgress : 1.0
        return ZStack(alignment: .bottom) {
            Color.black.opacity(0.22)
            Button {
                e.closeTopics()
            } label: {
                Color.clear.contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss topic selection")
            sheet
                .frame(width: CGFloat(f.width), height: CGFloat(sheetHeight))
                .padding(.bottom, CGFloat(e.keyboardHeight))
        }
        .frame(width: CGFloat(f.width), height: CGFloat(f.height))
        .offset(y: CGFloat(slide))
        .opacity(fade)
    }

    private var sheet: some View {
        VStack(spacing: 0) {
            FolioFeatureLibraryBlurViewport(engine: e, scroller: .topics, enabled: e.topicsOpen && !e.topicInputActive, reduceTransparency: f.reduceTransparency) {
                FolioFeatureLibraryScroller(engine: e, id: .topics, dismissesKeyboard: true, revision: e.layoutRevision) {
                    FolioFeatureLibraryTopicsContent(e: e, f: f)
                }
            }
            .frame(maxHeight: .infinity)
            band
            footer
        }
        .background(Color(folio: 0xF6F6F6))
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 30, topTrailingRadius: 30))
    }

    /// `topicBand` (line 687): how many topics are picked.
    private var band: some View {
        let count = e.selected.count
        let text = count == 0 ? "Random book summary" : "\(count) \(count == 1 ? "topic" : "topics") selected"
        return FolioFeatureLibraryLabel(text: text, style: FolioFeatureLibraryStyles.topicCount, type: f.type, alignment: .center)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity)
            .background(Color(folio: 0xEFEDF5))
    }

    private var footer: some View {
        FolioFeatureLibraryButton(
            title: e.generating ? "Generating…" : "Generate",
            look: .primary,
            busy: e.generating,
            disabled: e.generating,
            engine: e,
            frame: f
        ) {
            e.generate()
        }
        .padding(.horizontal, 18)
        .padding(.top, 14)
        .padding(.bottom, CGFloat(f.insetBottom + 10))
    }
}

/// The notice `Modal` (line 577): a short message on a white card over a dimmed screen.
struct FolioFeatureLibraryNoticeModal: View {
    let e: FolioFeatureLibraryEngine
    let f: FolioFeatureLibraryFrame

    var body: some View {
        let cardMax = f.height - f.insetTop - 48
        // The scroll view takes the height of its content, up to what the card leaves after its padding, the gap and the button.
        let room = max(0, cardMax - 44 - 16 - 52)
        let content = e.contentLengths[.notice] ?? 260
        let viewport = min(content, room)
        return ZStack {
            Color.black.opacity(0.3)
            VStack(spacing: 16) {
                FolioFeatureLibraryBlurViewport(engine: e, scroller: .notice, enabled: e.notice != nil, reduceTransparency: f.reduceTransparency) {
                    FolioFeatureLibraryScroller(engine: e, id: .notice, revision: e.layoutRevision) {
                        noticeContent
                    }
                }
                .frame(height: CGFloat(viewport))
                FolioFeatureLibraryButton(title: "Got it", look: .primary, engine: e, frame: f) {
                    e.closeNotice()
                }
            }
            .padding(22)
            .frame(width: CGFloat(f.width - 48))
            .background(FolioFeatureLibrarySurface(radius: 28))
        }
        .frame(width: CGFloat(f.width), height: CGFloat(f.height))
        .opacity(f.noticeProgress)
    }

    private var noticeContent: some View {
        VStack(spacing: 0) {
            Image(uiImage: FolioFeatureLibraryAssets.image(FolioFeatureLibraryData.features[e.feature].art))
                .resizable()
                .scaledToFit()
                .frame(width: 116, height: 116)
            FolioFeatureLibraryLabel(text: "Made for a little curiosity.", style: FolioFeatureLibraryStyles.noticeTitle, type: f.type, alignment: .center)
                .padding(.top, 8)
            FolioFeatureLibraryLabel(text: e.notice ?? "", style: FolioFeatureLibraryStyles.paragraph, type: f.type)
                .padding(.top, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity)
    }
}
