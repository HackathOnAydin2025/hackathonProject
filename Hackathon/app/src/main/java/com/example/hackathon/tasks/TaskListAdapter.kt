package com.example.hackathon.tasks // Kendi paket adınızı kullanın

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hackathon.R
import com.example.hackathon.data.Task
import com.example.hackathon.databinding.ItemTaskBinding // Eğer item_task.xml dosyanızın adı buysa. Değilse, doğru binding sınıfını import edin.
import java.text.SimpleDateFormat
import java.util.*

class TaskListAdapter(
    private val onStartPomodoroClicked: (Task) -> Unit,
    private val onTaskCheckedChange: (Task, Boolean) -> Unit,
    private val onDeleteClicked: (Task) -> Unit, // ItemTouchHelper için olmasa da, item içindeki buton için
    private val onEditTaskClicked: ((Task) -> Unit)? // Opsiyonel düzenleme
) : ListAdapter<Task, TaskListAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        // XML dosyanızın adı "item_task.xml" ise ItemTaskBinding doğrudur.
        // Eğer "item_task_xml_final_design.xml" ise, ItemTaskXmlFinalDesignBinding olmalıdır.
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val currentTask = getItem(position)
        holder.bind(currentTask)
    }

    inner class TaskViewHolder(private val binding: ItemTaskBinding) : // Binding sınıf adını kontrol edin
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Tüm karta tıklandığında (Pomodoro başlat veya detayları göster gibi)
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onStartPomodoroClicked(getItem(position)) // Veya onTaskClicked gibi genel bir isim
                }
            }

            binding.checkboxTaskCompleted.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onTaskCheckedChange(getItem(position), isChecked)
                }
            }

            // item_task.xml'de button_delete_task ID'li bir ImageButton varsa bu çalışır.
            // Önceki item_task.xml'de bu buton 'gone' durumundaydı.
            // Eğer görünür yaparsanız veya her zaman listener eklemek isterseniz:
            binding.buttonDeleteTask.setOnClickListener { // XML'de bu ID'li bir view olmalı
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClicked(getItem(position))
                }
            }

            // Opsiyonel: Eğer item_task.xml'de bir "düzenle" butonu/ikonu varsa
            // ve onEditTaskClicked null değilse:
            // Örneğin, binding.buttonEditTask.setOnClickListener { ... onEditTaskClicked?.invoke(...) ... }
        }

        fun bind(task: Task) {
            binding.textViewTaskTitle.text = task.title
            binding.checkboxTaskCompleted.isChecked = task.isCompleted
            updateTaskTitleAppearance(binding.textViewTaskTitle, task.isCompleted)

            // Görev Saati (item_task.xml'deki textView_task_time_range)
            if (task.startTime != null) {
                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                val startTimeFormatted = sdfTime.format(Date(task.startTime))

                // Bitiş saatini hesapla (opsiyonel, tasarıma göre)
                val endTimeCalendar = Calendar.getInstance().apply {
                    timeInMillis = task.startTime
                    add(Calendar.MINUTE, task.durationMinutes)
                }
                val endTimeFormatted = sdfTime.format(endTimeCalendar.time)

                // Tasarımdaki gibi alt alta göstermek için:
                binding.textViewTaskTimeRange.text = "$startTimeFormatted\n$endTimeFormatted"
                // Veya sadece başlangıç:
                // binding.textViewTaskTimeRange.text = startTimeFormatted
                binding.textViewTaskTimeRange.visibility = View.VISIBLE
            } else {
                // Başlangıç saati yoksa, belki sadece süreyi göster veya alanı gizle
                binding.textViewTaskTimeRange.text = "${task.durationMinutes} dk" // Alternatif
                // binding.textViewTaskTimeRange.visibility = View.GONE
            }

            // Görev Açıklaması (item_task.xml'deki textView_task_description)
            // Task entity'nizde bir 'description' alanı varsa onu kullanın.
            // Yoksa, bu alanı farklı bir bilgi için kullanabilir veya gizleyebilirsiniz.
            // Örneğin, eğer Task'ta description alanı yoksa:
            binding.textViewTaskDescription.text = "Tahmini süre: ${task.durationMinutes} dakika" // Örnek
            // Eğer Task'ta description alanı varsa:
            // if (task.description.isNullOrEmpty()) {
            //    binding.textViewTaskDescription.visibility = View.GONE
            // } else {
            //    binding.textViewTaskDescription.text = task.description
            //    binding.textViewTaskDescription.visibility = View.VISIBLE
            // }

            // Silme butonunun görünürlüğü (eğer XML'de varsa ve dinamik olarak yönetilecekse)
            // Şimdilik XML'deki visibility="gone" ayarına güveniyoruz.
            // binding.buttonDeleteTask.visibility = View.VISIBLE // Veya bir koşula göre
        }

        private fun updateTaskTitleAppearance(textView: TextView, isCompleted: Boolean) {
            if (isCompleted) {
                textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.app_on_card_text_secondary)) // Temadan renk
            } else {
                textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.app_on_card_text_primary)) // Temadan renk
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem == newItem
        }
    }
}
