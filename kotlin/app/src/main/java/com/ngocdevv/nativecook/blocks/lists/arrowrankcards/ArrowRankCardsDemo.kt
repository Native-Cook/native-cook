package com.ngocdevv.nativecook.blocks.lists.arrowrankcards
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
@Composable
fun ArrowRankCardsDemo() {
 val debug = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
 val manual=debug && (LocalActivity.current?.intent?.getBooleanExtra("arrowRankCardsManual",false)?:false)
 ArrowRankCards(autoPlay=!manual)
}
