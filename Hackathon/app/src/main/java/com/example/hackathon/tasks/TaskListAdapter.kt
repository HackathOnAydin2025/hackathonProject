package com.example.hackathon.tasks // Kendi paket adınızı kullanın

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hackathon.R
import com.example.hackathon.data.Task
import com.example.hackathon.databinding.ItemTaskBinding // ViewBinding ile
import java.text.SimpleDateFormat
import java.util.*

class TaskListAdapter(
    private val onTaskClicked: (Task) -> Unit,
    private val onTaskCheckedChange: (Task, Boolean) -> Unit,
    private val onDeleteClicked: (Task) -> Unit
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
                    onTaskClicked(getItem(position))
                }
            }
            binding.checkboxTaskCompleted.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onTaskCheckedChange(getItem(position), isChecked)
                }
            }
            binding.buttonDeleteTask.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClicked(getItem(position))
                }
            }
        }

        fun bind(task: Task) {
            binding.textViewTaskTitle.text = task.title
            binding.checkboxTaskCompleted.isChecked = task.isCompleted
            updateTaskTitleAppearance(binding.textViewTaskTitle, task.isCompleted)

            var detailsText = "Süre: ${task.durationMinutes} dk"
            task.startTime?.let {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                detailsText += " | Başlangıç: ${sdf.format(Date(it))}"
            }
            // Oluşturulma zamanını da ekleyebiliriz (isteğe bağlı)
            // val creationSdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            // detailsText += " | Oluşturulma: ${creationSdf.format(Date(task.creationTimestamp))}"

            binding.textViewTaskDetails.text = detailsText
            binding.textViewTaskDetails.visibility = if (detailsText.isNotEmpty()) ViewGroup.VISIBLE else ViewGroup.GONE
        }

        private fun updateTaskTitleAppearance(textView: TextView, isCompleted: Boolean) {
            if (isCompleted) {
                textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.grey_500)) // Örnek renk
            } else {
                textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.black)) // Varsayılan renk
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