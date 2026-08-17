package com.nur.sahayak

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nur.sahayak.utils.CropOverlayView
import java.io.File
import java.io.FileOutputStream

class CropImageActivity : AppCompatActivity(), View.OnTouchListener {

    companion object {
        var tempSourceBitmap: Bitmap? = null
        private const val MODE_NONE = 0
        private const val MODE_DRAG = 1
        private const val MODE_ZOOM = 2
    }

    private lateinit var ivTarget: ImageView
    private lateinit var btnDone: Button
    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var vCropOverlay: CropOverlayView

    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    private val startPoint = PointF()
    private val midPoint = PointF()
    private var oldDist = 1f

    private var mode = MODE_NONE
    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_image)

        // Transparent Status Bar
        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        ivTarget = findViewById(R.id.ivCropTarget)
        btnDone = findViewById(R.id.btnDoneCrop)
        btnBack = findViewById(R.id.btnBackCrop)
        tvTitle = findViewById(R.id.tvCropTitle)
        vCropOverlay = findViewById(R.id.vCropOverlay)

        btnBack.setOnClickListener { finish() }

        val imageUriStr = intent.getStringExtra("image_uri")
        val isCover = intent.getBooleanExtra("is_cover", false)

        if (isCover) {
            tvTitle.text = "কভার ছবি ক্রপ করুন (১৬:৯)"
            vCropOverlay.aspectRatio = 16f / 9f
        } else {
            tvTitle.text = "প্রোফাইল ছবি ক্রপ করুন (১:১)"
            vCropOverlay.aspectRatio = 1f
        }

        var sourceBitmap: Bitmap? = tempSourceBitmap

        if (sourceBitmap == null && imageUriStr != null) {
            try {
                val inputStream = contentResolver.openInputStream(Uri.parse(imageUriStr))
                sourceBitmap = BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                Toast.makeText(this, "ছবি লোড করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }

        if (sourceBitmap != null) {
            ivTarget.setImageBitmap(sourceBitmap)
            ivTarget.setOnTouchListener(this)

            scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                    ivTarget.imageMatrix = matrix
                    return true
                }
            })

            // Center and scale image initially after layout finishes
            vCropOverlay.post {
                val imgWidth = sourceBitmap.width.toFloat()
                val imgHeight = sourceBitmap.height.toFloat()
                val rectWidth = vCropOverlay.cropRect.width()
                val rectHeight = vCropOverlay.cropRect.height()

                val scale = Math.max(rectWidth / imgWidth, rectHeight / imgHeight)
                matrix.postScale(scale, scale)

                val dx = vCropOverlay.cropRect.centerX() - (imgWidth * scale) / 2
                val dy = vCropOverlay.cropRect.centerY() - (imgHeight * scale) / 2
                matrix.postTranslate(dx, dy)
                
                ivTarget.imageMatrix = matrix
            }

        } else {
            Toast.makeText(this, "ছবি পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        btnDone.setOnClickListener {
            try {
                val croppedBitmap = captureMaskedRegion()
                if (croppedBitmap == null) {
                    Toast.makeText(this, "ক্রপ করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                tempSourceBitmap = croppedBitmap

                val file = File(cacheDir, "cropped_temp.jpg")
                val fos = FileOutputStream(file)
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.flush()
                fos.close()

                val resultIntent = Intent()
                resultIntent.putExtra("cropped_uri", Uri.fromFile(file).toString())
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "ক্রপ করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event == null) return false
        if (::scaleDetector.isInitialized) {
            scaleDetector.onTouchEvent(event)
        }

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                startPoint.set(event.x, event.y)
                mode = MODE_DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(matrix)
                    midPoint(midPoint, event)
                    mode = MODE_ZOOM
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = MODE_NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == MODE_DRAG) {
                    matrix.set(savedMatrix)
                    matrix.postTranslate(event.x - startPoint.x, event.y - startPoint.y)
                } else if (mode == MODE_ZOOM) {
                    val newDist = spacing(event)
                    if (newDist > 10f) {
                        matrix.set(savedMatrix)
                        val scale = newDist / oldDist
                        matrix.postScale(scale, scale, midPoint.x, midPoint.y)
                    }
                }
            }
        }
        ivTarget.imageMatrix = matrix
        return true
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }

    private fun captureMaskedRegion(): Bitmap? {
        val viewWidth = ivTarget.width
        val viewHeight = ivTarget.height
        if (viewWidth <= 0 || viewHeight <= 0) return null

        val viewBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(viewBitmap)
        ivTarget.draw(canvas)

        val cropRect = vCropOverlay.cropRect
        var startX = cropRect.left.toInt()
        var startY = cropRect.top.toInt()
        var cropW = cropRect.width().toInt()
        var cropH = cropRect.height().toInt()

        startX = maxOf(0, startX)
        startY = maxOf(0, startY)
        if (startX + cropW > viewBitmap.width) cropW = viewBitmap.width - startX
        if (startY + cropH > viewBitmap.height) cropH = viewBitmap.height - startY

        if (cropW <= 0 || cropH <= 0) return null

        return Bitmap.createBitmap(viewBitmap, startX, startY, cropW, cropH)
    }
}
