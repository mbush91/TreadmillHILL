package org.mikebush.treadmilhiit

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    var scanList:ListView? = null
    var scanBtn:Button? = null
    var statusView:TextView? = null
    var bluetoothAdapter: BluetoothAdapter? = null
    var bluetoothLeScanner: BluetoothLeScanner? = null
    var connected = false


    private var requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )

    private fun getPermissions(): Unit {
        this.requestPermissions(requiredPermissions, 1)
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            if (action != null ) {
                Log.i("Action", action)
                when (intent.action) {
                    BluetoothLeService.ACTION_GATT_CONNECTED -> {
                        connected = true
                        //updateConnectionState(R.string.connected)
                    }
                    BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                        connected = false
                        //updateConnectionState(R.string.disconnected)
                    }
                }
            }
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                statusView?.setText("Finished")
                scanBtn?.isEnabled = true
            }
        }
    }

    //private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getPermissions()

        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.getAdapter()
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Log.e("BLE","No BLE support")
        }
        else {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        }

        scanList = findViewById<ListView>(R.id.scan_listView)
        scanBtn = findViewById<Button>(R.id.scan_button)
        statusView = findViewById<TextView>(R.id.status_textView)

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 0)
        }

        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }


    private var scanning = false
    private val handler = Handler()

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 10000
    val treadmill_addr = "FE:FA:59:F4:B9:B1"
    var leDevices = mutableListOf<BluetoothDevice>()
    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (treadmill_addr == result.device.address) {
                leDevices.add(result.device)
                Log.i(TAG, "Device " + result.device.address)
                bluetoothService?.connect(treadmill_addr)
            }
        }
    }

    fun scanBtnHandler(view: View) {
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            statusView?.setText("Scanning")
            scanBtn?.isEnabled = false
            handler.postDelayed({
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
                statusView?.setText("Finished")
                scanBtn?.isEnabled = true
            }, SCAN_PERIOD)
            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)

        }
    }

    // SERVICE STUFF

    private var bluetoothService : BluetoothLeService? = null

    // Code to manage Service lifecycle.
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            componentName: ComponentName,
            service: IBinder
        ) {
            bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService?.let { bluetooth ->
                if (!bluetooth.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth")
                    finish()
                }

            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothService = null
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        if (bluetoothService != null) {
            val result = bluetoothService!!.connect(treadmill_addr)
            Log.d(TAG, "Connect request result=$result")
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        }
    }


}