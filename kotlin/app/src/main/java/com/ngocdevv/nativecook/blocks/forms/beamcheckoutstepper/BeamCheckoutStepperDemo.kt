package com.ngocdevv.nativecook.blocks.forms.beamcheckoutstepper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.isActive
import kotlin.math.min
import kotlin.math.roundToInt

private val beamText=Color(0xFFF5F8FF)
private val beamMute=Color(0xFF9AA7BC)
private val beamError=Color(0xFFFF8A94)
private val beamRegular=FontFamily(Font(com.ngocdevv.nativecook.R.font.beamcheckoutstepper_inter_regular))
private val beamBold=FontFamily(Font(com.ngocdevv.nativecook.R.font.beamcheckoutstepper_inter_bold,FontWeight.Bold))
private val beamNames=listOf("Contact","Shipping","Payment","Review","Confirmed")
private val beamTitles=listOf("Your email","Delivery address","Card details","Review order","Order confirmed")
private val beamIcons=listOf(
    com.ngocdevv.nativecook.R.drawable.beamcheckoutstepper_icon_mail,
    com.ngocdevv.nativecook.R.drawable.beamcheckoutstepper_icon_box,
    com.ngocdevv.nativecook.R.drawable.beamcheckoutstepper_icon_card,
    com.ngocdevv.nativecook.R.drawable.beamcheckoutstepper_icon_receipt,
    com.ngocdevv.nativecook.R.drawable.beamcheckoutstepper_icon_seal
)

@Composable
fun BeamCheckoutStepperDemo(autoPlay:Boolean=true){
    val context=LocalContext.current
    DisposableEffect(context) {
        var host:Context=context
        while(host is ContextWrapper && host !is Activity)host=host.baseContext
        val decor=(host as? Activity)?.window?.decorView
        val before=decor?.systemUiVisibility
        decor?.systemUiVisibility=View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        onDispose { if(decor!=null&&before!=null)decor.systemUiVisibility=before }
    }
    val reduced=remember(context){Settings.Global.getFloat(context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}
    val controller=remember(autoPlay,reduced){BeamController(autoPlay,reduced)}
    val view=LocalView.current
    controller.onHaptic={ value ->
        val type=when(value){
            0 -> HapticFeedbackConstants.CLOCK_TICK
            1 -> HapticFeedbackConstants.CONTEXT_CLICK
            2 -> HapticFeedbackConstants.LONG_PRESS
            3 -> if(Build.VERSION.SDK_INT>=30)HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            else -> if(Build.VERSION.SDK_INT>=30)HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(type)
    }
    LaunchedEffect(controller){
        var previous=withFrameNanos{it}
        while(isActive){
            val now=withFrameNanos{it}
            controller.frame((now-previous)/1_000_000_000.0)
            previous=now
        }
    }
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()){
        val k=min(maxWidth.value/402f,maxHeight.value/874f)
        val sceneWidth=402*k
        val sceneHeight=874*k
        val imeBottom=WindowInsets.ime.getBottom(density)
        val availablePx=maxHeight.value*density.density
        Box(Modifier.align(Alignment.Center).width(sceneWidth.dp).height(sceneHeight.dp)
            .offset{
                controller.drawTick
                val lift=((446f+controller.core.frame[BF.PANEL_H])*k*density.density+
                    12*density.density-(availablePx-imeBottom)).coerceAtLeast(0f).roundToInt()
                IntOffset(0,-lift)
            }){
            BeamPicture(controller,k*density.density)
            BeamRowContents(controller,k)
            BeamRailTouch(controller,k,density.density)
            BeamPanelContents(controller,k,density.density)
        }
    }
}

@Composable private fun BeamLabel(text:String,x:Float,y:Float,k:Float,size:Float,
    bold:Boolean=false,color:Color=beamText,width:Float?=null,modifier:Modifier=Modifier,
    alignEnd:Boolean=false){
    BasicText(text,
        modifier.then(Modifier.offset((x*k).dp,(y*k).dp)).then(if(width!=null)Modifier.width((width*k).dp) else Modifier),
        style=TextStyle(fontFamily=if(bold)beamBold else beamRegular,fontSize=(size*k).sp,color=color,
            textAlign=if(alignEnd)TextAlign.End else TextAlign.Start,
            lineHeight=((size+5)*k).sp),
        maxLines=1,overflow=TextOverflow.Ellipsis)
}

@Composable private fun BeamRowContents(c:BeamController,k:Float){
    val f=c.form
    val summaries=beamSummaries(f)
    for(i in 0..4){
        val y=(128+68*i).toFloat()
        val enabled=!f.paid&&i<=f.furthest&&i!=f.step
        val alpha=if(i==f.step)1f else if(i<f.furthest||f.paid).66f else .38f
        Box(Modifier.offset(0.dp,((y-34)*k).dp).width((402*k).dp).height((68*k).dp)){
            Image(painterResource(beamIcons[i]),contentDescription=null,
                modifier=Modifier.offset((92*k).dp,(24*k).dp).size((20*k).dp).alpha(alpha))
            BeamLabel((i+1).toString(),49f,22f,k,17f,true,beamText.copy(alpha),
                modifier=Modifier.width((20*k).dp).graphicsLayer {
                    c.drawTick
                    this.alpha=(1f-c.core.frame[BF.NODE_DONE+i]).coerceIn(0f,1f)
                })
            BeamLabel(beamNames[i],122f,23f,k,16f,i==f.step,beamText.copy(alpha),width=120f)
            if(i<f.furthest||f.paid)BeamLabel(summaries[i],216f,24f,k,13f,
                color=beamMute.copy(alpha),width=138f,alignEnd=true)
            Box(Modifier.offset((32*k).dp,(4*k).dp).width((338*k).dp).height((60*k).dp)
                .semantics { role=Role.Button
                    contentDescription="Step ${i+1} of 5, ${beamNames[i]}, ${if(i==f.step)"current" else if(i<f.furthest||f.paid)"completed" else "not started"}"
                    if(!enabled) disabled()
                    onClick {
                        if(enabled){
                            c.touch(true)
                            c.touch(false)
                            c.action(BeamAction("go",target=i))
                        }
                        enabled
                    }
                }
                .pointerInput(enabled){
                    detectTapGestures(onPress={
                        if(enabled)c.press(BF.ROW_PRESS+i,true)
                        tryAwaitRelease()
                        if(enabled)c.press(BF.ROW_PRESS+i,false)
                    },onTap={if(enabled)c.action(BeamAction("go",target=i))})
                })
        }
    }
}

@Composable private fun BeamRailTouch(c:BeamController,k:Float,density:Float){
    var traveled by remember { mutableFloatStateOf(0f) }
    var lastNanos by remember { mutableLongStateOf(0L) }
    var velocity by remember { mutableFloatStateOf(0f) }
    Box(Modifier.offset((26*k).dp,(100*k).dp).width((60*k).dp).height((328*k).dp)
        .pointerInput(c,k,density){
            detectDragGestures(
                onDragStart={traveled=0f
                velocity=0f
                lastNanos=System.nanoTime()
                c.beginRail()},
                onDrag={change,dragAmount ->
                    change.consume()
                    traveled+=dragAmount.y
                    val now=System.nanoTime()
                    val dt=((now-lastNanos)/1_000_000_000f).coerceAtLeast(.001f)
                    velocity=dragAmount.y/(68*k*density*dt)
                    lastNanos=now
                    c.railDrag(traveled/(68*k*density),velocity)
                },
                onDragEnd={c.endRail(velocity)},
                onDragCancel={c.endRail(0f)}
            )
        }
        .semantics { contentDescription="Checkout progress rail"
        role=Role.Button })
}

@Composable private fun BeamPanelContents(c:BeamController,k:Float,density:Float){
    val f=c.form
    val focusManager=LocalFocusManager.current
    Box(Modifier.offset((24*k).dp,(446*k).dp).width((354*k).dp)
        .height((344*k).dp)){
        for(index in 0..4)BeamPanelPreview(c,index,k,density)
        Box(Modifier.fillMaxSize().graphicsLayer {
            c.drawTick
            val state=c.core.frame
            val from=state[BF.PANEL_FROM].roundToInt()
            val to=state[BF.PANEL_TO].roundToInt()
            val mix=state[BF.PANEL_MIX]
            alpha=when {
                from==to && f.step==to -> 1f
                from==f.step -> 1f-(mix/.3f).coerceIn(0f,1f)
                to==f.step -> beamContentIn(mix)
                else -> 0f
            }
        }){
        BeamLabel(beamTitles[f.step],16f,16f,k,17f,true,width=250f)
        BeamLabel(if(f.step==4)"#NC-20481" else "${f.step+1} of 5",
            if(f.step==4)238f else 290f,19f,k,13f,
            color=beamMute,width=if(f.step==4)100f else 48f,alignEnd=true)
        when(f.step){
            0 -> BeamInput(c,0,k,focusManager)
            1 -> {
                BeamInput(c,1,k,focusManager)
                BeamInput(c,2,k,focusManager)
                BeamInput(c,3,k,focusManager)
                BeamDelivery(c,k,density)
            }
            2 -> {
                BeamInput(c,4,k,focusManager)
                BeamInput(c,5,k,focusManager)
                BeamInput(c,6,k,focusManager)
            }
            3 -> beamReview(f).forEachIndexed{i,(name,value)->
                BeamLabel(name,16f,52f+32*i,k,14f,color=beamMute,width=86f)
                BeamLabel(value,104f,52f+32*i,k,14f,width=234f,alignEnd=true)
            }
            4 -> BeamLabel("Receipt sent to ${f.values[0]}",16f,48f,k,14f,
                color=beamMute,width=322f)
        }
        if(f.error!=null&&f.step<3){
            val bottom=intArrayOf(108,232,172)[f.step].toFloat()
            BeamLabel(f.error.message,16f,bottom+8f,k,13f,color=beamError,width=322f)
        }
        }
        if(f.step in 1..3){
            Box(Modifier.offset((16*k).dp,0.dp)
                .offset{c.drawTick
                IntOffset(0,((c.core.frame[BF.PANEL_H]-72)*k*density).roundToInt())}
                .size((56*k).dp)
                .graphicsLayer {
                    c.drawTick
                    alpha=c.core.frame[BF.CTA_SPLIT]*(1f-c.core.frame[BF.CTA_LENS])
                }
                .semantics { role=Role.Button
                contentDescription="Back"
                    onClick {
                        c.touch(true)
                        c.touch(false)
                        c.action(BeamAction("go",target=f.step-1))
                        true
                    } }
                .pointerInput(f.step){
                    detectTapGestures(onPress={c.press(BF.BACK_PRESS,true)
                    tryAwaitRelease()
                    c.press(BF.BACK_PRESS,false)},
                        onTap={c.action(BeamAction("go",target=f.step-1))})
                }){
                Image(painterResource(com.ngocdevv.nativecook.R.drawable.beamcheckoutstepper_icon_back),
                    contentDescription=null,modifier=Modifier.align(Alignment.Center).size((20*k).dp))
            }
        }
        val split=if(f.step in 1..3)1f else 0f
        val ctaLeft=16f+64f*split
        val ctaWidth=322f-64f*split
        Box(Modifier.offset((ctaLeft*k).dp,0.dp)
            .offset{c.drawTick
            IntOffset(0,((c.core.frame[BF.PANEL_H]-72)*k*density).roundToInt())}
            .width((ctaWidth*k).dp).height((56*k).dp)
            .semantics { role=Role.Button
            contentDescription=beamCta(f)
                onClick {
                    c.touch(true)
                    c.touch(false)
                    focusManager.clearFocus()
                    c.action(BeamAction("continue"))
                    true
                } }
            .pointerInput(f.step,f.values,f.express){
                detectTapGestures(onPress={c.press(BF.CTA_PRESS,true)
                tryAwaitRelease()
                c.press(BF.CTA_PRESS,false)},
                    onTap={focusManager.clearFocus()
                    c.action(BeamAction("continue"))})
            },contentAlignment=Alignment.Center){
            BasicText(beamCta(f),Modifier.graphicsLayer {
                c.drawTick
                alpha=1f-(c.core.frame[BF.CTA_LENS]/.3f).coerceIn(0f,1f)
            },style=TextStyle(fontFamily=beamBold,fontSize=(17*k).sp,
                color=if(f.paid)Color(0xFF06163A) else beamText))
        }
    }
}

private fun beamContentIn(mix:Float):Float {
    val u=((mix-.4f)/.6f).coerceIn(0f,1f)
    return u*u*(3f-2f*u)
}

@Composable private fun BeamPanelPreview(c:BeamController,index:Int,k:Float,density:Float){
    val f=c.form
    Box(Modifier.fillMaxSize().graphicsLayer {
        c.drawTick
        val frame=c.core.frame
        val from=frame[BF.PANEL_FROM].roundToInt()
        val to=frame[BF.PANEL_TO].roundToInt()
        val visibility=if(from!=to&&to==index&&index!=f.step)
            beamContentIn(frame[BF.PANEL_MIX]) else 0f
        alpha=visibility
        translationY=10f*(1f-visibility)*k*density
    }.clearAndSetSemantics {}){
        BeamLabel(beamTitles[index],16f,16f,k,17f,true,width=250f)
        BeamLabel(if(index==4)"#NC-20481" else "${index+1} of 5",
            if(index==4)238f else 290f,19f,k,13f,
            color=beamMute,width=if(index==4)100f else 48f,alignEnd=true)
        if(index<3){
            beamFieldRects.forEachIndexed{field,rect->
                if(rect[0]==index){
                    val x=rect[1].toFloat()
                    val y=rect[2].toFloat()
                    val w=rect[3].toFloat()
                    val value=f.values[field]
                    val label=listOf("Email","Street address","City","ZIP","Card number","MM / YY","CVC")[field]
                    BeamLabel(label,x+16f,y+if(value.isEmpty())18f else 7f,k,
                        if(value.isEmpty())16f else 11.5f,color=beamMute,width=w-32f)
                    if(value.isNotEmpty())BeamLabel(value,x+16f,y+25f,k,16f,width=w-32f)
                }
            }
            if(index==1){
                BeamLabel("Standard · Free",29f,197f,k,14f,bold=true,width=140f)
                BeamLabel("Express · $12",190f,197f,k,14f,bold=true,width=140f)
            }
        }else if(index==3){
            beamReview(f).forEachIndexed{i,(name,value)->
                BeamLabel(name,16f,52f+32*i,k,14f,color=beamMute,width=86f)
                BeamLabel(value,104f,52f+32*i,k,14f,width=234f,alignEnd=true)
            }
        }else BeamLabel("Receipt sent to ${f.values[0]}",16f,48f,k,14f,
            color=beamMute,width=322f)
    }
}

@Composable private fun BeamInput(c:BeamController,index:Int,k:Float,
    focusManager:androidx.compose.ui.focus.FocusManager){
    val rect=beamFieldRects[index]
    val x=rect[1].toFloat()
    val y=rect[2].toFloat()
    val w=rect[3].toFloat()
    val value=c.form.values[index]
    val focused=c.core.focusField==index
    var edit by remember(index) { mutableStateOf(TextFieldValue(value,TextRange(value.length))) }
    LaunchedEffect(value) {
        if(edit.text!=value)edit=TextFieldValue(value,TextRange(value.length))
    }
    Box(Modifier.offset((x*k).dp,(y*k).dp).width((w*k).dp).height((56*k).dp)){
        BeamLabel(listOf("Email","Street address","City","ZIP","Card number","MM / YY","CVC")[index],
            16f,if(value.isNotEmpty()||focused)7f else 18f,k,
            if(value.isNotEmpty()||focused)11.5f else 16f,
            color=beamMute,width=w-32f)
        BasicTextField(value=edit,onValueChange={incoming->
                val formatted=beamFormat(index,incoming.text)
                val cursor=beamFormat(index,incoming.text.take(incoming.selection.start))
                    .length.coerceAtMost(formatted.length)
                edit=TextFieldValue(formatted,TextRange(cursor))
                c.action(BeamAction("edit",field=index,value=formatted))
            },
            modifier=Modifier.offset((16*k).dp,(25*k).dp).width(((w-32)*k).dp)
                .height((28*k).dp)
                .onFocusChanged{if(it.isFocused)c.focus(index) else if(c.core.focusField==index)c.focus(-1)}
                .semantics { contentDescription=listOf("Email","Street address","City","ZIP","Card number","MM / YY","CVC")[index]+
                    (if(c.form.error?.field==index)", error: ${c.form.error?.message}" else "") },
            textStyle=TextStyle(fontFamily=beamRegular,fontSize=(16*k).sp,color=beamText),
            singleLine=true,cursorBrush=androidx.compose.ui.graphics.SolidColor(Color(0xFF5FA8FF)),
            keyboardOptions=KeyboardOptions(keyboardType=when(index){
                0->KeyboardType.Email
                3,4,5,6->KeyboardType.Number
                else->KeyboardType.Text
            },imeAction=if(index==0||index==3||index==6)ImeAction.Done else ImeAction.Next),
            keyboardActions=KeyboardActions(onDone={focusManager.clearFocus()
            c.action(BeamAction("continue"))}))
    }
}

@Composable private fun BeamDelivery(c:BeamController,k:Float,density:Float){
    var dragX by remember { mutableFloatStateOf(0f) }
    var velocity by remember { mutableFloatStateOf(0f) }
    var lastNanos by remember { mutableLongStateOf(0L) }
    Box(Modifier.offset((16*k).dp,(178*k).dp).width((322*k).dp).height((60*k).dp)
        .pointerInput(c,k,density){
            detectDragGestures(onDragStart={c.beginDelivery()
            dragX=0f
            lastNanos=System.nanoTime()},
                onDrag={change,amount->
                    change.consume()
                    dragX+=amount.x
                    val now=System.nanoTime()
                    val dt=((now-lastNanos)/1_000_000_000f).coerceAtLeast(.001f)
                    velocity=amount.x/(161*k*density*dt)
                    lastNanos=now
                    c.deliveryDrag(c.core.deliveryGrab+dragX/(161*k*density))
                },onDragEnd={c.endDelivery(velocity)},onDragCancel={c.endDelivery(0f)})
        }.semantics { contentDescription="Delivery speed" }){
        listOf(false to "Standard · Free",true to "Express · $12").forEachIndexed{index,(value,label)->
            Box(Modifier.offset((161*index*k).dp,0.dp).width((161*k).dp).fillMaxHeight()
                .semantics { role=Role.RadioButton
                contentDescription=label
                    selected=(c.form.express==value)
                    onClick {
                        c.touch(true)
                        c.touch(false)
                        c.action(BeamAction("delivery",express=value))
                        true
                    } }
                .pointerInput(value){
                    detectTapGestures(onPress={c.press(BF.SEG_PRESS,true)
                    tryAwaitRelease()
                    c.press(BF.SEG_PRESS,false)},
                        onTap={c.action(BeamAction("delivery",express=value))})
                },contentAlignment=Alignment.Center){
                BasicText(label,style=TextStyle(fontFamily=beamBold,fontSize=(14*k).sp,
                    color=if(c.form.express==value)beamText else beamMute))
            }
        }
    }
}
