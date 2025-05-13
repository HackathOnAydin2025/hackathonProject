package com.example.hackathon.data

import java.util.Date

data class DailyTaskSummary(
    val date: Date,
    val taskCount: Int,
    val completedTaskCount: Int, // EKLENEN ALAN
    val label: String // PieChart dilimi için etiket, örn: "12 May Pzt"
)