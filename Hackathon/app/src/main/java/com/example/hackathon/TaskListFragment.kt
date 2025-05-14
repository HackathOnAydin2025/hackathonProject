package com.example.hackathon // Ana paket adınız

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
// import androidx.appcompat.app.AppCompatActivity // Kullanılmıyorsa kaldır
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // activityViewModels için import
// import androidx.fragment.app.viewModels // activityViewModels kullanıldığı için bu gerekmeyebilir
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
// import androidx.navigation.fragment.navArgs // Kullanılmıyorsa kaldır
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hackathon.R
import com.example.hackathon.data.Task
import com.example.hackathon.databinding.FragmentTaskListBinding
import com.example.hackathon.tasks.TaskListAdapter
import com.example.hackathon.tasks.TaskViewModel
// Navigasyon için oluşturulan Directions sınıfını import edin
import com.example.hackathon.TaskListFragmentDirections
import com.example.hackathon.progress.viewmodel.GardenViewModel // GardenViewModel importu

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.PromptBlockedException
import com.google.ai.client.generativeai.type.UnsupportedUserLocationException
import com.google.ai.client.generativeai.type.ServerException
import com.google.ai.client.generativeai.type.RequestTimeoutException

import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.UnknownHostException
// import java.text.ParseException // Kullanılmıyorsa kaldır
import java.text.SimpleDateFormat
import java.util.*

class TaskListFragment : Fragment() {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!

    private val taskViewModel: TaskViewModel by activityViewModels()
    private val gardenViewModel: GardenViewModel by activityViewModels()
    private lateinit var taskListAdapter: TaskListAdapter
    private var selectedStartTimeMillis: Long? = null // Doğru yazım

    private val HARDCODED_GEMINI_API_KEY = "AIzaSyBZUK1zYNcZ7d3rnUQBZhwd6sGKwKRT95g" // KENDİ GERÇEK ANAHTARINIZLA DEĞİŞTİRİN
    private val PLACEHOLDER_API_KEY_FOR_CHECK = "YOUR_ACTUAL_API_KEY_PLACEHOLDER"
    private val EXAMPLE_API_KEY_TO_WARN_USER = "AIzaSyBZUK1zYNcZ7d3rnUQBZhwd6sGKwKRT95g"
    private val TAG = "TaskListFragment"

    private val generativeModel: GenerativeModel? by lazy {
        try {
            if (HARDCODED_GEMINI_API_KEY.isBlank() ||
                HARDCODED_GEMINI_API_KEY == PLACEHOLDER_API_KEY_FOR_CHECK ||
                HARDCODED_GEMINI_API_KEY == EXAMPLE_API_KEY_TO_WARN_USER) {
                Log.e(TAG, "Geçersiz, yer tutucu veya değiştirilmemiş örnek API Anahtarı. Gemini Modeli başlatılamadı.")
                return@lazy null
            }
            val config = generationConfig { temperature = 0.75f; topK = 40; topP = 0.95f; maxOutputTokens = 1024 }
            Log.d(TAG, "GenerativeModel başlatılıyor API Key: ${HARDCODED_GEMINI_API_KEY.take(5)}...")
            GenerativeModel(modelName = "gemini-1.5-flash-latest", apiKey = HARDCODED_GEMINI_API_KEY, generationConfig = config)
                .also { Log.d(TAG, "GenerativeModel başarıyla başlatıldı.") }
        } catch (e: Exception) {
            Log.e(TAG, "GenerativeModel başlatılırken hata oluştu: ${e.message}", e); null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView çağrıldı.")
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated çağrıldı.")
        try {
            setupHeaderActions()
            setupDateChips()
            setupRecyclerView()
            setupAddNewTaskButtonHeader()
            setupItemTouchHelper()
            setupGeminiButton()
            observeViewModel()
            logApiKeyStatus()
        } catch (e: Exception) {
            Log.e(TAG, "onViewCreated içinde bir kurulum sırasında hata oluştu: ${e.message}", e)
            Snackbar.make(binding.root, "Ekran yüklenirken bir hata oluştu.", Snackbar.LENGTH_LONG).show()
        }
        Log.d(TAG, "onViewCreated tamamlandı.")
    }

    private fun setupHeaderActions() {
        binding.iconGridMenu.setOnClickListener {
            try {
                val action = TaskListFragmentDirections.actionTaskListFragmentToProgressFragment()
                findNavController().navigate(action)
            } catch (e: Exception) {
                Log.e(TAG, "ProgressFragment'a navigasyon hatası: ${e.message}")
                Snackbar.make(binding.root, "İlerleme sayfasına gidilemedi.", Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.iconHistoryClock.setOnClickListener {
            try {
                val action = TaskListFragmentDirections.actionTaskListFragmentToGardenFragment()
                findNavController().navigate(action)
            } catch (e: Exception) {
                Log.e(TAG, "GardenFragment'a navigasyon hatası: ${e.message}")
                Snackbar.make(binding.root, "Bahçe sayfasına gidilemedi.", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDateChips() {
        val chipGroup = binding.chipGroupDates; chipGroup.removeAllViews()
        val calendar = Calendar.getInstance(); val numberOfDaysToShow = 7
        val sdfDayNumber = SimpleDateFormat("dd", Locale.getDefault()); val sdfDayName = SimpleDateFormat("EEE", Locale("tr"))
        for (i in 0 until numberOfDaysToShow) {
            val chip = layoutInflater.inflate(R.layout.item_date_chip_layout, chipGroup, false) as Chip
            val dayNumber = sdfDayNumber.format(calendar.time); val dayName = sdfDayName.format(calendar.time)
            chip.text = "$dayNumber\n$dayName"; chip.tag = calendar.clone() as Calendar
            chip.setOnCheckedChangeListener { currentChip, isChecked ->
                if (isChecked) {
                    for (j in 0 until chipGroup.childCount) { (chipGroup.getChildAt(j) as Chip).takeIf { it != currentChip }?.isChecked = false }
                    val selectedCal = currentChip.tag as Calendar; Log.d(TAG, "Seçilen Tarih (Çip): ${selectedCal.time}")
                    taskViewModel.loadTasksForDate(selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH))
                    updateHeaderForSelectedDate(selectedCal)
                }
            }
            chipGroup.addView(chip)
            if (i == 0) { chip.isChecked = true }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun updateHeaderForSelectedDate(selectedCalendar: Calendar) {
        val sdfHeaderDate = SimpleDateFormat("dd MMM", Locale("tr")); binding.textViewHeaderDateCentered.text = sdfHeaderDate.format(selectedCalendar.time)
        val todayCal = Calendar.getInstance()
        if (isSameDay(selectedCalendar, todayCal)) { binding.textViewHeaderToday.text = "Today" }
        else { val sdfDayNameFull = SimpleDateFormat("EEEE", Locale("tr")); binding.textViewHeaderToday.text = sdfDayNameFull.format(selectedCalendar.time) }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean { return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) }

    private fun setupRecyclerView() {
        taskListAdapter = TaskListAdapter(
            onTaskItemClicked = { task ->
                Log.d(TAG, "Görev tıklandı, Pomodoro başlatılacak: ${task.title}")
                taskViewModel.selectTaskForPomodoro(task)
                try {
                    val action = TaskListFragmentDirections.actionTaskListFragmentToPomodoroFragment()
                    findNavController().navigate(action)
                } catch (e: Exception) {
                    Log.e(TAG, "PomodoroFragment'a navigasyon hatası: ${e.message}", e)
                    Snackbar.make(binding.root, "Pomodoro ekranı açılamadı. Navigasyon ayarlarını kontrol edin.", Snackbar.LENGTH_LONG).show()
                }
            },
            onTaskCheckedChange = { task, isChecked ->
                val taskBeforeUpdate = task
                taskViewModel.toggleTaskCompleted(task.copy(isCompleted = isChecked))
                if (isChecked && !taskBeforeUpdate.isCompleted) {
                    val dropletsEarned = calculateDropletsForTask(taskBeforeUpdate)
                    gardenViewModel.addWaterDroplets(dropletsEarned)
                    Toast.makeText(context, "Tebrikler! $dropletsEarned damla kazandın!", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "Görev tamamlandı: '${task.title}', $dropletsEarned damla kazanıldı.")
                } else if (!isChecked && taskBeforeUpdate.isCompleted) {
                    Log.d(TAG, "Görev tamamlanmamış olarak işaretlendi: '${task.title}'. Damla değişikliği yok.")
                }
            },
            onDeleteClicked = { task ->
                showDeleteConfirmationDialog(task)
            },
            onEditTaskClicked = { task ->
                showAddTaskOrEditDialog(task)
            }
        )
        binding.recyclerViewTasks.apply {
            adapter = taskListAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun calculateDropletsForTask(task: Task): Int {
        return when {
            task.durationMinutes >= 60 -> 20
            task.durationMinutes >= 30 -> 15
            else -> 10
        }
    }

    private fun setupAddNewTaskButtonHeader() { binding.buttonAddNewTaskHeader.setOnClickListener { showAddTaskOrEditDialog(null) } }

    private fun setupGeminiButton() {
        binding.buttonAskGemini.setOnClickListener {
            if (generativeModel == null) { Log.e(TAG, "Gemini Modeli başlatılmadı."); Snackbar.make(binding.root, "Gemini servisi kullanılamıyor. API anahtarınızı kontrol edin.", Snackbar.LENGTH_LONG).show(); return@setOnClickListener }
            val uncompletedTasks = taskListAdapter.currentList.filter { !it.isCompleted }
            if (uncompletedTasks.isEmpty()) { Snackbar.make(binding.root, "Öneri almak için tamamlanmamış göreviniz olmalı.", Snackbar.LENGTH_LONG).show(); return@setOnClickListener }
            askGeminiForTimeManagementSuggestions(uncompletedTasks)
        }
    }

    private fun askGeminiForTimeManagementSuggestions(tasks: List<Task>) {
        val currentModel = generativeModel ?: return Unit.also { Log.e(TAG, "askGemini: Model null."); Snackbar.make(binding.root, "Gemini servisi başlatılamadı.", Snackbar.LENGTH_LONG).show() }
        showLoading(true); val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val taskDetails = tasks.joinToString(separator = "\n- ", prefix = "- ") { task -> val startTimeString = task.startTime?.let { " (Başlangıç: ${timeFormat.format(Date(it))})" } ?: ""; "'${task.title}' (Tahmini Süre: ${task.durationMinutes} dakika)$startTimeString" }
        val prompt = "Merhaba! Bugünkü tamamlanmamış görevlerim:\n$taskDetails\nBu görevleri en verimli şekilde tamamlamak için bir zaman planı oluştur. Görev sırası, süreler (tahmini ve başlangıç saatlerini dikkate al) ve kısa molalar içeren bir günlük akış öner. Cevabın sadece madde madde öneri listesi olsun, giriş/sonuç cümlesi ekleme.\nÖrnek:\n1. 'Kitap Oku' (09:00-09:30) - 30dk.\n2. Mola (09:30-09:35) - 5dk.\n3. 'Ödev Yap' (09:35-10:20) - 45dk."; Log.d(TAG, "Prompt: $prompt")
        lifecycleScope.launch { try { val response = currentModel.generateContent(prompt); showGeminiSuggestionsDialog(response.text ?: "Öneri alınamadı.") } catch (e: Exception) { Log.e(TAG, "Gemini hata: ${e.message}", e); handleGeminiError(e) } finally { showLoading(false) } }
    }

    private fun handleGeminiError(e: Exception) {
        val errorMessage = when (e) { is PromptBlockedException -> "İstek güvenlik nedeniyle engellendi."; is UnsupportedUserLocationException -> "Bölgeniz desteklenmiyor."; is ServerException -> "Gemini sunucu hatası."; is RequestTimeoutException -> "İstek zaman aşımına uğradı."; is UnknownHostException, is ConnectException -> "İnternet bağlantınızı kontrol edin."; else -> "Beklenmedik hata: (${e.javaClass.simpleName})" }; Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
    }

    private fun showGeminiSuggestionsDialog(suggestions: String) { MaterialAlertDialogBuilder(requireContext()).setTitle("✨ Gemini'den Zaman Planı").setMessage(suggestions).setPositiveButton("Harika!") { d, _ -> d.dismiss() }.show() }
    private fun showLoading(isLoading: Boolean) { binding.progressBarGemini.visibility = if (isLoading) View.VISIBLE else View.GONE; binding.buttonAskGemini.isEnabled = !isLoading; binding.buttonAddNewTaskHeader.isEnabled = !isLoading }

    private fun observeViewModel() {
        taskViewModel.todayTasks.observe(viewLifecycleOwner, Observer { tasks ->
            val todayCal = Calendar.getInstance()
            val selectedChipTag = binding.chipGroupDates.findViewById<Chip>(binding.chipGroupDates.checkedChipId)?.tag as? Calendar
            if (selectedChipTag == null || isSameDay(selectedChipTag, todayCal)) {
                if (selectedChipTag == null) { updateHeaderForSelectedDate(todayCal) }
                binding.textViewHeaderTaskCount.text = "${tasks.size} Görev"
                taskListAdapter.submitList(tasks.sortedByDescending { it.creationTimestamp })
                updateEmptyView(tasks.isEmpty())
            }
        })

        taskViewModel.tasksForSelectedDate.observe(viewLifecycleOwner, Observer { tasks ->
            binding.textViewHeaderTaskCount.text = "${tasks.size} Görev"
            taskListAdapter.submitList(tasks.sortedBy { it.creationTimestamp })
            updateEmptyView(tasks.isEmpty())
        })
    }

    private fun updateEmptyView(isEmpty: Boolean) { binding.recyclerViewTasks.visibility = if (isEmpty) View.GONE else View.VISIBLE; binding.textViewEmptyListPlaceholder.visibility = if (isEmpty) View.VISIBLE else View.GONE }

    private fun showAddTaskOrEditDialog(taskToEdit: Task?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_task, null)
        val editTextTitle = dialogView.findViewById<EditText>(R.id.edit_text_task_title)
        val editTextDuration = dialogView.findViewById<EditText>(R.id.edit_text_task_duration)
        val buttonSetStartTime = dialogView.findViewById<Button>(R.id.button_set_start_time)

        selectedStartTimeMillis = taskToEdit?.startTime // Doğru yazım

        taskToEdit?.let {
            editTextTitle.setText(it.title)
            editTextDuration.setText(it.durationMinutes.toString())
            updateStartTimeButtonText(buttonSetStartTime, it.startTime)
        } ?: run {
            editTextDuration.setText("25") // Default duration
            updateStartTimeButtonText(buttonSetStartTime, null)
        }

        buttonSetStartTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            selectedStartTimeMillis?.let { currentTime -> calendar.timeInMillis = currentTime } // Doğru yazım

            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val selectedCalendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    selectedStartTimeMillis = selectedCalendar.timeInMillis // Doğru yazım
                    updateStartTimeButtonText(buttonSetStartTime, selectedStartTimeMillis) // Doğru yazım
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (taskToEdit == null) "Yeni Görev Ekle" else "Görevi Düzenle")
            .setView(dialogView)
            .setNegativeButton("İptal", null)
            .setPositiveButton(if (taskToEdit == null) "Ekle" else "Kaydet") { _, _ ->
                val title = editTextTitle.text.toString().trim()
                val duration = editTextDuration.text.toString().toIntOrNull() ?: 25

                if (title.isNotEmpty()) {
                    if (taskToEdit == null) {
                        taskViewModel.addNewTask(title, duration, selectedStartTimeMillis) // Doğru yazım
                    } else {
                        val updatedTask = taskToEdit.copy(
                            title = title,
                            durationMinutes = duration,
                            startTime = selectedStartTimeMillis // Doğru yazım
                        )
                        taskViewModel.updateTask(updatedTask)
                    }
                    selectedStartTimeMillis = null // Reset after use
                } else {
                    Snackbar.make(binding.root, "Görev başlığı boş olamaz!", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setOnDismissListener { selectedStartTimeMillis = null } // Reset if dialog is dismissed
            .show()
    }

    private fun updateStartTimeButtonText(button: Button, timeMillis: Long?) {
        if (timeMillis != null) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            button.text = "Başlangıç: ${sdf.format(Date(timeMillis))}"
        } else {
            button.text = "Başlangıç Zamanı Ayarla (Opsiyonel)"
        }
    }

    private fun showDeleteConfirmationDialog(task: Task) { MaterialAlertDialogBuilder(requireContext()).setTitle("Görevi Sil").setMessage("'${task.title}' başlıklı görevi silmek istediğinizden emin misiniz?").setNegativeButton("İptal", null).setPositiveButton("Sil") { _, _ -> taskViewModel.deleteTask(task); Snackbar.make(binding.root, "'${task.title}' silindi.", Snackbar.LENGTH_LONG).setAction("Geri Al") { taskViewModel.insertTask(task.copy(id = 0)) }.show() }.show() }
    private fun setupItemTouchHelper() { val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) { override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false; override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { val position = viewHolder.adapterPosition; if (position != RecyclerView.NO_POSITION) { val taskToDelete = taskListAdapter.currentList[position]; showDeleteConfirmationDialog(taskToDelete); taskListAdapter.notifyItemChanged(position) } } }; ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.recyclerViewTasks) }
    private fun logApiKeyStatus() { try { val apiKeyStatus = if (HARDCODED_GEMINI_API_KEY.isBlank() || HARDCODED_GEMINI_API_KEY == PLACEHOLDER_API_KEY_FOR_CHECK || HARDCODED_GEMINI_API_KEY == EXAMPLE_API_KEY_TO_WARN_USER) "Eksik/Yer Tutucu/Örnek Anahtar" else "Mevcut (Hardcoded)"; Log.d("GeminiApiKeyCheck", "API Anahtarı Durumu: $apiKeyStatus") } catch (e: Exception) { Log.e("BuildConfigCheck", "API Key loglama hatası: ", e) } }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView çağrıldı.")
    }
}
