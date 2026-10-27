import SwiftUI
import UIKit

/// Where one topic chip sits in the sheet's wrapping row, relative to the top-left of the chip area.
struct FolioFeatureLibraryChipFrame: Equatable {
    var x: Double
    var y: Double
    var width: Double
}

/// The frames of all chips and of the "Add yours" element.
struct FolioFeatureLibraryChipLayout: Equatable {
    var frames: [String: FolioFeatureLibraryChipFrame] = [:]
    var height = 0.0
}

/// What the chip layout depends on, so it is computed again only when one of them changes.
struct FolioFeatureLibraryChipInputs: Equatable {
    var items: [String]
    var selected: [String]
    var adding: Bool
    var custom: String
    var width: Double
    var scale: Double
    var reduced: Bool
}

// The topic sheet's chips (lines 566-569): `flexDirection: 'row', flexWrap: 'wrap', gap: 10, rowGap: 10`, each chip 40 pt
// high with 15 pt of padding. Reanimated's `LinearTransition.duration(240).easing(OUT)` moves a chip from its old frame to
// its new one when the row changes, so the frames are computed here (the way Yoga would) and animated by the clock.

extension FolioFeatureLibraryEngine {
    static let chipKeyAdd = "__add"

    private func measuredWidth(_ text: String, size: Double, weight: UIFont.Weight) -> Double {
        let key = "\(text)|\(size)|\(weight.rawValue)"
        if let cached = textWidths[key] { return cached }
        let value = FolioFeatureLibraryMetrics.textWidth(text, size: size, weight: weight, pixelScale: pixelScale)
        textWidths[key] = value
        return value
    }

    /// The row as Yoga lays it out now.
    func computeChipLayout() -> FolioFeatureLibraryChipLayout {
        let containerWidth = width - 36
        let scale = textType.factor(maxScale: nil, scales: true)
        let size = 15 * scale
        var layout = FolioFeatureLibraryChipLayout()
        var x = 0.0
        var y = 0.0

        func place(_ key: String, _ itemWidth: Double) {
            if x > 0 && x + itemWidth > containerWidth + 0.01 {
                x = 0
                y += 50
            }
            layout.frames[key] = FolioFeatureLibraryChipFrame(x: x, y: y, width: itemWidth)
            x += itemWidth + 10
        }

        for topic in topicItems {
            var itemWidth = 30 + measuredWidth(topic, size: size, weight: .medium)
            // The check mark is an Ionicons glyph one em wide, at 17 pt times the text size.
            if selected.contains(topic) { itemWidth += 6 + 17 * scale }
            place(topic, itemWidth)
        }
        if addingTopic {
            let sample = customTopic.isEmpty ? "Your topic" : customTopic
            place(Self.chipKeyAdd, max(140, measuredWidth(sample, size: size, weight: .regular) + 33))
        } else {
            place(Self.chipKeyAdd, 3 + 30 + 17 * scale + 6 + measuredWidth("Add yours", size: size, weight: .medium))
        }
        layout.height = y + 40
        return layout
    }

    /// A chip's frame at a time, part-way along its layout transition.
    func chipFrame(_ key: String, at time: Double) -> FolioFeatureLibraryChipFrame? {
        guard let target = chipTarget.frames[key] else { return nil }
        guard !reduced, let origin = chipFrom.frames[key] else { return target }
        let p = FolioFeatureLibraryCurve.out.value((time - chipSince) / 0.24)
        return FolioFeatureLibraryChipFrame(
            x: FolioFeatureLibraryMath.lerp(origin.x, target.x, p),
            y: FolioFeatureLibraryMath.lerp(origin.y, target.y, p),
            width: FolioFeatureLibraryMath.lerp(origin.width, target.width, p)
        )
    }

    /// Called after every commit: starts a layout transition when the row changed.
    func updateChipLayout() {
        if !topicsMounted(at: clock) {
            chipTarget = FolioFeatureLibraryChipLayout()
            chipFrom = FolioFeatureLibraryChipLayout()
            chipInputs = nil
            return
        }
        let inputs = FolioFeatureLibraryChipInputs(
            items: topicItems,
            selected: selected,
            adding: addingTopic,
            custom: customTopic,
            width: width,
            scale: textType.scale,
            reduced: reduced
        )
        if inputs == chipInputs { return }
        chipInputs = inputs
        let next = computeChipLayout()
        if next == chipTarget { return }
        var origin = FolioFeatureLibraryChipLayout()
        for key in next.frames.keys {
            if let current = chipFrame(key, at: clock) { origin.frames[key] = current }
        }
        origin.height = next.height
        chipFrom = origin
        chipTarget = next
        chipSince = clock
    }
}
