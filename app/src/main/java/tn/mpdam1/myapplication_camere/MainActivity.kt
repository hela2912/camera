package tn.mpdam1.myapplication_camere
import android.Manifest
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.*
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private var isCameraPreviewing = false
    private var camera: Camera? = null
    private var surfaceView: SurfaceView? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private var videoFile: File? = null
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001
    private val WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 1002
    private val cameraPermission = Manifest.permission.CAMERA
    private val writeExternalStoragePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        surfaceHolder = surfaceView?.holder
        surfaceHolder?.addCallback(this)

        requestPermissions()

        val btnStartRecord = findViewById<Button>(R.id.btnStartRecord)
        btnStartRecord.setOnClickListener {
            if (!isRecording) startRecording()
        }

        val btnStopRecord = findViewById<Button>(R.id.btnStopRecord)
        btnStopRecord.setOnClickListener {
            if (isRecording) stopRecording()
        }

        val btnCapture = findViewById<Button>(R.id.btnCapture)
        btnCapture.setOnClickListener {
            captureSnapshot()
        }

        val btnOpenGallery = findViewById<Button>(R.id.btnOpenGallery)
        btnOpenGallery.setOnClickListener {
            openGallery()
        }
    }
    private fun getSurfaceForMediaRecorder(): Surface {
        // Use the surface from the surfaceHolder
        return surfaceHolder?.surface ?: throw IllegalStateException("Surface is null")
    }

    private fun startRecording() {
        if (checkCameraHardware()) {
            releaseCamera()
            initializeCamera()

            // Check if the mediaRecorder is already recording, release it before starting a new recording
            if (isRecording) {
                stopRecording()
            }

            camera?.unlock()
            isRecording = true

            mediaRecorder = MediaRecorder()
            mediaRecorder?.setCamera(camera)

            // Set the video and audio source as well as the output format
            mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.CAMERA)
            mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            // Set the video and audio encoders
            mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            videoFile = getOutputMediaFile(".mp4") // Specify the extension as ".mp4" for video
            mediaRecorder?.setOutputFile(videoFile?.path)

            try {
                // Set the preview display before starting the recorder
                val surface = getSurfaceForMediaRecorder()
                mediaRecorder?.setPreviewDisplay(surface)
                mediaRecorder?.prepare()
                mediaRecorder?.start()
                Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("Recording", "Error starting recording: ${e.message}")
                Toast.makeText(this, "Error starting recording", Toast.LENGTH_SHORT).show()

                // Handle the error, release resources, and stop recording
                releaseMediaRecorder()
                isRecording = false
            }
        }
    }


    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Release the camera here
            releaseCamera()
        }
    }



    private fun startCameraPreview() {
        try {
            camera?.setPreviewDisplay(surfaceHolder)
            camera?.startPreview()
            isCameraPreviewing = true
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopCameraPreview() {
        camera?.stopPreview()
        isCameraPreviewing = false
    }

    private fun reinitializeAndStartCamera() {
        releaseCamera()
        initializeCamera()
        setCameraDisplayOrientation()
        startCameraPreview()
    }

    private fun stopRecording() {
        isRecording = false

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()

            // Notify the system to scan the file and add it to the media store
            MediaScannerConnection.scanFile(
                this,
                arrayOf(videoFile?.absolutePath),
                arrayOf("video/mp4"),
                null
            )

            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("Recording", "Error stopping recording: ${e.message}")
            Toast.makeText(this, "Error stopping recording", Toast.LENGTH_SHORT).show()
        } finally {
            releaseMediaRecorder()  // Release MediaRecorder here
            stopCameraPreview()     // Stop camera preview
            reinitializeAndStartCamera() // Reinitialize and start the camera
        }
    }


    private fun captureSnapshot() {
        camera?.takePicture(null, null, Camera.PictureCallback { data, camera ->
            val pictureFile = getOutputMediaFile(".jpg") // Specify the extension as ".jpg" for snapshots

            try {
                val fos = FileOutputStream(pictureFile)
                fos.write(data)
                fos.close()

                // Notify the system to scan the file and add it to the media store
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(pictureFile?.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )

                Log.d("CaptureSnapshot", "Snapshot saved: ${pictureFile?.absolutePath}")
                Toast.makeText(this, "Snapshot saved", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("CaptureSnapshot", "Failed to save snapshot: ${e.message}")
                Toast.makeText(this, "Failed to save snapshot", Toast.LENGTH_SHORT).show()
            } finally {
                // Restart the preview to continue capturing snapshots
                camera?.startPreview()
            }
        })
    }



    private fun checkCameraHardware(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    private fun initializeCamera() {
        releaseCamera()
        camera = Camera.open()
        setCameraDisplayOrientation()
    }



    private fun setCameraDisplayOrientation() {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info)
        val rotation = windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360  // compensate for mirror
        } else {
            result = (info.orientation - degrees + 360) % 360
        }

        camera?.setDisplayOrientation(result)
    }

    private fun getOutputMediaFile(extension: String): File? {
        val mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        return mediaStorageDir?.let {
            if (!it.exists() && !it.mkdirs()) {
                Toast.makeText(this, "Failed to create directory", Toast.LENGTH_SHORT).show()
                null
            } else {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                File("${it.path}${File.separator}VID_$timeStamp$extension")
            }
        } ?: run {
            Toast.makeText(this, "External storage not available", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }


    private fun requestPermissions() {
        requestCameraPermission()
        requestWriteExternalStoragePermission()
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, cameraPermission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(cameraPermission),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun requestWriteExternalStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, writeExternalStoragePermission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(writeExternalStoragePermission),
                WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.type = "image/* video/*"
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed with camera operations
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE -> {
                // Handle write external storage permission result if needed
            }
            // Handle other permission requests if needed
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseCamera()
    }

    private fun releaseCamera() {
        camera?.release()
        camera = null
    }

    override fun onStop() {
        super.onStop()
        releaseCamera()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            initializeCamera()
            surfaceHolder = holder  // Set surfaceHolder here
            camera?.setPreviewDisplay(holder)
            camera?.startPreview()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (surfaceHolder?.surface == null) {
            return
        }

        try {
            camera?.stopPreview()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            camera?.setPreviewDisplay(surfaceHolder)
            camera?.startPreview()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        camera?.stopPreview()
        camera?.release()
        camera = null
    }


}