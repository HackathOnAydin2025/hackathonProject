package com.example.hackathon

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController // Navigasyon için import
import com.example.hackathon.data.DailyTaskSummary
import com.example.hackathon.data.Task // Task importu gereksizse kaldırılabilir, dialog kalktı
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
// MaterialAlertDialogBuilder importu artık gerekmeyebilir, dialog kalktı
// import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class ProgressFragment : Fragment() {

    private lateinit var binding: FragmentProgressBinding
    private val gardenViewModel: GardenViewModel by lazy {
        ViewModelProvider(this).get(GardenViewModel::class.java)
    }
    private val taskViewModel: TaskViewModel by activityViewModels()

    private lateinit var pieChart: PieChart
    private val pieChartSliceDateMap = mutableMapOf<Int, Date>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pieChart = binding.pieChartTasks
        binding.textDate.text = getWeekDateRange()
        setupBasePieChartAppearance()
        loadAndObserveWeeklyTaskSummary()

        gardenViewModel.loadTrees()
        gardenViewModel.loadDrops()

        gardenViewModel.trees.observe(viewLifecycleOwner) { trees ->
            val treeText = trees?.joinToString { "${it.count} ${it.name}" } ?: "Ağaç yok"
            binding.treeText.text = treeText
        }

        gardenViewModel.drops.observe(viewLifecycleOwner) { drops ->
            val dropText = drops?.joinToString { "${it.count} ${it.name}" } ?: "Damla yok"
            binding.dropText.text = dropText
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
                    navigateToTaskListWithDate(selectedDate) // Dialog yerine navigasyon fonksiyonu çağrılacak
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

        val dataSet = PieDataSet(entries, "Haftalık Görev Dağılımı")
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

    // ESKİ showTasksForSelectedDate METODU YERİNE BU KULLANILACAK:
    private fun navigateToTaskListWithDate(date: Date) {
        val queryDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStringForNavigation = queryDateFormat.format(date)

        Log.d("ProgressFragment", "Navigating to TaskListFragment with date: $dateStringForNavigation")

        // NavController ile TaskListFragment'a git ve tarihi argüman olarak gönder
        // action_progressFragment_to_taskListFragment ID'si nav_graph.xml'deki action ID'nizle eşleşmeli
        val action = ProgressFragmentDirections.progressToTaskList(dateStringForNavigation)
        findNavController().navigate(action)
    }

    private fun updateFocusProgress(focusMinutes: Int) {
        val progressBar: ProgressBar = binding.progressBarFocus
        val maxFocusTime = 120
        val progress = if (maxFocusTime > 0) {
            (focusMinutes.toFloat() / maxFocusTime * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        progressBar.progress = progress
    }

    private fun getWeekDateRange(): String {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val startOfWeek = calendar.time
        calendar.add(Calendar.DATE, 6)
        val endOfWeek = calendar.time

        val dayFormat = SimpleDateFormat("d", Locale("tr"))
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("tr")) // Yıl için 'yyyy' kullanıldı

        val startDay = dayFormat.format(startOfWeek)
        val endDay = dayFormat.format(endOfWeek)
        val monthYearString = monthYearFormat.format(startOfWeek)

        return "$startDay - $endDay $monthYearString"
    }
}
