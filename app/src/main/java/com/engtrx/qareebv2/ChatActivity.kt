package com.engtrx.qareebv2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var chatTextView: TextView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var scrollView: ScrollView

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        val deviceName = intent.getStringExtra("DEVICE_NAME") ?: "جهاز"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        val title = TextView(this).apply {
            text = "محادثة مع: $deviceName"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0088cc"))
            setPadding(30, 40, 30, 40)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }

        chatTextView = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(30, 30, 30, 30)
        }

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(chatTextView)
        }

        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.WHITE)
        }

        inputEditText = EditText(this).apply {
            hint = "اكتب رسالة..."
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        sendButton = Button(this).apply {
            text = "إرسال"
            setBackgroundColor(Color.parseColor("#0088cc"))
            setTextColor(Color.WHITE)
            setOnClickListener { sendMessage() }
        }

        inputLayout.addView(inputEditText)
        inputLayout.addView(sendButton)
        layout.addView(title)
        layout.addView(scrollView)
        layout.addView(inputLayout)
        setContentView(layout)

        if (deviceAddress != null) {
            connectToDevice(deviceAddress)
        } else {
            appendMessage("خطأ: لم يتم العثور على عنوان الجهاز.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(address: String) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(address)
        appendMessage("جاري الاتصال...")

        Thread {
            try {
                bluetoothAdapter?.cancelDiscovery()
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                inputStream = bluetoothSocket?.inputStream
                isConnected = true

                runOnUiThread { appendMessage("تم الاتصال بنجاح!") }
                listenForMessages()
            } catch (e: IOException) {
                runOnUiThread { appendMessage("فشل الاتصال.") }
                try { bluetoothSocket?.close() } catch (ex: IOException) {}
            }
        }.start()
    }

    private fun listenForMessages() {
        val buffer = ByteArray(1024)
        var bytes: Int
        while (isConnected) {
            try {
                bytes = inputStream?.read(buffer) ?: 0
                if (bytes > 0) {
                    val message = String(buffer, 0, bytes)
                    runOnUiThread { appendMessage("الطرف الآخر: $message") }
                }
            } catch (e: IOException) {
                isConnected = false
                runOnUiThread { appendMessage("انقطع الاتصال.") }
                break
            }
        }
    }

    private fun sendMessage() {
        val message = inputEditText.text.toString()
        if (message.isNotEmpty() && isConnected) {
            try {
                outputStream?.write(message.toByteArray())
                runOnUiThread {
                    appendMessage("أنت: $message")
                    inputEditText.text.clear()
                }
            } catch (e: IOException) {
                appendMessage("فشل الإرسال.")
            }
        }
    }

    private fun appendMessage(message: String) {
        chatTextView.append("$message\n\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { bluetoothSocket?.close() } catch (e: IOException) {}
    }
}
