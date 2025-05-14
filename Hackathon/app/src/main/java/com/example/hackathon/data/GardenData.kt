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
    // Gelecekte yeni obje türleri eklenebilir
}

// Tüm bahçe objeleri için genel veri yapısı
data class GardenObjectData(
    val uuid: String = UUID.randomUUID().toString(),
    val type: GardenObjectType, // Objenin türü
    var x: Float,       // Üç boyutlu dünyadaki x pozisyonu
    var y: Float,       // Üç boyutlu dünyadaki y pozisyonu
    var z: Float,       // Üç boyutlu dünyadaki z pozisyonu

    // Bitkilere özel alanlar (diğer türler için null olabilir)
    var growthStage: Int? = null,
    var waterLevel: Int? = null,

    // Zemin bloklarına özel alanlar (diğer türler için null olabilir)
    var colorHex: Int? = null
    // Diğer obje türleri için gelecekte özel alanlar eklenebilir
    // Örneğin, duvar için yükseklik, çit için bağlantı noktaları vb.
)

// Tüm bahçe durumunu içeren ana data class
data class GardenState(
    var waterDroplets: Int,
    val objects: MutableList<GardenObjectData> // Artık tek bir obje listesi var
)