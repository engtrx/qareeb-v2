package com.engtrx.qareebv2

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
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
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    companion object {
        var serverSocket: BluetoothSocket? = null // لاستقبال الاتصال من MainActivity
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var chatLayout: LinearLayout
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var micButton: Button
    private lateinit var attachButton: Button
    private lateinit var scrollView: ScrollView

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var audioFile: File? = null

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) toggleRecording()
        else Toast.makeText(this, "يجب السماح بالوصول للميكروفون", Toast.LENGTH_SHORT).show()
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) sendFile(uri)
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        val deviceName = intent.getStringExtra("DEVICE_NAME") ?: "جهاز"
        val isServer = intent.getBooleanExtra("IS_SERVER", false)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#ECE5DD"))
        }

        val title = TextView(this).apply {
            text = deviceName
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#075E54"))
            setPadding(30, 40, 30, 40)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }

        chatLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(chatLayout)
        }

        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(5, 10, 5, 10)
            setBackgroundColor(Color.WHITE)
        }

        // زر الوسائط (+)
        attachButton = Button(this).apply {
            text = "+"
            textSize = 20f
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            setTextColor(Color.BLACK)
            setOnClickListener { getContent.launch("*/*") }
        }

        // زر الميكروفون (أيقونة أندرويد قياسية)
        micButton = Button(this).apply {
            text = "صوت" // سنستخدم نص بدلاً من الإيموجي
            textSize = 12f
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            setTextColor(Color.BLACK)
            setOnClickListener { checkAudioPermissionAndRecord() }
        }

        inputEditText = EditText(this).apply {
            hint = "اكتب رسالة..."
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        sendButton = Button(this).apply {
            text = "إرسال"
            setBackgroundColor(Color.parseColor("#128C7E"))
            setTextColor(Color.WHITE)
            setOnClickListener { sendMessage() }
        }

        inputLayout.addView(attachButton)
        inputLayout.addView(micButton)
        inputLayout.addView(inputEditText)
        inputLayout.addView(sendButton)
        mainLayout.addView(title)
        mainLayout.addView(scrollView)
        mainLayout.addView(inputLayout)
        setContentView(mainLayout)

        // إصلاح الاتصال: إذا كنا خادم، نستخدم الـ Socket الذي أتى من MainActivity
        if (isServer) {
            bluetoothSocket = serverSocket
            if (bluetoothSocket != null) {
                setupStreams()
                addMessageBubble("تم الاتصال (مستقبل)!", false)
            } else {
                addMessageBubble("خطأ: لم يتم استلام الاتصال.", false)
            }
        } else if (deviceAddress != null) {
            connectToDevice(deviceAddress)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(address: String) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(address)
        addMessageBubble("جاري الاتصال...", false)
        
        Thread {
            try {
                bluetoothAdapter?.cancelDiscovery()
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()
                setupStreams()
                runOnUiThread { addMessageBubble("تم الاتصال بنجاح!", false) }
            } catch (e: IOException) {
                runOnUiThread { addMessageBubble("فشل الاتصال. تأكد من أن الهاتف الآخر فتح التطبيق.", false) }
            }
        }.start()
    }

    private fun setupStreams() {
        try {
            outputStream = bluetoothSocket?.outputStream
            inputStream = bluetoothSocket?.inputStream
            isConnected = true
            listenForMessages()
        } catch (e: IOException) {
            isConnected = false
        }
    }

    private fun listenForMessages() {
        val buffer = ByteArray(16384) 
        var bytes: Int
        while (isConnected) {
            try {
                bytes = inputStream?.read(buffer) ?: 0
                if (bytes > 0) {
                    val rawMessage = String(buffer, 0, bytes)
                    if (rawMessage.startsWith("FILE|")) {
                        handleIncomingFile(rawMessage)
                    } else {
                        runOnUiThread { addMessageBubble(rawMessage, false) }
                    }
                }
            } catch (e: IOException) {
                isConnected = false
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
                    addMessageBubble(message, true)
                    inputEditText.text.clear()
                }
            } catch (e: IOException) {}
        }
    }

    private fun checkAudioPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            toggleRecording()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
                micButton.text = "صوت"
                Toast.makeText(this, "تم التسجيل، جاري الإرسال...", Toast.LENGTH_SHORT).show()
                sendAudioFile()
            } catch (e: Exception) {
                Toast.makeText(this, "فشل التسجيل", Toast.LENGTH_SHORT).show()
            }
        } else {
            val file = File(externalCacheDir, "voice.3gp")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            audioFile = file
            isRecording = true
            micButton.text = "إيقاف"
            Toast.makeText(this, "جاري التسجيل... اضغط مجدداً للإيقاف", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendAudioFile() {
        if (!isConnected || audioFile == null || !audioFile!!.exists()) return
        try {
            val bytes = audioFile!!.readBytes()
            if (bytes.size > 1.5 * 1024 * 1024) {
                Toast.makeText(this, "الملف الصوتي كبير جداً", Toast.LENGTH_SHORT).show()
                return
            }
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val fileMessage = "FILE|voice.3gp|$base64Data"
            
            Thread {
                try {
                    outputStream?.write(fileMessage.toByteArray())
                    runOnUiThread { addMessageBubble("رسالة صوتية", true) }
                } catch (e: IOException) {}
            }.start()
        } catch (e: Exception) {}
    }

    private fun sendFile(uri: Uri) {
        if (!isConnected) return
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            if (bytes != null) {
                if (bytes.size > 1.5 * 1024 * 1024) {
                    Toast.makeText(this, "الملف كبير جداً (الحد 1.5MB)", Toast.LENGTH_LONG).show()
                    return
                }
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val fileMessage = "FILE|ملف_مرسل|$base64Data"
                Thread {
                    try {
                        outputStream?.write(fileMessage.toByteArray())
                        runOnUiThread { addMessageBubble("أنت: أرسلت ملف", true) }
                    } catch (e: IOException) {}
                }.start()
            }
        } catch (e: Exception) {}
    }

    private fun handleIncomingFile(message: String) {
        try {
            val parts = message.split("|")
            if (parts.size == 3) {
                val fileName = parts[1]
                val base64Data = parts[2]
                val fileBytes = Base64.decode(base64Data, Base64.DEFAULT)

                if (fileName == "voice.3gp") {
                    val tempFile = File(externalCacheDir, "received_voice.3gp")
                    tempFile.writeBytes(fileBytes)
                    runOnUiThread {
                        addMessageBubble("رسالة صوتية", false)
                        playAudio(tempFile)
                    }
                } else {
                    val resolver = contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "Qareeb_$fileName")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let {
                        val os = resolver.openOutputStream(it)
                        os?.write(fileBytes)
                        os?.close()
                        runOnUiThread { addMessageBubble("ملف: $fileName", false) }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private fun playAudio(file: File) {
        try {
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.start()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في تشغيل الصوت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addMessageBubble(text: String, isMine: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(30, 20, 30, 20)
            
            val bg = GradientDrawable().apply {
                setColor(if (isMine) Color.parseColor("#DCF8C6") else Color.WHITE)
                cornerRadius = 30f
            }
            background = bg

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 10, 0, 10)
            params.gravity = if (isMine) android.view.Gravity.END else android.view.Gravity.START
            layoutParams = params
        }
        chatLayout.addView(bubble)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { bluetoothSocket?.close() } catch (e: IOException) {}
        mediaRecorder?.release()
    }
}
