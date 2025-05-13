package com.example.hackathon // Kendi paket adınızı kullanın

import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hackathon.R
import com.example.hackathon.data.Task
import com.example.hackathon.databinding.FragmentTaskListBinding

// Gemini API için gerekli importlar
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
// Gemini SDK Hata Sınıfları için Importlar
//import com.google.ai.client.generativeai.type.InvalidApiKeyException // BU IMPORT AKTİF EDİLDİ
import com.google.ai.client.generativeai.type.PromptBlockedException
import com.google.ai.client.generativeai.type.UnsupportedUserLocationException
import com.google.ai.client.generativeai.type.ServerException
import com.google.ai.client.generativeai.type.RequestTimeoutException


import com.example.hackathon.BuildConfig
import com.example.hackathon.tasks.TaskListAdapter
import com.example.hackathon.tasks.TaskViewModel


import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*


class TaskListFragment : Fragment() {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!

    private val taskViewModel: TaskViewModel by viewModels()
    private lateinit var taskListAdapter: TaskListAdapter
    private var selectedStartTimeMillis: Long? = null

    // !!! ÇOK ÖNEMLİ: BU SATIRA KENDİ GERÇEK GEMINI API ANAHTARINIZI YAZIN !!!
    // Eğer bu anahtar sizin gerçek anahtarınız değilse, lütfen değiştirin.
    private val HARDCODED_GEMINI_API_KEY = "AIzaSyBZUK1zYNcZ7d3rnUQBZhwd6sGKwKRT95g" // KENDİ GERÇEK ANAHTARINIZLA DEĞİŞTİRİN
    private val PLACEHOLDER_API_KEY_FOR_CHECK = "YOUR_ACTUAL_API_KEY_PLACEHOLDER"
    private val EXAMPLE_API_KEY_TO_WARN_USER = "AIzaSyBZUK1zYNcZ7d3rnUQBZhwd6sGK" // Bu, kullanıcıyı uyarmak için kullandığımız örnek anahtar
    private val TAG = "TaskListFragment"

    // Gemini Model
    private val generativeModel: GenerativeModel? by lazy {
        try {
            // API Anahtarı kontrolü
            if (HARDCODED_GEMINI_API_KEY.isBlank() ||
                HARDCODED_GEMINI_API_KEY == PLACEHOLDER_API_KEY_FOR_CHECK ||
                HARDCODED_GEMINI_API_KEY == EXAMPLE_API_KEY_TO_WARN_USER) { // Eğer hala örnek anahtar kullanılıyorsa
                Log.e(TAG, "Geçersiz, yer tutucu veya değiştirilmemiş örnek API Anahtarı hardcoded edilmiş. Gemini Modeli başlatılamadı. Lütfen 'HARDCODED_GEMINI_API_KEY' sabitini kendi geçerli API anahtarınızla güncelleyin.")
                return@lazy null // Modeli başlatma
            }
            val config = generationConfig {
                temperature = 0.75f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 1024
            }
            Log.d(TAG, "GenerativeModel başlatılıyor API Key: ${HARDCODED_GEMINI_API_KEY.take(5)}... (ilk 5 karakter)")
            GenerativeModel(
                modelName = "gemini-1.5-flash-latest",
                apiKey = HARDCODED_GEMINI_API_KEY,
                generationConfig = config
            ).also {
                Log.d(TAG, "GenerativeModel başarıyla başlatıldı.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "GenerativeModel başlatılırken hata oluştu: ${e.message}", e)
            null
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView çağrıldı.")
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        Log.d(TAG, "onCreateView tamamlandı.")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated çağrıldı.")

        try {
            Log.d(TAG, "setupToolbar çağrılıyor...")
            setupToolbar()
            Log.d(TAG, "setupToolbar tamamlandı.")

            Log.d(TAG, "setupRecyclerView çağrılıyor...")
            setupRecyclerView()
            Log.d(TAG, "setupRecyclerView tamamlandı.")

            Log.d(TAG, "setupFab çağrılıyor...")
            setupFab()
            Log.d(TAG, "setupFab tamamlandı.")

            Log.d(TAG, "observeViewModel çağrılıyor...")
            observeViewModel()
            Log.d(TAG, "observeViewModel tamamlandı.")

            Log.d(TAG, "setupItemTouchHelper çağrılıyor...")
            setupItemTouchHelper()
            Log.d(TAG, "setupItemTouchHelper tamamlandı.")

            Log.d(TAG, "setupGeminiButton çağrılıyor...")
            setupGeminiButton()
            Log.d(TAG, "setupGeminiButton tamamlandı.")

        } catch (e: Exception) {
            Log.e(TAG, "onViewCreated içinde bir kurulum sırasında hata oluştu: ${e.message}", e)
            Snackbar.make(binding.root, "Ekran yüklenirken bir hata oluştu.", Snackbar.LENGTH_LONG).show()
        }

        try {
            Log.d("BuildConfigCheck", "Application ID from BuildConfig: ${BuildConfig.APPLICATION_ID}")
            val apiKeyStatus = if (HARDCODED_GEMINI_API_KEY.isBlank() ||
                HARDCODED_GEMINI_API_KEY == PLACEHOLDER_API_KEY_FOR_CHECK ||
                HARDCODED_GEMINI_API_KEY == EXAMPLE_API_KEY_TO_WARN_USER) {
                "Eksik, Yer Tutucu veya Değiştirilmemiş Örnek Anahtar"
            } else {
                "Mevcut ve Kullanıcı Tanımlı (Hardcoded)"
            }
            Log.d("GeminiApiKeyCheck", "Kullanılan API Anahtarı Durumu: $apiKeyStatus")
        } catch (e: Exception) {
            Log.e("BuildConfigCheck", "BuildConfig'e erişirken hata (normal olabilir, API key hardcoded): ", e)
        }
        Log.d(TAG, "onViewCreated tamamlandı.")
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbarTaskList)
    }

    private fun setupRecyclerView() {
        taskListAdapter = TaskListAdapter(
            onTaskClicked = { task ->
                showAddTaskOrEditDialog(task)
            },
            onTaskCheckedChange = { task, _ ->
                taskViewModel.toggleTaskCompleted(task)
            },
            onDeleteClicked = { task ->
                showDeleteConfirmationDialog(task)
            }
        )
        binding.recyclerViewTasks.apply {
            adapter = taskListAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupFab() {
        binding.fabAddTask.setOnClickListener {
            showAddTaskOrEditDialog(null)
        }
    }

    private fun setupGeminiButton() {
        binding.buttonAskGemini.setOnClickListener {
            if (generativeModel == null) {
                Log.e(TAG, "Gemini Modeli düzgün başlatılmadığı için istek gönderilemiyor. Lütfen 'HARDCODED_GEMINI_API_KEY' sabitini kendi geçerli API anahtarınızla güncelleyin.")
                Snackbar.make(binding.root, "Gemini servisi şu anda kullanılamıyor. Lütfen API anahtarınızı kontrol edin.", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val uncompletedTasks = taskListAdapter.currentList.filter { !it.isCompleted }
            if (uncompletedTasks.isEmpty()) {
                Snackbar.make(binding.root, "Öneri almak için önce tamamlanmamış göreviniz olmalı.", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            askGeminiForTimeManagementSuggestions(uncompletedTasks)
        }
    }

    private fun askGeminiForTimeManagementSuggestions(tasks: List<Task>) {
        val currentModel = generativeModel
        if (currentModel == null) {
            Log.e(TAG, "askGeminiForTimeManagementSuggestions: Gemini Modeli null, istek gönderilemiyor.")
            Snackbar.make(binding.root, "Gemini servisi başlatılamadı.", Snackbar.LENGTH_LONG).show()
            return
        }

        showLoading(true)

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val taskDetails = tasks.joinToString(separator = "\n- ", prefix = "- ") { task ->
            val startTimeString = task.startTime?.let { " (Başlangıç: ${timeFormat.format(Date(it))})" } ?: ""
            "'${task.title}' (Tahmini Süre: ${task.durationMinutes} dakika)$startTimeString"
        }

        val prompt = """
        Merhaba! Ben bir zaman yönetimi uygulaması kullanıyorum ve bugünkü tamamlanmamış görevlerim şunlar:
        $taskDetails

        Bu görevleri en verimli şekilde tamamlamak için bana bir zaman planı oluşturabilir misin? Lütfen görevleri hangi sırayla yapmam gerektiğini, her bir göreve ne kadar zaman ayırmam gerektiğini (verilen tahmini süreleri ve varsa başlangıç zamanlarını göz önünde bulundurarak) ve mümkünse aralara kısa molalar ekleyerek bir günlük akış öner. Eğer görevlerin başlangıç saatleri belirtilmişse, planı bu saatlere uygun yapmaya çalış. Cevabını madde madde, anlaşılır ve motive edici bir dille yaz. Cevabın sadece öneri listesi olsun, giriş veya sonuç cümlesi ekleme.
        Örnek format:
        1. 'Kitap Oku' görevine başla (09:00 - 09:30) - 30 dakika. Bu, zihnini açmak için harika bir başlangıç!
        2. Kısa bir mola ver (09:30 - 09:35) - 5 dakika. Biraz esne ve bir bardak su iç.
        3. 'Ödev Yap' görevine odaklan (09:35 - 10:20) - 45 dakika. En zor kısımları önce halletmek iyi bir stratejidir.
        """.trimIndent()

        Log.d(TAG, "Gönderilen Prompt: $prompt")

        lifecycleScope.launch {
            try {
                val response = currentModel.generateContent(prompt)
                val geminiSuggestions = response.text ?: "Gemini'den bir öneri alınamadı. Lütfen tekrar deneyin."

                Log.d(TAG, "Alınan Cevap: $geminiSuggestions")
                showGeminiSuggestionsDialog(geminiSuggestions)

            } catch (e: Exception) {
                Log.e(TAG, "Gemini'den öneri alınırken hata oluştu: ${e.message}", e)
                val errorMessage = when (e) {
                    //is InvalidApiKeyException -> "API anahtarı geçersiz. Lütfen 'HARDCODED_GEMINI_API_KEY' sabitine kendi geçerli API anahtarınızı girdiğinizden emin olun."
                    is PromptBlockedException -> "İsteğiniz güvenlik politikaları nedeniyle engellendi. Lütfen prompt'unuzu gözden geçirin."
                    is UnsupportedUserLocationException -> "Bulunduğunuz bölge Gemini API tarafından desteklenmiyor."
                    is ServerException -> "Gemini sunucularında bir sorun oluştu. Lütfen daha sonra tekrar deneyin."
                    is RequestTimeoutException -> "İstek zaman aşımına uğradı. İnternet bağlantınızı kontrol edin veya daha sonra tekrar deneyin."
                    is UnknownHostException, is ConnectException -> "İnternet bağlantınızı kontrol edin."
                    else -> "Öneri alınırken beklenmedik bir hata oluştu. (${e.javaClass.simpleName})"
                }
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showGeminiSuggestionsDialog(suggestions: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("✨ Gemini'den Zaman Planı Önerisi")
            .setMessage(suggestions)
            .setPositiveButton("Harika, Teşekkürler!") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBarGemini.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonAskGemini.isEnabled = !isLoading
        binding.fabAddTask.isEnabled = !isLoading
        binding.recyclerViewTasks.isEnabled = !isLoading
    }


    private fun observeViewModel() {
        taskViewModel.todayTasks.observe(viewLifecycleOwner, Observer { tasks ->
            val today = SimpleDateFormat("dd MMMM yyyy", Locale("tr")).format(Date())
            binding.toolbarTaskList.title = "$today (${tasks.size})"
            updateEmptyView(tasks.isEmpty())
            taskListAdapter.submitList(tasks.sortedBy { it.creationTimestamp })
        })

        taskViewModel.tasksForSelectedDate.observe(viewLifecycleOwner, Observer { tasks ->
            val selectedDateString = taskViewModel.tasksForSelectedDate.value?.firstOrNull()?.creationTimestamp?.let {
                SimpleDateFormat("dd MMMM yyyy", Locale("tr")).format(Date(it))
            } ?: SimpleDateFormat("dd MMMM yyyy", Locale("tr")).format(Date())

            if (taskViewModel.todayTasks.value != tasks) {
                binding.toolbarTaskList.title = "$selectedDateString (${tasks.size})"
            }
            updateEmptyView(tasks.isEmpty())
            taskListAdapter.submitList(tasks.sortedBy { it.creationTimestamp })
        })
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.recyclerViewTasks.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.textViewEmptyListPlaceholder.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.buttonAskGemini.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showAddTaskOrEditDialog(taskToEdit: Task?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_task, null)
        val editTextTitle = dialogView.findViewById<EditText>(R.id.edit_text_task_title)
        val editTextDuration = dialogView.findViewById<EditText>(R.id.edit_text_task_duration)
        val buttonSetStartTime = dialogView.findViewById<Button>(R.id.button_set_start_time)

        selectedStartTimeMillis = taskToEdit?.startTime

        taskToEdit?.let {
            editTextTitle.setText(it.title)
            editTextDuration.setText(it.durationMinutes.toString())
            updateStartTimeButtonText(buttonSetStartTime, it.startTime)
        } ?: run {
            editTextDuration.setText("25")
            updateStartTimeButtonText(buttonSetStartTime, null)
        }

        buttonSetStartTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            selectedStartTimeMillis?.let { calendar.timeInMillis = it }

            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val selectedCalendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    selectedStartTimeMillis = selectedCalendar.timeInMillis
                    updateStartTimeButtonText(buttonSetStartTime, selectedStartTimeMillis)
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
                        taskViewModel.addNewTask(title, duration, selectedStartTimeMillis)
                    } else {
                        val updatedTask = taskToEdit.copy(
                            title = title,
                            durationMinutes = duration,
                            startTime = selectedStartTimeMillis
                        )
                        taskViewModel.updateTask(updatedTask)
                    }
                    selectedStartTimeMillis = null
                } else {
                    Snackbar.make(binding.root, "Görev başlığı boş olamaz!", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setOnDismissListener { selectedStartTimeMillis = null }
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

    private fun showDeleteConfirmationDialog(task: Task) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Görevi Sil")
            .setMessage("'${task.title}' başlıklı görevi silmek istediğinizden emin misiniz?")
            .setNegativeButton("İptal", null)
            .setPositiveButton("Sil") { _, _ ->
                taskViewModel.deleteTask(task)
                Snackbar.make(binding.root, "'${task.title}' silindi.", Snackbar.LENGTH_LONG)
                    .setAction("Geri Al") {
                        taskViewModel.insertTask(task.copy(id = 0))
                    }
                    .show()
            }
            .show()
    }

    private fun setupItemTouchHelper() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val taskToDelete = taskListAdapter.currentList[position]
                    showDeleteConfirmationDialog(taskToDelete)
                    taskListAdapter.notifyItemChanged(position)
                }
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.recyclerViewTasks)
    }

    private fun showCalendarPicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Tarih Seçin")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selection
            taskViewModel.loadTasksForDate(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
        }
        datePicker.show(childFragmentManager, "MATERIAL_DATE_PICKER")
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_task_list, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_completed_tasks -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Tamamlanan Görevleri Sil")
                    .setMessage("Tamamlanmış tüm görevleri silmek istediğinizden emin misiniz?")
                    .setNegativeButton("İptal", null)
                    .setPositiveButton("Sil") { _, _ ->
                        taskViewModel.deleteCompletedTasks()
                        Snackbar.make(binding.root, "Tamamlanan görevler silindi.", Snackbar.LENGTH_SHORT).show()
                    }
                    .show()
                true
            }
            R.id.action_show_calendar -> {
                showCalendarPicker()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView çağrıldı.")
    }
}
