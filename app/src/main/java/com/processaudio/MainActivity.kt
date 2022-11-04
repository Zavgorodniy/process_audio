package com.processaudio

import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.processaudio.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*


private const val LOG_TAG = "AudioRecordTest"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var fileName: String = ""

    private var recorder: MediaRecorder? = null

    private var isRecording = false

    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(RECORD_AUDIO)

    private val buffer = StringBuilder()

    private val locale = Locale.UK
    private val timeFormatPattern = "yyyy/MM/dd HH:mm:ss SSS'ms'"
    private val formatter = SimpleDateFormat(timeFormatPattern, locale)

    private var startNano = 0L
    private var startMs = 0L
    private var endNano = 0L
    private var endMs = 0L

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) finish()
    }

    private fun onRecord(start: Boolean) = if (start) {
        binding.btStart.text = "Stop"
        startRecording()
    } else {
        binding.btStart.text = "Start"
        stopRecording()
    }

    private fun startRecording() {
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }
        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            try {
                prepare()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }

            startMs = System.currentTimeMillis()
            startNano = System.nanoTime()
            start() // recording start, native code
            endNano = System.nanoTime()
            endMs = System.currentTimeMillis()
            val deltaMcs = (endNano - startNano) / 1000
            val deltaMs = (endMs - startMs)

            val startDate = formatter.format(Date(startMs))
            val endDate = formatter.format(Date(endMs))

            binding.tvLog.text = ""
            buffer.apply {
                clear()
                appendLine("start time(real time): " + startDate)
                appendLine("start time ms(real time): " + startMs.toString())
                appendLine("start time ms(nano time): " + (startNano / 1000000).toString())
                appendLine("after start time(real time): " + endDate)
                appendLine("after start time ms(real time): " + endMs.toString())
                appendLine("after start time ms(nano time): " + (endNano / 1000000).toString())
                appendLine("Delay mcs(nano time): " + deltaMcs.toString())
                appendLine("Delay ms(real time): " + deltaMs.toString())
                appendLine("")
            }

            Log.e(LOG_TAG, "Record start time ms(real time): " + startMs.toString())
            Log.e(LOG_TAG, "Record start time(real time): " + startDate)
            Log.e(LOG_TAG, "Record start time ms(nano time): " + (startNano / 1000000).toString())
            Log.e(LOG_TAG, "Record success start time ms(real time): " + endMs.toString())
            Log.e(LOG_TAG, "Record success start time(real time): " + endDate)
            Log.e(
                LOG_TAG,
                "Record success start time ms(nano time): " + (endNano / 1000000).toString()
            )
            Log.e(LOG_TAG, "Delay in the recording start mcs(nano time): " + deltaMcs.toString())
            Log.e(LOG_TAG, "Delay in the recording start ms(real time): " + deltaMs.toString())
        }
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            reset()
            release()
        }
        recorder = null

        val file = File(fileName)
        if (file.exists()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                buffer.appendLine("File metadata:")
                for (i in 0..999) {
                    var data = retriever.extractMetadata(i)
                    if (data != null) {
                        if (data.endsWith('Z')) {
                            buffer.appendLine(data)
                            val parser = SimpleDateFormat("yyyyMMdd'T'HHmmss'.'SSS'Z'", locale)
                            try {
                                val date = parser.parse(data)
                                date?.let {
                                    data = formatter.format(date)
                                }
                            } catch (e: Exception) {
                                Log.i(LOG_TAG, "Exception : " + e.message)
                            }
                        }
                        buffer.appendLine(data)
                        Log.i(LOG_TAG, "$data")
                    }
                }
            } catch (e: Exception) {
                val msg = "Exception : " + e.message
                buffer.appendLine(msg)
                Log.e(LOG_TAG, msg)
            }
        } else {
            Log.e(LOG_TAG, "$fileName file doesn´t exist.")
        }
        binding.tvLog.text = buffer.toString()
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        formatter.timeZone = TimeZone.getDefault()

        // Record to the external cache directory for visibility
        fileName = "${cacheDir?.absolutePath}/audiorecordtest.wav"

        binding.btStart.setOnClickListener {
            if (permissionToRecordAccepted) {
                isRecording = !isRecording
                onRecord(isRecording)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    REQUEST_RECORD_AUDIO_PERMISSION
                )
            }
        }
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
    }

//    @Throws(IOException::class)
//    fun saveFile(
//        context: Context,
//        mimeType: String, displayName: String
//    ): Uri {
//
//        val values = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
//            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
//            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
//        }
//
//        val resolver = context.contentResolver
//        var uri: Uri? = null
//
//        try {
//            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
//                ?: throw IOException("Failed to create new MediaStore record.")
//
//            resolver.openOutputStream(uri)?.use {
//                if (!bitmap.compress(format, 95, it))
//                    throw IOException("Failed to save bitmap.")
//            } ?: throw IOException("Failed to open output stream.")
//
//            return uri
//
//        } catch (e: IOException) {
//
//            uri?.let { orphanUri ->
//                // Don't leave an orphan entry in the MediaStore
//                resolver.delete(orphanUri, null, null)
//            }
//
//            throw e
//        }
//
//    @RequiresApi(Build.VERSION_CODES.Q)
//    private fun saveFileUsingMediaStore(context: Context, url: String, fileName: String) {
//        val resolver = contentResolver
//        val contentValues = ContentValues()
//        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name.toString() + ".jpg")
//        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
//        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
//        val imageUri: Uri? =
//            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
//        fos = resolver.openOutputStream(Objects.requireNonNull(imageUri))
//
//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
//            put(MediaStore.MediaColumns.MIME_TYPE, "audio/vnd.wave")
//            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
//        }
//        val resolver = context.contentResolver
//        val uri = resolver.insert(EXTERNAL_CONTENT_URI, contentValues)
//        if (uri != null) {
//            URL(url).openStream().use { input ->
//                resolver.openOutputStream(uri).use { output ->
//                    input.copyTo(output!!, DEFAULT_BUFFER_SIZE)
//                }
//            }
//        }
//    }

    override fun onStop() {
        super.onStop()
        recorder?.release()
        recorder = null
    }
}