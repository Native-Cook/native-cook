import SwiftUI
import UIKit

/// One of the shop's pieces: a sneaker or a scent, with its Unsplash photo.
struct LiquidSearchMorphPiece: Identifiable, Equatable {
    let id: String
    let name: String
    let kind: String
    let price: String
    let photo: String
}

enum LiquidSearchMorphCatalog {
    static let pieces: [LiquidSearchMorphPiece] = [
        .init(id: "fairline", name: "Fairline Duo", kind: "Runner", price: "€168", photo: "photo-1560769629-975ec94e6a86"),
        .init(id: "bordeaux", name: "Bordeaux Garnet", kind: "Ring", price: "€212", photo: "photo-1735212214451-a8f43a015309"),
        .init(id: "stairway", name: "Stairway Hoops", kind: "Earrings", price: "€86", photo: "photo-1751552147823-e2a844bce504"),
        .init(id: "airlace", name: "Airlace Chain", kind: "Necklace", price: "€74", photo: "photo-1751552147774-c374ae8e9910"),
        .init(id: "dune", name: "Dune Slip Low", kind: "Slip-on", price: "€148", photo: "photo-1603808033192-082d6919d3e1"),
        .init(id: "paired", name: "Paired Crimson 4", kind: "Court", price: "€189", photo: "photo-1605523741177-cd660595c2cf"),
        .init(id: "daisy", name: "Daisy Cluster", kind: "Ring", price: "€164", photo: "photo-1751551683594-ab054a031696"),
        .init(id: "rose", name: "Rose Braid", kind: "Bangle", price: "€128", photo: "photo-1735905131335-a921150a017d"),
        .init(id: "gilded", name: "Gilded Air Extrait", kind: "Extrait · 30 ml", price: "€138", photo: "photo-1622618991746-fe6004db3a47"),
        .init(id: "cabinet", name: "Cabinet Brooch", kind: "Brooch", price: "€236", photo: "photo-1615957579676-d1db3be6effe"),
        .init(id: "amber", name: "Amber Heirloom", kind: "Eau de parfum · 75 ml", price: "€118", photo: "photo-1535683577427-740aaac4ec25"),
        .init(id: "satin", name: "Satin Petal", kind: "Eau de parfum · 50 ml", price: "€104", photo: "photo-1543422655-ac1c6ca993ed"),
    ]

    static let recent = ["runner", "amber", "court", "ring", "extrait", "parfum"]

    static let popular = ["Fairline Duo", "Gilded Air Extrait", "Daisy Cluster", "Satin Petal"]

    /// The pieces whose name or kind holds the query, in the shop's order.
    static func search(_ query: String) -> [LiquidSearchMorphPiece] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return [] }
        return pieces.filter { $0.name.lowercased().contains(q) || $0.kind.lowercased().contains(q) }
    }

    static func photo(_ id: String, width: Int) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(width)&q=80"
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded for every view that shows it.
enum LiquidSearchMorphPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func cached(_ address: String) -> UIImage? {
        cache.object(forKey: address as NSString)
    }

    static func load(_ address: String) async -> UIImage? {
        if let hit = cached(address) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}

/// A photo cropped to fill its frame over a sand placeholder; it fades in when it arrives.
struct LiquidSearchMorphPhoto: View {
    let id: String
    let width: Int
    var fade = 0.2

    @State private var image: UIImage?

    var body: some View {
        let address = LiquidSearchMorphCatalog.photo(id, width: width)
        Color(hex: 0xe5dfd4)
            .overlay {
                if let image = image ?? LiquidSearchMorphPhotos.cached(address) {
                    Image(uiImage: image)
                        .resizable()
                        .interpolation(.high)
                        .aspectRatio(contentMode: .fill)
                        .transition(.opacity.animation(.easeInOut(duration: fade)))
                }
            }
            .clipped()
            .task(id: address) {
                guard LiquidSearchMorphPhotos.cached(address) == nil else { return }
                image = await LiquidSearchMorphPhotos.load(address)
            }
    }
}
