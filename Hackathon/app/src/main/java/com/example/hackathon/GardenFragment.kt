package com.example.hackathon.garden // Kendi paket yapınıza göre güncelleyin

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
import androidx.fragment.app.viewModels
import com.example.hackathon.databinding.FragmentGardenBinding // XML dosyanızın adına göre (fragment_garden_3d_xml.xml -> FragmentGarden3dXmlBinding)
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Basit bir bitki veri sınıfı (JavaScript ile senkronizasyon için)
data class PlantData(
    val uuid: String = UUID.randomUUID().toString(),
    var x: Float,
    var z: Float,
    var color: Int = 0x228B22, // ForestGreen
    var width: Float = 0.2f,
    var height: Float = 0.4f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var scaleZ: Float = 1f,
    var growthStage: Int = 0,
    var waterLevel: Int = 0
)

class GardenFragment : Fragment() {

    private var _binding: FragmentGardenBinding? = null
    private val binding get() = _binding!!

    private lateinit var webView: WebView
    private val TAG = "GardenFragment"

    // Bahçe verilerini saklamak için (ViewModel'de olması daha iyi)
    private val gardenPlants = mutableListOf<PlantData>()
    private var waterDroplets = 10 // Başlangıç su damlası (ViewModel'den gelmeli)

    // Örnek ViewModel (Gerçek uygulamanızda daha kapsamlı olmalı)
    // private val gardenViewModel: GardenViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGardenBinding.inflate(inflater, container, false)
        webView = WebView(requireContext())
        binding.frameLayoutGarden3dView.addView(webView) // WebView'ı FrameLayout'a ekle
        setupWebView()
        Log.d(TAG, "onCreateView tamamlandı.")
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true // Gerekli olabilir
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = false // Zoom kontrollerini kapat
        webView.settings.displayZoomControls = false

        // JavaScript loglarını Android Logcat'e yönlendirmek için
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                Log.d("WebViewConsole", "${message.message()} -- From line ${message.lineNumber()} of ${message.sourceId()}")
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "WebView sayfası yüklendi: $url")
                // Sayfa yüklendikten sonra Android'den JavaScript'e veri gönder
                updateJavaScriptWaterCount()
                loadGardenStateToJavaScript()
            }
        }

        // Android ve JavaScript arasında köprü kurmak için
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        // assets klasöründeki HTML dosyasını yükle
        webView.loadUrl("file:///android_asset/garden_scene.html")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated çağrıldı.")

        // UI güncellemeleri (örneğin su damlası sayısı)
        binding.textViewWaterDropletsCount.text = "$waterDroplets Damla Toplandı"

        // Buton tıklama olayları (XML'deki butonlara bağlanacak)
        binding.buttonPlantSapling.setOnClickListener {
            // Bu buton artık HTML içinde, oradan tetiklenecek.
            // İsterseniz buradan da JavaScript fonksiyonu çağırabilirsiniz.
            // webView.evaluateJavascript("plantNewSapling();", null)
            Toast.makeText(requireContext(), "Fidan dikme HTML içinden yönetiliyor.", Toast.LENGTH_SHORT).show()
        }

        // Başlangıçta bahçe durumunu yükle (eğer varsa)
        // loadGardenStateFromPreferencesOrDb()
    }


    private fun updateJavaScriptWaterCount() {
        webView.evaluateJavascript("javascript:setWaterCount($waterDroplets);", null)
    }

    private fun loadGardenStateToJavaScript() {
        val jsonArray = JSONArray()
        gardenPlants.forEach { plantData ->
            val jsonObject = JSONObject()
            jsonObject.put("uuid", plantData.uuid)
            jsonObject.put("x", plantData.x)
            jsonObject.put("z", plantData.z)
            jsonObject.put("color", plantData.color)
            jsonObject.put("width", plantData.width)
            jsonObject.put("height", plantData.height)
            jsonObject.put("scaleX", plantData.scaleX)
            jsonObject.put("scaleY", plantData.scaleY)
            jsonObject.put("scaleZ", plantData.scaleZ)
            jsonObject.put("growthStage", plantData.growthStage)
            jsonObject.put("waterLevel", plantData.waterLevel)
            jsonArray.put(jsonObject)
        }
        val gardenData = JSONObject().apply { put("plants", jsonArray) }.toString()
        Log.d(TAG, "JavaScript'e gönderilen bahçe durumu: $gardenData")
        // JSON string'ini JavaScript'e güvenli bir şekilde göndermek için escape etmek gerekebilir.
        // Şimdilik direkt gönderiyoruz.
        webView.evaluateJavascript("javascript:loadGardenState('$gardenData');", null)
    }


    // JavaScript'ten Android'e çağrılacak metodlar için arayüz
    inner class WebAppInterface {
        @JavascriptInterface
        fun log(message: String) {
            Log.i("WebAppInterface", message)
        }

        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun canPlantNewSapling(): Boolean {
            val canPlant = waterDroplets >= 10 // Örnek: Yeni fidan için 10 damla su gerekiyor
            if (!canPlant) {
                Log.d(TAG, "Yeni fidan dikilemez, yetersiz su: $waterDroplets")
            }
            return canPlant
        }

        @JavascriptInterface
        fun onSaplingPlanted(uuid: String, x: Float, z: Float) {
            // Yeni fidan dikildiğinde çağrılır
            activity?.runOnUiThread {
                if (waterDroplets >= 10) {
                    waterDroplets -= 10
                    binding.textViewWaterDropletsCount.text = "$waterDroplets Damla Toplandı"
                    updateJavaScriptWaterCount()

                    val newPlant = PlantData(uuid = uuid, x = x, z = z) // Diğer özellikler varsayılan
                    gardenPlants.add(newPlant)
                    Log.i(TAG, "Yeni fidan Android tarafına eklendi: $uuid, Su: $waterDroplets")
                    // saveGardenStateToPreferencesOrDb() // Bahçe durumunu kaydet
                }
            }
        }

        @JavascriptInterface
        fun onPlantWatered(uuid: String) {
            // Bitki sulandığında çağrılır
            activity?.runOnUiThread {
                val plant = gardenPlants.find { it.uuid == uuid }
                if (plant != null) {
                    plant.waterLevel += 1
                    // Büyüme mantığını burada da güncelleyebilir veya sadece JavaScript'te bırakabilirsiniz.
                    // Örnek: plant.scaleY *= 1.1f (eğer Android'de de takip ediyorsanız)
                    Log.i(TAG, "Bitki sulandı (Android): $uuid, Su Seviyesi: ${plant.waterLevel}")
                    // saveGardenStateToPreferencesOrDb() // Bahçe durumunu kaydet
                }
            }
        }
    }

    // TODO: Bahçe durumunu kaydetme ve yükleme fonksiyonları (SharedPreferences, Room DB vb.)
    // private fun saveGardenStateToPreferencesOrDb() { ... }
    // private fun loadGardenStateFromPreferencesOrDb() { ... }


    override fun onDestroyView() {
        binding.frameLayoutGarden3dView.removeView(webView) // WebView'ı kaldır
        webView.destroy() // WebView kaynaklarını serbest bırak
        _binding = null
        Log.d(TAG, "onDestroyView çağrıldı.")
        super.onDestroyView()
    }
}
