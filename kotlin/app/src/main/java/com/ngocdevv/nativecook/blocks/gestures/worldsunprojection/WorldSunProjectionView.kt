package com.ngocdevv.nativecook.blocks.gestures.worldsunprojection

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.accessibility.*
import kotlin.math.*

internal class WorldSunProjectionView(context:Context):View(context),Choreographer.FrameCallback{
    private val art=WorldSunProjectionPainter(WorldSunProjectionResources(context))
    private val driver=WorldSunProjectionDriver(::wake,{announceForAccessibility(it);sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)},{performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)})
    private var queued=false;private var pointer= -1;private var control= -1;private val ignored=HashSet<Int>();private var focus= -1
    private var scale=1.0;private var offsetX=0.0;private var offsetY=0.0
    init{isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;setBackgroundColor(Color.rgb(41,52,50))}
    fun props(auto:Boolean,preview:Double?,reduced:Boolean){driver.props(auto,preview,reduced);wake()}
    private fun wake(){postInvalidateOnAnimation();if(!queued&&isAttachedToWindow&&driver.ticks()){queued=true;Choreographer.getInstance().postFrameCallback(this)}}
    override fun doFrame(frameTimeNanos:Long){queued=false;invalidate();if(driver.ticks())wake()}
    override fun onAttachedToWindow(){super.onAttachedToWindow();driver.active(true);wake()}
    override fun onWindowFocusChanged(hasWindowFocus:Boolean){super.onWindowFocusChanged(hasWindowFocus);pointer= -1;ignored.clear();control= -1;driver.active(hasWindowFocus);wake()}
    override fun onDetachedFromWindow(){Choreographer.getInstance().removeFrameCallback(this);queued=false;driver.active(false);super.onDetachedFromWindow()}
    fun release(){driver.close()}
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);if(width==0||height==0)return;scale=min(width/393.0,height/852.0);offsetX=(width-393*scale)/2;offsetY=(height-852*scale)/2
        canvas.drawColor(Color.rgb(41,52,50));canvas.save();canvas.translate(offsetX.toFloat(),offsetY.toFloat());canvas.scale(scale.toFloat(),scale.toFloat());val p=driver.frame();art.draw(canvas,p,driver);driver.drawn(p);canvas.restore()
    }
    private data class Node(val label:String,val value:String,val rect:RectF,val kind:String,val amount:Double=0.0,val button:Boolean=true)
    private fun nodes():List<Node>{val p=driver.snapshot();fun r(x:Double,y:Double,w:Double,h:Double)=RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat())
        return listOf(Node("Alder Reach projected view",driver.description(p),r(8.0,114.0,315.0,596.0),"time",button=false),Node("Planning time rail",WorldSunProjectionMath.time(p.q),r(331.0,323.0,62.0,174.0),"time",button=false),Node("Reset planning time","",r(12.0,508.0,63.0,44.0),"time",0.0),Node("Select 05:09","",r(340.5,216.0,44.0,44.0),"time",0.0),Node("Select 07:08","",r(340.5,248.0,44.0,44.0),"time",.34),Node("Select 10:59","",r(340.5,502.0,44.0,44.0),"time",1.0),Node("Show planning details",if(driver.information)"Offline solar plan"else"East coast UTC+0",r(12.0,736.0,44.0,44.0),"information"),Node("Reset view direction","",r(337.0,736.0,44.0,44.0),"reset"),Node(if(driver.grid)"Hide horizon grid"else"Show horizon grid","",r(24.0,776.0,105.0,44.0),"grid"),Node("Reset view direction","",r(144.0,776.0,105.0,44.0),"reset"),Node(if(driver.frozen)"Unfreeze planning time"else"Freeze planning time","",r(264.0,776.0,105.0,44.0),"freeze"))
    }
    private fun point(e:MotionEvent,index:Int)=WorldSunProjectionPoint((e.getX(index)-offsetX)/scale,(e.getY(index)-offsetY)/scale)
    override fun onTouchEvent(e:MotionEvent):Boolean{val id=e.getPointerId(e.actionIndex);val p=point(e,e.actionIndex)
        when(e.actionMasked){MotionEvent.ACTION_DOWN->{if(ignored.isNotEmpty()||driver.preview!=null)return false;val ns=nodes();control=ns.indexOfLast{it.button&&it.rect.contains(p.x.toFloat(),p.y.toFloat())};if(control>=0){pointer=id;return true};if(p.y<114||p.y>735)return false;if(driver.begin(id,p)){pointer=id;parent?.requestDisallowInterceptTouchEvent(true);wake();return true};return false}
            MotionEvent.ACTION_POINTER_DOWN->{ignored+=id;return true}
            MotionEvent.ACTION_MOVE->{val index=e.findPointerIndex(pointer);if(index>=0&&control<0){driver.move(pointer,point(e,index));wake()};return true}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP->{if(pointer==id){if(control>=0){nodes().getOrNull(control)?.takeIf{it.rect.contains(p.x.toFloat(),p.y.toFloat())}?.let{driver.action(it.kind,it.amount)}}else driver.end(id,p);pointer= -1;control= -1;parent?.requestDisallowInterceptTouchEvent(false);wake();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)};ignored-=id;return true}
            MotionEvent.ACTION_CANCEL->{if(pointer>=0&&control<0)driver.end(pointer,null,true);pointer= -1;control= -1;ignored.clear();parent?.requestDisallowInterceptTouchEvent(false);wake();return true}}
        return true
    }
    private val provider=object:AccessibilityNodeProvider(){
        override fun createAccessibilityNodeInfo(virtualViewId:Int):AccessibilityNodeInfo?{
            if(virtualViewId==HOST_VIEW_ID){val i=AccessibilityNodeInfo.obtain(this@WorldSunProjectionView);onInitializeAccessibilityNodeInfo(i);nodes().indices.forEach{i.addChild(this@WorldSunProjectionView,it)};return i}
            val n=nodes().getOrNull(virtualViewId)?:return null;val i=AccessibilityNodeInfo.obtain();i.setSource(this@WorldSunProjectionView,virtualViewId);i.setParent(this@WorldSunProjectionView);i.packageName=context.packageName;i.className=if(n.button)"android.widget.Button"else"android.widget.SeekBar";i.contentDescription=n.label+if(n.value.isEmpty())""else", "+n.value;i.isVisibleToUser=true;i.isEnabled=driver.preview==null;i.isFocusable=true;i.isAccessibilityFocused=focus==virtualViewId;i.isSelected=n.kind=="grid"&&driver.grid||n.kind=="freeze"&&driver.frozen||virtualViewId==9
            val rect=Rect((n.rect.left*scale+offsetX).toInt(),(n.rect.top*scale+offsetY).toInt(),(n.rect.right*scale+offsetX).toInt(),(n.rect.bottom*scale+offsetY).toInt());i.setBoundsInParent(rect);val origin=IntArray(2);getLocationOnScreen(origin);rect.offset(origin[0],origin[1]);i.setBoundsInScreen(rect);i.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);i.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            if(n.button)i.addAction(AccessibilityNodeInfo.ACTION_CLICK)else{i.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);i.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)};return i
        }
        override fun performAction(virtualViewId:Int,action:Int,arguments:Bundle?):Boolean{val n=nodes().getOrNull(virtualViewId)?:return false
            when(action){AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS->{focus=virtualViewId;sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);return true};AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS->{focus= -1;return true};AccessibilityNodeInfo.ACTION_CLICK->{if(!n.button)return false;driver.action(n.kind,n.amount);wake();sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);return true};AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD->{if(n.button)return false;val sign=if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)1 else -1;driver.action("time",driver.snapshot().q+sign*15.0/350);wake();return true}}
            return false
        }
    }
    override fun getAccessibilityNodeProvider():AccessibilityNodeProvider=provider
}
