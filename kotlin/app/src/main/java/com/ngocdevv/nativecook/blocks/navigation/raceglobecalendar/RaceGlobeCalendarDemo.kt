package com.ngocdevv.nativecook.blocks.navigation.raceglobecalendar
import android.content.pm.ApplicationInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
@Composable
fun RaceGlobeCalendarDemo() {
 val debug=(LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)!=0
 val manual=debug && (LocalActivity.current?.intent?.getBooleanExtra("raceGlobeCalendarManual",false)?:false)
 RaceGlobeCalendar(autoPlay=!manual)
}
