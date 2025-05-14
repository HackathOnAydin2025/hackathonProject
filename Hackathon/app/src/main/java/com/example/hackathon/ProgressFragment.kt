package com.example.hackathon // Kendi paket yapınıza göre güncelleyin

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.example.hackathon.data.DailyTaskSummary
import com.example.hackathon.databinding.FragmentProgressBinding
import com.example.hackathon.progress.viewmodel.GardenViewModel
import com.example.hackathon.tasks.TaskViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.DefaultValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.*
import com.github.mikephil.charting.components.Legend // Legend importu

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    private val gardenViewModel: GardenViewModel by activityViewModels()
    private val taskViewModel: TaskViewModel by activityViewModels()

    private lateinit var pieChart: PieChart
    private val pieChartSliceDateMap = mutableMapOf<Int, Date>() // PieChart dilim indeksi ile tarihi eşleştirmek için
    private val TAG_PROGRESS = "ProgressFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        Log.d(TAG_PROGRESS, "onCreateView çağrıldı.")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG_PROGRESS, "onViewCreated BAŞLADI.")
        pieChart = binding.pieChartTasks
        binding.textDate.text = getWeekDateRange() // Haftalık tarih aralığını ayarla

        setupBasePieChartAppearance()    // PieChart'ın temel görünüm ayarları
        loadAndObserveWeeklyTaskSummary() // ViewModel'den haftalık görev özetini yükle ve gözlemle

        // Bahçedeki ağaç bilgisini gözlemle
        gardenViewModel.gardenTreeInfo.observe(viewLifecycleOwner) { treeInfoList ->
            val treeSummary = if (treeInfoList.isNullOrEmpty()) {
                "Bahçede ağaç yok"
            } else {
                treeInfoList.joinToString(", ") { "${it.count} ${it.name}" }.ifEmpty { "Bahçede ağaç yok" }
            }
            binding.treeText.text = treeSummary
        }

        // Su damlası sayısını gözlemle
        gardenViewModel.waterDroplets.observe(viewLifecycleOwner) { waterCount ->
            Log.d(TAG_PROGRESS, "ProgressFragment: Gözlemlenen su damlası sayısı: $waterCount")
            binding.dropText.text = "$waterCount Damla"
        }

        // Bugün için planlanan toplam odak süresini gözlemle
        taskViewModel.totalPlannedFocusTimeToday.observe(viewLifecycleOwner, Observer { totalPlannedMinutes ->
            binding.progressBarFocus.max = (totalPlannedMinutes ?: 0).coerceAtLeast(1) // Max 0 olmamalı
            updateFocusStatsText()
        })

        // Bugün harcanan gerçek odak süresini gözlemle
        taskViewModel.actualFocusTimeSpentToday.observe(viewLifecycleOwner, Observer { totalActualMinutes ->
            binding.progressBarFocus.progress = totalActualMinutes ?: 0
            updateFocusStatsText()
        })

        // Bahçeye geri dön butonu
        binding.buttonBackToGarden.setOnClickListener {
            try {
                // NavGraph'ınızda GardenFragment'a bir action tanımlıysa onu kullanın.
                // findNavController().navigate(R.id.action_progressFragment_to_gardenFragment)
                // Eğer sadece bir önceki ekrana dönmek yeterliyse popBackStack() kullanılabilir.
                if (!findNavController().popBackStack()) {
                    // Eğer popBackStack false dönerse (yığında başka fragment yoksa),
                    // ana ekrana (muhtemelen TaskListFragment) gitmeyi deneyebiliriz.
                    // Bu kısım uygulamanızın navigasyon akışına göre düzenlenmeli.
                    // findNavController().navigate(R.id.action_global_taskListFragment) // Örnek
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG_PROGRESS, "popBackStack yapılamadı: ${e.message}")
                Toast.makeText(context, "Önceki ekrana dönülemedi.", Toast.LENGTH_SHORT).show()
            }
        }
        Log.d(TAG_PROGRESS, "onViewCreated TAMAMLANDI.")
    }

    private fun updateFocusStatsText() {
        val actualMinutes = taskViewModel.actualFocusTimeSpentToday.value ?: 0
        val plannedMinutes = taskViewModel.totalPlannedFocusTimeToday.value ?: 0
        binding.textViewFocusStats.text = if (plannedMinutes > 0) {
            "Bugün: $actualMinutes dk / $plannedMinutes dk odaklanıldı"
        } else {
            if (actualMinutes > 0) "Bugün: $actualMinutes dk odaklanıldı (Plansız)"
            else "Bugün odaklanma kaydı yok"
        }
        updateMotivationText(actualMinutes, plannedMinutes)
    }

    private fun updateMotivationText(actualMinutes: Int, plannedMinutes: Int) {
        binding.textViewMotivation.text = when {
            plannedMinutes == 0 && actualMinutes == 0 -> "Bugün için bir odak planı yapmaya ne dersin? 🌱"
            actualMinutes == 0 && plannedMinutes > 0 -> "İlk adımını at, harika şeyler başarabilirsin! ✨"
            actualMinutes > 0 && actualMinutes < plannedMinutes -> "Harika gidiyorsun, devam et! 🚀"
            actualMinutes > 0 && actualMinutes >= plannedMinutes && plannedMinutes > 0 -> "Tebrikler! Bugünkü hedefine ulaştın! 🎉"
            actualMinutes > 0 && plannedMinutes == 0 -> "Plansız da olsa odaklanmak harika! 💪"
            else -> "Odaklanmaya devam et, potansiyelin sınırsız! 💪"
        }
    }

    private fun loadAndObserveWeeklyTaskSummary() {
        val calendar = Calendar.getInstance()
        // Haftanın Pazartesi günü ile başlamasını sağla
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        // Saat, dakika, saniye ve milisaniyeyi sıfırla (sadece tarih karşılaştırması için)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val sdfLog = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault())
        Log.d(TAG_PROGRESS, "Haftalık özet için ViewModel'e gönderilen başlangıç tarihi: ${sdfLog.format(calendar.time)}")

        taskViewModel.loadWeeklyTaskSummary(calendar.time) // ViewModel'deki fonksiyonu çağır

        taskViewModel.weeklyTaskSummary.observe(viewLifecycleOwner) { summaryList ->
            Log.d(TAG_PROGRESS, "weeklyTaskSummary observer tetiklendi. Liste boyutu: ${summaryList?.size ?: "null"}")
            if (summaryList.isNullOrEmpty()) {
                pieChart.data = null // Veri yoksa grafiği temizle
                pieChart.setNoDataText("Bu hafta için görev verisi bulunmamaktadır.")
                Log.d(TAG_PROGRESS, "PieChart için veri yok veya boş, 'veri yok' metni ayarlandı.")
            } else {
                Log.d(TAG_PROGRESS, "PieChart için veri geldi, grafik güncelleniyor.")
                updatePieChartWithTaskData(summaryList)
            }
            pieChart.invalidate() // Grafiği her durumda yenile (veri olsun veya olmasın)
        }
    }

    private fun setupBasePieChartAppearance() {
        pieChart.description.isEnabled = false
        pieChart.isRotationEnabled = true
        pieChart.holeRadius = 45f
        pieChart.transparentCircleRadius = 50f
        pieChart.setEntryLabelColor(android.graphics.Color.DKGRAY)
        pieChart.setEntryLabelTextSize(10f)
        pieChart.setUsePercentValues(false) // Gerçek değerleri göster
        pieChart.setDrawEntryLabels(true) // Dilim etiketlerini göster (örn: "12 Pzt")

        // Legend (Açıklama) ayarları
        val legend = pieChart.legend
        legend.isEnabled = true
        legend.textSize = 11f
        legend.isWordWrapEnabled = true
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false) // Grafiğin içine çizme

        // Tıklama dinleyicisi
        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (e == null || h == null) return
                val pieEntry = e as? PieEntry
                val sliceIndex = h.x.toInt() // Bu, PieDataSet'teki entries listesinin indeksidir
                Log.d(TAG_PROGRESS, "PieChart dilimi seçildi: Etiket='${pieEntry?.label}', Değer='${e.y}', Verilen İndeks=${sliceIndex}")

                pieChartSliceDateMap[sliceIndex]?.let { selectedDate ->
                    navigateToTaskListWithDate(selectedDate)
                } ?: run {
                    Log.w(TAG_PROGRESS, "Seçilen dilim için tarih bulunamadı. İndeks: $sliceIndex, Map: $pieChartSliceDateMap")
                }
            }
            override fun onNothingSelected() {}
        })
        Log.d(TAG_PROGRESS, "PieChart temel görünüm ayarları yapıldı.")
    }

    private fun updatePieChartWithTaskData(summaryList: List<DailyTaskSummary>) {
        pieChartSliceDateMap.clear() // Önceki eşleşmeleri temizle
        val entries = ArrayList<PieEntry>()

        // Sadece görevi olan günler için dilim ekle
        val summariesWithTasks = summaryList.filter { it.taskCount > 0 }

        if (summariesWithTasks.isEmpty()) {
            pieChart.data = null
            pieChart.setNoDataText("Bu hafta gösterilecek görev verisi yok.")
            Log.d(TAG_PROGRESS, "PieChart için hiç giriş oluşturulamadı (tüm günlerde görev sayısı 0).")
            // pieChart.invalidate() zaten observer'da çağrılıyor.
            return
        }

        summariesWithTasks.forEachIndexed { index, summary ->
            // PieEntry(değer, etiket)
            entries.add(PieEntry(summary.taskCount.toFloat(), summary.label))
            // Harita için, entries listesine eklenme sırasına göre indeksi kullan
            pieChartSliceDateMap[index] = summary.date
            Log.d(TAG_PROGRESS, "PieChart'a eklendi: Değer=${summary.taskCount}, Etiket='${summary.label}', İndeks=$index, Tarih=${summary.date}")
        }

        val dataSet = PieDataSet(entries, "") // Başlık boş bırakılabilir, legend zaten etiketleri gösterir
        dataSet.setColors(*ColorTemplate.MATERIAL_COLORS) // Renk şablonu
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = android.graphics.Color.BLACK
        dataSet.sliceSpace = 3f // Dilimler arası boşluk
        dataSet.valueFormatter = DefaultValueFormatter(0) // Değerleri tam sayı olarak göster (örn: 3, 5)

        val pieData = PieData(dataSet)
        pieChart.data = pieData
        pieChart.centerText = "Haftalık Dağılım" // Ortadaki metin
        pieChart.setCenterTextSize(16f)

        pieChart.animateY(1200, com.github.mikephil.charting.animation.Easing.EaseInOutQuad)
        Log.d(TAG_PROGRESS, "PieChart verisi ayarlandı ve animasyon başlatıldı. Giriş sayısı: ${entries.size}")
        // pieChart.invalidate() zaten observer'da çağrılıyor.
    }

    private fun navigateToTaskListWithDate(date: Date) {
        val queryDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStringForNavigation = queryDateFormat.format(date)
        Log.d(TAG_PROGRESS, "TaskListFragment'a şu tarihle gidiliyor: $dateStringForNavigation")
        try {
            val action = ProgressFragmentDirections.actionProgressFragmentToTaskListFragment(dateStringForNavigation)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Log.e(TAG_PROGRESS, "Navigasyon hatası (navigateToTaskListWithDate): ${e.message}")
            Toast.makeText(context, "Görev listesi sayfasına gidilemedi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getWeekDateRange(): String {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val startOfWeek = calendar.time
        calendar.add(Calendar.DATE, 6)
        val endOfWeek = calendar.time

        val dayFormat = SimpleDateFormat("d", Locale("tr")) // Gün (sadece sayı)
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("tr")) // Ay Adı Yıl (örn: Mayıs 2024)

        val startDay = dayFormat.format(startOfWeek)
        val endDay = dayFormat.format(endOfWeek)
        val monthYearString = monthYearFormat.format(startOfWeek) // Başlangıç ayını kullan

        return "$startDay - $endDay $monthYearString"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pieChart.setOnChartValueSelectedListener(null) // Listener'ı temizle
        _binding = null // Bellek sızıntılarını önlemek için
        Log.d(TAG_PROGRESS, "onDestroyView çağrıldı.")
    }
}
