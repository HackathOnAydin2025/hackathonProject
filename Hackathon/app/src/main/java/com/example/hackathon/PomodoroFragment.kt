package com.example.hackathon // Kendi paket adınızı kullanın

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
import com.example.hackathon.data.Task
import com.example.hackathon.databinding.FragmentPomodoroBinding
import com.example.hackathon.tasks.TaskViewModel
import java.util.concurrent.TimeUnit

class PomodoroFragment : Fragment() {

    private var _binding: FragmentPomodoroBinding? = null
    private val binding get() = _binding!!

    private var isRunning = false
    private var countDownTimer: CountDownTimer? = null

    private val taskViewModel: TaskViewModel by activityViewModels()

    // Bu, o anki Pomodoro SEANSININ başlangıç süresini tutar.
    // Görevin toplam süresi değil, bir Pomodoro seansının süresi olmalı.
    // Şimdilik, seçilen görevin durationMinutes'ını seans süresi olarak alıyoruz.
    private var currentSessionTimeInMillis: Long = TimeUnit.MINUTES.toMillis(DEFAULT_POMODORO_SESSION_MINUTES.toLong())
    private var timeLeftInMillis: Long = currentSessionTimeInMillis

    private var colorOrange: Int = 0
    private var colorRed: Int = 0

    // Şu an üzerinde çalışılan görevin ID'si
    private var currentTaskIdForPomodoro: Int? = null

    companion object {
        // Bir Pomodoro seansının varsayılan süresi (örn: 25 dakika)
        const val DEFAULT_POMODORO_SESSION_MINUTES = 25
        private const val ONE_MINUTE_IN_MILLIS = 60000L
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

        colorOrange = ContextCompat.getColor(requireContext(), R.color.user_orange) // Renklerinizi tanımlayın
        colorRed = ContextCompat.getColor(requireContext(), R.color.md_theme_onErrorContainer) // Renklerinizi tanımlayın

        setupClickListeners()
        setupObservers()

        if (taskViewModel.selectedTaskForPomodoro.value == null) {
            updateUIForTask(null)
            resetTimerUIAndState()
        }
    }

    private fun setupClickListeners() {
        binding.buttonStartPause.setOnClickListener {
            if (isRunning) {
                pauseTimer()
            } else {
                if (timeLeftInMillis == 0L) { // Timer bittiyse veya hiç başlamadıysa
                    timeLeftInMillis = currentSessionTimeInMillis // Süreyi baştan ayarla
                    updateProgress()
                    updateTimerText()
                    binding.progressCircular.setIndicatorColor(colorOrange)
                }
                startTimer()
            }
        }
    }

    private fun setupObservers() {
        taskViewModel.selectedTaskForPomodoro.observe(viewLifecycleOwner) { task ->
            Log.d("PomodoroFragment", "Selected task changed: ${task?.title}")
            updateUIForTask(task)
            resetTimerUIAndState() // Timer'ı yeni göreve (veya varsayılana) göre resetle
        }
    }

    private fun updateUIForTask(task: Task?) {
        currentTaskIdForPomodoro = task?.id // Çalışılan görevin ID'sini sakla
        if (task != null) {
            binding.textTask.text = task.title
            // Pomodoro seans süresini görevin süresinden alıyoruz.
            // Eğer standart Pomodoro (örn: 25dk) kullanmak isterseniz, burayı değiştirin.
            currentSessionTimeInMillis = TimeUnit.MINUTES.toMillis(task.durationMinutes.toLong())
            Log.d("PomodoroFragment", "UI updated for task '${task.title}', session duration: ${task.durationMinutes}min")
        } else {
            binding.textTask.text = "Odaklanma Zamanı!"
            currentSessionTimeInMillis = TimeUnit.MINUTES.toMillis(DEFAULT_POMODORO_SESSION_MINUTES.toLong())
            Log.d("PomodoroFragment", "UI updated for default, session duration: $DEFAULT_POMODORO_SESSION_MINUTES min")
        }
    }

    private fun resetTimerUIAndState() {
        countDownTimer?.cancel()
        isRunning = false
        timeLeftInMillis = currentSessionTimeInMillis // Ayarlanan seans süresini kullan
        updateTimerText()
        binding.progressCircular.max = 100 // Progress bar 0-100 arası yüzdeyi gösterir
        updateProgress() // Progress bar'ı tam yap

        binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_play)
        // Buton arkaplanını drawable ile ayarlamak daha esnek olabilir.
        // binding.buttonStartPause.setBackgroundResource(R.drawable.rounded_button_orange)

        binding.progressCircular.setIndicatorColor(colorOrange)
        binding.celebrationAnimation.visibility = View.GONE
        binding.celebrationAnimation.cancelAnimation()
        binding.buttonStartPause.isEnabled = true
        Log.d("PomodoroFragment", "Timer UI and state reset. Session duration set to: ${timeLeftInMillis / 1000 / 60}min")
    }


    private fun startTimer() {
        if (isRunning || (timeLeftInMillis == 0L && currentSessionTimeInMillis == 0L) ) return

        if (timeLeftInMillis == 0L) { // Eğer süre bittiyse ve yeniden başlatılıyorsa
            timeLeftInMillis = currentSessionTimeInMillis
            updateTimerText()
            updateProgress() // Progress'i 100 yap
        }

        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerText()
                updateProgress()

                val currentIndicatorColors = binding.progressCircular.indicatorColor
                val currentColor = if (currentIndicatorColors.isNotEmpty()) currentIndicatorColors[0] else colorOrange

                if (millisUntilFinished <= ONE_MINUTE_IN_MILLIS) {
                    if (currentColor != colorRed) binding.progressCircular.setIndicatorColor(colorRed)
                } else {
                    if (currentColor != colorOrange) binding.progressCircular.setIndicatorColor(colorOrange)
                }
            }

            override fun onFinish() {
                Log.d("PomodoroFragment", "Timer finished for task ID: $currentTaskIdForPomodoro")
                isRunning = false
                timeLeftInMillis = 0L
                updateTimerText()
                updateProgress() // Progress'i 0 yap

                binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_play)
                binding.progressCircular.setIndicatorColor(colorOrange)

                val taskTitle = taskViewModel.selectedTaskForPomodoro.value?.title ?: "Seans"
                binding.textTask.text = "'$taskTitle' tamamlandı!"

                // YENİ: Fiilen odaklanılan süreyi kaydet
                val sessionMinutes = TimeUnit.MILLISECONDS.toMinutes(currentSessionTimeInMillis).toInt()
                currentTaskIdForPomodoro?.let { taskId ->
                    if (sessionMinutes > 0) { // Sadece pozitif süreleri kaydet
                        taskViewModel.recordFocusedSession(taskId, sessionMinutes)
                    }
                }

                // Animasyon ve seçili görevi temizleme
                binding.celebrationAnimation.apply {
                    visibility = View.VISIBLE
                    playAnimation()
                    addAnimatorListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            visibility = View.GONE
                        }
                    })
                }
                taskViewModel.clearSelectedTaskForPomodoro() // Seçili görevi temizle
                currentTaskIdForPomodoro = null // Çalışılan ID'yi sıfırla
            }
        }.start()

        isRunning = true
        binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_pause)
        Log.d("PomodoroFragment", "Timer started. Session duration: ${timeLeftInMillis / 1000 / 60}min")
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isRunning = false
        binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_play)
        Log.d("PomodoroFragment", "Timer paused.")
    }

    private fun updateTimerText() {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeftInMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeftInMillis) - TimeUnit.MINUTES.toSeconds(minutes)
        binding.textTimer.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateProgress() {
        val progress = if (currentSessionTimeInMillis > 0) {
            (timeLeftInMillis * 100 / currentSessionTimeInMillis).toInt()
        } else {
            if (timeLeftInMillis > 0) 100 else 0 // Eğer başlangıç süresi 0 ama kalan süre varsa %100
        }
        binding.progressCircular.progress = progress
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
        Log.d("PomodoroFragment", "onDestroyView called.")
    }
}
