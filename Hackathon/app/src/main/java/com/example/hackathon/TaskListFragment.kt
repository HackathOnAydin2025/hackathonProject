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
import androidx.navigation.fragment.navArgs // Navigasyon argümanları için eklendi
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
import java.text.ParseException // Tarih parse hatası için eklendi
import java.text.SimpleDateFormat
import java.util.*


class TaskListFragment : Fragment() {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!

    private val taskViewModel: TaskViewModel by viewModels()
    private lateinit var taskListAdapter: TaskListAdapter
    private var selectedStartTimeMillis: Long? = null

    // Navigasyon argümanlarını almak için
    private val navArgs: TaskListFragmentArgs by navArgs()

    // !!! ÇOK ÖNEMLİ: BU SATIRA KENDİ GERÇEK GEMINI API ANAHTARINIZI YAZIN !!!
    private val HARDCODED_GEMINI_API_KEY = "AIzaSyBZUK1zYNcZ7d3rnUQBZhwd6sGKwKRT95g" // KENDİ GERÇEK ANAHTARINIZLA DEĞİŞTİRİN
    private val PLACEHOLDER_API_KEY_FOR_CHECK = "YOUR_ACTUAL_API_KEY_PLACEHOLDER"
    private val EXAMPLE_API_KEY_TO_WARN_USER = "AIzaSyBZUK1zYNcZ7d3rnUQBZhwd6sGK"
    private val TAG = "TaskListFragment"

    // Gemini Model
    private val generativeModel: GenerativeModel? by lazy {
        try {
            if (HARDCODED_GEMINI_API_KEY.isBlank() ||
                HARDCODED_GEMINI_API_KEY == PLACEHOLDER_API_KEY_FOR_CHECK ||
                HARDCODED_GEMINI_API_KEY == EXAMPLE_API_KEY_TO_WARN_USER) {
                Log.e(TAG, "Geçersiz, yer tutucu veya değiştirilmemiş örnek API Anahtarı. Gemini Modeli başlatılamadı.")
                return@lazy null
            }
            val config = generationConfig {
                temperature = 0.75f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 1024
            }
            Log.d(TAG, "GenerativeModel başlatılıyor API Key: ${HARDCODED_GEMINI_API_KEY.take(5)}...")
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
        setHasOptionsMenu(true) // Menü için
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated çağrıldı.")

        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupItemTouchHelper()
        setupGeminiButton()

        // Navigasyondan gelen tarihi işle
        handleNavigationArguments()

        observeViewModel() // ViewModel gözlemcilerini başlat

        // API Anahtarı kontrol logları (isteğe bağlı)
        logApiKeyStatus()
    }

    private fun setupToolbar() {
        // Toolbar'ı Fragment'ın kendi ActionBar'ı olarak ayarla
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbarTaskList)
        // Başlangıç başlığını ayarla (observeViewModel içinde güncellenecek)
        updateToolbarTitle(null, 0) // Varsayılan başlık
    }

    private fun handleNavigationArguments() {
        val selectedDateStr = navArgs.selectedDateString
        if (selectedDateStr != null) {
            Log.d(TAG, "Navigasyondan gelen tarih: $selectedDateStr")
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = sdf.parse(selectedDateStr)
                if (date != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    taskViewModel.loadTasksForDate(
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH), // Calendar.MONTH 0-indexed
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                } else {
                    Log.w(TAG, "Argümandan gelen tarih parse edilemedi (null). Varsayılan yükleniyor.")
                    loadTodaysTasksAsFallback()
                }
            } catch (e: ParseException) {
                Log.e(TAG, "Argümandan gelen tarih parse edilirken hata: $selectedDateStr", e)
                loadTodaysTasksAsFallback()
            }
        } else {
            Log.d(TAG, "Navigasyon argümanı olarak tarih gelmedi. Varsayılan (bugün) yükleniyor.")
            // ViewModel'in init bloğu zaten todayTasks'ı yüklüyor olabilir.
            // Ya da burada bugünün görevlerini yüklemek için loadTodaysTasksAsFallback() çağrılabilir.
            // Eğer ViewModel.init todayTasks'ı yüklüyorsa, burası boş kalabilir
            // veya `taskViewModel.todayTasks` gözlemcisinin tetiklenmesini bekleyebiliriz.
            // Şimdilik, `observeViewModel` içindeki `todayTasks` gözlemcisine güveniyoruz.
        }
    }

    private fun loadTodaysTasksAsFallback() {
        // Bu fonksiyon, ViewModel'in init bloğunda todayTasks zaten yükleniyorsa
        // veya _selectedDate'e bugünün tarihi atanarak tasksForSelectedDate tetikleniyorsa
        // farklı bir şekilde ele alınabilir.
        // Şimdilik, bugünün tarihini _selectedDate'e atayarak tasksForSelectedDate'i tetikleyelim.
        val todayCalendar = Calendar.getInstance()
        taskViewModel.loadTasksForDate(
            todayCalendar.get(Calendar.YEAR),
            todayCalendar.get(Calendar.MONTH),
            todayCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }


    private fun observeViewModel() {
        // Argüman yoksa veya bugün gösteriliyorsa todayTasks'ı dinle
        taskViewModel.todayTasks.observe(viewLifecycleOwner, Observer { tasks ->
            if (navArgs.selectedDateString == null) { // Sadece argüman yoksa bugünün görevlerini ve başlığını güncelle
                updateToolbarTitle(Date(), tasks.size) // Bugünün tarihi ve görev sayısı
                taskListAdapter.submitList(tasks.sortedBy { it.creationTimestamp })
                updateEmptyView(tasks.isEmpty())
            }
        })

        // Argüman varsa veya belirli bir tarih seçildiyse tasksForSelectedDate'i dinle
        taskViewModel.tasksForSelectedDate.observe(viewLifecycleOwner, Observer { tasks ->
            // tasksForSelectedDate her zaman bir değere sahip olmayabilir (başlangıçta null olabilir)
            // Bu nedenle, sadece selectedDateString argümanı varsa bu bloğu çalıştırmak daha güvenli.
            navArgs.selectedDateString?.let { dateStrArg ->
                try {
                    val sdfQuery = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val parsedDate = sdfQuery.parse(dateStrArg)
                    if (parsedDate != null) {
                        updateToolbarTitle(parsedDate, tasks.size) // Seçilen tarih ve görev sayısı
                    } else {
                        updateToolbarTitle(null, tasks.size) // Parse edilemezse genel başlık
                    }
                } catch (e: ParseException) {
                    Log.e(TAG, "Toolbar başlığı için tarih parse hatası: $dateStrArg", e)
                    updateToolbarTitle(null, tasks.size) // Hata durumunda genel başlık
                }
                taskListAdapter.submitList(tasks.sortedBy { it.creationTimestamp })
                updateEmptyView(tasks.isEmpty())
            }
        })
    }

    private fun updateToolbarTitle(date: Date?, taskCount: Int) {
        val title: String = if (date != null) {
            // Eğer tarih bugüne eşitse "Bugün" yaz, değilse formatla
            val calendar = Calendar.getInstance()
            val todayYear = calendar.get(Calendar.YEAR)
            val todayMonth = calendar.get(Calendar.MONTH)
            val todayDay = calendar.get(Calendar.DAY_OF_MONTH)

            calendar.time = date
            val dateYear = calendar.get(Calendar.YEAR)
            val dateMonth = calendar.get(Calendar.MONTH)
            val dateDay = calendar.get(Calendar.DAY_OF_MONTH)

            if (todayYear == dateYear && todayMonth == dateMonth && todayDay == dateDay) {
                "Bugün"
            } else {
                SimpleDateFormat("dd MMMM yyyy", Locale("tr")).format(date)
            }
        } else if (navArgs.selectedDateString != null) {
            // Argüman var ama parse edilemediyse veya null ise
            "Seçili Gün"
        }
        else {
            "Bugün" // Varsayılan (argüman yoksa)
        }
        binding.toolbarTaskList.title = "$title ($taskCount)"
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
                Log.e(TAG, "Gemini Modeli düzgün başlatılmadığı için istek gönderilemiyor.")
                Snackbar.make(binding.root, "Gemini servisi şu anda kullanılamıyor. API anahtarınızı kontrol edin.", Snackbar.LENGTH_LONG).show()
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
        val currentModel = generativeModel ?: return Unit.also {
            Snackbar.make(binding.root, "Gemini servisi başlatılamadı.", Snackbar.LENGTH_LONG).show()
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
                showGeminiSuggestionsDialog(response.text ?: "Gemini'den bir öneri alınamadı.")
            } catch (e: Exception) {
                Log.e(TAG, "Gemini'den öneri alınırken hata: ${e.message}", e)
                handleGeminiError(e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun handleGeminiError(e: Exception) {
        val errorMessage = when (e) {
            is PromptBlockedException -> "İsteğiniz güvenlik politikaları nedeniyle engellendi."
            is UnsupportedUserLocationException -> "Bulunduğunuz bölge Gemini API tarafından desteklenmiyor."
            is ServerException -> "Gemini sunucularında bir sorun oluştu. Lütfen daha sonra tekrar deneyin."
            is RequestTimeoutException -> "İstek zaman aşımına uğradı. İnternet bağlantınızı kontrol edin."
            is UnknownHostException, is ConnectException -> "İnternet bağlantınızı kontrol edin."
            else -> "Öneri alınırken beklenmedik bir hata oluştu. (${e.javaClass.simpleName})"
        }
        Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
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
        // binding.recyclerViewTasks.isEnabled = !isLoading // Kullanıcı etkileşimini engellemek için
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.recyclerViewTasks.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.textViewEmptyListPlaceholder.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.buttonAskGemini.visibility = if (isEmpty) View.GONE else View.VISIBLE // Gemini butonu da boş duruma göre
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
            editTextDuration.setText("25") // Varsayılan süre
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
                    selectedStartTimeMillis = null // İşlem sonrası sıfırla
                } else {
                    Snackbar.make(binding.root, "Görev başlığı boş olamaz!", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setOnDismissListener { selectedStartTimeMillis = null } // Dialog kapanınca da sıfırla
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
                        taskViewModel.insertTask(task.copy(id = 0)) // id'yi sıfırlayarak yeniden ekle
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
                    // Silme işlemi dialog ile onaylandığı için adapter'ı hemen güncellemek yerine
                    // LiveData'nın güncellemesini beklemek daha doğru olabilir.
                    // Ancak, kullanıcıya anında geri bildirim için bu satır tutulabilir veya
                    // silme işlemi sonrası LiveData güncellendiğinde liste otomatik yenilenecektir.
                    taskListAdapter.notifyItemChanged(position) // Kaydırılan öğeyi eski haline getirir
                }
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.recyclerViewTasks)
    }

    private fun showCalendarPicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Tarih Seçin")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds()) // Varsayılan olarak bugünü seç
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")) // UTC ile al
            calendar.timeInMillis = selection
            taskViewModel.loadTasksForDate(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            // Takvimden tarih seçildiğinde, selectedDateString argümanı null olacağı için
            // tasksForSelectedDate gözlemcisi bu yeni tarihi yükleyecektir.
            // Argümanı manuel olarak null'a çekmeye gerek yok, navArgs.selectedDateString zaten
            // bu fragment örneği için sabittir. Yeni bir fragment örneği oluşturulmadıkça değişmez.
            // Ancak, takvimden seçim yapıldığında `navArgs.selectedDateString`'in etkisini
            // kırmak için bir state yönetimi (örn: ViewModel'da ayrı bir LiveData) düşünülebilir
            // ya da `tasksForSelectedDate` gözlemcisindeki koşul güncellenebilir.
            // Şimdilik, `loadTasksForDate` çağrısı `tasksForSelectedDate` LiveData'sını tetikleyecektir.
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

    private fun logApiKeyStatus() {
        try {
            // BuildConfig.APPLICATION_ID yerine direkt bir string loglayabiliriz.
            // Log.d("BuildConfigCheck", "App ID: com.example.hackathon")
            val apiKeyStatus = if (HARDCODED_GEMINI_API_KEY.isBlank() ||
                HARDCODED_GEMINI_API_KEY == PLACEHOLDER_API_KEY_FOR_CHECK ||
                HARDCODED_GEMINI_API_KEY == EXAMPLE_API_KEY_TO_WARN_USER) {
                "Eksik, Yer Tutucu veya Değiştirilmemiş Örnek Anahtar"
            } else {
                "Mevcut ve Kullanıcı Tanımlı (Hardcoded)"
            }
            Log.d("GeminiApiKeyCheck", "Kullanılan API Anahtarı Durumu: $apiKeyStatus")
        } catch (e: Exception) {
            Log.e("BuildConfigCheck", "API Key loglama sırasında hata: ", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Bellek sızıntılarını önlemek için
        Log.d(TAG, "onDestroyView çağrıldı.")
    }
}
