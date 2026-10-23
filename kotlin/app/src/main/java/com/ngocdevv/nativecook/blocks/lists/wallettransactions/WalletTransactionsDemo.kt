package com.ngocdevv.nativecook.blocks.lists.wallettransactions

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun WalletTransactionsDemo() {
    WalletTransactions(autoPlay = true)
}

@Preview
@Composable
private fun WalletTransactionsDemoPreview() {
    WalletTransactionsDemo()
}
