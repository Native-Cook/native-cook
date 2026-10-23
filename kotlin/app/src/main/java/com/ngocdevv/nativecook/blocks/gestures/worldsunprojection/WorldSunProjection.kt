package com.ngocdevv.nativecook.blocks.gestures.worldsunprojection

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion

const val WORLD_SUN_PROJECTION_LOOP=8.0
@Composable
fun WorldSunProjection(modifier:Modifier=Modifier,autoPlay:Boolean=false,previewTime:Double?=null){
    val auto=rememberUpdatedState(autoPlay);val preview=rememberUpdatedState(previewTime);val reduced=rememberUpdatedState(rememberReducedMotion());val activity=worldSunProjectionActivity(LocalContext.current)
    DisposableEffect(activity){val controller=activity?.window?.let{WindowCompat.getInsetsController(it,it.decorView)};val oldStatus=controller?.isAppearanceLightStatusBars;val oldNav=controller?.isAppearanceLightNavigationBars;controller?.isAppearanceLightStatusBars=false;controller?.isAppearanceLightNavigationBars=false;onDispose{if(oldStatus!=null)controller?.isAppearanceLightStatusBars=oldStatus;if(oldNav!=null)controller?.isAppearanceLightNavigationBars=oldNav}}
    AndroidView(factory={WorldSunProjectionView(it)},update={it.props(auto.value,preview.value,reduced.value)},onReset=null,onRelease={it.release()},modifier=modifier.fillMaxSize())
}
private fun worldSunProjectionActivity(context:Context):Activity?=when(context){is Activity->context;is ContextWrapper->worldSunProjectionActivity(context.baseContext);else->null}
