package com.example.sensortest

import android.Manifest
import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.activity_motion_function.*
import kotlinx.android.synthetic.main.activity_raw_data_function.*
import kotlinx.coroutines.*
import org.jetbrains.anko.toast
import threeDvector.Vec3D
import java.text.DecimalFormat


class Raw_data_function : AppCompatActivity() {

    val df = DecimalFormat("####0.00")
    val AccObserver = Observer<Vec3D> {
        tv_step.text = "X: " + df.format(it.x)
        tv_step2.text = "Y: " + df.format(it.y)
        tv_step3.text = "Z: " + df.format(it.z)
    }
    val GRVObserver = Observer<Vec3D> {
        tv_Gstep.text = "X: " + df.format(it.x)
        tv_Gstep2.text = "Y: " + df.format(it.y)
        tv_Gstep3.text = "Z: " + df.format(it.z)
    }
    var processState = false
    private val LOCATION_PERMISSION = 1


    private lateinit var mService: SensorRecord
    private var mBound: Boolean = false
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as SensorRecord.LocalBinder
            mService = binder.getService()
            mService.currentAcc.observe(this@Raw_data_function, AccObserver)
            mService.currentGRV.observe(this@Raw_data_function, GRVObserver)
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action!!
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                    //val deviceName = device.name
                    if (device.name == TARGET_DEVICE_NAME) {
                        bluetoothAdapter.cancelDiscovery();
                        applicationContext.toast("蓝牙已开启")
                        GlobalScope.launch { BluetoothService.connectDevice(device) }
                        return
                    }
                    //val deviceHardwareAddress = device.address // MAC address
                }
            }
        }
    }
    private val bluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val TARGET_DEVICE_NAME = "DESKTOP-Q4HJGK7"

    private fun getPairedDevices(): Unit {
        // 获得和当前Android已经配对的蓝牙设备。
        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.getBondedDevices()
        if (pairedDevices.isNotEmpty()) {
            for (device in pairedDevices) {
                if (device.name == TARGET_DEVICE_NAME) {
                    applicationContext.toast("蓝牙已开启")
                    GlobalScope.launch { BluetoothService.connectDevice(device) }
                    return
                }
            }
        }
        bluetoothAdapter.startDiscovery()
    }


    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_raw_data_function)
        //蓝牙,暂时默认蓝牙是开的，调试时请注意。。。下面这段从官方文档抄来的有暂时用不了
        /*if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }*/
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)
        //
        if (ServiceCheckUtil.isRunning(applicationContext, SensorRecord::class.qualifiedName)) {
            //若SensorRecord已在运行，绑定并更改相应设置
            processState = true
            val intent = Intent(this, SensorRecord::class.java)
            intent.setAction("com.example.server.SensorRecord")
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        BindViews()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun BindViews() {
        btn_start.text = if (processState) "停止" else "开始记录数据"
        btn_start.setOnClickListener {
            if (processState) {
                val intent = Intent(this, SensorRecord::class.java)
                intent.setAction("com.example.server.SensorRecord")
                unbindService(connection)
                stopService(intent)
                //关闭蓝牙
                BluetoothService.cancel()

                btn_start.text = "开始记录数据"
            } else {
                //开启蓝牙
                getPairedDevices()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        //requestPermissions是异步执行的
                        requestPermissions(arrayOf(ACCESS_BACKGROUND_LOCATION),
                                LOCATION_PERMISSION)
                        while (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        }
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        //requestPermissions是异步执行的
                        requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                                LOCATION_PERMISSION)
                        while (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        }
                    }
                }

                val intent = Intent(this, SensorRecord::class.java)
                intent.setAction("com.example.server.SensorRecord")
                startService(intent)
                bindService(intent, connection, Context.BIND_AUTO_CREATE)

                btn_start.text = "停止"
            }
            processState = !processState
        }


    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toast("获取了位置权限")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}