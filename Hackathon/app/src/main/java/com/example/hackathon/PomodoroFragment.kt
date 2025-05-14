package com.example.hackathon // Ana paket adınız

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.media.MediaPlayer // MediaPlayer importu eklendi
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast // Toast importu eklendi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController // Navigasyon için
import com.example.hackathon.R
import com.example.hackathon.data.Task
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

    // --- SES ÇALAR İÇİN DEĞİŞKEN ---
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        const val DEFAULT_POMODORO_SESSION_MINUTES = 25
        private const val ONE_MINUTE_IN_MILLIS = 60000L
        private const val TAG = "PomodoroFragment"
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

    // --- SES ÇALAR METOTLARI ---
    private fun initMediaPlayer() {
        if (mediaPlayer == null && context != null) {
            mediaPlayer = MediaPlayer.create(requireContext(), R.raw.soft_brown_noise) // Ses dosyanızın adı
            mediaPlayer?.isLooping = true // Sesi döngüye al
            mediaPlayer?.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer Hatası: what $what, extra $extra")
                releaseMediaPlayer() // Hata durumunda kaynakları serbest bırak
                true // Hatayı ele aldığımızı belirtir
            }
            Log.d(TAG, "MediaPlayer başlatıldı (soft_brown_noise).")
        }
    }

    private fun playSound() {
        initMediaPlayer() // MediaPlayer'ın başlatıldığından emin ol
        try {
            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                Log.d(TAG, "Ses çalmaya başladı.")
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaPlayer başlatılırken hata: ${e.message}", e)
            releaseMediaPlayer() // Hata durumunda kaynakları serbest bırak
        }
    }

    private fun pauseSound() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            Log.d(TAG, "Ses duraklatıldı.")
        }
    }

    private fun stopAndReleaseMediaPlayer() {
        try {
            mediaPlayer?.stop() // Önce durdur (eğer çalıyorsa)
            mediaPlayer?.release() // Kaynakları serbest bırak
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer durdurulurken/serbest bırakılırken hata: ${e.message}", e)
        } finally {
            mediaPlayer = null // Referansı temizle
            Log.d(TAG, "MediaPlayer durduruldu ve kaynaklar serbest bırakıldı.")
        }
    }

    private fun releaseMediaPlayer() { // Sadece release için ayrı bir metot
        mediaPlayer?.release()
        mediaPlayer = null
        Log.d(TAG, "MediaPlayer kaynakları serbest bırakıldı (releaseMediaPlayer).")
    }
    // --- SES ÇALAR METOTLARI SONU ---

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
                startTimer() // Bu metot içinde playSound() çağrılacak
            }
        }
        // XML'inizde buttonStopPomodoro ID'li bir buton varsa bu listener'ı ekleyin.
        // binding.buttonStopPomodoro.setOnClickListener { stopPomodoroSession() }
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
        pauseSound() // Yeni görev yapılandırılırken veya restore edilirken sesi duraklat

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
        binding.buttonStartPause.setIconResource(android.R.drawable.ic_media_play)
    }

    private fun configureTimerForTask(task: Task) {
        val totalPlannedMinutes = task.durationMinutes
        val alreadyFocusedMinutes = task.actualFocusedMinutes
        var remainingMinutesForTask = totalPlannedMinutes - alreadyFocusedMinutes

        if (remainingMinutesForTask <= 0 && totalPlannedMinutes > 0) {
            remainingMinutesForTask = DEFAULT_POMODORO_SESSION_MINUTES
        } else if (totalPlannedMinutes == 0 || remainingMinutesForTask <= 0) {
            remainingMinutesForTask = DEFAULT_POMODORO_SESSION_MINUTES
        }

        val sessionMinutesToSet = min(DEFAULT_POMODORO_SESSION_MINUTES, remainingMinutesForTask.coerceAtLeast(1))
        currentPomodoroSessionDurationMillis = TimeUnit.MINUTES.toMillis(sessionMinutesToSet.toLong())
        timeLeftInCurrentSessionMillis = currentPomodoroSessionDurationMillis
        binding.buttonStartPause.isEnabled = true
        Log.d(TAG, "Yeni Pomodoro seansı ayarlandı: ${task.title} için $sessionMinutesToSet dk")
    }

    private fun resetTimerToDefaultSession() {
        countDownTimer?.cancel()
        isRunning = false
        pauseSound() // Zamanlayıcı sıfırlanırken sesi duraklat

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
        binding.buttonStartPause.setIconResource(android.R.drawable.ic_media_play)
        binding.buttonStartPause.isEnabled = true
        taskViewModel.clearPausedPomodoroState()
        Log.d(TAG, "Zamanlayıcı varsayılan seansa sıfırlandı: $DEFAULT_POMODORO_SESSION_MINUTES dk")
    }

    private fun startTimer() {
        if (timeLeftInCurrentSessionMillis <= 0 && !isRunning) {
            if (currentTaskIdForPomodoro != null) {
                taskViewModel.selectedTaskForPomodoro.value?.let {
                    configureTimerForTask(it)
                    if (timeLeftInCurrentSessionMillis <= 0) {
                        binding.buttonStartPause.isEnabled = false
                        Log.w(TAG, "Görev için yapılandırma sonrası süre hala sıfır. Başlatılmıyor.")
                        return
                    }
                    binding.buttonStartPause.isEnabled = true
                }
            } else {
                // Eğer görev yoksa ve süre sıfırsa, varsayılan seansı resetleyip başlatmaya çalışabiliriz
                // veya kullanıcıya görev seçmesini söyleyebiliriz.
                // resetTimerToDefaultSession() // Zaten play'e basınca bu mantık çalışıyor
                // if (timeLeftInCurrentSessionMillis <= 0) { // Hala sıfırsa
                Toast.makeText(context, "Lütfen bir görev seçin veya seansı yapılandırın.", Toast.LENGTH_SHORT).show()
                return
                // }
            }
        }
        if (isRunning) {
            Log.d(TAG, "Zamanlayıcı zaten çalışıyor.")
            return
        }

        taskViewModel.clearPausedPomodoroState()
        playSound() // Zamanlayıcı başladığında sesi çal

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
                pauseSound() // Zamanlayıcı bittiğinde sesi duraklat

                val sessionMinutesFocused = TimeUnit.MILLISECONDS.toMinutes(currentPomodoroSessionDurationMillis).toInt()
                timeLeftInCurrentSessionMillis = 0L
                updateTimerText()
                updateProgress()

                binding.buttonStartPause.setIconResource(android.R.drawable.ic_media_play)
                val taskTitle = currentTaskTitleForPomodoro ?: "Seans"
                binding.textTask.text = "'$taskTitle' seansı tamamlandı!"

                currentTaskIdForPomodoro?.let { taskId ->
                    if (sessionMinutesFocused > 0) {
                        taskViewModel.recordFocusedSession(taskId, sessionMinutesFocused)
                    }
                }
                binding.celebrationAnimation.apply {
                    visibility = View.VISIBLE
                    playAnimation()
                    addAnimatorListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            visibility = View.GONE
                        }
                    })
                }
                binding.buttonStartPause.isEnabled = true
                // Otomatik olarak bir sonraki ekrana gitmek veya seçili görevi temizlemek
                // taskViewModel.clearSelectedTaskForPomodoro() // Bu, observer'ı tetikler ve UI'ı sıfırlar
                // if(isAdded) findNavController().popBackStack() // Ya da geri git
            }
        }.start()
        isRunning = true
        binding.buttonStartPause.setIconResource(android.R.drawable.ic_media_pause)
        Log.d(TAG, "Zamanlayıcı başlatıldı.")
    }

    private fun pauseTimer() {
        if (!isRunning) return // Zaten duraklatılmışsa bir şey yapma
        countDownTimer?.cancel()
        isRunning = false
        pauseSound() // Zamanlayıcı duraklatıldığında sesi duraklat
        binding.buttonStartPause.setIconResource(android.R.drawable.ic_media_play)
        Log.d(TAG, "Zamanlayıcı duraklatıldı. Kalan süre: $timeLeftInCurrentSessionMillis ms")
        currentTaskIdForPomodoro?.let { taskId ->
            taskViewModel.storePausedPomodoroState(taskId, timeLeftInCurrentSessionMillis, currentPomodoroSessionDurationMillis)
        }
    }

    private fun updateTimerText() {
        if (_binding == null) return
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeftInCurrentSessionMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeftInCurrentSessionMillis) - TimeUnit.MINUTES.toSeconds(minutes)
        binding.textTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun updateProgress() {
        if (_binding == null) return
        val progress = if (currentPomodoroSessionDurationMillis > 0) {
            val elapsedMillis = currentPomodoroSessionDurationMillis - timeLeftInCurrentSessionMillis
            (elapsedMillis * 100 / currentPomodoroSessionDurationMillis).toInt()
        } else { 100 }
        binding.progressCircular.progress = progress.coerceIn(0, 100)
    }

    override fun onPause() {
        super.onPause()
        if (isRunning) {
            pauseTimer() // Bu metot sesi de duraklatacak ve durumu kaydedecek
            Log.d(TAG, "Fragment onPause: Çalışan zamanlayıcı duraklatıldı ve durumu kaydedildi.")
        }
    }

    override fun onResume() {
        super.onResume()
        // Eğer bir görev seçiliyse ve duraklatılmış bir durum varsa, UI'ı ve timer'ı ona göre ayarla.
        // Bu mantık onViewCreated ve setupObservers içinde zaten ele alınıyor.
        // Eğer timer onPause'da durdurulmuşsa ve kullanıcı geri geldiğinde otomatik devam etmesi
        // isteniyorsa, burada ek bir kontrol ve startTimer() çağrısı gerekebilir.
        // Ancak mevcut durumda kullanıcı manuel olarak play'e basmalı.
        Log.d(TAG, "Fragment onResume.")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        countDownTimer = null
        stopAndReleaseMediaPlayer() // MediaPlayer kaynaklarını serbest bırak
        _binding = null
        Log.d(TAG, "onDestroyView çağrıldı, MediaPlayer serbest bırakıldı.")
    }
}