package com.example.hackathon // Ana paket adınız

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

        colorOrange = ContextCompat.getColor(requireContext(), R.color.user_orange)
        colorRed = ContextCompat.getColor(requireContext(), R.color.md_theme_onErrorContainer)

        setupClickListeners()
        setupObservers()

        taskViewModel.selectedTaskForPomodoro.value?.let { task ->
            updateUIForTask(task)
            restoreOrConfigureTimerForTask(task)
        } ?: run {
            updateUIForTask(null)
            resetTimerToDefaultSession()
        }
    }

    private fun setupClickListeners() {
        binding.buttonStartPause.setOnClickListener {
            if (isRunning) {
                pauseTimer()
            } else {
                if (timeLeftInCurrentSessionMillis <= 0L && currentTaskIdForPomodoro != null) {
                    taskViewModel.selectedTaskForPomodoro.value?.let { task ->
                        configureTimerForTask(task)
                    }
                } else if (timeLeftInCurrentSessionMillis <= 0L && currentTaskIdForPomodoro == null) {
                    resetTimerToDefaultSession()
                }
                updateTimerText()
                updateProgress()
                binding.progressCircular.setIndicatorColor(colorOrange)
                startTimer()
            }
        }
    }

    private fun setupObservers() {
        taskViewModel.selectedTaskForPomodoro.observe(viewLifecycleOwner) { task ->
            Log.d(TAG, "Gözlemlenen seçili görev: ${task?.title}, Duraklatılmış ID: ${taskViewModel.pausedPomodoroTaskId}")
            updateUIForTask(task)
            if (task != null) {
                restoreOrConfigureTimerForTask(task)
            } else {
                resetTimerToDefaultSession()
            }
        }
    }

    private fun updateUIForTask(task: Task?) {
        currentTaskIdForPomodoro = task?.id
        currentTaskTitleForPomodoro = task?.title ?: "Odaklanma Zamanı!"
        binding.textTask.text = currentTaskTitleForPomodoro
        Log.d(TAG, "UI güncellendi: Görev='${currentTaskTitleForPomodoro}', ID=$currentTaskIdForPomodoro")
    }

    private fun restoreOrConfigureTimerForTask(task: Task) {
        countDownTimer?.cancel()
        isRunning = false

        if (task.id == taskViewModel.pausedPomodoroTaskId &&
            taskViewModel.pausedPomodoroTimeLeftMillis != null &&
            taskViewModel.pausedPomodoroSessionDurationMillis != null) {
            Log.d(TAG, "Duraklatılmış Pomodoro durumu geri yükleniyor: TaskID=${task.id}")
            timeLeftInCurrentSessionMillis = taskViewModel.pausedPomodoroTimeLeftMillis!!
            currentPomodoroSessionDurationMillis = taskViewModel.pausedPomodoroSessionDurationMillis!!
            binding.buttonStartPause.isEnabled = true
        } else {
            Log.d(TAG, "Yeni Pomodoro seansı yapılandırılıyor: TaskID=${task.id}")
            configureTimerForTask(task)
        }

        updateTimerText()
        updateProgress()
        binding.progressCircular.setIndicatorColor(colorOrange)
        binding.celebrationAnimation.visibility = View.GONE
        binding.celebrationAnimation.cancelAnimation()
        binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_play)
    }

    private fun configureTimerForTask(task: Task) {
        val totalPlannedMinutes = task.durationMinutes
        val alreadyFocusedMinutes = task.actualFocusedMinutes
        var remainingMinutesForTask = totalPlannedMinutes - alreadyFocusedMinutes

        if (remainingMinutesForTask <= 0 && totalPlannedMinutes > 0) {
            Log.i(TAG, "'${task.title}' görevi için planlanan süre zaten tamamlanmış veya aşılmış.")
            remainingMinutesForTask = DEFAULT_POMODORO_SESSION_MINUTES
        } else if (remainingMinutesForTask <= 0 && totalPlannedMinutes == 0) { // Plansız görev ve hiç odaklanılmamışsa veya bitmişse
            remainingMinutesForTask = DEFAULT_POMODORO_SESSION_MINUTES
        }

        val sessionMinutesToSet = min(DEFAULT_POMODORO_SESSION_MINUTES, remainingMinutesForTask.coerceAtLeast(1))
        currentPomodoroSessionDurationMillis = TimeUnit.MINUTES.toMillis(sessionMinutesToSet.toLong())
        timeLeftInCurrentSessionMillis = currentPomodoroSessionDurationMillis
        binding.buttonStartPause.isEnabled = true
        Log.d(TAG, "Yeni Pomodoro seansı ayarlandı: ${task.title} için $sessionMinutesToSet dk (Toplam Kalan: $remainingMinutesForTask dk)")
    }

    private fun resetTimerToDefaultSession() {
        countDownTimer?.cancel()
        isRunning = false
        currentTaskIdForPomodoro = null
        currentTaskTitleForPomodoro = "Odaklanma Zamanı!"
        binding.textTask.text = currentTaskTitleForPomodoro

        currentPomodoroSessionDurationMillis = TimeUnit.MINUTES.toMillis(DEFAULT_POMODORO_SESSION_MINUTES.toLong())
        timeLeftInCurrentSessionMillis = currentPomodoroSessionDurationMillis
        updateTimerText()
        updateProgress()
        binding.progressCircular.setIndicatorColor(colorOrange)
        binding.celebrationAnimation.visibility = View.GONE
        binding.celebrationAnimation.cancelAnimation()
        binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_play)
        binding.buttonStartPause.isEnabled = true
        taskViewModel.clearPausedPomodoroState()
        Log.d(TAG, "Zamanlayıcı varsayılan seansa sıfırlandı: $DEFAULT_POMODORO_SESSION_MINUTES dk")
    }

    private fun startTimer() {
        if (isRunning || timeLeftInCurrentSessionMillis <= 0) {
            Log.d(TAG, "Zamanlayıcı zaten çalışıyor veya süre dolmuş, başlatılmadı. Kalan Süre: $timeLeftInCurrentSessionMillis, Görev ID: $currentTaskIdForPomodoro")
            // Eğer süre bittiyse ve bir görev hala seçiliyse, göreve göre yeni bir seans için configure et
            if (timeLeftInCurrentSessionMillis <= 0 && currentTaskIdForPomodoro != null) {
                taskViewModel.selectedTaskForPomodoro.value?.let {
                    Log.d(TAG, "Süre bitti, görev için zamanlayıcı yeniden yapılandırılıyor: ${it.title}")
                    configureTimerForTask(it)
                }
                // configureTimerForTask süreyi ayarladıktan sonra hala 0 ise (örn. görev tamamen bittiyse) başlatma.
                if (timeLeftInCurrentSessionMillis <= 0) {
                    Log.d(TAG, "Yapılandırma sonrası hala süre yok, zamanlayıcı başlatılmıyor.")
                    binding.buttonStartPause.isEnabled = false // Butonu devre dışı bırakabiliriz
                    return
                }
            } else if (timeLeftInCurrentSessionMillis <= 0) { // Görev yok ve süre yok
                Log.d(TAG, "Görev yok ve süre yok, zamanlayıcı başlatılmıyor.")
                return
            }
        }

        taskViewModel.clearPausedPomodoroState() // Zamanlayıcı başladığında duraklatılmış durumu temizle

        countDownTimer = object : CountDownTimer(timeLeftInCurrentSessionMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInCurrentSessionMillis = millisUntilFinished
                updateTimerText()
                updateProgress()
                val currentIndicatorColors = binding.progressCircular.indicatorColor
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
                val sessionMinutesFocused = TimeUnit.MILLISECONDS.toMinutes(currentPomodoroSessionDurationMillis).toInt()
                timeLeftInCurrentSessionMillis = 0L
                updateTimerText()
                updateProgress()

                binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_play)
                val taskTitle = currentTaskTitleForPomodoro ?: "Seans"
                binding.textTask.text = "'$taskTitle' seansı tamamlandı!"

                // --- LOG EKLENDİ ---
                Log.d(TAG, "onFinish: Kaydedilecek odak süresi: $sessionMinutesFocused dk, Görev ID: $currentTaskIdForPomodoro")

                currentTaskIdForPomodoro?.let { taskId ->
                    if (sessionMinutesFocused > 0) {
                        taskViewModel.recordFocusedSession(taskId, sessionMinutesFocused)
                    } else {
                        Log.w(TAG, "onFinish: Odak süresi 0 olduğu için kayıt yapılmadı.")
                    }
                }

                binding.celebrationAnimation.apply {
                    visibility = View.VISIBLE; playAnimation()
                    addAnimatorListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) { visibility = View.GONE }
                    })
                }
                binding.buttonStartPause.isEnabled = true // Kullanıcı aynı göreve tekrar başlayabilsin
            }
        }.start()

        isRunning = true
        binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_pause)
        Log.d(TAG, "Zamanlayıcı başlatıldı. Seans Süresi: ${currentPomodoroSessionDurationMillis / 1000 / 60} dk, Kalan: ${timeLeftInCurrentSessionMillis / 1000} sn")
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isRunning = false
        binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_play)
        Log.d(TAG, "Zamanlayıcı duraklatıldı. Kalan süre: $timeLeftInCurrentSessionMillis ms")

        currentTaskIdForPomodoro?.let { taskId ->
            taskViewModel.storePausedPomodoroState(taskId, timeLeftInCurrentSessionMillis, currentPomodoroSessionDurationMillis)
        }
    }

    private fun updateTimerText() {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeftInCurrentSessionMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeftInCurrentSessionMillis) - TimeUnit.MINUTES.toSeconds(minutes)
        binding.textTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun updateProgress() {
        val progress = if (currentPomodoroSessionDurationMillis > 0) {
            (timeLeftInCurrentSessionMillis * 100 / currentPomodoroSessionDurationMillis).toInt()
        } else { 0 }
        binding.progressCircular.progress = progress.coerceIn(0,100)
    }

    override fun onPause() {
        super.onPause()
        if (isRunning) {
            pauseTimer()
            Log.d(TAG, "Fragment onPause: Çalışan zamanlayıcı duraklatıldı ve durumu kaydedildi.")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
        Log.d(TAG, "onDestroyView çağrıldı.")
    }
}