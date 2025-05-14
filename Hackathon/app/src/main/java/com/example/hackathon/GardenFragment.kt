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
import androidx.fragment.app.activityViewModels // Paylaşılan ViewModel için
import com.example.hackathon.databinding.FragmentGardenBinding
import com.example.hackathon.data.GardenObjectType
import com.example.hackathon.data.GardenState // GardenState importu
import com.example.hackathon.progress.viewmodel.GardenViewModel // ViewModel importu
import com.google.gson.Gson
import java.util.Locale

class GardenFragment : Fragment() {

    private var _binding: FragmentGardenBinding? = null
    private val binding get() = _binding!!

    private lateinit var webView: WebView
    private val TAG = "GardenFragment"
    private val gson = Gson()

    // Paylaşılan GardenViewModel'i al
    private val gardenViewModel: GardenViewModel by activityViewModels()

    // UserColorsJs objesi artık ViewModel içinde, gerekirse oradan erişilebilir
    // object UserColorsJs { ... }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView çağrıldı.")
        _binding = FragmentGardenBinding.inflate(inflater, container, false)
        // WebView'ı burada oluşturmak daha güvenli, context kesinleşmiş olur.
        webView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        binding.frameLayoutGarden3dView.addView(webView)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated çağrıldı.")
        setupWebView()

        // ViewModel'deki su damlası sayısını gözlemle
        gardenViewModel.waterDroplets.observe(viewLifecycleOwner) { waterCount ->
            Log.d(TAG, "GardenFragment: Gözlemlenen su damlası sayısı: $waterCount")
            binding.textViewWaterDropletsCount.text = "$waterCount Damla Toplandı" // Android UI güncelle
            updateJavaScriptWaterCount(waterCount) // JavaScript UI güncelle
        }

        // ViewModel'deki tüm bahçe durumunu gözlemle (gerekirse ilk yükleme için)
        // Bu, sayfa ilk yüklendiğinde veya konfigürasyon değişikliğinde JS'e state'i göndermek için.
        // onPageFinished içinde zaten yükleme yapılıyor, bu kısım çift yüklemeyi önlemek için dikkatli yönetilmeli.
        // Genellikle onPageFinished yeterli olur.
        /*
        gardenViewModel.gardenState.observe(viewLifecycleOwner) { state ->
            if (state != null && webView.url != null) { // WebView yüklendikten sonra
                Log.d(TAG, "GardenFragment: Gözlemlenen gardenState, JS'e yükleniyor. Obje sayısı: ${state.objects.size}")
                // loadGardenStateToJavaScript(state) // Bu onPageFinished'de zaten yapılıyor.
            }
        }
        */
        Log.d(TAG, "onViewCreated tamamlandı.")
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
                // Sayfa yüklendiğinde ViewModel'deki mevcut durumu JS'e gönder
                gardenViewModel.gardenState.value?.let { currentState ->
                    Log.d(TAG, "onPageFinished: Mevcut bahçe durumu JS'e yükleniyor. Su: ${currentState.waterDroplets}, Objeler: ${currentState.objects.size}")
                    updateJavaScriptWaterCount(currentState.waterDroplets)
                    loadGardenStateToJavaScript(currentState)
                } ?: run {
                    Log.w(TAG, "onPageFinished: gardenState henüz ViewModel'de mevcut değil. Yüklenmesi bekleniyor...")
                    // ViewModel init'te zaten yükleme yapıyor.
                }
            }
        }
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/garden_scene.html")
        Log.d(TAG, "setupWebView tamamlandı.")
    }

    private fun updateJavaScriptWaterCount(count: Int) {
        if (::webView.isInitialized && webView.parent != null) { // WebView'ın hala view hiyerarşisinde olduğundan emin ol
            val script = "javascript:if(typeof window.setWaterCount === 'function') { window.setWaterCount($count); } else { console.warn('window.setWaterCount JS fonksiyonu bulunamadı'); }"
            webView.evaluateJavascript(script, null)
            Log.d(TAG, "JS'e su sayısı gönderildi: $count")
        } else {
            Log.w(TAG, "updateJavaScriptWaterCount: WebView başlatılmamış veya view'dan kaldırılmış.")
        }
    }

    private fun loadGardenStateToJavaScript(state: GardenState) {
        if (!::webView.isInitialized || webView.parent == null) {
            Log.w(TAG, "loadGardenStateToJavaScript: WebView başlatılmamış veya view'dan kaldırılmış.")
            return
        }
        try {
            val jsonState = gson.toJson(state)
            Log.d(TAG, "JS'e gönderilen bahçe durumu (loadGardenStateToJavaScript): ${jsonState.take(200)}")
            val escapedJsonState = jsonState.replace("\\", "\\\\").replace("'", "\\'")
            val script = "javascript:if(typeof window.loadGardenState === 'function') { window.loadGardenState('$escapedJsonState'); } else { console.warn('window.loadGardenState JS fonksiyonu bulunamadı'); }"
            webView.evaluateJavascript(script, null)
        } catch (e: Exception) {
            Log.e(TAG, "loadGardenStateToJavaScript sırasında hata: ${e.message}", e)
        }
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
            val objectType = try { GardenObjectType.valueOf(typeString.uppercase(Locale.ROOT)) } catch (e: IllegalArgumentException) {
                Log.e(TAG, "canPlaceObject: Bilinmeyen obje türü: $typeString")
                return false
            }
            return gardenViewModel.canAffordObject(objectType)
        }

        @JavascriptInterface
        fun onObjectPlaced(typeString: String, uuid: String, x: Float, y: Float, z: Float, typeSpecificDataJson: String) {
            Log.d(TAG, "onObjectPlaced JS'den çağrıldı: type=$typeString, uuid=$uuid, x=$x, y=$y, z=$z, data=$typeSpecificDataJson")
            activity?.runOnUiThread { // ViewModel güncellemeleri ana thread'de yapılmalı (LiveData için)
                gardenViewModel.placeObjectIn3DGarden(typeString, uuid, x, y, z, typeSpecificDataJson)
            }
        }

        @JavascriptInterface
        fun onGroundColorChanged(xFloat: Float, zFloat: Float, newColorHex: Int) {
            Log.d(TAG, "onGroundColorChanged JS'den çağrıldı: x=$xFloat, z=$zFloat, color=0x${newColorHex.toString(16)}")
            activity?.runOnUiThread {
                gardenViewModel.updateGroundColorIn3DGarden(xFloat, zFloat, newColorHex)
            }
        }

        @JavascriptInterface
        fun onObjectModified(typeString: String, uuid: String, newGrowthStage: Int, newWaterLevel: Int) {
            Log.d(TAG, "onObjectModified JS'den çağrıldı: type=$typeString, uuid=$uuid, stage=$newGrowthStage, water=$newWaterLevel")
            activity?.runOnUiThread {
                gardenViewModel.updatePlantDetailsIn3DGarden(uuid, newGrowthStage, newWaterLevel)
            }
        }

        @JavascriptInterface
        fun updateObjectCount(count: Int) {
            Log.d("WebAppInterface", "JavaScript'ten gelen 3D obje sayısı (info): $count")
        }
    }

    override fun onPause() {
        super.onPause()
        // ViewModel durumu zaten periyodik olarak veya her değişiklikte kaydediyor.
        // gardenViewModel.saveGardenStateToFile() // Gerekirse burada manuel kaydetme.
        Log.d(TAG, "onPause çağrıldı.")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::webView.isInitialized) {
            binding.frameLayoutGarden3dView.removeView(webView) // Önce view hiyerarşisinden kaldır
            webView.destroy()
        }
        _binding = null // Bellek sızıntılarını önle
        Log.d(TAG, "onDestroyView çağrıldı.")
    }
}
