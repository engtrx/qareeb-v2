package com.engtrx.qareebv2

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var statusText: TextView
    private lateinit var deviceListView: ListView
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val discoveredDevices = mutableListOf<String>()
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    private var acceptThread: AcceptThread? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startBluetooth()
        else statusText.text = "يجب الموافقة على الأذونات."
    }

    private val enableDiscoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { 
        statusText.text = "الهاتف جاهز للاستقبال والبحث."
        startBluetooth()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
                    // إصلاح اسم الجهاز: نقرأ الاسم من الـ Intent مباشرة
                    val deviceName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: device?.name ?: "جهاز غير معروف"
                    
                    device?.let {
                        val mac = it.address
                        val displayString = "$deviceName\n$mac"
                        if (!discoveredDevices.contains(displayString)) {
                            discoveredDevices.add(displayString)
                            deviceListAdapter.notifyDataSetChanged()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> statusText.text = "جاري البحث..."
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> statusText.text = "انتهى البحث."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        val title = TextView(this).apply {
            text = "قريب V2"
            textSize = 28f
            setTextColor(Color.parseColor("#1A1A1A"))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "جاري تجهيز البلوتوث..."
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, 40)
        }

        val searchButton = Button(this).apply {
            text = "بدء البحث"
            setBackgroundColor(Color.parseColor("#0088cc"))
            setTextColor(Color.WHITE)
            setOnClickListener { checkPermissionsAndStart() }
        }

        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, discoveredDevices)
        deviceListView = ListView(this).apply {
            adapter = deviceListAdapter
            setOnItemClickListener { _, _, position, _ ->
                val info = discoveredDevices[position].split("\n")
                if (info.size >= 2) {
                    val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                        putExtra("DEVICE_ADDRESS", info[1])
                        putExtra("DEVICE_NAME", info[0])
                        putExtra("IS_SERVER", false)
                    }
                    startActivity(intent)
                }
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(searchButton)
        layout.addView(deviceListView)
        setContentView(layout)

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            makeDiscoverable()
        } else {
            requestPermissionsLauncher.launch(permissions)
        }
    }

    @SuppressLint("MissingPermission")
    private fun makeDiscoverable() {
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bluetoothAdapter?.isEnabled == false) {
            statusText.text = "الرجاء تشغيل البلوتوث."
            return
        }
        
        if (acceptThread == null) {
            acceptThread = AcceptThread()
            acceptThread?.start()
        }

        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        enableDiscoverableLauncher.launch(discoverableIntent)
    }

    @SuppressLint("MissingPermission")
    private fun startBluetooth() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(receiver, filter)
        discoveredDevices.clear()
        deviceListAdapter.notifyDataSetChanged()
        bluetoothAdapter?.startDiscovery()
    }

    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter?.listenUsingRfcommWithServiceRecord("Qareeb", MY_UUID)
        }

        @SuppressLint("MissingPermission")
        override fun run() {
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    shouldLoop = false
                    null
                }
                
                if (socket != null) {
                    // نمرر الـ Socket لشاشة الدردشة
                    ChatActivity.serverSocket = socket
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "تم استلام طلب دردشة!", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                            putExtra("DEVICE_ADDRESS", socket.remoteDevice.address)
                            putExtra("DEVICE_NAME", socket.remoteDevice.name ?: "جهاز")
                            putExtra("IS_SERVER", true)
                        }
                        startActivity(intent)
                    }
                    shouldLoop = false
                }
            }
        }

        fun cancel() {
            try { mmServerSocket?.close() } catch (e: IOException) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
            bluetoothAdapter?.cancelDiscovery()
            acceptThread?.cancel()
        } catch (e: Exception) {}
    }
}
