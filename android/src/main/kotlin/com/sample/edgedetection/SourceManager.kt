package com.sample.edgedetection


import com.sample.edgedetection.processor.Corners
import org.opencv.core.Mat
import android.net.Uri

class SourceManager {
    companion object {
        var pic: Mat? = null
        var corners: Corners? = null
        var orgianlPic: Uri? = null
    }
}