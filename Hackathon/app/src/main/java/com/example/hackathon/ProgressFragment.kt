package com.example.hackathon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.hackathon.databinding.FragmentProgressBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import android.graphics.Color

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Başlık ve tarih
        binding.textTitle.text = "İlerleme Raporu"
        binding.textDate.text = "1-7 Mayıs 2025"

        // Buton
        binding.buttonBackToGarden.setOnClickListener {
            // Bahçeye dönüş işlevi
        }

        // PieChart verisi örneği
        setupPieChart()
    }

    private fun setupPieChart() {
        val entries = listOf(
            PieEntry(3f, "Gün 1"),
            PieEntry(5f, "Gün 2"),
            PieEntry(2f, "Gün 3"),
            PieEntry(4f, "Gün 4"),
            PieEntry(6f, "Gün 5"),
            PieEntry(1f, "Gün 6"),
            PieEntry(3f, "Gün 7")
        )

        val dataSet = PieDataSet(entries, "Görevler")
        dataSet.colors = listOf(
            Color.parseColor("#81C784"),
            Color.parseColor("#4DB6AC"),
            Color.parseColor("#64B5F6"),
            Color.parseColor("#9575CD"),
            Color.parseColor("#FFD54F"),
            Color.parseColor("#FF8A65"),
            Color.parseColor("#E57373")
        )

        val pieData = PieData(dataSet)
        binding.pieChartTasks.data = pieData
        binding.pieChartTasks.description.isEnabled = false
        binding.pieChartTasks.centerText = "Haftalık"
        binding.pieChartTasks.setEntryLabelColor(Color.BLACK)
        binding.pieChartTasks.invalidate() // Grafiği yenile
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

