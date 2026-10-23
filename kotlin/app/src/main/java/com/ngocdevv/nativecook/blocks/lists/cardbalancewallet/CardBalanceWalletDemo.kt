package com.ngocdevv.nativecook.blocks.lists.cardbalancewallet

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun CardBalanceWalletDemo() {
    CardBalanceWallet(autoPlay = true)
}

@Preview
@Composable
private fun CardBalanceWalletDemoPreview() {
    CardBalanceWalletDemo()
}
