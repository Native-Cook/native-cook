package com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.*

internal class AuroraTeammatesOnboardingTilt(context:Context):SensorEventListener {
    private val manager=context.getSystemService(SensorManager::class.java)
    private val sensor=manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val matrix=FloatArray(9)
    private val angles=FloatArray(3)
    private var pitch=0.0;private var roll=0.0;private var basePitch=0.0;private var baseRoll=0.0;private var initialized=false
    val available get()=sensor!=null
    var x=0.0;private set
    var y=0.0;private set
    fun start(){sensor?.let{manager?.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}}
    fun stop(){manager?.unregisterListener(this)}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
    override fun onSensorChanged(event:SensorEvent){SensorManager.getRotationMatrixFromVector(matrix,event.values);SensorManager.getOrientation(matrix,angles);pitch=angles[1].toDouble();roll=angles[2].toDouble()}
    fun step(dt:Double){
        if(!initialized&&(pitch!=0.0||roll!=0.0)){initialized=true;basePitch=pitch;baseRoll=roll}
        val kb=min(1.0,dt*.5);basePitch+=(pitch-basePitch)*kb;baseRoll+=(roll-baseRoll)*kb
        val ks=1-exp(-dt*9);x+=(((roll-baseRoll)/.35).coerceIn(-1.0,1.0)-x)*ks;y+=(((pitch-basePitch)/.35).coerceIn(-1.0,1.0)-y)*ks
    }
}
