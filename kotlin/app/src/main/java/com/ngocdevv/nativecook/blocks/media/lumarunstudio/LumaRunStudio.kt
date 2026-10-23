package com.ngocdevv.nativecook.blocks.media.lumarunstudio

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion

const val LUMA_RUN_STUDIO_LOOP=8.0
@Composable
fun LumaRunStudio(modifier:Modifier=Modifier,autoPlay:Boolean=false,previewTime:Double?=null) {
    val context=LocalContext.current
    val auto=rememberUpdatedState(autoPlay);val sample=rememberUpdatedState(previewTime);val reduce=rememberUpdatedState(rememberReducedMotion())
    val activity=lumaRunStudioActivity(context)
    DisposableEffect(activity){val window=activity?.window;val controller=window?.let {WindowCompat.getInsetsController(it,it.decorView)};val previousStatus=controller?.isAppearanceLightStatusBars;val previousNav=controller?.isAppearanceLightNavigationBars;controller?.isAppearanceLightStatusBars=false;controller?.isAppearanceLightNavigationBars=false
        onDispose {if(previousStatus!=null)controller?.isAppearanceLightStatusBars=previousStatus;if(previousNav!=null)controller?.isAppearanceLightNavigationBars=previousNav}}
    AndroidView(factory={ctx->LumaRunStudioView(ctx){(activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()}},update={it.props(auto.value,sample.value,reduce.value)},onReset=null,onRelease={it.release()},modifier=modifier.fillMaxSize())
}
private fun lumaRunStudioActivity(context:Context):Activity?=when(context){is Activity->context;is ContextWrapper->lumaRunStudioActivity(context.baseContext);else->null}
