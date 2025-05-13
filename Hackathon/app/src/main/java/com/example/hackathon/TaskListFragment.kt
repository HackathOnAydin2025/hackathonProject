package com.example.hackathon // Ana paket adınız

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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs // Navigasyon argümanları için
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hackathon.R
import com.example.hackathon.data.Task
import com.example.hackathon.databinding.FragmentTaskListBinding // ViewBinding sınıfınız
import com.example.hackathon.tasks.TaskListAdapter // TaskListAdapter importunuz
import com.example.hackathon.tasks.TaskViewModel // ViewModel'inizin doğru yolu

// Gemini API için gerekli importlar
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
//import com.google.ai.client.generativeai.type.InvalidApiKeyException // Aktif edildi
import com.google.ai.client.generativeai.type.PromptBlockedException
import com.google.ai.client.generativeai.type.UnsupportedUserLocationException
import com.google.ai.client.generativeai.type.ServerException
import com.google.ai.client.generativeai.type.RequestTimeoutException

import com.google.android.material.chip.Chip
// MaterialDatePicker importu kaldırıldı, çünkü showCalendarPicker() artık kullanılmıyor.
// Eğer başka bir yerde kullanılacaksa geri eklenebilir.
// import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.UnknownHostException
import java.text.ParseException // Tarih parse hatası için
import java.text.SimpleDateFormat
import java.util.*

class TaskListFragment : Fragment() {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!

    private val taskViewModel: TaskViewModel by viewModels()
    private lateinit var taskListAdapter: TaskListAdapter
    private var selectedStartTimeMillis: Long? = null

    // Navigasyon argümanlarını almak için (eğer kullanılıyorsa)
    // Eğer bu fragment'a argümanla gelinmiyorsa bu satır ve ilgili kodlar kaldırılabilir.
    // private val navArgs: TaskListFragmentArgs by navArgs() // nav_graph.xml'de bu fragment için argüman tanımlı olmalı

    // !!! ÇOK ÖNEMLİ: BU SATIRA KENDİ GERÇEK GEMINI API ANAHTARINIZI YAZIN !!!
    private val HARDCODED_GEMINI_API_KEY = "AIzaSyBZUK1zYNcZ7d3rnUQBZhwd6sGKwKRT95g" // KENDİ GERÇEK ANAHTARINIZLA DEĞİŞTİRİN
    private val PLACEHOLDER_API_KEY_FOR_CHECK = "YOUR_ACTUAL_API_KEY_PLACEHOLDER"
    private val EXAMPLE_API_KEY_TO_WARN_USER = "AIzaSyBZUK1zYNcZ7d3rnUQBZhwd6sGKwKRT95g" // Kendi örnek anahtarınızı buraya yazın
    private val TAG = "TaskListFragment"

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
        // setHasOptionsMenu(true) // Eski toolbar için menü, yeni tasarımda header içinde yönetiliyor
        Log.d(TAG, "onCreateView tamamlandı.")
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
            // handleNavigationArguments() // Eğer navigasyon argümanı kullanılıyorsa çağrılmalı
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
            Snackbar.make(binding.root, "Menü ikonu tıklandı.", Snackbar.LENGTH_SHORT).show()
            // TODO: Menü işlevselliği (örneğin, Ayarlar'a gitmek, Bahçe ekranına gitmek vb.)
            // findNavController().navigate(R.id.action_taskListFragment_to_gardenFragment) // Örnek navigasyon
        }
        binding.iconHistoryClock.setOnClickListener {
            Snackbar.make(binding.root, "Geçmiş/Takvim ikonu tıklandı.", Snackbar.LENGTH_SHORT).show()
            // TODO: Takvim görünümü veya görev geçmişi işlevselliği
        }
    }

    private fun setupDateChips() {
        val chipGroup = binding.chipGroupDates
        chipGroup.removeAllViews()

        val calendar = Calendar.getInstance()
        val numberOfDaysToShow = 7 // Gösterilecek gün sayısı
        val sdfDayNumber = SimpleDateFormat("dd", Locale.getDefault())
        val sdfDayName = SimpleDateFormat("EEE", Locale("tr")) // Gün adı için Türkçe

        for (i in 0 until numberOfDaysToShow) {
            // item_date_chip_layout.xml'i inflate et
            val chip = layoutInflater.inflate(R.layout.item_date_chip_layout, chipGroup, false) as Chip

            val dayNumber = sdfDayNumber.format(calendar.time)
            val dayName = sdfDayName.format(calendar.time)
            chip.text = "$dayNumber\n$dayName"
            chip.tag = calendar.clone() as Calendar // Tarih bilgisini sakla

            chip.setOnCheckedChangeListener { currentChip, isChecked ->
                if (isChecked) {
                    // Diğer çiplerin seçimini kaldır (singleSelection true olsa da bazen manuel gerekebilir)
                    for (j in 0 until chipGroup.childCount) {
                        val otherChip = chipGroup.getChildAt(j) as Chip
                        if (otherChip != currentChip) {
                            otherChip.isChecked = false
                        }
                    }
                    val selectedCal = currentChip.tag as Calendar
                    Log.d(TAG, "Seçilen Tarih (Çip): ${selectedCal.time}")
                    taskViewModel.loadTasksForDate(
                        selectedCal.get(Calendar.YEAR),
                        selectedCal.get(Calendar.MONTH),
                        selectedCal.get(Calendar.DAY_OF_MONTH)
                    )
                    updateHeaderForSelectedDate(selectedCal)
                }
            }
            chipGroup.addView(chip)

            if (i == 0) { // İlk çip (bugün) seçili başlasın
                chip.isChecked = true
                // updateHeaderForSelectedDate(calendar) // Bu, observeViewModel içinde de yapılabilir
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun updateHeaderForSelectedDate(selectedCalendar: Calendar) {
        val sdfHeaderDate = SimpleDateFormat("dd MMM", Locale("tr"))
        binding.textViewHeaderDateCentered.text = sdfHeaderDate.format(selectedCalendar.time)

        val todayCal = Calendar.getInstance()
        if (isSameDay(selectedCalendar, todayCal)) {
            binding.textViewHeaderToday.text = "Today"
        } else {
            val sdfDayNameFull = SimpleDateFormat("EEEE", Locale("tr"))
            binding.textViewHeaderToday.text = sdfDayNameFull.format(selectedCalendar.time)
        }
        // Görev sayısı ViewModel'den gelen listeye göre güncellenecek
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun setupRecyclerView() {
        taskListAdapter = TaskListAdapter(
            onStartPomodoroClicked = { task ->
                Log.d(TAG, "Pomodoro başlatılıyor: ${task.title}, Süre: ${task.durationMinutes}dk")
                // taskViewModel.startPomodoroForTask(task) // ViewModel'de bu fonksiyon olmalı
                try {
                    // findNavController().navigate(R.id.action_taskListFragment_to_pomodoroFragment) // NavGraph action ID'niz
                    Log.d(TAG, "Pomodoro ekranına navigasyon (henüz eklenmedi).")
                    Snackbar.make(binding.root, "${task.title} için Pomodoro başlatılacak.", Snackbar.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    Log.e(TAG, "Navigasyon hatası - PomodoroFragment'a geçilemedi: ${e.message}", e)
                    Snackbar.make(binding.root, "Pomodoro sayfasına şu an geçilemiyor.", Snackbar.LENGTH_LONG).show()
                }
            },
            onTaskCheckedChange = { task, isChecked ->
                taskViewModel.toggleTaskCompleted(task.copy(isCompleted = isChecked))
            },
            onDeleteClicked = { task ->
                // Bu lambda, eğer item_task.xml içinde bir silme butonu varsa ve
                // TaskListAdapter içinde bu butona listener atanmışsa çağrılır.
                // ItemTouchHelper ile swipe-to-delete zaten showDeleteConfirmationDialog'u çağırıyor.
                // Eğer item içinde buton yoksa, bu lambda TaskListAdapter constructor'ından kaldırılabilir.
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

    private fun setupAddNewTaskButtonHeader() {
        binding.buttonAddNewTaskHeader.setOnClickListener {
            showAddTaskOrEditDialog(null)
        }
    }

    private fun setupGeminiButton() {
        binding.buttonAskGemini.setOnClickListener {
            if (generativeModel == null) {
                Log.e(TAG, "Gemini Modeli düzgün başlatılmadığı için istek gönderilemiyor.")
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
        val currentModel = generativeModel ?: return Unit.also {
            Log.e(TAG, "askGeminiForTimeManagementSuggestions: Gemini Modeli null.")
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
            //is InvalidApiKeyException -> "API anahtarı geçersiz. Lütfen 'HARDCODED_GEMINI_API_KEY' sabitini kendi geçerli API anahtarınızı girdiğinizden emin olun."
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
        binding.buttonAddNewTaskHeader.isEnabled = !isLoading
    }

    private fun observeViewModel() {
        // todayTasks gözlemcisi, başlangıçta bugünün görevlerini ve başlığını yükler.
        // Çip seçimiyle tasksForSelectedDate tetiklenince bu gözlemciye gerek kalmayabilir
        // veya sadece ilk yükleme için kullanılabilir.
        taskViewModel.todayTasks.observe(viewLifecycleOwner, Observer { tasks ->
            // Sadece ilk yüklemede ve henüz bir çip seçilmemişse (veya bugün seçiliyse) başlığı güncelle
            val isTodaySelectedOrInitial = binding.chipGroupDates.checkedChipId == View.NO_ID ||
                    isSameDay(binding.chipGroupDates.findViewById<Chip>(binding.chipGroupDates.checkedChipId)?.tag as? Calendar ?: Calendar.getInstance(), Calendar.getInstance())

            if (isTodaySelectedOrInitial) {
                updateHeaderForSelectedDate(Calendar.getInstance()) // Bugünün tarihiyle header'ı ayarla
                binding.textViewHeaderTaskCount.text = "${tasks.size} Tasks"
                taskListAdapter.submitList(tasks.sortedBy { it.creationTimestamp })
                updateEmptyView(tasks.isEmpty())
            }
        })

        taskViewModel.tasksForSelectedDate.observe(viewLifecycleOwner, Observer { tasks ->
            // Bu gözlemci, loadTasksForDate çağrıldığında (çip seçimiyle) tetiklenir.
            // Header'daki görev sayısı ve liste güncellenir.
            // updateHeaderForSelectedDate zaten çip tıklandığında çağrılıyor.
            binding.textViewHeaderTaskCount.text = "${tasks.size} Tasks"
            taskListAdapter.submitList(tasks.sortedBy { it.creationTimestamp })
            updateEmptyView(tasks.isEmpty())
        })
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.recyclerViewTasks.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.textViewEmptyListPlaceholder.visibility = if (isEmpty) View.VISIBLE else View.GONE
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

    private fun logApiKeyStatus() {
        try {
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
        _binding = null
        Log.d(TAG, "onDestroyView çağrıldı.")
    }
}
