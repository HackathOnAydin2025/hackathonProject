package com.example.hackathon.tasks // Kendi paket adınızı kullanın

import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController // Navigasyon için
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hackathon.R
import com.example.hackathon.data.Task
import com.example.hackathon.databinding.ItemTaskBinding
import com.example.hackathon.TaskListFragmentDirections // Oluşturulan Directions sınıfı
import java.text.SimpleDateFormat
import java.util.*

class TaskListAdapter(
    // onStartPomodoroClicked artık doğrudan navigasyon yapacak, TaskViewModel'i Fragment'tan alacak.
    // Bu yüzden lambda imzası sadece Task alabilir veya Fragment'ın kendisini alabilir.
    // Şimdilik, tıklanan görevi döndüren bir lambda bırakıyoruz, navigasyonu Fragment halledecek.
    private val onTaskItemClicked: (Task) -> Unit,
    private val onTaskCheckedChange: (Task, Boolean) -> Unit,
    private val onDeleteClicked: (Task) -> Unit,
    private val onEditTaskClicked: ((Task) -> Unit)?
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
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onTaskItemClicked(getItem(position)) // Fragment bu tıklamayı alıp ViewModel'i güncelleyecek ve navigasyon yapacak
                }
            }

            binding.checkboxTaskCompleted.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // CheckBox durumu değiştiğinde, görevin isCompleted durumunu güncelleyip callback'i çağır.
                    // ViewModel'i doğrudan burada güncellemek yerine, değişikliği Fragment'a bildiriyoruz.
                    val task = getItem(position)
                    // task.isCompleted = isChecked // Bu satır burada olmamalı, state yönetimi ViewModel'de
                    onTaskCheckedChange(task, isChecked)
                }
            }

            binding.buttonDeleteTask.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClicked(getItem(position))
                }
            }

            // Edit butonu için (eğer varsa)
            // binding.buttonEditTask.setOnClickListener { ... }
        }

        fun bind(task: Task) {
            binding.textViewTaskTitle.text = task.title
            binding.checkboxTaskCompleted.isChecked = task.isCompleted // CheckBox'ı görevin durumuna göre ayarla
            updateTaskTitleAppearance(binding.textViewTaskTitle, task.isCompleted)

            if (task.startTime != null) {
                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                val startTimeFormatted = sdfTime.format(Date(task.startTime))
                val endTimeCalendar = Calendar.getInstance().apply {
                    timeInMillis = task.startTime
                    add(Calendar.MINUTE, task.durationMinutes)
                }
                val endTimeFormatted = sdfTime.format(endTimeCalendar.time)
                binding.textViewTaskTimeRange.text = "$startTimeFormatted\n$endTimeFormatted"
                binding.textViewTaskTimeRange.visibility = View.VISIBLE
            } else {
                binding.textViewTaskTimeRange.text = "${task.durationMinutes} dk"
                // binding.textViewTaskTimeRange.visibility = View.GONE // Veya süreyi göster
            }
            // Kalan süreyi göstermek için (opsiyonel)
            val remainingMinutes = task.durationMinutes - task.actualFocusedMinutes
            val focusedInfo = if (task.actualFocusedMinutes > 0) " (${task.actualFocusedMinutes} dk odaklanıldı)" else ""
            binding.textViewTaskDescription.text = "Kalan süre: $remainingMinutes dk / Toplam: ${task.durationMinutes} dk$focusedInfo"

            // Eğer düzenleme butonu varsa
            /*binding.buttonEditTask?.setOnClickListener { // XML'de buttonEditTask ID'li bir view olmalı
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEditTaskClicked?.invoke(getItem(position))
                }
            }*/
        }

        private fun updateTaskTitleAppearance(textView: TextView, isCompleted: Boolean) {
            if (isCompleted) {
                textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.app_on_card_text_secondary))
            } else {
                textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.app_on_card_text_primary))
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            // isCompleted, title, durationMinutes, actualFocusedMinutes, startTime karşılaştırılmalı
            return oldItem == newItem
        }
    }
}