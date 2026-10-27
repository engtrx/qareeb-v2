package com.engtrx.qareebv2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ContentValues
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var attachButton: Button
    private lateinit var scrollView: ScrollView

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // لاختيار الملفات والصور والصوتيات
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) sendFile(uri)
    }

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
            setPadding(10, 20, 10, 20)
            setBackgroundColor(Color.WHITE)
        }

        attachButton = Button(this).apply {
            text = "+"
            textSize = 22f
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            setTextColor(Color.BLACK)
            setOnClickListener { 
                // يفتح مدير الملفات لاختيار صور أو صوتيات
                getContent.launch("*/*") 
            }
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

        inputLayout.addView(attachButton)
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
                runOnUiThread { appendMessage("فشل الاتصال. تأكد أن الجهاز الآخر فتح التطبيق.") }
                try { bluetoothSocket?.close() } catch (ex: IOException) {}
            }
        }.start()
    }

    private fun listenForMessages() {
        val buffer = ByteArray(8192) // مخزن أكبر للملفات
        var bytes: Int
        while (isConnected) {
            try {
                bytes = inputStream?.read(buffer) ?: 0
                if (bytes > 0) {
                    val rawMessage = String(buffer, 0, bytes)
                    
                    if (rawMessage.startsWith("FILE|")) {
                        handleIncomingFile(rawMessage)
                    } else {
                        runOnUiThread { appendMessage("الطرف الآخر: $rawMessage") }
                    }
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

    // دالة إرسال الملفات والصور
    private fun sendFile(uri: Uri) {
        if (!isConnected) {
            Toast.makeText(this, "غير متصل", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            
            if (bytes != null) {
                if (bytes.size > 1.5 * 1024 * 1024) { // حد أقصى 1.5 ميجابايت للبلوتوث
                    Toast.makeText(this, "الملف كبير جداً (الحد 1.5MB)", Toast.LENGTH_LONG).show()
                    return
                }

                // استخراج اسم الملف
                var fileName = "ملف_مرسل"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                // تحويل الملف إلى نص Base64 لإرساله
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val fileMessage = "FILE|$fileName|$base64Data"

                Thread {
                    try {
                        outputStream?.write(fileMessage.toByteArray())
                        runOnUiThread { appendMessage("أنت: أرسلت ($fileName)") }
                    } catch (e: IOException) {
                        runOnUiThread { appendMessage("فشل إرسال الملف.") }
                    }
                }.start()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في قراءة الملف", Toast.LENGTH_SHORT).show()
        }
    }

    // دالة استقبال الملفات وحفظها في التنزيلات
    private fun handleIncomingFile(message: String) {
        try {
            val parts = message.split("|")
            if (parts.size == 3) {
                val fileName = parts[1]
                val base64Data = parts[2]
                val fileBytes = Base64.decode(base64Data, Base64.DEFAULT)

                // حفظ في مجلد التنزيلات
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "Qareeb_$fileName")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    val os = resolver.openOutputStream(uri)
                    os?.write(fileBytes)
                    os?.close()
                    runOnUiThread { appendMessage("الطرف الآخر: أرسل ($fileName)\nتم حفظه في التنزيلات.") }
                }
            }
        } catch (e: Exception) {
            runOnUiThread { appendMessage("خطأ في استقبال الملف.") }
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
