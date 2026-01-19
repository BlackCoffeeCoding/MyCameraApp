package org.blackcoffeecoding.mycameraapp.activities

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.blackcoffeecoding.mycameraapp.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    // ViewBinding для доступа к элементам UI
    private lateinit var binding: ActivityMainBinding
    // Executor для операций камеры (выполнение в отдельном потоке)
    private lateinit var cameraExecutor: ExecutorService
    // UseCase для захвата фото
    private var imageCapture: ImageCapture? = null
    // объект Camera для управления зумом и фокусом
    private var camera: Camera? = null
    // для видео
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null // текущая сессия записи
    // переменная для выбора камеры (Front/Back)
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private var isVideoMode = false // флаг текущего режима

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // включаем режим Edge-to-Edge
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // обработка отступов для системных панелей (чтобы кнопки не перекрывались статус-баром)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // проверяем разрешения
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // навешиваем слушатели на кнопки
        binding.imageCaptureButton.setOnClickListener {
            if (isVideoMode) {
                captureVideo()
            } else {
                takePhoto()
            }
        }
        binding.btnSwitchCamera.setOnClickListener { switchCamera() }
        binding.tvPhotoMode.setOnClickListener { switchMode(false) }
        binding.tvVideoMode.setOnClickListener { switchMode(true) }
        binding.btnGallery.setOnClickListener {
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // используем переменную класса, а не локальную константу
            // val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                if (isVideoMode) {
                    // настройка для ВИДЕО
                    // создаем Recorder с качеством по умолчанию
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build()
                    videoCapture = VideoCapture.withOutput(recorder)

                    // привязываем videoCapture
                    camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, videoCapture // cameraSelector
                    )
                } else {
                    // настройка для ФОТО
                    imageCapture = ImageCapture.Builder().build()

                    // привязываем imageCapture
                    camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture // cameraSelector
                    )
                }

                setupZoomAndTapToFocus()

            } catch (exc: Exception) {
                Log.e("CameraX", "Binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // проверяем,что use case инициализирован
        val imageCapture = imageCapture ?: return

        // cоздаем уникальное имя файла на основе даты и времени)
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        // настраиваем метаданные для сохранения через MediaStore
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            // для Android 10+ указываем папку Pictures/CameraX-Image
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // куда сохраняем
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        // делаем снимок
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this), // запускаем колбэк на главном потоке
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Фото сохранено: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d("CameraX", msg)

                    // эффект моргания экрана
                    animateFlash()
                }
            }
        )
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        // если запись уже идет — останавливаем её
        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }

        // создаем имя файла
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        // начинаем запись
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                // включаем запись звука, если есть разрешение
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                // лямбда для обработки событий записи (старт, пауза, прогресс, финиш)
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        // запись пошла: меняем кнопку на "Стоп"
                        binding.imageCaptureButton.text = "STOP" // Или иконка
                        binding.imageCaptureButton.isEnabled = true
                        binding.tvTimer.visibility = View.VISIBLE
                    }
                    is VideoRecordEvent.Status -> {
                        // обновляем таймер
                        val stats = recordEvent.recordingStats
                        val time = TimeUnit.NANOSECONDS.toSeconds(stats.recordedDurationNanos)
                        binding.tvTimer.text = String.format("%02d:%02d", time / 60, time % 60)
                    }
                    is VideoRecordEvent.Finalize -> {
                        // запись завершена
                        if (!recordEvent.hasError()) {
                            val msg = "Видео сохранено: ${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            recording?.close()
                            recording = null
                            Log.e("CameraX", "Video capture ends with error: ${recordEvent.error}")
                        }
                        // сбрасываем UI
                        binding.imageCaptureButton.text = ""
                        binding.tvTimer.visibility = View.GONE
                        binding.imageCaptureButton.isEnabled = true
                    }
                }
            }
    }

    // анимация моргания экрана для визуального подтверждения снимка
    private fun animateFlash() {
        binding.root.postDelayed({
            binding.root.foreground = ColorDrawable(Color.WHITE)
            binding.root.postDelayed({
                binding.root.foreground = null
            }, 50)
        }, 100)
    }

    private fun setupZoomAndTapToFocus() {
        val viewFinder = binding.viewFinder

        // cлушатель касаний для фокуса
        viewFinder.setOnTouchListener { view, event ->
            // если это не одиночное касание (например жест зума), игнорируем. для простоты привяжемся к ACTION_UP
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                // создаем действие фокусировки (автофокус + экспозиция)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS) // сброс фокуса через n сек
                    .build()

                camera?.cameraControl?.startFocusAndMetering(action)
                view.performClick() // для доступности
            }
            // возвращаем true, но даем работать детектору жестов
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

    // детектор жеста "щипок" (pinch-to-zoom)
    private val scaleGestureDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val zoomState = camera?.cameraInfo?.zoomState?.value ?: return false
                val currentZoomRatio = zoomState.zoomRatio
                val newZoom = currentZoomRatio * detector.scaleFactor
                // ограничиваем зум допустимыми пределами камеры
                val clampedZoom = newZoom.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                // применяем зум к камере
                camera?.cameraControl?.setZoomRatio(clampedZoom)
                return true
            }
        })
    }

    private fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera() // перезапускаем камеру с новым селектором
    }

    private fun switchMode(isVideo: Boolean) {
        if (isVideoMode == isVideo) return // Режим уже выбран

        isVideoMode = isVideo
        startCamera() // Перезапуск камеры с новыми настройками

        // Обновляем UI (текст и цвет кнопки)
        if (isVideo) {
            binding.tvVideoMode.setTextColor(Color.WHITE)
            binding.tvPhotoMode.setTextColor(Color.parseColor("#80FFFFFF"))
            binding.imageCaptureButton.background.setTint(Color.RED) // Красная кнопка
        } else {
            binding.tvPhotoMode.setTextColor(Color.WHITE)
            binding.tvVideoMode.setTextColor(Color.parseColor("#80FFFFFF"))
            binding.imageCaptureButton.background.setTint(Color.WHITE) // Белая кнопка
            binding.tvTimer.visibility = View.GONE
        }
    }

    // в следующем блоке работа с разрешениями
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext, "Разрешение отклонено", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                // Для Android 9 и ниже нужно разрешение на запись
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                // Для Android 10+ WRITE_EXTERNAL_STORAGE не нужен для сохранения в галерею
            }.toTypedArray()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}