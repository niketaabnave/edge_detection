package com.sample.edgedetection.processor

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

const val TAG: String = "PaperProcessor"

fun processPicture(previewFrame: Mat): Corners? {
    val contours = findContours(previewFrame)
    return getCorners(contours, previewFrame.size())
}

fun cropPicture(picture: Mat, pts: List<Point>): Mat {

    pts.forEach { Log.i(TAG, "point: $it") }
    val tl = pts[0]
    val tr = pts[1]
    val br = pts[2]
    val bl = pts[3]

    val widthA = sqrt((br.x - bl.x).pow(2.0) + (br.y - bl.y).pow(2.0))
    val widthB = sqrt((tr.x - tl.x).pow(2.0) + (tr.y - tl.y).pow(2.0))

    val dw = max(widthA, widthB)
    val maxWidth = java.lang.Double.valueOf(dw).toInt()


    val heightA = sqrt((tr.x - br.x).pow(2.0) + (tr.y - br.y).pow(2.0))
    val heightB = sqrt((tl.x - bl.x).pow(2.0) + (tl.y - bl.y).pow(2.0))

    val dh = max(heightA, heightB)
    val maxHeight = java.lang.Double.valueOf(dh).toInt()

    val croppedPic = Mat(maxHeight, maxWidth, CvType.CV_8UC4)

    val srcMat = Mat(4, 1, CvType.CV_32FC2)
    val dstMat = Mat(4, 1, CvType.CV_32FC2)

    srcMat.put(0, 0, tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
    dstMat.put(0, 0, 0.0, 0.0, dw, 0.0, dw, dh, 0.0, dh)

    val m = Imgproc.getPerspectiveTransform(srcMat, dstMat)

    Imgproc.warpPerspective(picture, croppedPic, m, croppedPic.size())
    m.release()
    srcMat.release()
    dstMat.release()
    Log.i(TAG, "crop finish")
    return croppedPic
}

fun enhancePicture(src: Bitmap?): Bitmap {
    val srcMat = Mat()
    Utils.bitmapToMat(src, srcMat)
    Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
    Imgproc.adaptiveThreshold(
        srcMat,
        srcMat,
        255.0,
        Imgproc.ADAPTIVE_THRESH_MEAN_C,
        Imgproc.THRESH_BINARY,
        15,
        15.0
    )
    val result = Bitmap.createBitmap(src?.width ?: 1080, src?.height ?: 1920, Bitmap.Config.RGB_565)
    Utils.matToBitmap(srcMat, result, true)
    srcMat.release()
    return result
}

private fun findContours(src: Mat): List<MatOfPoint> {
    // Add padding to help detect contours touching image edges
    val padded = Mat()
    Core.copyMakeBorder(src, padded, 10, 10, 10, 10, Core.BORDER_CONSTANT, Scalar(255.0, 255.0, 255.0))

    // Prepare Mats
    val grayImage = Mat()
    val cannedImage = Mat()
    val dilatedImage = Mat()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)) // Smaller kernel

    // Convert to grayscale
    Imgproc.cvtColor(padded, grayImage, Imgproc.COLOR_BGR2GRAY)

    // Apply CLAHE for better local contrast
    val clahe = Imgproc.createCLAHE()
    clahe.clipLimit = 4.0
    clahe.apply(grayImage, grayImage)

    // Optional Gaussian blur
    Imgproc.GaussianBlur(grayImage, grayImage, Size(5.0, 5.0), 0.0)

    // Adaptive threshold (better for uneven lighting)
    Imgproc.adaptiveThreshold(
        grayImage,
        grayImage,
        255.0,
        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
        Imgproc.THRESH_BINARY,
        11,
        2.0
    )

    // Edge detection
    Imgproc.Canny(grayImage, cannedImage, 50.0, 150.0)

    // Dilation to strengthen edges
    Imgproc.dilate(cannedImage, dilatedImage, kernel)

    // Find contours
    val contours = ArrayList<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(
        dilatedImage,
        contours,
        hierarchy,
        Imgproc.RETR_EXTERNAL,  // Only outer contours
        Imgproc.CHAIN_APPROX_SIMPLE
    )

    // Filter contours based on area relative to image size
    val imageArea = padded.size().area()
    val minArea = 0.05 * imageArea  // Filter out very small contours

    val filteredContours = contours
        .filter { p -> Imgproc.contourArea(p) > minArea }
        .sortedByDescending { p -> Imgproc.contourArea(p) }
        .take(25)

    // Release memory
    grayImage.release()
    cannedImage.release()
    dilatedImage.release()
    kernel.release()
    hierarchy.release()
    padded.release()

    return filteredContours
}

private  fun matToBitmap(srcMat: Mat): Bitmap {
    val result = Bitmap.createBitmap(srcMat.cols(), srcMat.rows(), Bitmap.Config.RGB_565)
    try {
        Utils.matToBitmap(srcMat, result, true)
    }catch (e:Exception){
        Log.e("Exception", e.toString())
    }
    return result;
}
private fun getCorners(contours: List<MatOfPoint>, size: Size): Corners? {
    val indexTo: Int = when (contours.size) {
        in 0..5 -> contours.size - 1
        else -> 4
    }
    for (index in 0..contours.size) {
        if (index in 0..indexTo) {
            val c2f = MatOfPoint2f(*contours[index].toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.03 * peri, true)
            val points = approx.toArray().asList()
            val convex = MatOfPoint()
            approx.convertTo(convex, CvType.CV_32S)
            // select biggest 4 angles polygon
            if (points.size == 4 && Imgproc.isContourConvex(convex)) { 
                val foundPoints = sortPoints(points)
                return Corners(foundPoints, size)
            }
        } else {
            return null
        }
    }

    return null
}
private fun sortPoints(points: List<Point>): List<Point> {
    val p0 = points.minByOrNull { point -> point.x + point.y } ?: Point()
    val p1 = points.minByOrNull { point: Point -> point.y - point.x } ?: Point()
    val p2 = points.maxByOrNull { point: Point -> point.x + point.y } ?: Point()
    val p3 = points.maxByOrNull { point: Point -> point.y - point.x } ?: Point()
    return listOf(p0, p1, p2, p3)
}
