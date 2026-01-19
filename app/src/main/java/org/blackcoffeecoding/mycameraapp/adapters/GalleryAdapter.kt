package org.blackcoffeecoding.mycameraapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.blackcoffeecoding.mycameraapp.databinding.ItemGalleryBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// адаптер принимает список файлов и функцию-слушатель нажатий (что делать при клике)
class GalleryAdapter(
    private val fileList: List<File>,
    private val onFileClick: (File) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    // класс, хранящий ссылки на элементы одной ячейки
    inner class GalleryViewHolder(val binding: ItemGalleryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val binding = ItemGalleryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GalleryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        val file = fileList[position]

        // используем библиотеку Glide для загрузки картинки файла. Glide сам понимает, как сделать превью даже из видео
        Glide.with(holder.binding.root)
            .load(file)
            .into(holder.binding.ivThumbnail)

        // если расширение mp4, показываем иконку Play
        if (file.extension == "mp4") {
            holder.binding.ivPlayIcon.visibility = View.VISIBLE
        } else {
            holder.binding.ivPlayIcon.visibility = View.GONE
        }

        // вешаем дату и время на объекты в галерее
        val date = Date(file.lastModified())
        val format = SimpleDateFormat("dd.MM.yyyy\nHH:mm", Locale.getDefault())
        holder.binding.tvDate.text = format.format(date)

        // обработка нажатия на ячейку
        holder.itemView.setOnClickListener {
            onFileClick(file)
        }
    }

    override fun getItemCount(): Int = fileList.size
}