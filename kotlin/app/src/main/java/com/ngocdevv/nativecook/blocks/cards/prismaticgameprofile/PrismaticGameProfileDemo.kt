package com.ngocdevv.nativecook.blocks.cards.prismaticgameprofile

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun PrismaticGameProfileDemo() {
    var context=LocalContext.current
    while(context is ContextWrapper&&context !is Activity)context=context.baseContext
    val intent=(context as? Activity)?.intent
    // Scoped launch fixtures stay out of the public interface and default to the10s showcase.
    val time=remember(intent){
        @Suppress("DEPRECATION")
        val value=intent?.extras?.get("prismaticTime")
        when(value){is Number->value.toDouble();is String->value.toDoubleOrNull();else->null}?.takeIf {it.isFinite()}?.coerceIn(0.0,PrismaticGameProfileMotion.LOOP)
    }
    PrismaticGameProfile(autoPlay=intent?.getBooleanExtra("prismaticManual",false)!=true,previewTime=time,previewReducedMotion=intent?.getBooleanExtra("prismaticReduced",false)==true)
}

@Preview(widthDp=393,heightDp=852)
@Composable
private fun PrismaticGameProfileDemoPreview(){PrismaticGameProfile(previewTime=0.0)}
