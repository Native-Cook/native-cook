import Foundation

struct OwnedGalleryItem: Hashable {
    let cardId: String
    let count: Int
    let id: String
    /// Asset-catalog image name.
    let image: String
    let name: String
}

struct LockedGalleryItem: Hashable {
    let id: String
    let lockCount: Int
}

enum GalleryItem: Identifiable, Hashable {
    case owned(OwnedGalleryItem)
    case locked(LockedGalleryItem)

    var id: String {
        switch self {
        case .owned(let item): return item.id
        case .locked(let item): return item.id
        }
    }
}
