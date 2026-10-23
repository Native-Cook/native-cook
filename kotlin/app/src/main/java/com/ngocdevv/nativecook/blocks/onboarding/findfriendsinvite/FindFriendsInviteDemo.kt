package com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun FindFriendsInviteDemo() {
    FindFriendsInvite(
        modifier = Modifier,
        autoPlay = true,
    )
}

@Preview
@Composable
private fun FindFriendsInviteDemoPreview() {
    FindFriendsInviteDemo()
}
