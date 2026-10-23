package com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal class FindFriendsInviteAssets(context: Context) {
    val cup = context.image("findfriendsinvite/findfriendsinvite_cup.png")
    val head1 = context.image("findfriendsinvite/findfriendsinvite_head_1.png")
    val head2 = context.image("findfriendsinvite/findfriendsinvite_head_2.png")
    val head3 = context.image("findfriendsinvite/findfriendsinvite_head_3.png")
    val head5 = context.image("findfriendsinvite/findfriendsinvite_head_5.png")

    val medium: Typeface = Typeface.createFromAsset(context.assets, FindFriendsInviteTheme.Font.medium)
    val semi: Typeface = Typeface.createFromAsset(context.assets, FindFriendsInviteTheme.Font.semi)
    val bold: Typeface = Typeface.createFromAsset(context.assets, FindFriendsInviteTheme.Font.bold)
    val extra: Typeface = Typeface.createFromAsset(context.assets, FindFriendsInviteTheme.Font.extra)

    fun head(source: String): ImageBitmap {
        return when (source) {
            "h1" -> head1
            "h2" -> head2
            "h3" -> head3
            "h5" -> head5
            else -> error("Unknown Find Friends Invite avatar: $source")
        }
    }
}

private fun Context.image(path: String): ImageBitmap {
    return assets.open(path).use { stream ->
        BitmapFactory.decodeStream(stream).asImageBitmap()
    }
}
