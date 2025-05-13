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
import androidx.fragment.app.activityViewModels // Paylaşılan ViewModel için
import com.example.hackathon.data.Task // Task modelinizi import edin
import com.example.hackathon.databinding.FragmentPomodoroBinding
import com.example.hackathon.tasks.TaskViewModel // ViewModel'ınızı import edin
import java.util.concurrent.TimeUnit

class PomodoroFragment : Fragment() {

    private var _binding: FragmentPomodoroBinding? = null
    private val binding get() = _binding!!

    private var isRunning = false
    private var countDownTimer: CountDownTimer? = null

    // Paylaşılan TaskViewModel'ı alıyoruz
    private val taskViewModel: TaskViewModel by activityViewModels()

    private var currentInitialTimeInMillis: Long = TimeUnit.MINUTES.toMillis(DEFAULT_POMODORO_DURATION_MINUTES.toLong())
    private var timeLeftInMillis: Long = currentInitialTimeInMillis

    private var colorOrange: Int = 0
    private var colorRed: Int = 0

    companion object {
        const val DEFAULT_POMODORO_DURATION_MINUTES = 25
        private const val ONE_MINUTE_IN_MILLIS = 60000L // 1 dakika = 60 * 1000 milisaniye
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

        // Fragment ilk oluşturulduğunda veya seçili bir görev yoksa varsayılan durumu yükle
        if (taskViewModel.selectedTaskForPomodoro.value == null) {
            updateUIForTask(null) // Varsayılan başlık ve süre
            resetTimerStateOnlyUI() // Sadece timer UI'ını resetle
        }
    }

    private fun setupClickListeners() {
        binding.buttonStartPause.setOnClickListener {
            if (isRunning) {
                pauseTimer()
            } else {
                // Eğer timer sıfırsa (bitmiş veya hiç başlamamışsa), süreyi baştan ayarla
                // Bu, currentInitialTimeInMillis'in doğru ayarlandığını varsayar.
                if (timeLeftInMillis == 0L) {
                    timeLeftInMillis = currentInitialTimeInMillis
                    updateProgress() // Progress bar'ı tam yap
                    updateTimerText() // Süre yazısını güncelle
                    binding.progressCircular.setIndicatorColor(colorOrange) // Rengi turuncuya döndür
                }
                startTimer()
            }
        }
    }

    private fun setupObservers() {
        taskViewModel.selectedTaskForPomodoro.observe(viewLifecycleOwner) { task ->
            Log.d("PomodoroFragment", "Seçili görev değişti: ${task?.title}")
            updateUIForTask(task) // Görev başlığını ve süreyi güncelle
            resetTimerStateOnlyUI()    // Timer'ı yeni süreye göre resetle
        }
    }

    // Göreve özel UI elemanlarını (başlık) ve timer için başlangıç süresini günceller
    private fun updateUIForTask(task: Task?) {
        if (task != null) {
            binding.textTask.text = task.title // Görev başlığını göster
            currentInitialTimeInMillis = TimeUnit.MINUTES.toMillis(task.durationMinutes.toLong())
            Log.d("PomodoroFragment", "UI güncellendi: Görev '${task.title}', Süre: ${task.durationMinutes}dk")
        } else {
            binding.textTask.text = "Odaklanma Zamanı!" // Varsayılan başlık
            currentInitialTimeInMillis = TimeUnit.MINUTES.toMillis(DEFAULT_POMODORO_DURATION_MINUTES.toLong())
            Log.d("PomodoroFragment", "UI güncellendi: Varsayılan görev, Süre: $DEFAULT_POMODORO_DURATION_MINUTES dk")
        }
        // Bu fonksiyon timer'ı başlatmaz, sadece UI ve süre değişkenlerini ayarlar.
    }

    // Bu fonksiyon sadece timer'ı ve timer ile doğrudan ilgili UI elemanlarını resetler.
    // currentInitialTimeInMillis'in updateUIForTask tarafından ayarlandığını varsayar.
    private fun resetTimerStateOnlyUI() {
        countDownTimer?.cancel()
        isRunning = false
        timeLeftInMillis = currentInitialTimeInMillis // ViewModel'dan veya varsayılandan gelen SÜREYİ KULLAN
        updateTimerText()
        binding.progressCircular.max = 100
        updateProgress() // Progress bar'ı güncelle (tam dolu olmalı)

        binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_play)
        binding.buttonStartPause.setBackgroundResource(R.drawable.rounded_button_orange)

        binding.progressCircular.setIndicatorColor(colorOrange) // Daire rengini başlangıçta turuncu yap

        binding.celebrationAnimation.visibility = View.GONE
        binding.celebrationAnimation.cancelAnimation()
        binding.buttonStartPause.isEnabled = true
        Log.d("PomodoroFragment", "Timer state UI resetlendi. Ayarlanan süre: ${timeLeftInMillis / 1000 / 60}dk")
    }


    private fun startTimer() {
        // Eğer timer zaten çalışıyorsa veya süre sıfırsa (ve resetlenmemişse) başlatma
        if (isRunning || timeLeftInMillis == 0L && currentInitialTimeInMillis == 0L) {
            Log.d("PomodoroFragment", "Timer zaten çalışıyor veya süre sıfır, başlatılmadı.")
            return
        }
        // Eğer süre bittiyse ve tekrar başlatılıyorsa, süreyi baştan ayarla
        if (timeLeftInMillis == 0L) {
            timeLeftInMillis = currentInitialTimeInMillis
            updateTimerText()
            updateProgress()
        }


        // Timer başlarken dairenin rengini turuncu yap (eğer kırmızıda kalmışsa)
        // Ancak, eğer son 1dk içindeysek ve devam ediyorsak kırmızı kalmalı.
        // Bu yüzden bu kontrolü onTick'e bırakmak daha iyi.
        // binding.progressCircular.setIndicatorColor(colorOrange) // Şimdilik kaldırıldı, onTick yönetecek

        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerText()
                updateProgress()

                val currentIndicatorColors = binding.progressCircular.indicatorColor
                val currentColor = if (currentIndicatorColors.isNotEmpty()) currentIndicatorColors[0] else colorOrange

                if (millisUntilFinished <= ONE_MINUTE_IN_MILLIS) {
                    if (currentColor != colorRed) {
                        binding.progressCircular.setIndicatorColor(colorRed)
                        Log.d("PomodoroFragment", "Daire kırmızıya döndü: Son 1 dakika!")
                    }
                } else {
                    if (currentColor != colorOrange) {
                        binding.progressCircular.setIndicatorColor(colorOrange)
                        Log.d("PomodoroFragment", "Daire turuncuya döndü.")
                    }
                }
            }

            override fun onFinish() {
                Log.d("PomodoroFragment", "onFinish metodu çağrıldı!")
                isRunning = false
                timeLeftInMillis = 0L // Sürenin bittiğini netleştir
                updateTimerText()
                updateProgress() // Progress'i 0 yap

                binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_play)
                binding.textTask.text = "'${binding.textTask.text}' tamamlandı!" // Mevcut görev başlığını kullanarak

                binding.progressCircular.setIndicatorColor(colorOrange) // Daireyi tekrar turuncu yap

                // Seçili Pomodoro görevini ViewModel'dan temizle
                taskViewModel.clearSelectedTaskForPomodoro()
                Log.d("PomodoroFragment", "Pomodoro bitti, seçili görev temizlendi.")


                binding.celebrationAnimation.apply {
                    visibility = View.VISIBLE
                    playAnimation()
                    addAnimatorListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            visibility = View.GONE
                        }
                    })
                }
            }
        }.start()

        isRunning = true
        binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_pause)
        // binding.textTask.text kısmı updateUIForTask ile ayarlanıyor.
        Log.d("PomodoroFragment", "Timer başlatıldı. Süre: ${timeLeftInMillis / 1000 / 60}dk")
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isRunning = false
        binding.buttonStartPause.setImageResource(android.R.drawable.ic_media_play)
        // Duraklatıldığında görev metni aynı kalır.
        Log.d("PomodoroFragment", "Timer duraklatıldı.")
    }

    private fun updateTimerText() {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeftInMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeftInMillis) -
                TimeUnit.MINUTES.toSeconds(minutes)
        binding.textTimer.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateProgress() {
        val progress = if (currentInitialTimeInMillis > 0) {
            (timeLeftInMillis * 100 / currentInitialTimeInMillis).toInt()
        } else {
            0
        }
        binding.progressCircular.progress = progress
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
        Log.d("PomodoroFragment", "onDestroyView çağrıldı.")
    }
}