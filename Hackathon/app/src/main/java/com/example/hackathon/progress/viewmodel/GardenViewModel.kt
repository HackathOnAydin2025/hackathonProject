package com.example.hackathon.progress.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.hackathon.data.AppDatabase
import com.example.hackathon.progress.dao.DropDao
import com.example.hackathon.progress.dao.TreeDao
import com.example.hackathon.progress.entity.Drop
import com.example.hackathon.progress.entity.Tree
import com.example.hackathon.data.GardenObjectData
import com.example.hackathon.data.GardenObjectType
import com.example.hackathon.data.GardenState
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.Locale
import java.util.UUID

// ProgressFragment'ta ağaç bilgisini göstermek için basit bir data class
data class DisplayableTreeInfo(val name: String, val count: Int)

class GardenViewModel(application: Application) : AndroidViewModel(application) {
    private val treeDao: TreeDao
    private val dropDao: DropDao

    // --- 3D Bahçe Durumu (JSON ile saklanır) ---
    private val _gardenState = MutableLiveData<GardenState>()
    val gardenState: LiveData<GardenState> = _gardenState

    // Ana "para birimi" olan su damlaları için LiveData (gardenState'ten türetilir)
    private val _waterDroplets = MutableLiveData<Int>()
    val waterDroplets: LiveData<Int> = _waterDroplets

    // --- ProgressFragment için Room'dan gelen veriler (Mevcut yapınız) ---
    private val _roomTrees = MutableLiveData<List<Tree>>() // Room'dan gelen ağaçlar için ayrı LiveData
    val roomTrees: LiveData<List<Tree>> = _roomTrees

    private val _roomDrops = MutableLiveData<List<Drop>>()
    val roomDrops: LiveData<List<Drop>> = _roomDrops

    // --- ProgressFragment için 3D Bahçeden Türetilen Ağaç Bilgisi ---
    private val _gardenTreeInfo = MutableLiveData<List<DisplayableTreeInfo>>()
    val gardenTreeInfo: LiveData<List<DisplayableTreeInfo>> = _gardenTreeInfo


    private val gson = Gson()
    private val GARDEN_STATE_FILENAME = "garden_state_v6_unified.json"
    private val TAG = "GardenViewModel"

    object UserColorsJs {
        const val light_green = 0xA4B465
        const val dirt_brown = 0x964B00
    }

    init {
        val database = AppDatabase.getDatabase(application)
        treeDao = database.treeDao()
        dropDao = database.dropDao()

        loadGardenStateFromFile() // Bu fonksiyon içinde _gardenTreeInfo da güncellenecek
        loadTreesFromDb()         // Room ağaçlarını yükle (belki farklı bir amaç için)
        loadDropsFromDb()
    }

    private fun updateGardenTreeInfoFromState(currentState: GardenState?) {
        if (currentState == null) {
            _gardenTreeInfo.postValue(emptyList())
            return
        }
        val plantsIn3DGarden = currentState.objects.filter { it.type == GardenObjectType.PLANT }

        val fidanCount = plantsIn3DGarden.count { (it.growthStage ?: 0) == 0 }
        val kucukAgacCount = plantsIn3DGarden.count { (it.growthStage ?: 0) == 1 }
        val buyukAgacCount = plantsIn3DGarden.count { (it.growthStage ?: 0) >= 2 }

        val displayableTreeList = mutableListOf<DisplayableTreeInfo>()
        if (fidanCount > 0) {
            displayableTreeList.add(DisplayableTreeInfo("Fidan", fidanCount))
        }
        // Küçük ve büyük ağaçları tek bir "Ağaç" kategorisinde toplayabiliriz veya ayrı ayrı gösterebiliriz.
        // Şimdilik ayrı gösterelim, ProgressFragment'ta birleştirilebilir.
        if (kucukAgacCount > 0) {
            displayableTreeList.add(DisplayableTreeInfo("Küçük Ağaç", kucukAgacCount))
        }
        if (buyukAgacCount > 0) {
            displayableTreeList.add(DisplayableTreeInfo("Büyük Ağaç", buyukAgacCount))
        }
        // Veya tek bir "Ağaç" olarak:
        // val totalAgacCount = kucukAgacCount + buyukAgacCount
        // if (totalAgacCount > 0) {
        //     displayableTreeList.add(DisplayableTreeInfo("Ağaç", totalAgacCount))
        // }

        _gardenTreeInfo.postValue(displayableTreeList)
        Log.d(TAG, "3D Bahçe Ağaç Bilgisi Güncellendi: Fidan-$fidanCount, K.Ağaç-$kucukAgacCount, B.Ağaç-$buyukAgacCount")
    }

    private fun loadGardenStateFromFile() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val file = File(context.filesDir, GARDEN_STATE_FILENAME)
            var loadedState: GardenState? = null

            if (file.exists() && file.length() > 0) {
                try {
                    FileReader(file).use { reader ->
                        loadedState = gson.fromJson(reader, GardenState::class.java)
                        if (loadedState != null) {
                            loadedState!!.objects.filter { it.type == GardenObjectType.GROUND }.forEach { groundObject ->
                                if (groundObject.y != 0f) {
                                    Log.w(TAG, "Yüklenen zemin objesi (${groundObject.uuid}) için Y koordinatı ${groundObject.y} -> 0f olarak düzeltiliyor.")
                                    groundObject.y = 0f
                                }
                            }
                            Log.i(TAG, "GardenState JSON'dan yüklendi. Su: ${loadedState!!.waterDroplets}, Objeler: ${loadedState!!.objects.size}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "GardenState JSON'dan yüklenirken hata: ${e.message}", e)
                }
            }

            if (loadedState == null) {
                Log.i(TAG, "Kaydedilmiş GardenState bulunamadı veya hatalı. Varsayılan boş bahçe oluşturuluyor.")
                loadedState = GardenState(
                    waterDroplets = 500,
                    objects = mutableListOf()
                )
            }
            _gardenState.postValue(loadedState!!)
            _waterDroplets.postValue(loadedState!!.waterDroplets)
            updateGardenTreeInfoFromState(loadedState) // Yükleme sonrası ağaç bilgisini güncelle
        }
    }

    private fun saveGardenStateToFile() {
        viewModelScope.launch {
            _gardenState.value?.let { currentState ->
                try {
                    val jsonString = gson.toJson(currentState)
                    val file = File(getApplication<Application>().applicationContext.filesDir, GARDEN_STATE_FILENAME)
                    FileWriter(file).use { it.write(jsonString) }
                    Log.i(TAG, "GardenState JSON'a kaydedildi. Objeler: ${currentState.objects.size}, Su: ${currentState.waterDroplets}")
                } catch (e: Exception) {
                    Log.e(TAG, "GardenState JSON'a kaydedilirken hata: ${e.message}", e)
                }
            }
        }
    }

    fun canAffordObject(objectType: GardenObjectType): Boolean {
        val cost = getObjectCost(objectType)
        val currentWater = _waterDroplets.value ?: 0
        return currentWater >= cost
    }

    fun placeObjectIn3DGarden(typeString: String, uuidProvided: String?, x: Float, y: Float, z: Float, typeSpecificDataJson: String?) {
        val objectType = try { GardenObjectType.valueOf(typeString.uppercase(Locale.ROOT)) } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Bilinmeyen obje türü: $typeString"); return
        }
        val cost = getObjectCost(objectType)
        val currentWater = _waterDroplets.value ?: 0

        if (currentWater >= cost) {
            val currentState = _gardenState.value ?: return
            val newWaterAmount = currentWater - cost
            val uuid = uuidProvided ?: UUID.randomUUID().toString()
            var objectY = y
            if (objectType == GardenObjectType.GROUND) objectY = 0f

            val newObjectData = GardenObjectData(uuid = uuid, type = objectType, x = x, y = objectY, z = z)
            typeSpecificDataJson?.let {
                try {
                    val specificData = gson.fromJson(it, Map::class.java) as? Map<String, Any>
                    specificData?.get("colorHex")?.let { cv -> if (cv is Number) newObjectData.colorHex = cv.toInt() }
                    specificData?.get("rotationY")?.let { rv -> if (rv is Number) newObjectData.rotationY = rv.toFloat() }
                } catch (e: Exception) { Log.e(TAG, "placeObject typeSpecificDataJson parse hatası: ${e.message}") }
            }
            if (objectType == GardenObjectType.GROUND && newObjectData.colorHex == null) newObjectData.colorHex = UserColorsJs.light_green
            else if (objectType == GardenObjectType.PLANT) {
                newObjectData.growthStage = 0; newObjectData.waterLevel = 0
                currentState.objects.find { o -> o.type == GardenObjectType.GROUND && o.x == x && o.z == z }?.colorHex = UserColorsJs.dirt_brown
            }
            val updatedObjects = currentState.objects.filterNot { it.uuid == uuid }.toMutableList()
            updatedObjects.add(newObjectData)
            val newState = currentState.copy(waterDroplets = newWaterAmount, objects = updatedObjects)
            _gardenState.postValue(newState)
            _waterDroplets.postValue(newWaterAmount)
            updateGardenTreeInfoFromState(newState) // Ağaç bilgisi güncelle
            Log.i(TAG, "Obje ViewModel'e eklendi/güncellendi: $objectType - $uuid. Su: $newWaterAmount")
            saveGardenStateToFile()
        }
    }

    fun updateGroundColorIn3DGarden(xFloat: Float, zFloat: Float, newColorHex: Int) {
        _gardenState.value?.let { currentState ->
            var updated = false
            val updatedObjects = currentState.objects.map {
                if (it.type == GardenObjectType.GROUND && it.x == xFloat && it.z == zFloat) {
                    updated = true; it.copy(colorHex = newColorHex)
                } else it
            }.toMutableList()
            if (updated) {
                val newState = currentState.copy(objects = updatedObjects)
                _gardenState.postValue(newState)
                // updateGardenTreeInfoFromState(newState) // Zemin rengi ağaç sayısını etkilemez
                saveGardenStateToFile()
            }
        }
    }

    fun updatePlantDetailsIn3DGarden(uuid: String, newGrowthStage: Int, newWaterLevel: Int) {
        _gardenState.value?.let { currentState ->
            var plantUpdated = false
            val updatedObjects = currentState.objects.map {
                if (it.uuid == uuid && it.type == GardenObjectType.PLANT) {
                    plantUpdated = true; it.copy(growthStage = newGrowthStage, waterLevel = newWaterLevel)
                } else it
            }.toMutableList()
            if (plantUpdated) {
                val newState = currentState.copy(objects = updatedObjects)
                _gardenState.postValue(newState)
                updateGardenTreeInfoFromState(newState) // Ağaç bilgisi güncelle
                saveGardenStateToFile()
            }
        }
    }

    private fun getObjectCost(objectType: GardenObjectType): Int {
        return when (objectType) {
            GardenObjectType.GROUND -> 5; GardenObjectType.PLANT -> 10
            GardenObjectType.WALL -> 3; GardenObjectType.FENCE -> 2
            GardenObjectType.FLOWER_RED, GardenObjectType.FLOWER_YELLOW -> 1
        }
    }

    // --- Room DB Metodları (ProgressFragment için mevcut yapı) ---
    fun insertTree(tree: Tree) = viewModelScope.launch {
        treeDao.insert(tree); loadTreesFromDb()
    }
    fun insertDrop(drop: Drop) = viewModelScope.launch {
        dropDao.insert(drop); loadDropsFromDb()
    }
    fun loadTreesFromDb() = viewModelScope.launch {
        _roomTrees.postValue(treeDao.getAllTrees()) // _roomTrees olarak güncellendi
    }
    fun loadDropsFromDb() = viewModelScope.launch {
        _roomDrops.postValue(dropDao.getAllDrops()) // _roomDrops olarak güncellendi
    }

    fun addWaterDroplets(amount: Int) {
        _gardenState.value?.let { currentState ->
            val newWaterAmount = currentState.waterDroplets + amount
            val newState = currentState.copy(waterDroplets = newWaterAmount)
            _gardenState.postValue(newState)
            _waterDroplets.postValue(newWaterAmount)
            saveGardenStateToFile()
        }
    }
}