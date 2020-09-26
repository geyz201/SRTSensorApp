package com.example.sensortest

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import kotlinx.android.synthetic.main.activity_main.*
import threeDvector.Vec3D
import java.text.DecimalFormat
import java.util.ArrayList

class SensorRecord : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private lateinit var m_wkik: PowerManager.WakeLock

    private val sensorData_Acc = ArrayList<Vec3D>()
    private val sensorData_GRV = ArrayList<Vec3D>()

    override fun onCreate() {
        super.onCreate()
        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorAcc = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensorGRV = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        sensorManager.registerListener(this, sensorAcc, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, sensorGRV, SensorManager.SENSOR_DELAY_NORMAL)

        powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        m_wkik = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,SensorRecord::class.qualifiedName)
        m_wkik.acquire()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        TODO("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
    }
    override fun onSensorChanged(event: SensorEvent) {
        val range = 1.0 //设定一个精度范围
        val sensor = event.sensor
        when (sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                //Data Receive from sensor
                val tmpVec = Vec3D(event.values)
                sensorData_Acc.add(tmpVec)
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val tmpVec = Vec3D(event.values)
                sensorData_GRV.add(tmpVec)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        m_wkik.release()
    }
}