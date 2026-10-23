package com.ngocdevv.nativecook.blocks.gestures.receiptprinter

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ReceiptPrinterDemo() {
    ReceiptPrinter(autoPlay = true)
}

@Preview
@Composable
private fun ReceiptPrinterDemoPreview() {
    ReceiptPrinterDemo()
}
