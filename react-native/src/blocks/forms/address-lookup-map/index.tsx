import { AddressLookupMap } from './AddressLookupMap';

/**
 Address Lookup Map: the delivery step of a checkout. Suggestions rise in with the matched letters
 in bold, a pick folds them into the field, a map drawn in code flies to the address and a pin drops
 with a bounce while the details fill in. Drag the map to fine-tune the drop-off point.
 Component: AddressLookupMap.tsx (screen), StreetMap.tsx (the reusable drawn map).
 */
export default function AddressLookupMapDemo() {
  return <AddressLookupMap autoPlay />;
}
