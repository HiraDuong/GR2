package com.example.gr2

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.icu.util.Output
import android.media.ImageReader
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.FileProvider
import com.example.gr2.ml.AutoModel1

import com.example.gr2.ml.BestFloat16
import com.example.gr2.ml.BestFloat32
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays

import java.util.Date


class MainActivity : AppCompatActivity() {
    lateinit var capReq: CaptureRequest.Builder
    lateinit var handler: Handler
    lateinit var handlerThread: HandlerThread
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var cameraDevice: CameraDevice
    lateinit var captureRequest: CaptureRequest
    lateinit var imageReader: ImageReader
    lateinit var imageView: ImageView
    private var mediaPlayer: MediaPlayer? = null
    lateinit var bitmap: android.graphics.Bitmap
    lateinit var model: AutoModel1
    lateinit var labels:List<String>
    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mediaPlayer = MediaPlayer.create(this, R.raw.main)
        val mediaPlayerleft = MediaPlayer.create(this, R.raw.left)
        val mediaPlayerright = MediaPlayer.create(this, R.raw.right)
        val mediaPlayerup = MediaPlayer.create(this, R.raw.up)
        val mediaPlayerdown = MediaPlayer.create(this, R.raw.down)
        val mediaOutSide = MediaPlayer.create(this, R.raw.outside)
        val mediaPlayerInside= MediaPlayer.create(this, R.raw.inside)
        mediaPlayer?.start()
        get_permissons()
        val intent = Intent(this, DetectActivity::class.java)
        labels = FileUtil.loadLabels(this, "labels.txt")

        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        textureView = findViewById(R.id.textureView)
        imageView = findViewById(R.id.imageView)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler((handlerThread).looper)



        val model = AutoModel1.newInstance(this)

        textureView.surfaceTextureListener  = object: TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                bitmap = textureView.bitmap!!

                // Creates inputs for reference.
                var detectImage = TensorImage.fromBitmap(bitmap)
                detectImage  = imageProcessor.process(detectImage)
                // Runs model inference and gets result.
                val outputs = model.process(detectImage)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width
                paint.textSize = h/15f
                paint.strokeWidth = h/85f
                var x = 0
                 var pos = "None"
                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if(fl > 0.5 && labels.get(classes.get(index).toInt()) == "book"){
                        paint.setColor(colors.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(locations.get(x+1)*w, locations.get(x)*h, locations.get(x+3)*w, locations.get(x+2)*h), paint)
                        val left = locations.get(x+1)*w
                        val top = locations.get(x)*h
                        val right = locations.get(x+3)*w
                        val bot = locations.get(x+2)*h

                        Log.d("TAG", "Location: left=$left, top=$top, right=$right, bot=$bot")
                        if (left > 0.01*w && top > 0.01*h && right < 0.95*w && bot < 0.95*h) {
                            pos = "inside"

                            capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                            capReq.addTarget(imageReader.surface)
                            cameraCaptureSession.capture(capReq.build(),null,null)

                            model.close()

                            mediaOutSide?.pause()
                            mediaPlayerInside?.start()
                            mediaPlayer?.release()

                        }
                        else if (left <0.01*w){
                            pos = "left"
                            mediaPlayerleft?.start()
                        }
                        else if (right > 0.95*w){
                            pos = "right"

                            mediaPlayerright?.start()
                        }
                        else if (top < 0.01*h){
                            pos = "top"

                            mediaPlayerup?.start()
                        }
                        else if (bot > 0.95*h){
                            pos = "bot"

                            mediaPlayerdown?.start()
                        }
                        else {
                            pos = "outside"
                            mediaPlayerup?.pause()
                            mediaOutSide?.start()
                        }
                        paint.style = Paint.Style.FILL
                        canvas.drawText(pos+" "+fl.toString(), locations.get(x+1)*w, locations.get(x)*h, paint)

                    }
                }

                imageView.setImageBitmap(mutable)
            }

        }

        imageReader =  ImageReader.newInstance(1080,1920,ImageFormat.JPEG,1)

        imageReader.setOnImageAvailableListener(object:ImageReader.OnImageAvailableListener{
            override fun onImageAvailable(reader: ImageReader?) {
                // lấy ảnh trên cùng
                var image  = reader?.acquireLatestImage()
                var buffer =  image!!.planes[0].buffer
                var bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                val timeStamp: String = android.icu.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                var file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "IMG_$timeStamp.jpeg")

                var opStream =  FileOutputStream(file)
                opStream.write(bytes)
                opStream.close()
                image.close()
                intent.putExtra("IMAGE_PATH", file.absolutePath)

                // Mở hoạt động mới với Intent
                startActivity(intent)
                // thông báo
                Toast.makeText(this@MainActivity,"image captured",Toast.LENGTH_SHORT).show()
            }

        },handler)

        findViewById<Button>(R.id.capture).apply {
            setOnClickListener{
                // gửi request tới camera, yêu cầu chụp ảnh
                capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                capReq.addTarget(imageReader.surface)
                cameraCaptureSession.capture(capReq.build(),null,null)
            }
        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onDestroy() {
        super.onDestroy()
      model.close()

        cameraDevice.close()
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
        mediaPlayer?.release()

    }
    @SuppressLint("MissingPermission")
    fun  openCamera(){
        cameraManager.openCamera(cameraManager.cameraIdList[0],object: CameraDevice.StateCallback(){
            override fun onOpened(camera: CameraDevice) {
                cameraDevice  = camera

                capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                var surface  = Surface(textureView.surfaceTexture)
                capReq.addTarget((surface))

                cameraDevice.createCaptureSession(listOf(surface,imageReader.surface),object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        cameraCaptureSession.setRepeatingRequest(capReq.build(),null,null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {

                    }

                },handler)
            }

            override fun onDisconnected(camera: CameraDevice) {

            }

            override fun onError(camera: CameraDevice, error: Int) {

            }
        },handler)
    }

    fun get_permissons() {
        var permissionsLst  =  mutableListOf<String>()


        //   Kiểm  tra quyền truy cập
        if(checkSelfPermission(android.Manifest.permission.CAMERA)  != PackageManager.PERMISSION_GRANTED)
            permissionsLst.add(android.Manifest.permission.CAMERA)
        if(checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)  != PackageManager.PERMISSION_GRANTED)
            permissionsLst.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        if(checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)  != PackageManager.PERMISSION_GRANTED)
            permissionsLst.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if(permissionsLst.size  >0){
            requestPermissions(permissionsLst.toTypedArray(),101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        grantResults.forEach {
            if(it != PackageManager.PERMISSION_GRANTED){
                get_permissons()
            }
        }
    }
}
