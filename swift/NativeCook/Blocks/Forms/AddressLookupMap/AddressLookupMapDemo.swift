import SwiftUI

/// Address Lookup Map: the delivery step of a checkout. Suggestions rise in with the matched
/// letters in bold, a pick folds them into the field, a map drawn in code flies to the address and
/// a pin drops with a bounce while the details fill in. Drag the map to fine-tune the drop-off point.
struct AddressLookupMapDemo: View {
    var body: some View {
        AddressLookupMap(autoPlay: true)
    }
}

#Preview {
    AddressLookupMapDemo()
}
