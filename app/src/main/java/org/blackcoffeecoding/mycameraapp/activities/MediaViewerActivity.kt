package org.blackcoffeecoding.mycameraapp.activities

import android.os.Bundle
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import org.blackcoffeecoding.mycameraapp.databinding.ActivityMediaViewerBinding
import java.io.File

class MediaViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // получаем путь к файлу, который передали из галереи
        val path = intent.getStringExtra("filePath") ?: return finish()
        val file = File(path)

        if (file.extension == "mp4") {
            // ВИДЕО
            binding.imageView.visibility = View.GONE
            binding.videoView.visibility = View.VISIBLE

            binding.videoView.setVideoPath(path)
            // добавляем кнопки управления (пауза, стоп)
            val mediaController = MediaController(this)
            binding.videoView.setMediaController(mediaController)
            binding.videoView.start()
        } else {
            // ФОТО
            binding.videoView.visibility = View.GONE
            binding.imageView.visibility = View.VISIBLE

            Glide.with(this).load(file).into(binding.imageView)
        }

        // кнопка удаления
        binding.btnDelete.setOnClickListener {
            if (file.exists()) {
                if (file.delete()) {
                    Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
                    finish() // Закрываем экран, возвращаемся в галерею
                } else {
                    Toast.makeText(this, "Ошибка удаления", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // кнопка назад
        binding.btnBack.setOnClickListener { finish() }
    }
}