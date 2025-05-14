package com.example.hackathon

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
// import android.widget.ProgressBar // XML'de ID ile erişiliyor, doğrudan import gerekmeyebilir
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Paylaşılan ViewModel için
import androidx.lifecycle.Observer
// import androidx.lifecycle.ViewModelProvider // activityViewModels kullanıldığı için gereksiz
import androidx.navigation.fragment.findNavController
import com.example.hackathon.data.DailyTaskSummary
// import com.example.hackathon.data.Task // Kullanılmıyorsa kaldırılabilir
import com.example.hackathon.databinding.FragmentProgressBinding
import com.example.hackathon.progress.viewmodel.GardenViewModel // ViewModel importu
// DisplayableTreeInfo GardenViewModel içinde tanımlı olduğu için ayrıca import etmeye gerek yok,
// eğer farklı bir dosyadaysa import edilmeli. Şimdilik ViewModel içinde olduğunu varsayıyoruz.
// import com.example.hackathon.progress.viewmodel.DisplayableTreeInfo
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

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    // Paylaşılan GardenViewModel'i al
    private val gardenViewModel: GardenViewModel by activityViewModels()
    private val taskViewModel: TaskViewModel by activityViewModels()

    private lateinit var pieChart: PieChart
    private val pieChartSliceDateMap = mutableMapOf<Int, Date>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pieChart = binding.pieChartTasks
        binding.textDate.text = getWeekDateRange()
        setupBasePieChartAppearance()
        loadAndObserveWeeklyTaskSummary()

        // GardenViewModel'den 3D BAHÇE ağaç bilgilerini gözlemle
        // Önceki gardenViewModel.trees yerine gardenViewModel.gardenTreeInfo kullanılacak.
        gardenViewModel.gardenTreeInfo.observe(viewLifecycleOwner) { treeInfoList ->
            // treeInfoList, List<DisplayableTreeInfo> tipindedir.
            // DisplayableTreeInfo(name: String, count: Int)
            if (treeInfoList.isNullOrEmpty()) {
                binding.treeText.text = "Bahçede ağaç yok"
            } else {
                val treeSummary = treeInfoList.joinToString(", ") { "${it.count} ${it.name}" }
                binding.treeText.text = if (treeSummary.isNotEmpty()) treeSummary else "Bahçede ağaç yok"
            }
        }

        // GardenViewModel'den su damlası sayısını gözlemle (JSON'dan gelen GardenState'ten)
        gardenViewModel.waterDroplets.observe(viewLifecycleOwner) { waterCount ->
            Log.d("ProgressFragment", "ProgressFragment: Gözlemlenen su damlası sayısı: $waterCount")
            binding.dropText.text = "$waterCount Damla" // XML'deki ID'ye göre
        }

        // Eğer Room'dan gelen _roomTrees ve _roomDrops LiveData'larını
        // farklı bir amaç için kullanmak isterseniz, onları da gözlemleyebilirsiniz.
        // gardenViewModel.roomTrees.observe(viewLifecycleOwner) { /* ... */ }
        // gardenViewModel.roomDrops.observe(viewLifecycleOwner) { /* ... */ }


        taskViewModel.totalPlannedFocusTimeToday.observe(viewLifecycleOwner, Observer { totalPlannedMinutes ->
            val plannedMinutes = totalPlannedMinutes ?: 0
            binding.progressBarFocus.max = if (plannedMinutes > 0) plannedMinutes else 1
            updateFocusStatsText()
        })

        taskViewModel.actualFocusTimeSpentToday.observe(viewLifecycleOwner, Observer { totalActualMinutes ->
            val actualMinutes = totalActualMinutes ?: 0
            binding.progressBarFocus.progress = actualMinutes
            updateFocusStatsText()
        })

        binding.buttonBackToGarden.setOnClickListener {
            findNavController().popBackStack() // Veya GardenFragment'a özel bir action ID
        }
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
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        taskViewModel.loadWeeklyTaskSummary(calendar.time)

        taskViewModel.weeklyTaskSummary.observe(viewLifecycleOwner) { summaryList ->
            if (summaryList.isNullOrEmpty()) {
                pieChart.data = null
                pieChart.setNoDataText("Bu hafta için görev bulunmamaktadır.")
            } else {
                updatePieChartWithTaskData(summaryList)
            }
            pieChart.invalidate()
        }
    }

    private fun setupBasePieChartAppearance() {
        pieChart.description.isEnabled = false
        pieChart.isRotationEnabled = true
        pieChart.holeRadius = 40f
        pieChart.transparentCircleRadius = 45f
        pieChart.setEntryLabelColor(android.graphics.Color.BLACK)
        pieChart.setEntryLabelTextSize(11f)
        pieChart.legend.isEnabled = true
        pieChart.legend.textSize = 12f
        pieChart.legend.isWordWrapEnabled = true

        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry, h: Highlight) {
                val sliceIndex = h.x.toInt()
                pieChartSliceDateMap[sliceIndex]?.let { selectedDate ->
                    navigateToTaskListWithDate(selectedDate)
                }
            }
            override fun onNothingSelected() {}
        })
    }

    private fun updatePieChartWithTaskData(summaryList: List<DailyTaskSummary>) {
        val entries = ArrayList<PieEntry>()
        pieChartSliceDateMap.clear()
        var currentSliceIndex = 0
        summaryList.forEach { summary ->
            if (summary.taskCount > 0) {
                entries.add(PieEntry(summary.taskCount.toFloat(), summary.label))
                pieChartSliceDateMap[currentSliceIndex] = summary.date
                currentSliceIndex++
            }
        }

        if (entries.isEmpty()) {
            pieChart.data = null
            pieChart.setNoDataText("Bu hafta gösterilecek görev verisi yok.")
            pieChart.invalidate()
            return
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.setColors(*ColorTemplate.MATERIAL_COLORS)
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = android.graphics.Color.BLACK
        dataSet.sliceSpace = 2f
        dataSet.valueFormatter = DefaultValueFormatter(0)

        val pieData = PieData(dataSet)
        pieChart.data = pieData
        pieChart.animateY(1000, com.github.mikephil.charting.animation.Easing.EaseInOutQuad)
        pieChart.invalidate()
    }

    private fun navigateToTaskListWithDate(date: Date) {
        val queryDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStringForNavigation = queryDateFormat.format(date)
        Log.d("ProgressFragment", "Navigating to TaskListFragment with date: $dateStringForNavigation")
        try {
            val action = ProgressFragmentDirections.actionProgressFragmentToTaskListFragment(dateStringForNavigation)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Log.e("ProgressFragment", "Navigasyon hatası: ${e.message}")
            Toast.makeText(context, "Sayfa bulunamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getWeekDateRange(): String {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val startOfWeek = calendar.time
        calendar.add(Calendar.DATE, 6)
        val endOfWeek = calendar.time
        val dayFormat = SimpleDateFormat("d", Locale("tr"))
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("tr")) // Yıl için 'yyyy'
        val startDay = dayFormat.format(startOfWeek)
        val endDay = dayFormat.format(endOfWeek)
        val monthYearString = monthYearFormat.format(startOfWeek)
        return "$startDay - $endDay $monthYearString"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}