import UIKit

// The same TextKit configuration used by RN's RCTTextLayoutManager on iOS.
// These layouts are created only while rendering the static page, never in the frame loop.
enum StickerPeelProfileTypography {
    final class Label {
        let storage: NSTextStorage
        let manager: NSLayoutManager
        let container: NSTextContainer
        let size: CGSize
        init(_ value: String,_ font: UIFont,_ color: UIColor) {
            container = NSTextContainer(size:CGSize(width:10000,height:10000))
            container.lineFragmentPadding = 0
            manager = NSLayoutManager(); manager.usesFontLeading = false
            storage = NSTextStorage(string:value,attributes:[.font:font,.foregroundColor:color])
            manager.addTextContainer(container); storage.addLayoutManager(manager)
            manager.ensureLayout(for:container)
            let rect = manager.usedRect(for:container)
            let scale = UIScreen.main.scale
            size = CGSize(width:ceil(rect.width*scale)/scale,height:ceil(rect.height*scale)/scale)
        }
        func draw(_ origin: CGPoint) {
            let range = manager.glyphRange(for:container)
            manager.drawGlyphs(forGlyphRange:range,at:origin)
        }
    }
    private static var cache: [String:Label] = [:]
    static func label(_ text: String,_ size: Double,_ weight: String,_ color: UInt32 = 0xffffff,_ alpha: Double = 1) -> Label {
        let key = "\(text)|\(size)|\(weight)|\(color)|\(alpha)"
        if let found = cache[key] { return found }
        let label = Label(text,StickerPeelProfileResources.font(weight,size),StickerPeelProfileResources.ui(color,alpha))
        cache[key] = label
        return label
    }
}
