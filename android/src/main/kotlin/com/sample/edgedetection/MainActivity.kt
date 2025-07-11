package com.sample.edgedetection


import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sample.edgedetection.MainActivity.EdgeDetectionHandler.Companion.CROP_BLACK_WHITE_TITLE
import com.sample.edgedetection.MainActivity.EdgeDetectionHandler.Companion.CROP_RESET_TITLE
import com.sample.edgedetection.MainActivity.EdgeDetectionHandler.Companion.CROP_TITLE
import com.sample.edgedetection.MainActivity.EdgeDetectionHandler.Companion.FROM_GALLERY
import com.sample.edgedetection.MainActivity.EdgeDetectionHandler.Companion.INITIAL_BUNDLE
import com.sample.edgedetection.MainActivity.EdgeDetectionHandler.Companion.SAVE_TO
import com.sample.edgedetection.scan.ScanActivity
import java.io.File
import java.nio.file.Files

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE = 1
    private val ERROR_CODE = 2
    private var isGallary = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonCamera: Button = findViewById(R.id.buttonCamera)
        val buttonGallery: Button = findViewById(R.id.buttonGallery)

        buttonCamera.setOnClickListener {
            isGallary = false
            if (allPermissionsGranted()) {
                openCameraORGalleryActivity()
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }

        buttonGallery.setOnClickListener {
            isGallary = true
            if (allPermissionsGranted()) {
                openCameraORGalleryActivity()
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }


    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun openCameraORGalleryActivity() {

        val tempDir = Files.createTempDirectory("").toFile()

        val imagePath = File(tempDir, "document_${System.currentTimeMillis()}.jpg").absolutePath

        val initialIntent = Intent(applicationContext, ScanActivity::class.java)

        val bundle = Bundle()
        bundle.putString(SAVE_TO, imagePath)
        bundle.putString(CROP_TITLE, "Crop")
        bundle.putString(CROP_BLACK_WHITE_TITLE, "Black White")
        bundle.putString(CROP_RESET_TITLE, "Reset")
        bundle.putBoolean(FROM_GALLERY, isGallary)

        initialIntent.putExtra(INITIAL_BUNDLE, bundle)

        startActivityForResult(initialIntent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e("EdgeDetectionPlugin", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode, data=$data")
        if (requestCode == REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                   // finishWithSuccess(true)
                }
                Activity.RESULT_CANCELED -> {
                   // finishWithSuccess(false)
                }
                ERROR_CODE -> {
                   // finishWithError(ERROR_CODE.toString(), data?.getStringExtra("RESULT") ?: "ERROR")
                }
            }
         //   return true
        }
       // return false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                openCameraORGalleryActivity()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES)
                } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else {
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
                }
    }
    class EdgeDetectionHandler {

        companion object {
            const val INITIAL_BUNDLE = "initial_bundle"
            const val FROM_GALLERY = "from_gallery"
            const val SAVE_TO = "save_to"
            const val CAN_USE_GALLERY = "can_use_gallery"
            const val SCAN_TITLE = "scan_title"
            const val CROP_TITLE = "crop_title"
            const val CROP_BLACK_WHITE_TITLE = "crop_black_white_title"
            const val CROP_RESET_TITLE = "crop_reset_title"
        }
    }
}
