package com.example.sensortest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.ArrayMap
import androidx.annotation.RequiresApi
import androidx.constraintlayout.motion.widget.Debug.getLocation
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.MutableLiveData
import kotlinx.android.synthetic.main.activity_motion_function.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.toast
import threeDvector.Rotate
import threeDvector.Slerp
import threeDvector.Vec3D
import threeDvector.Vec3D_t
import java.util.*
import kotlin.concurrent.timerTask


class SensorRecord : Service(), SensorEventListener {
    private val binder = LocalBinder()
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private lateinit var powerManager: PowerManager
    private lateinit var m_wkik: PowerManager.WakeLock
    private val sensorData_Speed = ArrayList<Vec3D_t>()
    private val sensorData = ArrayList<Triple<Long, Vec3D, Float>>()
    private val GPS_Timing = ArrayList<Long>()
    private val GPS_location = ArrayList<Pair<Double,Double>>()
    private var Acc0 = Vec3D()

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
    private val SpeedCalculator = object {
        private var lastT_Acc: Long = 0
        private var lastT_GRV: Long = 0
        private var lastT_AccX: Long = 0
        private var T_AccX: Long = 0
        private lateinit var lastAcc: Vec3D
        private lateinit var lastAccX: Vec3D
        private lateinit var lastGRV: Vec3D
        private lateinit var AccX: Vec3D //已经转换坐标系的加速度
        private var Speed = Vec3D()
        private var lastSpeed = 0F

        private var AccCount = 0
        private var AccSum = Vec3D()

        fun GRV_Update(time: Long, GRV: Vec3D) {
            if (this::lastGRV.isInitialized && this::lastAcc.isInitialized && lastT_GRV <= lastT_Acc) {
                AccX = lastAcc.Rotate(Slerp(lastGRV, GRV, (lastT_Acc - lastT_GRV).toDouble() / (time - lastT_GRV).toDouble()));
                AccX_Update(lastT_Acc, AccX)
            }
            lastT_GRV = time
            lastGRV = GRV
        }

        @SuppressLint("MissingPermission")
        fun Acc_Update(time: Long, Acc: Vec3D) {
            lastT_Acc = time
            lastAcc = Acc
            lastSpeed = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)!!.speed
        }

        private fun AccX_Update(time: Long, AccX: Vec3D) {
            //sensorData.add(Triple(time, AccX.copy(), lastSpeed))
            lastT_AccX = time
            lastAccX = AccX
            AccCount++;
            AccSum = AccSum + AccX;
        }

        fun Acc_Clear(): Vec3D {
            val ans = if (AccCount > 0) AccSum * (1.0 / AccCount) else Vec3D()
            AccCount = 0
            AccSum = Vec3D()
            return ans
        }

        fun sample() {
            sensorData_Speed.add(Vec3D_t(Speed.copy(), lastT_AccX))
            if (BluetoothService.isConnected) GlobalScope.launch { BluetoothService.sendData(serialize(lastAccX).toByteArray()) }
        }
    }

    private val mHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == 0x2739) {
                SpeedCalculator.sample()
            }
            super.handleMessage(msg)
        }
    }

    val locationListener = object : LocationListener {
        override fun onProviderDisabled(provider: String) {
            toast("关闭了GPS")
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onProviderEnabled(provider: String) {
            toast("打开了GPS")
        }

        @SuppressLint("MissingPermission")
        override fun onLocationChanged(location: Location) {
            sensorData.add(Triple(location.time, SpeedCalculator.Acc_Clear(), location.speed))
            GPS_location.add(Pair(location.latitude,location.longitude))
            //GPS_Timing.add(location.time)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorAcc = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensorGRV = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        sensorManager.registerListener(this, sensorAcc, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, sensorGRV, SensorManager.SENSOR_DELAY_NORMAL)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 20, 0F, locationListener)
        //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 20, 0F, locationListener)

        powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        m_wkik = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, SensorRecord::class.qualifiedName)
        m_wkik.acquire()
        //读取校零值
        val tmp = applicationContext.FileLoad(filename = "Avg.JSON")
        if (tmp != null) Acc0 = deserialize<Vec3D>(tmp)
        //定时采样
        //Timer().schedule(timerTask { mHandler.sendEmptyMessage(0x2739) }, 3_000, 1_000)
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

    override fun onSensorChanged(event: SensorEvent) {
        val range = 1.0 //设定一个精度范围
        val sensor = event.sensor

        when (sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                //Data Receive from sensor
                val tmpVec = Vec3D(event.values)
                currentAcc.value = tmpVec
                SpeedCalculator.Acc_Update(event.timestamp, tmpVec - Acc0)
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val tmpVec = Vec3D(event.values)
                currentGRV.value = tmpVec
                SpeedCalculator.GRV_Update(event.timestamp, tmpVec)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(locationListener)
        m_wkik.release()
        applicationContext.FileSave(serialize(sensorData), filename = "SensorRecord.JSON")
        applicationContext.FileSave(serialize(GPS_location), filename = "GPSRecord.JSON")
        //applicationContext.FileSave(serialize(GPS_Timing), filename = "GPSTiming.JSON")
    }
}