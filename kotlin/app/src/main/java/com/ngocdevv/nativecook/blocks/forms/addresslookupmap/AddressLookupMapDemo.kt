package com.ngocdevv.nativecook.blocks.forms.addresslookupmap

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 Address Lookup Map: the delivery step of a checkout. Suggestions rise in with the matched letters
 in bold, a pick folds them into the field, a map drawn in code flies to the address and a pin drops
 with a bounce while the details fill in. Drag the map to fine-tune the drop-off point.
 Component: AddressLookupMap.kt (screen), AddressLookupMapStreet.kt (the reusable drawn map).
 */
@Composable
fun AddressLookupMapDemo() {
    AddressLookupMap(autoPlay = true)
}

@Preview
@Composable
private fun AddressLookupMapDemoPreview() {
    AddressLookupMapDemo()
}
