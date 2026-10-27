package com.engtrx.qareebv2

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var statusText: TextView
    private lateinit var deviceListView: ListView
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val discoveredDevices = mutableListOf<String>()

    // طلب أذونات البلوتوث
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBluetooth()
        } else {
            statusText.text = "يجب الموافقة على الأذونات ليعمل البحث."
        }
    }

    // المستقبل (Receiver) الذي يلتقط الأجهزة التي يتم العثور عليها
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    if (device != null) {
                        val deviceName = device.name ?: "جهاز غير معروف"
                        val deviceMac = device.address
                        val displayString = "$deviceName\n$deviceMac"
                        
                        // إضافة الجهاز للقائمة إذا لم يكن موجوداً مسبقاً
                        if (!discoveredDevices.contains(displayString)) {
                            discoveredDevices.add(displayString)
                            deviceListAdapter.notifyDataSetChanged()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    statusText.text = "جاري البحث الفعلي... الرجاء الانتظار."
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    statusText.text = "انتهى البحث. تم العثور على ${discoveredDevices.size} جهاز."
                }
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
            text = "اضغط على الزر بالأسفل للبحث."
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, 40)
        }

        val searchButton = Button(this).apply {
            text = "البحث عن الأجهزة"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#0088cc"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                checkPermissionsAndStart()
            }
        }

        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, discoveredDevices)
        deviceListView = ListView(this).apply {
            adapter = deviceListAdapter
            setPadding(0, 20, 0, 0)
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(searchButton)
        layout.addView(deviceListView)

        setContentView(layout)
    }

    private fun checkPermissionsAndStart() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val hasPermission = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            startBluetooth()
        } else {
            requestPermissionsLauncher.launch(permissions)
        }
    }

    private fun startBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            statusText.text = "الجهاز لا يدعم البلوتوث."
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            statusText.text = "البلوتوث مغلق. يرجى تشغيله."
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
            return
        }

        // إعداد المستقبل لبدء استقبال الأجهزة
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(receiver, filter)

        // مسح القائمة القديمة وبدء بحث جديد
        discoveredDevices.clear()
        deviceListAdapter.notifyDataSetChanged()
        
        Toast.makeText(this, "بدأ البحث الفعلي...", Toast.LENGTH_SHORT).show()
        bluetoothAdapter?.startDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        // إيقاف البحث وإلغاء تسجيل المستقبل عند إغلاق التطبيق لتوفير البطارية
        try {
            unregisterReceiver(receiver)
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: Exception) {
            // تجاهل الخطأ إذا لم يكن مسجلاً
        }
    }
}
