package org.blackcoffeecoding.mycameraapp.activities

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import org.blackcoffeecoding.mycameraapp.adapters.GalleryAdapter
import org.blackcoffeecoding.mycameraapp.databinding.ActivityGalleryBinding
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // находим файлы в двух папках (Pictures и Movies)
        val files = getAllMediaFiles()
        // настраиваем RecyclerView (сетка в 3 колонки)
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        // подключаем адаптер
        binding.recyclerView.adapter = GalleryAdapter(files) { file ->
            // при клике открываем просмотр на весь экран
            val intent = Intent(this, MediaViewerActivity::class.java)
            intent.putExtra("filePath", file.absolutePath)
            startActivity(intent)
        }
        // закрываем галерею,т.к. под ней уже открыта камера
        binding.fabBackToCamera.setOnClickListener {
            finish()
        }
    }

    // вспомогательная функция для сбора всех файлов
    private fun getAllMediaFiles(): List<File> {
        val mediaList = mutableListOf<File>()
        // папка с фото
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appPicturesDir = File(picturesDir, "CameraX-Image")
        if (appPicturesDir.exists()) {
            mediaList.addAll(appPicturesDir.listFiles()?.filter { it.extension == "jpg" } ?: emptyList())
        }
        // папка с видео
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appMoviesDir = File(moviesDir, "CameraX-Video")
        if (appMoviesDir.exists()) {
            mediaList.addAll(appMoviesDir.listFiles()?.filter { it.extension == "mp4" } ?: emptyList())
        }
        // сортируем: новые сверху
        return mediaList.sortedByDescending { it.lastModified() }
    }

    // обновляем список, когда возвращаемся с экрана просмотра
    override fun onResume() {
        super.onResume()
        val files = getAllMediaFiles()
        binding.recyclerView.adapter = GalleryAdapter(files) { file ->
            val intent = Intent(this, MediaViewerActivity::class.java)
            intent.putExtra("filePath", file.absolutePath)
            startActivity(intent)
        }
    }
}