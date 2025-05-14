package com.example.hackathon.tasks // Kendi paket adınız

import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hackathon.R
import com.example.hackathon.data.Task // Task veri sınıfınızın importu
import com.example.hackathon.databinding.ItemTaskBinding // ViewBinding sınıfınız
import java.text.SimpleDateFormat
import java.util.*

class TaskListAdapter(
    private val onTaskItemClicked: (Task) -> Unit,
    // Bu callback, Fragment tarafından görev durumu değişikliğini ve damla yönetimini ele almak için kullanılır
    private val onTaskCheckedChange: (task: Task, isChecked: Boolean) -> Unit,
    private val onDeleteClicked: (Task) -> Unit,
    private val onEditTaskClicked: ((Task) -> Unit)? = null // Opsiyonel düzenleme butonu için
) : ListAdapter<Task, TaskListAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val currentTask = getItem(position)
        holder.bind(currentTask)
    }

    inner class TaskViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition // Daha güvenli
                if (position != RecyclerView.NO_POSITION) {
                    onTaskItemClicked(getItem(position))
                }
            }

            binding.checkboxTaskCompleted.setOnCheckedChangeListener { _, isChecked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val task = getItem(position)
                    // Gerçek state yönetimi ve damla ödüllendirme Fragment'ta bu callback ile yapılır.
                    // Adapter, görevin 'isCompleted' durumunu doğrudan değiştirmemelidir.
                    // Bu değişiklik ViewModel üzerinden yapılmalı ve LiveData ile UI güncellenmelidir.
                    onTaskCheckedChange(task, isChecked)
                }
            }

            binding.buttonDeleteTask.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClicked(getItem(position))
                }
            }

            // Opsiyonel düzenleme butonu için (XML'de ID'si buttonEditTask ise)
            /*binding.buttonEditTask?.setOnClickListener { // buttonEditTask null-safe erişim
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEditTaskClicked?.invoke(getItem(position))
                }
            }*/
        }

        fun bind(task: Task) {
            binding.textViewTaskTitle.text = task.title
            // CheckBox'ın durumu, ViewHolder yeniden kullanıldığında onCheckedChangeListener'ı tetiklememesi için
            // listener null iken ayarlanabilir veya listener içinde eski ve yeni durum karşılaştırılabilir.
            // Şimdilik basit tutuyoruz:
            binding.checkboxTaskCompleted.isChecked = task.isCompleted
            updateTaskTitleAppearance(binding.textViewTaskTitle, task.isCompleted)

            if (task.startTime != null) {
                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                val startTimeFormatted = sdfTime.format(Date(task.startTime))
                val endTimeCalendar = Calendar.getInstance().apply {
                    timeInMillis = task.startTime!! // Null olamayacağı varsayımı
                    add(Calendar.MINUTE, task.durationMinutes)
                }
                val endTimeFormatted = sdfTime.format(endTimeCalendar.time)
                binding.textViewTaskTimeRange.text = "$startTimeFormatted\n$endTimeFormatted"
                binding.textViewTaskTimeRange.visibility = View.VISIBLE
            } else {
                binding.textViewTaskTimeRange.text = "${task.durationMinutes} dk"
                binding.textViewTaskTimeRange.visibility = View.VISIBLE // Süreyi her zaman göster
            }

            val remainingMinutes = task.durationMinutes - task.actualFocusedMinutes
            val focusedInfo = if (task.actualFocusedMinutes > 0) " (${task.actualFocusedMinutes} dk odaklanıldı)" else ""
            binding.textViewTaskDescription.text = "Kalan süre: $remainingMinutes dk / Toplam: ${task.durationMinutes} dk$focusedInfo"

            // Düzenleme butonunun görünürlüğü (eğer XML'de buttonEditTask varsa)
            //binding.buttonEditTask?.visibility = if (onEditTaskClicked != null) View.VISIBLE else View.GONE
        }

        private fun updateTaskTitleAppearance(textView: TextView, isCompleted: Boolean) {
            if (isCompleted) {
                textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.app_on_card_text_secondary)) // Renkleri R.color dosyanızdan alın
            } else {
                textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.app_on_card_text_primary)) // Renkleri R.color dosyanızdan alın
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            // Task bir data class ise, '==' tüm alanları karşılaştırır.
            // Eğer manuel karşılaştırma gerekiyorsa (örn: dropletsAwarded gibi transient olmayan alanlar):
            // return oldItem.title == newItem.title &&
            //        oldItem.isCompleted == newItem.isCompleted &&
            //        oldItem.durationMinutes == newItem.durationMinutes &&
            //        oldItem.actualFocusedMinutes == newItem.actualFocusedMinutes &&
            //        oldItem.date == newItem.date &&
            //        oldItem.startTime == newItem.startTime
            return oldItem == newItem
        }
    }
}