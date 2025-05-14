package com.example.hackathon // Kendi paket yapınıza göre güncelleyin

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.hackathon.databinding.FragmentGardenBinding // XML dosyanızın adına göre
import com.example.hackathon.data.GardenObjectData // Güncellenmiş data class importu
import com.example.hackathon.data.GardenObjectType // Enum importu
import com.example.hackathon.data.GardenState // Güncellenmiş data class importu
import com.google.gson.Gson
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.Locale
import java.util.UUID

class GardenFragment : Fragment() {

    private var _binding: FragmentGardenBinding? = null
    private val binding get() = _binding!!

    private lateinit var webView: WebView
    private val TAG = "GardenFragment"
    private val GARDEN_STATE_FILENAME = "garden_state_v3_placeables.json" // Dosya adını güncelledim
    private val gson = Gson()

    private lateinit var currentGardenState: GardenState

    // JavaScript tarafındaki renklerle eşleşen sabitler
    object UserColorsJs {
        const val light_green = 0xA4B465
        const val dirt_brown = 0x964B00
        // Diğer renkler HTML'deki userColors objesinden alınabilir
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate çağrıldı.")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView çağrıldı.")
        _binding = FragmentGardenBinding.inflate(inflater, container, false)
        webView = WebView(requireContext())
        val layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        binding.frameLayoutGarden3dView.addView(webView, layoutParams)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated çağrıldı.")
        currentGardenState = loadInitialGardenState()
        setupWebView() // WebView kurulumu currentGardenState yüklendikten sonra
        updateWaterDropletsUI()

        // XML'deki butonlar artık HTML içinde olduğu için buradaki listener'lar kaldırıldı.
        // binding.buttonPlantSapling.setOnClickListener { ... }
        Log.d(TAG, "onViewCreated tamamlandı. Yüklenen su: ${currentGardenState.waterDroplets}")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        Log.d(TAG, "setupWebView çağrılıyor.")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                Log.d("WebViewConsole", "[${message.sourceId()}:${message.lineNumber()}] ${message.message()}")
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "WebView sayfası yüklendi: $url")
                if (::currentGardenState.isInitialized) {
                    updateJavaScriptWaterCount(currentGardenState.waterDroplets)
                    loadGardenStateToJavaScript(currentGardenState)
                } else {
                    Log.e(TAG, "onPageFinished: currentGardenState başlatılmamış!")
                }
            }
        }
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/garden_scene.html") // HTML dosya adınızın bu olduğundan emin olun
        Log.d(TAG, "setupWebView tamamlandı.")
    }

    private fun updateWaterDropletsUI() {
        if (_binding != null && ::currentGardenState.isInitialized) {
            binding.textViewWaterDropletsCount.text = "${currentGardenState.waterDroplets} Damla Toplandı"
        }
    }

    private fun updateJavaScriptWaterCount(count: Int) {
        if (::webView.isInitialized) {
            webView.evaluateJavascript("javascript:setWaterCount($count);", null)
        }
    }

    private fun loadGardenStateToJavaScript(state: GardenState) {
        if (!::webView.isInitialized) {
            Log.w(TAG, "loadGardenStateToJavaScript: WebView henüz başlatılmadı.")
            return
        }
        try {
            val jsonState = gson.toJson(state)
            Log.d(TAG, "JavaScript'e gönderilen bahçe durumu (ilk 200 karakter): ${jsonState.take(200)}")
            val escapedJsonState = jsonState.replace("\\", "\\\\").replace("'", "\\'")
            webView.evaluateJavascript("javascript:loadGardenState('$escapedJsonState');", null)
        } catch (e: Exception) {
            Log.e(TAG, "loadGardenStateToJavaScript sırasında hata: ${e.message}", e)
        }
    }

    private fun saveCurrentGardenState() {
        if (!isAdded || context == null || !::currentGardenState.isInitialized) {
            Log.w(TAG, "saveCurrentGardenState: Fragment context'e bağlı değil veya state başlatılmamış, kaydetme atlandı.")
            return
        }
        try {
            val jsonString = gson.toJson(currentGardenState)
            val file = File(requireContext().filesDir, GARDEN_STATE_FILENAME)
            FileWriter(file).use { it.write(jsonString) }
            Log.i(TAG, "Bahçe durumu kaydedildi: $GARDEN_STATE_FILENAME.")
        } catch (e: Exception) {
            Log.e(TAG, "Bahçe durumu kaydedilirken hata: ${e.message}", e)
        }
    }

    private fun loadInitialGardenState(): GardenState {
        if (!isAdded) {
            Log.w(TAG, "loadInitialGardenState: Fragment henüz eklenmemiş, varsayılan durum oluşturuluyor.")
            return GardenState(
                waterDroplets = 50, // Başlangıç suyu
                objects = mutableListOf(
                    GardenObjectData(type = GardenObjectType.GROUND, x = 0f, y = -0.5f, z = 0f, colorHex = UserColorsJs.light_green)
                )
            )
        }
        val file = File(requireContext().filesDir, GARDEN_STATE_FILENAME)
        if (file.exists() && file.length() > 0) {
            try {
                FileReader(file).use {
                    val state = gson.fromJson(it, GardenState::class.java)
                    if (state != null) {
                        Log.i(TAG, "Kaydedilmiş bahçe durumu yüklendi. Su: ${state.waterDroplets}, Obje Sayısı: ${state.objects.size}")
                        return state
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Kaydedilmiş bahçe durumu yüklenirken hata: ${e.message}", e)
            }
        }
        Log.i(TAG, "Kaydedilmiş bahçe durumu bulunamadı veya boş, varsayılan oluşturuluyor.")
        return GardenState(
            waterDroplets = 500, // Başlangıç suyu
            objects = mutableListOf(
                // Başlangıçta bir zemin bloğu ekle
                GardenObjectData(type = GardenObjectType.GROUND, x = 0f, y = -0.5f, z = 0f, colorHex = UserColorsJs.light_green)
            )
        )
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun log(message: String) {
            Log.i("WebAppInterface", message)
        }

        @JavascriptInterface
        fun showToast(message: String) {
            activity?.runOnUiThread {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun canPlaceObject(typeString: String, x: Float, y: Float, z: Float): Boolean {
            if (!::currentGardenState.isInitialized) return false
            val objectType = GardenObjectType.valueOf(typeString.uppercase(Locale.ROOT))
            val cost = when (objectType) {
                GardenObjectType.GROUND -> 5
                GardenObjectType.PLANT -> 10
                GardenObjectType.WALL -> 3
                GardenObjectType.FENCE -> 2
                GardenObjectType.FLOWER_RED, GardenObjectType.FLOWER_YELLOW -> 1
                else -> 0
            }
            val canPlace = currentGardenState.waterDroplets >= cost
            if (!canPlace) Log.d(TAG, "$objectType yerleştirilemez, yetersiz su: ${currentGardenState.waterDroplets}, Gerekli: $cost")
            return canPlace
        }

        @JavascriptInterface
        fun onObjectPlaced(typeString: String, uuid: String, x: Float, y: Float, z: Float) {
            if (!::currentGardenState.isInitialized) return
            activity?.runOnUiThread {
                val objectType = GardenObjectType.valueOf(typeString.uppercase(Locale.ROOT))
                val cost = when (objectType) {
                    GardenObjectType.GROUND -> 5
                    GardenObjectType.PLANT -> 10
                    GardenObjectType.WALL -> 3
                    GardenObjectType.FENCE -> 2
                    GardenObjectType.FLOWER_RED, GardenObjectType.FLOWER_YELLOW -> 1
                    else -> 0
                }

                if (currentGardenState.waterDroplets >= cost) {
                    currentGardenState.waterDroplets -= cost
                    updateWaterDropletsUI()
                    updateJavaScriptWaterCount(currentGardenState.waterDroplets)

                    val newObjectData = GardenObjectData(uuid = uuid, type = objectType, x = x, y = y, z = z)
                    if (objectType == GardenObjectType.GROUND) {
                        newObjectData.colorHex = UserColorsJs.light_green // Varsayılan zemin rengi
                    } else if (objectType == GardenObjectType.PLANT) {
                        newObjectData.growthStage = 0
                        newObjectData.waterLevel = 0
                        // Altındaki zemin bloğunun rengini de güncelle (dirt_brown)
                        val groundBlock = currentGardenState.objects.find {
                            it.type == GardenObjectType.GROUND && it.x == x && it.z == z
                        }
                        groundBlock?.colorHex = UserColorsJs.dirt_brown
                    }
                    currentGardenState.objects.add(newObjectData)
                    Log.i(TAG, "Yeni obje Android tarafına eklendi: $objectType - $uuid, Su: ${currentGardenState.waterDroplets}")
                    saveCurrentGardenState()
                }
            }
        }

        @JavascriptInterface
        fun onGroundColorChanged(x: Int, z: Int, newColorHex: Int) {
            if (!::currentGardenState.isInitialized) return
            activity?.runOnUiThread {
                val groundBlock = currentGardenState.objects.find {
                    it.type == GardenObjectType.GROUND && it.x.toInt() == x && it.z.toInt() == z
                }
                if (groundBlock != null) {
                    groundBlock.colorHex = newColorHex
                    Log.i(TAG, "Zemin ($x,$z) rengi güncellendi: $newColorHex")
                    saveCurrentGardenState()
                } else {
                    Log.w(TAG, "Renk değiştirmek için zemin bloğu bulunamadı: ($x,$z)")
                }
            }
        }


        @JavascriptInterface
        fun onObjectModified(typeString: String, uuid: String, newGrowthStage: Int, newWaterLevel: Int) {
            if (!::currentGardenState.isInitialized) return
            activity?.runOnUiThread {
                val objectType = GardenObjectType.valueOf(typeString.uppercase(Locale.ROOT))
                if (objectType == GardenObjectType.PLANT) {
                    val plant = currentGardenState.objects.find { it.uuid == uuid && it.type == GardenObjectType.PLANT }
                    if (plant != null) {
                        plant.waterLevel = newWaterLevel
                        plant.growthStage = newGrowthStage
                        Log.i(TAG, "Bitki güncellendi (Android): $uuid, Yeni Aşama: ${plant.growthStage}, Su Seviyesi: ${plant.waterLevel}")
                        saveCurrentGardenState()
                    } else {
                        Log.w(TAG, "Güncellenecek bitki bulunamadı (Android): $uuid")
                    }
                }
            }
        }

        @JavascriptInterface
        fun updateObjectCount(count: Int) {
            Log.d(TAG, "JavaScript'ten gelen obje sayısı (info): $count")
            // İsterseniz bu bilgiyi UI'da başka bir yerde gösterebilirsiniz.
            // binding.textViewInfoObjectCount.text = "Objeler: $count"
        }
    }

    override fun onPause() {
        super.onPause()
        if (::currentGardenState.isInitialized) {
            saveCurrentGardenState()
            Log.d(TAG, "onPause çağrıldı ve bahçe durumu kaydedildi.")
        } else {
            Log.w(TAG, "onPause: currentGardenState başlatılmamış, kaydetme atlandı.")
        }
    }

    override fun onDestroyView() {
        if (::webView.isInitialized && webView.parent != null) {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
        if (::webView.isInitialized) {
            webView.destroy()
        }
        _binding = null
        Log.d(TAG, "onDestroyView çağrıldı.")
        super.onDestroyView()
    }
}
