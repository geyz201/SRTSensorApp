package com.example.sensortest

import android.R
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import threeDvector.Rotate
import threeDvector.Slerp
import threeDvector.Vec3D
import java.lang.StringBuilder
import java.util.*


class SensorRecord : Service(), SensorEventListener {
    private val binder = LocalBinder()
    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private lateinit var m_wkik: PowerManager.WakeLock

    //private val sensorData_Acc = ArrayList<Vec3D>()
    //private val sensorData_GRV = ArrayList<Vec3D>()

    val currentAcc: MutableLiveData<Vec3D> by lazy {
        MutableLiveData<Vec3D>()
    }
    val currentGRV: MutableLiveData<Vec3D> by lazy {
        MutableLiveData<Vec3D>()
    }
    val currentSpeed: MutableLiveData<Vec3D> by lazy {
        MutableLiveData<Vec3D>()
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): SensorRecord = this@SensorRecord
    }

    //速度计算
    private val SpeedCaculator = object {
        private var lastT_Acc: Long = 0
        private var lastT_GRV: Long = 0
        private var lastT_AccX: Long = 0
        private var T_AccX: Long = 0
        private lateinit var lastAcc: Vec3D
        private lateinit var lastAccX: Vec3D
        private lateinit var lastGRV: Vec3D
        private lateinit var AccX: Vec3D //已经转换坐标系的加速度
        private val Speed = Vec3D() //目前初始为0
        private val sensorData_Speed = ArrayList<Vec3D>()

        fun GRV_Update(time: Long, GRV: Vec3D) {
            if (this::lastGRV.isInitialized && this::lastAcc.isInitialized && lastT_GRV <= lastT_Acc) {
                AccX = lastAcc.Rotate(Slerp(lastGRV, GRV, (lastT_Acc - lastT_GRV).toDouble() / (time - lastT_GRV).toDouble()));
                AccX_Update(lastT_Acc, AccX)
            }
            lastT_GRV = time
            lastGRV = GRV
        }

        fun Acc_Update(time: Long, Acc: Vec3D) {
            lastT_Acc = time
            lastAcc = Acc
        }

        private fun AccX_Update(time: Long, Acc: Vec3D) {
            if (this::lastAccX.isInitialized) {
                Speed += (lastAccX + AccX) * ((time - lastT_AccX).toDouble() / 2e9)
                currentSpeed.value = Speed
            }
            lastT_AccX = time
            lastAccX = Acc
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorAcc = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensorGRV = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        sensorManager.registerListener(this, sensorAcc, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, sensorGRV, SensorManager.SENSOR_DELAY_NORMAL)

        powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        m_wkik = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, SensorRecord::class.qualifiedName)
        m_wkik.acquire()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
    }

    val test=StringBuilder()
    var starttime:Long=0

    override fun onSensorChanged(event: SensorEvent) {
        val range = 1.0 //设定一个精度范围
        val sensor = event.sensor
        if (starttime==0L) starttime=event.timestamp

        when (sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                //Data Receive from sensor
                val tmpVec = Vec3D(event.values)
                currentAcc.value = tmpVec
                SpeedCaculator.Acc_Update(event.timestamp, tmpVec)
                test.append("Acc:$(event.timestamp)")
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val tmpVec = Vec3D(event.values)
                //currentGRV.value = tmpVec
                SpeedCaculator.GRV_Update(event.timestamp, tmpVec)
                test.append("GRV:$(event.timestamp)")
            }
        }
        if (event.timestamp-starttime>100_000_000_000) applicationContext.FileSave(test.toString(),filename = "tser.txt")
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        m_wkik.release()
    }
}