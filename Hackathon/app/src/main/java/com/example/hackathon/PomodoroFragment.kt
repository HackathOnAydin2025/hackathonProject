package com.example.hackathon // Ana paket adınız (XML'deki tools:context ile ve NavGraph'taki fragment yolu ile tutarlı olmalı)

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.hackathon.data.Task // Task importu gerekli
import com.example.hackathon.databinding.FragmentPomodoroBinding
import com.example.hackathon.tasks.TaskViewModel
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min
import com.example.hackathon.R // R sınıfı importu

class PomodoroFragment : Fragment() {

    private var _binding: FragmentPomodoroBinding? = null
    private val binding get() = _binding!!

    private var isRunning = false
    private var countDownTimer: CountDownTimer? = null

    private val taskViewModel: TaskViewModel by activityViewModels()

    private var currentPomodoroSessionDurationMillis: Long = TimeUnit.MINUTES.toMillis(DEFAULT_POMODORO_SESSION_MINUTES.toLong())
    private var timeLeftInCurrentSessionMillis: Long = currentPomodoroSessionDurationMillis

    private var colorOrange: Int = 0
    private var colorRed: Int = 0

    private var currentTaskIdForPomodoro: Int? = null
    private var currentTaskTitleForPomodoro: String? = "Odaklanma Zamanı!"

    companion object {
        const val DEFAULT_POMODORO_SESSION_MINUTES = 25
        private const val ONE_MINUTE_IN_MILLIS = 60000L
        private const val TAG = "PomodoroFragment" // Loglama için TAG
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPomodoroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Renkleri context gerektirdiği için onViewCreated içinde initialize ediyoruz.
        colorOrange = ContextCompat.getColor(requireContext(), R.color.user_orange)
        colorRed = ContextCompat.getColor(requireContext(), R.color.md_theme_onErrorContainer) // XML'de md_theme_error olabilir, kontrol edin.

        setupClickListeners()
        setupObservers()

        // Fragment ilk oluşturulduğunda veya yeniden oluşturulduğunda seçili görevi kontrol et
        taskViewModel.selectedTaskForPomodoro.value?.let { task ->
            updateUIForTask(task)
            restoreOrConfigureTimerForTask(task)
        } ?: run {
            // Seçili görev yoksa varsayılan duruma ayarla
            updateUIForTask(null)
            resetTimerToDefaultSession()
        }
    }

    private fun setupClickListeners() {
        binding.buttonStartPause.setOnClickListener {
            if (isRunning) {
                pauseTimer()
            } else {
                // Eğer süre bittiyse ve bir görev seçiliyse, göreve göre zamanlayıcıyı yeniden başlat/yapılandır.
                if (timeLeftInCurrentSessionMillis <= 0L && currentTaskIdForPomodoro != null) {
                    taskViewModel.selectedTaskForPomodoro.value?.let { task ->
                        // Görevin kalan süresine göre yeni bir seans yapılandır
                        configureTimerForTask(task)
                    }
                } else if (timeLeftInCurrentSessionMillis <= 0L && currentTaskIdForPomodoro == null) {
                    // Görev yoksa ve süre bittiyse varsayılan seansı yeniden yükle
                    resetTimerToDefaultSession()
                }
                // Her durumda (devam etme veya yeni başlatma) UI güncellemeleri
                updateTimerText() // Metni hemen güncelle
                updateProgress()  // İlerlemeyi hemen güncelle
                binding.progressCircular.setIndicatorColor(colorOrange) // Rengi turuncuya ayarla (kırmızıya dönmüş olabilir)
                startTimer()
            }
        }
    }

    private fun setupObservers() {
        taskViewModel.selectedTaskForPomodoro.observe(viewLifecycleOwner) { task ->
            Log.d(TAG, "Gözlemlenen seçili görev: ${task?.title}, Duraklatılmış ID: ${taskViewModel.pausedPomodoroTaskId}")
            updateUIForTask(task) // Önce UI'ı yeni görev bilgisiyle güncelle
            if (task != null) {
                // Timer'ı bu görev için ya restore et ya da yeniden yapılandır
                restoreOrConfigureTimerForTask(task)
            } else {
                // Seçili görev yoksa (null ise) varsayılan seansa sıfırla
                resetTimerToDefaultSession()
            }
        }
    }

    private fun updateUIForTask(task: Task?) {
        currentTaskIdForPomodoro = task?.id
        currentTaskTitleForPomodoro = task?.title ?: "Odaklanma Zamanı!" // XML'deki textTask
        binding.textTask.text = currentTaskTitleForPomodoro
        Log.d(TAG, "UI güncellendi: Görev='${currentTaskTitleForPomodoro}', ID=$currentTaskIdForPomodoro")
    }

    private fun restoreOrConfigureTimerForTask(task: Task) {
        countDownTimer?.cancel() // Her zaman önce çalışan bir timer varsa iptal et
        isRunning = false

        // ViewModel'de bu görev için duraklatılmış bir seans var mı kontrol et
        if (task.id == taskViewModel.pausedPomodoroTaskId &&
            taskViewModel.pausedPomodoroTimeLeftMillis != null &&
            taskViewModel.pausedPomodoroSessionDurationMillis != null) {
            Log.d(TAG, "Duraklatılmış Pomodoro durumu geri yükleniyor: TaskID=${task.id}")
            timeLeftInCurrentSessionMillis = taskViewModel.pausedPomodoroTimeLeftMillis!!
            currentPomodoroSessionDurationMillis = taskViewModel.pausedPomodoroSessionDurationMillis!!
            binding.buttonStartPause.isEnabled = true // Buton tıklanabilir olmalı
        } else {
            // Duraklatılmış seans yoksa, bu görev için yeni bir seans yapılandır
            Log.d(TAG, "Yeni Pomodoro seansı yapılandırılıyor: TaskID=${task.id}")
            configureTimerForTask(task)
        }

        updateTimerText() // XML'deki textTimer
        updateProgress()  // XML'deki progressCircular
        binding.progressCircular.setIndicatorColor(colorOrange) // Başlangıçta veya restore edildiğinde turuncu
        binding.celebrationAnimation.visibility = View.GONE // XML'deki celebrationAnimation
        binding.celebrationAnimation.cancelAnimation()
        binding.buttonStartPause.setIconResource(android.R.drawable.ic_media_play) // XML'deki buttonStartPause
    }

    private fun configureTimerForTask(task: Task) {
        val totalPlannedMinutes = task.durationMinutes
        val alreadyFocusedMinutes = task.actualFocusedMinutes
        var remainingMinutesForTask = totalPlannedMinutes - alreadyFocusedMinutes

        // Eğer görev için planlanan süre zaten bitmişse veya hiç planlanmamışsa varsayılan süreyi kullan
        if (remainingMinutesForTask <= 0 && totalPlannedMinutes > 0) { // Planlı ve bitmiş
            Log.i(TAG, "'${task.title}' görevi için planlanan süre zaten tamamlanmış. Varsayılan veya yeni bir seans başlatılabilir.")
            // Burada kullanıcıya bir seçenek sunulabilir veya varsayılan bir seans (örn. 5 dk) başlatılabilir.
            // Şimdilik, eğer görev bittiyse, yeni bir standart pomodoro seansı için süreyi ayarlayalım.
            remainingMinutesForTask = DEFAULT_POMODORO_SESSION_MINUTES // Veya küçük bir "ek çalışma" süresi
        } else if (totalPlannedMinutes == 0) { // Plansız görev (durationMinutes = 0)
            remainingMinutesForTask = DEFAULT_POMODORO_SESSION_MINUTES
        } else if (remainingMinutesForTask <= 0) { // Diğer (negatif kalmışsa vb.)
            remainingMinutesForTask = DEFAULT_POMODORO_SESSION_MINUTES
        }


        // Bir Pomodoro seansı genellikle 25dk'dır, ancak görevin kalan süresi daha azsa onu kullanırız.
        val sessionMinutesToSet = min(DEFAULT_POMODORO_SESSION_MINUTES, remainingMinutesForTask.coerceAtLeast(1))
        currentPomodoroSessionDurationMillis = TimeUnit.MINUTES.toMillis(sessionMinutesToSet.toLong())
        timeLeftInCurrentSessionMillis = currentPomodoroSessionDurationMillis
        binding.buttonStartPause.isEnabled = true // Yeni süre ayarlandığı için buton aktif
        Log.d(TAG, "Yeni Pomodoro seansı ayarlandı: ${task.title} için $sessionMinutesToSet dk (Görevin Toplam Kalanı: $remainingMinutesForTask dk)")
    }


    private fun resetTimerToDefaultSession() {
        countDownTimer?.cancel()
        isRunning = false
        currentTaskIdForPomodoro = null // Aktif görev ID'sini temizle
        currentTaskTitleForPomodoro = "Odaklanma Zamanı!"
        binding.textTask.text = currentTaskTitleForPomodoro // XML'deki textTask

        currentPomodoroSessionDurationMillis = TimeUnit.MINUTES.toMillis(DEFAULT_POMODORO_SESSION_MINUTES.toLong())
        timeLeftInCurrentSessionMillis = currentPomodoroSessionDurationMillis
        updateTimerText() // XML'deki textTimer
        updateProgress()  // XML'deki progressCircular
        binding.progressCircular.setIndicatorColor(colorOrange)
        binding.celebrationAnimation.visibility = View.GONE // XML'deki celebrationAnimation
        binding.celebrationAnimation.cancelAnimation()
        binding.buttonStartPause.setIconResource(android.R.drawable.ic_media_play) // XML'deki buttonStartPause
        binding.buttonStartPause.isEnabled = true // Her zaman tıklanabilir olmalı
        taskViewModel.clearPausedPomodoroState() // Duraklatılmış durumu temizle
        Log.d(TAG, "Zamanlayıcı varsayılan seansa sıfırlandı: $DEFAULT_POMODORO_SESSION_MINUTES dk")
    }

    private fun startTimer() {
        // Süre zaten sıfırsa veya daha azsa ve çalışan bir timer yoksa başlatma.
        if (timeLeftInCurrentSessionMillis <= 0 && !isRunning) {
            Log.d(TAG, "Süre dolmuş ve timer çalışmıyor, başlatılmadı. Görev ID: $currentTaskIdForPomodoro")
            // Eğer görev bittiyse ve kullanıcı başlat'a basarsa, görevi yeniden yapılandır ve başlat.
            if (currentTaskIdForPomodoro != null) {
                taskViewModel.selectedTaskForPomodoro.value?.let {
                    configureTimerForTask(it) // Süreyi yeniden ayarla
                    if (timeLeftInCurrentSessionMillis <= 0) { // Yapılandırma sonrası hala süre yoksa
                        binding.buttonStartPause.isEnabled = false // Butonu devre dışı bırak
                        Log.w(TAG, "Görev için yapılandırma sonrası süre hala sıfır. Başlatılmıyor.")
                        return
                    }
                    binding.buttonStartPause.isEnabled = true
                }
            } else { // Görev yok ve süre bitmiş
                binding.buttonStartPause.isEnabled = true // Varsayılan seans için etkin kalabilir
                // resetTimerToDefaultSession() // Ya da varsayılana döndür, ama bu play'e basınca zaten oluyor
                return
            }
        }

        if (isRunning) { // Zaten çalışıyorsa tekrar başlatma
            Log.d(TAG, "Zamanlayıcı zaten çalışıyor.")
            return
        }


        taskViewModel.clearPausedPomodoroState() // Zamanlayıcı başladığında duraklatılmış durumu temizle

        countDownTimer = object : CountDownTimer(timeLeftInCurrentSessionMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInCurrentSessionMillis = millisUntilFinished
                updateTimerText()
                updateProgress()

                // Son 1 dakika kala progress bar rengini değiştir
                val currentIndicatorColors = binding.progressCircular.indicatorColor // Bu bir dizi döner
                val currentColor = if (currentIndicatorColors.isNotEmpty()) currentIndicatorColors[0] else colorOrange

                if (millisUntilFinished <= ONE_MINUTE_IN_MILLIS && currentColor != colorRed) {
                    binding.progressCircular.setIndicatorColor(colorRed)
                } else if (millisUntilFinished > ONE_MINUTE_IN_MILLIS && currentColor != colorOrange) {
                    binding.progressCircular.setIndicatorColor(colorOrange)
                }
            }

            override fun onFinish() {
                Log.d(TAG, "Zamanlayıcı bitti. Görev ID: $currentTaskIdForPomodoro")
                isRunning = false
                // Bu seans için odaklanılan süre, seansın başlangıçtaki toplam süresidir.
                // Eğer seans yarıda kesilip tekrar başlatıldıysa bu mantık değişebilir.
                // Şimdilik, tamamlanan seansın tamamını kaydediyoruz.
                val sessionMinutesFocused = TimeUnit.MILLISECONDS.toMinutes(currentPomodoroSessionDurationMillis).toInt()
                timeLeftInCurrentSessionMillis = 0L // Süreyi sıfırla
                updateTimerText()
                updateProgress()

                binding.buttonStartPause.setIconResource(android.R.drawable.ic_media_play)
                val taskTitle = currentTaskTitleForPomodoro ?: "Seans" // Görev adı yoksa "Seans"
                binding.textTask.text = "'$taskTitle' seansı tamamlandı!"

                Log.d(TAG, "onFinish: Kaydedilecek odak süresi: $sessionMinutesFocused dk, Görev ID: $currentTaskIdForPomodoro")

                currentTaskIdForPomodoro?.let { taskId ->
                    if (sessionMinutesFocused > 0) {
                        taskViewModel.recordFocusedSession(taskId, sessionMinutesFocused)
                    } else {
                        Log.w(TAG, "onFinish: Odak süresi 0 olduğu için kayıt yapılmadı.")
                    }
                }

                binding.celebrationAnimation.apply {
                    visibility = View.VISIBLE
                    playAnimation()
                    // Animasyon bitince gizlemek için listener
                    addAnimatorListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            super.onAnimationEnd(animation)
                            visibility = View.GONE
                        }
                    })
                }
                // Kullanıcı aynı göreve (veya yeni bir seansa) tekrar başlayabilsin
                binding.buttonStartPause.isEnabled = true
            }
        }.start()

        isRunning = true
        binding.buttonStartPause.setIconResource(android.R.drawable.ic_media_pause) // XML'deki buttonStartPause
        Log.d(TAG, "Zamanlayıcı başlatıldı. Seans Süresi: ${currentPomodoroSessionDurationMillis / 1000 / 60} dk, Kalan: ${timeLeftInCurrentSessionMillis / 1000} sn")
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isRunning = false
        binding.buttonStartPause.setIconResource(android.R.drawable.ic_media_play) // XML'deki buttonStartPause
        Log.d(TAG, "Zamanlayıcı duraklatıldı. Kalan süre: $timeLeftInCurrentSessionMillis ms")

        // Duraklatıldığında mevcut durumu ViewModel'e kaydet
        currentTaskIdForPomodoro?.let { taskId ->
            taskViewModel.storePausedPomodoroState(taskId, timeLeftInCurrentSessionMillis, currentPomodoroSessionDurationMillis)
        }
    }

    private fun updateTimerText() {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeftInCurrentSessionMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeftInCurrentSessionMillis) - TimeUnit.MINUTES.toSeconds(minutes)
        binding.textTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds) // XML'deki textTimer
    }

    private fun updateProgress() {
        val progress = if (currentPomodoroSessionDurationMillis > 0) {
            // İlerlemeyi, geçen süreye göre değil, kalan süreye göre ters olarak hesaplamak daha doğru olabilir
            // Ancak CircularProgressIndicator genellikle 0'dan 100'e doğru ilerler.
            // Eğer max'tan 0'a doğru bir ilerleme isteniyorsa, indicatorDirection="counterclockwise" ve progress hesaplaması (total - kalan) / total
            // Mevcut durumda (clockwise), ilerleme = (total - kalan) / total olmalı.
            // Ya da progress'i (total - kalan) olarak set edip, max'ı total olarak ayarlamalıyız.
            // `binding.progressCircular.max = currentPomodoroSessionDurationMillis.toInt()`
            // `binding.progressCircular.progress = (currentPomodoroSessionDurationMillis - timeLeftInCurrentSessionMillis).toInt()`
            // Şimdiki implementasyon, progress'i 0 (başlangıç) ile 100 (bitiş) arasında ayarlar.
            // Kalan süre azaldıkça progress artmalı.
            // ((currentPomodoroSessionDurationMillis - timeLeftInCurrentSessionMillis) * 100 / currentPomodoroSessionDurationMillis).toInt()

            // Orijinal mantığınızda timeLeftInCurrentSessionMillis azaldıkça progress azalıyordu, bu genellikle ters bir ilerlemedir.
            // Doğrusu: Geçen süre / Toplam süre * 100
            val elapsedMillis = currentPomodoroSessionDurationMillis - timeLeftInCurrentSessionMillis
            (elapsedMillis * 100 / currentPomodoroSessionDurationMillis).toInt()

        } else { 100 } // Eğer toplam süre 0 ise, başlangıçta dolu göster (veya 0) - duruma göre
        binding.progressCircular.progress = progress.coerceIn(0,100) // XML'deki progressCircular
    }


    override fun onPause() {
        super.onPause()
        // Eğer zamanlayıcı çalışıyorsa ve fragment duraklatılıyorsa (örn. başka bir app'e geçiş)
        // mevcut durumu kaydet
        if (isRunning) {
            pauseTimer() // pauseTimer zaten durumu ViewModel'e kaydediyor.
            Log.d(TAG, "Fragment onPause: Çalışan zamanlayıcı duraklatıldı ve durumu kaydedildi.")
        }
        // Eğer çalışmıyorsa ama geçerli bir timeLeft varsa (yani manuel duraklatılmışsa),
        // bu durum zaten pauseTimer içinde storePausedPomodoroState ile kaydedilmiş olmalı.
    }


    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel() // Emin olmak için timer'ı iptal et
        countDownTimer = null    // Referansı temizle
        _binding = null // View binding referansını temizle
        Log.d(TAG, "onDestroyView çağrıldı.")
    }
}