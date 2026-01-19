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
import org.blackcoffeecoding.mycameraapp.R
import org.blackcoffeecoding.mycameraapp.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// экспериментальные опции
import androidx.annotation.OptIn
import androidx.camera.video.ExperimentalPersistentRecording

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

    // храним рекордер отдельно, чтобы не пересоздавать
    private var recorder: Recorder? = null

    private var isVideoMode = false // флаг текущего режима

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // включаем режим Edge-to-Edge
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.imageCaptureButton.setBackgroundResource(R.drawable.btn_shutter_photo)

        // обработка отступов для системных панелей (чтобы кнопки не перекрывались статус-баром)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // проверяем разрешение именно на камеру. Аудио нам пока не важно.
        if (ContextCompat.checkSelfPermission(baseContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            // если камеры нет — запрашиваем (список запроса остается полным, чтобы спросить и аудио если его нет)
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
        // если разрешения на камеру нет, мы просто не запускаем метод.
        if (ContextCompat.checkSelfPermission(baseContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Нет доступа к камере", Toast.LENGTH_SHORT).show()
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            try {
                // важно: unbindAll ставит Persistent-запись на ПАУЗУ, но не закрывает файл
                cameraProvider.unbindAll()

                if (isVideoMode) {
                    // настройка для ВИДЕО
                    // если рекордер еще не создан — создаем
                    if (recorder == null) {
                        recorder = Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                            .build()
                    }

                    // Используем существующий recorder
                    videoCapture = VideoCapture.withOutput(recorder!!)

                    // привязываем videoCapture
                    camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, videoCapture
                    )
                } else {
                    // настройка для ФОТО
                    imageCapture = ImageCapture.Builder().build()

                    // привязываем imageCapture
                    camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture
                    )
                }

                setupZoomAndTapToFocus()

                // если у нас идет запись, мы её возобновляем после переключения
                if (recording != null) {
                    recording?.resume()
                }

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
                    val msg = "Фото успешно сохранено в Галерею"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d("CameraX", msg)

                    // эффект моргания экрана
                    animateFlash()
                }
            }
        )
    }

    @OptIn(ExperimentalPersistentRecording::class)
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

        // начинаем запись (с поддержкой бесшовного переключения)
        val pendingRecording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .asPersistentRecording()

        // включаем запись звука, если есть разрешение
        if (PermissionChecker.checkSelfPermission(this@MainActivity,
                Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED) {
            pendingRecording.withAudioEnabled()
        }

        // запускаем
        recording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { recordEvent ->
            // лямбда для обработки событий записи (старт, пауза, прогресс, финиш)
            when(recordEvent) {
                is VideoRecordEvent.Start -> {
                    // запись пошла: меняем кнопку на "Стоп" (Квадратик)
                    binding.imageCaptureButton.setBackgroundResource(R.drawable.btn_shutter_video_recording)
                    binding.imageCaptureButton.isEnabled = true
                    binding.tvTimer.visibility = View.VISIBLE
                    binding.btnGallery.visibility = View.INVISIBLE
                    binding.tvPhotoMode.visibility = View.INVISIBLE
                    binding.tvVideoMode.visibility = View.INVISIBLE
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
                        val msg = "Видео успешно сохранено в Галерею"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    } else {
                        recording?.close()
                        recording = null
                        Log.e("CameraX", "Video capture ends with error: ${recordEvent.error}")
                    }

                    // сбрасываем UI: возвращаем кнопку записи (Круг с точкой)
                    binding.imageCaptureButton.setBackgroundResource(R.drawable.btn_shutter_video_idle)
                    binding.tvTimer.visibility = View.GONE
                    binding.imageCaptureButton.isEnabled = true
                    binding.btnGallery.visibility = View.VISIBLE
                    binding.tvPhotoMode.visibility = View.VISIBLE
                    binding.tvVideoMode.visibility = View.VISIBLE
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

    // НОВАЯ ФУНКЦИЯ: Показывает и анимирует кольцо фокуса
    private fun showFocusRing(x: Float, y: Float) {
        val ring = binding.ivFocusRing // Убедись, что добавил ImageView с id ivFocusRing в XML!

        // 1. Ставим кольцо в точку касания (центруем его)
        ring.x = x - (ring.width / 2)
        ring.y = y - (ring.height / 2)

        // 2. Делаем видимым и сбрасываем прозрачность
        ring.visibility = View.VISIBLE
        ring.alpha = 1f
        ring.scaleX = 1.2f
        ring.scaleY = 1.2f

        // 3. Запускаем анимацию: уменьшение + исчезновение
        ring.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(0f)
            .setDuration(1000) // Длится 1 секунду
            .withEndAction {
                ring.visibility = View.GONE
            }
            .start()
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

                // ЗАПУСКАЕМ АНИМАЦИЮ КОЛЬЦА ФОКУСА
                showFocusRing(event.x, event.y)

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

        // текст и цвет кнопки
        if (isVideo) {
            binding.tvVideoMode.setTextColor(Color.WHITE)
            binding.tvPhotoMode.setTextColor(Color.parseColor("#80FFFFFF"))

            // ставим красную кнопку (Idle)
            binding.imageCaptureButton.setBackgroundResource(R.drawable.btn_shutter_video_idle)
            binding.imageCaptureButton.text = ""
        } else {
            // включаем стиль Фото
            binding.tvPhotoMode.setTextColor(Color.WHITE)
            binding.tvVideoMode.setTextColor(Color.parseColor("#80FFFFFF"))

            // ставим белое кольцо
            binding.imageCaptureButton.setBackgroundResource(R.drawable.btn_shutter_photo)
            binding.tvTimer.visibility = View.GONE
            binding.imageCaptureButton.text = ""
        }
    }

    // в следующем блоке работа с разрешениями
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // нам важно только разрешение на КАМЕРУ для старта превью
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

            if (cameraGranted) {
                // если камеру разрешили — запускаем, даже если аудио запрещено
                startCamera()
            } else {
                Toast.makeText(baseContext, "Без доступа к камере приложение не может работать", Toast.LENGTH_SHORT).show()
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
                // для Android 9 и ниже нужно разрешение на запись
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                // для Android 10+ WRITE_EXTERNAL_STORAGE не нужен для сохранения в галерею
            }.toTypedArray()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}