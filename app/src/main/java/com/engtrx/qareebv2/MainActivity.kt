package com.engtrx.qareebv2

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var statusText: TextView

    // طلب أذونات البلوتوث من المستخدم
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBluetooth()
        } else {
            statusText.text = "يجب الموافقة على أذونات البلوتوث ليعمل التطبيق بشكل صحيح."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // بناء الواجهة برمجياً بتصميم حديث
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 120, 60, 60)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        val title = TextView(this).apply {
            text = "قريب V2"
            textSize = 32f
            setTextColor(Color.parseColor("#1A1A1A"))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "اضغط على الزر بالأسفل للبحث عن الأجهزة القريبة منك."
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, 80)
        }

        val searchButton = Button(this).apply {
            text = "البحث والاقتران"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#0088cc"))
            setTextColor(Color.WHITE)
            setPadding(0, 40, 0, 40)
            setOnClickListener {
                checkPermissionsAndStart()
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(searchButton)

        setContentView(layout)
    }

    private fun checkPermissionsAndStart() {
        // تحديد الأذونات المطلوبة بناءً على إصدار الأندرويد
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // التحقق مما إذا كانت الأذونات ممنوحة مسبقاً
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
            statusText.text = "عذراً، هذا الجهاز لا يدعم تقنية البلوتوث."
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            statusText.text = "البلوتوث مغلق. يرجى تشغيله للمتابعة."
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
            return
        }

        // إذا وصلنا هنا، فالبلوتوث يعمل والأذونات سليمة
        statusText.text = "جاري البحث عن الأجهزة...\n(سيتم عرض قائمة الأجهزة هنا في الخطوة القادمة)"
        Toast.makeText(this, "البلوتوث جاهز للعمل!", Toast.LENGTH_SHORT).show()
    }
}
