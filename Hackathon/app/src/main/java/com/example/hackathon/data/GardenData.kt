package com.example.hackathon.data // Kendi paket yapınıza göre güncelleyin

import java.util.UUID

// Farklı bahçe objelerinin türlerini belirtmek için enum
enum class GardenObjectType {
    GROUND,
    PLANT,
    WALL,
    FENCE,
    FLOWER_RED,
    FLOWER_YELLOW
}

// Tüm bahçe objeleri için genel veri yapısı
data class GardenObjectData(
    val uuid: String = UUID.randomUUID().toString(),
    val type: GardenObjectType,
    var x: Float,
    var y: Float,
    var z: Float,
    var growthStage: Int? = null,
    var waterLevel: Int? = null,
    var colorHex: Int? = null,
    var rotationY: Float? = 0f // EKLENDİ: Objelerin Y eksenindeki rotasyonu
)

// Tüm bahçe durumunu içeren ana data class
data class GardenState(
    var waterDroplets: Int,
    val objects: MutableList<GardenObjectData>
)
