package com.sample.edgedetection.scan

import com.sample.edgedetection.SourceManager
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.exifinterface.media.ExifInterface
import com.sample.edgedetection.ERROR_CODE
import com.sample.edgedetection.EdgeDetectionHandler
import com.sample.edgedetection.R
import com.sample.edgedetection.REQUEST_CODE
import com.sample.edgedetection.SKIP_CODE
import com.sample.edgedetection.base.BaseActivity
import com.sample.edgedetection.view.PaperRectangle
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import java.io.*

class ScanActivity : BaseActivity(), IScanView.Proxy {

    private lateinit var mPresenter: ScanPresenter

    override fun provideContentViewId(): Int = R.layout.activity_scan

    override fun initPresenter() {
        val initialBundle = intent.getBundleExtra(EdgeDetectionHandler.INITIAL_BUNDLE) as Bundle
        mPresenter = ScanPresenter(this, this, initialBundle)
    }

    override fun prepare() {
        if (!OpenCVLoader.initDebug()) {
            Log.i(TAG, "loading opencv error, exit")
            finish()
        }
        else {
            Log.i("OpenCV", "OpenCV loaded Successfully!");
        }

        findViewById<View>(R.id.shut).setOnClickListener {
            if (mPresenter.canShut) {
                mPresenter.shut()
            }
        }

        // to hide the flashLight button from  SDK versions which we do not handle the permission for!
        findViewById<View>(R.id.flash).visibility = if
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU && baseContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))
            View.VISIBLE else
                View.GONE

        findViewById<View>(R.id.flash).setOnClickListener {
            mPresenter.toggleFlash()
        }

        val initialBundle = intent.getBundleExtra(EdgeDetectionHandler.INITIAL_BUNDLE) as Bundle

        if(!initialBundle.containsKey(EdgeDetectionHandler.FROM_GALLERY)){
            this.title = initialBundle.getString(EdgeDetectionHandler.SCAN_TITLE, "") as String
        }

        findViewById<View>(R.id.gallery).visibility =
                if (initialBundle.getBoolean(EdgeDetectionHandler.CAN_USE_GALLERY, true))
                    View.VISIBLE
                else View.GONE

        findViewById<View>(R.id.gallery).setOnClickListener {
            pickupFromGallery()
        }

        if (initialBundle.containsKey(EdgeDetectionHandler.FROM_GALLERY) && initialBundle.getBoolean(EdgeDetectionHandler.FROM_GALLERY,false))
        {
            pickFromGallary = true
            pickupFromGallery()
        }
    }

    private fun pickupFromGallery() {
        mPresenter.stop()
        val gallery = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply{type="image/*"}
        ActivityCompat.startActivityForResult(this, gallery, 1, null)
    }

    override fun onStart() {
        super.onStart()
        mPresenter.start()
    }

    override fun onStop() {
        super.onStop()
        mPresenter.stop()
    }

    override fun exit() {
        finish()
    }

    override fun getCurrentDisplay(): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.display
        } else {
            this.windowManager.defaultDisplay
        }
    }

    override fun getSurfaceView() = findViewById<SurfaceView>(R.id.surface)

    override fun getPaperRect() = findViewById<PaperRectangle>(R.id.paper_rect)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e(TAG, "ScanActivity: requestCode: $requestCode, resultCode: $resultCode, data: $data")
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                if (intent.hasExtra(EdgeDetectionHandler.FROM_GALLERY) && intent.getBooleanExtra(EdgeDetectionHandler.FROM_GALLERY, false))
                    finish()
            }
        }

        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                val uri: Uri = data!!.data!!
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    SourceManager.orgianlPic = uri
                    onImageSelected(uri)
                }
            }else if(resultCode == Activity.RESULT_CANCELED){
                mPresenter.start()
                if(pickFromGallary){
                    finish()
                }
            }else if(resultCode == SKIP_CODE){
                setResult(Activity.RESULT_OK)
                finish()
            }
            else {
                if (intent.hasExtra(EdgeDetectionHandler.FROM_GALLERY) && intent.getBooleanExtra(EdgeDetectionHandler.FROM_GALLERY,false))
                    finish()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun onImageSelected(imageUri: Uri) {
        try {


            Log.d(TAG, "onImageSelected: $imageUri")
            val iStream: InputStream = contentResolver.openInputStream(imageUri)!!
            Log.d(TAG, "InputStream opened successfully for URI: $imageUri")
            val exif = ExifInterface(iStream)
            Log.d(TAG, "ExifInterface created successfully for URI: $imageUri")
            var rotation = -1
            val orientation: Int = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
            )
            Log.d(TAG, "Image orientation: $orientation")
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotation = Core.ROTATE_90_CLOCKWISE
                ExifInterface.ORIENTATION_ROTATE_180 -> rotation = Core.ROTATE_180
                ExifInterface.ORIENTATION_ROTATE_270 -> rotation = Core.ROTATE_90_COUNTERCLOCKWISE
            }
            Log.d(TAG, "Image rotation: $rotation")
            val mimeType = contentResolver.getType(imageUri)
            var imageWidth: Double
            var imageHeight: Double
            Log.d(TAG, "Image MIME type: $mimeType")
            if (mimeType?.startsWith("image/png") == true) {
                Log.d(TAG, "Processing PNG image")
                val source = ImageDecoder.createSource(contentResolver, imageUri)
                val drawable = ImageDecoder.decodeDrawable(source)

                imageWidth = drawable.intrinsicWidth.toDouble()
                imageHeight = drawable.intrinsicHeight.toDouble()

                if (rotation == Core.ROTATE_90_CLOCKWISE || rotation == Core.ROTATE_90_COUNTERCLOCKWISE) {
                    Log.d(TAG, "Adjusting dimensions for rotation")
                    imageWidth = drawable.intrinsicHeight.toDouble()
                    imageHeight = drawable.intrinsicWidth.toDouble()
                }
            } else {
                Log.d(TAG, "Processing JPEG image")
                imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).toDouble()
                imageHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).toDouble()
                if (rotation == Core.ROTATE_90_CLOCKWISE || rotation == Core.ROTATE_90_COUNTERCLOCKWISE) {
                    imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).toDouble()
                    imageHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).toDouble()
                }
            }
            Log.d(TAG, "Image dimensions: $imageWidth x $imageHeight")
            val inputData: ByteArray? = getBytes(contentResolver.openInputStream(imageUri)!!)
            if (inputData == null || inputData.isEmpty()) {
                throw Exception("Image byte data is empty!")
            }
            val mat = Mat(1, inputData.size, CvType.CV_8U)
            mat.put(0, 0, inputData)
            val pic = Imgcodecs.imdecode(mat, Imgcodecs.IMREAD_UNCHANGED)
            Log.d(TAG, "Image decoded successfully, applying rotation if needed")

            val source = ImageDecoder.createSource(contentResolver, imageUri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
            val tempPic = Mat()
            Utils.bitmapToMat(bitmap, tempPic)  // Convert Bitmap to Mat (color preserved)

            if (rotation > -1) Core.rotate(pic, pic, rotation)

            // Now `tempPic` is the color-accurate image
            SourceManager.originalMat = tempPic


            mat.release()
            Log.d(TAG, "Image rotation applied, starting edge detection")

            mPresenter.detectEdge(pic)
            Log.d(TAG, "Edge detection started successfully")
        } catch (error: Exception) {
            Log.e(TAG, "Error processing image: ${error.message}", error)
            val intent = Intent()
            intent.putExtra("RESULT", error.toString())
            setResult(ERROR_CODE, intent)
            finish()
        }

    }

    @Throws(IOException::class)
    fun getBytes(inputStream: InputStream): ByteArray? {
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            byteBuffer.write(buffer, 0, len)
        }
        return byteBuffer.toByteArray()
    }

    companion object {
        var pickFromGallary: Boolean= false
    }

}
