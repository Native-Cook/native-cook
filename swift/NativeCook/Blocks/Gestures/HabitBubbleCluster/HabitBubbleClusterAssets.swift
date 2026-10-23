import SwiftUI
import UIKit

struct HabitBubbleClusterAssets {
    let speckle: Image?
    let sprites: [Image]

    init() {
        let speckleImage = UIImage(named: "HabitBubbleCluster-speckle.jpg")
        let atlas = UIImage(named: "HabitBubbleCluster-glass-atlas.jpg")
        #if DEBUG
        assert(speckleImage != nil, "Missing HabitBubbleCluster-speckle.jpg")
        assert(atlas != nil, "Missing HabitBubbleCluster-glass-atlas.jpg")
        #endif
        speckle = speckleImage.map { Image(uiImage: $0) }
        guard let source = atlas?.cgImage else { sprites = []; return }
        let tile = Int(HabitBubbleClusterTokens.atlas.size)
        var images: [Image] = []
        for index in HabitBubbleClusterTokens.atlas.order.indices {
            let rect = CGRect(x: CGFloat((index % HabitBubbleClusterTokens.atlas.columns) * tile), y: CGFloat((index / HabitBubbleClusterTokens.atlas.columns) * tile), width: CGFloat(tile), height: CGFloat(tile))
            if let crop = source.cropping(to: rect) { images.append(Image(uiImage: UIImage(cgImage: crop))) }
        }
        sprites = images
    }

    func sprite(for id: String) -> Image? {
        let resolvedID = id == "note" || id == "quiet" ? "journal" : id
        guard let index = HabitBubbleClusterTokens.atlas.order.firstIndex(of: resolvedID), sprites.indices.contains(index) else { return nil }
        return sprites[index]
    }
}
