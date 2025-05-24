# Florado - Android Uygulama

Florado, Pomodoro tekniğini kullanarak görev yönetimi ve odaklanma süresini artırmayı hedefleyen, aynı zamanda kullanıcıları motive etmek için 3D bir bahçe yetiştirme deneyimi sunan bir Android uygulamasıdır. Tamamladığınız görevler ve odaklandığınız süreler boyunca "su damlaları" kazanarak sanal bahçenizi dekore edebilir ve bitkilerinizi büyütebilirsiniz.

## 🎯 Temel Özellikler

* **Görev Yönetimi:**
    * Günlük ve haftalık görevler oluşturma, düzenleme ve silme.
    * Görevler için süre (Pomodoro seansları) ve başlangıç zamanı belirleme.
    * Tamamlanan görevleri işaretleme.
    * Görevler için harcanan gerçek odak süresini takip etme.
* **Pomodoro Zamanlayıcısı:**
    * Özelleştirilebilir Pomodoro seansları.
    * Başlatma, duraklatma ve sıfırlama işlevleri.
    * Görsel ilerleme göstergesi ve kalan süre takibi.
    * Odaklanmayı artırmak için arka planda çalabilen "soft brown noise" (yumuşak kahverengi gürültü).
    * Seans tamamlandığında kutlama animasyonu.
* **3D Sanal Bahçe:**
    * Görevleri tamamlayarak kazanılan "su damlaları" ile bahçe objeleri (zemin, fidan, duvar, çit, çiçekler) satın alma.
    * Fidanları sulayarak farklı büyüme aşamalarına (fidan -> küçük ağaç -> büyük ağaç) getirme.
    * Bahçe düzenini özelleştirme ve objeleri döndürme.
    * WebView içinde çalışan Three.js ile oluşturulmuş interaktif 3D sahne.
    * Bahçe durumunun JSON formatında cihazda saklanması.
* **İlerleme Takibi:**
    * Haftalık görev tamamlanma dağılımını gösteren pasta grafiği.
    * Bahçedeki toplam ağaç sayısı ve biriktirilen su damlası miktarının özeti.
    * Günlük planlanan ve gerçekleşen odak süresi için ilerleme çubuğu.
    * Kullanıcıyı motive edici mesajlar.
* **📅 Tarih Bazlı Görev Görünümü:**
    * Çip tabanlı arayüz ile farklı günler arasında kolayca geçiş yapma.
    * Seçilen tarihe ait görevleri listeleme.
* **✨ Gemini AI Entegrasyonu:**
    * Tamamlanmamış görevlerinize göre Gemini AI'dan zaman yönetimi ve görev planlama önerileri alma.

## 🛠️ Kullanılan Teknolojiler

* **Programlama Dili:** Kotlin
* **Mimarisi:** MVVM (Model-View-ViewModel)
* **Android Jetpack:**
    * LiveData
    * ViewModel
    * Room Persistence Library (Veritabanı)
    * Navigation Component (Fragment navigasyonu)
    * ViewBinding
* **Asenkron Programlama:** Kotlin Coroutines
* **3D Grafik:** Three.js (WebView içinde)
* **Veri Serileştirme:** Gson (Bahçe durumunu JSON olarak kaydetmek için)
* **Grafik Kütüphanesi:** MPAndroidChart (İlerleme ekranındaki pasta grafik için)
* **Animasyonlar:** Lottie (Kutlama ve arayüz animasyonları için)
* **Yapay Zeka:** Google Gemini API (Görev planlama önerileri için)
* **UI/UX:** Material Design Components

## 🖼️ Ekran Görüntüleri
![garden](https://github.com/user-attachments/assets/c5c2928c-c05d-4bc9-b7c5-153b217de68f)
![pamodoro](https://github.com/user-attachments/assets/f9daced4-8428-47a3-aaf2-edd8ba39c08e)
![task](https://github.com/user-attachments/assets/3530f3a4-547e-4536-99c5-b651a3e8b19e)
![ilerleme](https://github.com/user-attachments/assets/c4691a8c-426e-49f0-916b-1801f6c56c06)

## 🧩 Modüller ve Ana Bileşenler

* **`data` Paketi:** Room veritabanı varlıkları (Task, Tree, Drop), DAO (Data Access Object) arayüzleri, AppDatabase sınıfı ve `GardenObjectData`, `DailyTaskSummary` gibi veri sınıflarını içerir.
* **`tasks` Paketi:**
    * `TaskViewModel`: Görevlerle ilgili tüm mantığı yönetir, Room veritabanı ile etkileşime girer, LiveData'ları günceller.
    * `TaskListFragment`: Kullanıcının görevlerini listeler, yeni görev eklemesine olanak tanır, Gemini AI önerilerini tetikler.
    * `TaskListAdapter`: RecyclerView için görev listesini yönetir.
    * `PomodoroFragment`: Seçilen görev için Pomodoro zamanlayıcısını çalıştırır.
* **`progress` Paketi (ve `GardenFragment`):**
    * `GardenViewModel`: 3D bahçe durumunu (JSON ve LiveData üzerinden), su damlalarını ve bahçedeki ağaç bilgilerini yönetir. Three.js ile Android arasındaki köprüyü kurar.
    * `GardenFragment`: WebView içinde Three.js ile oluşturulan 3D bahçe sahnesini görüntüler. Kullanıcı etkileşimlerini (`placeObject`, `waterPlant` vb.) `GardenViewModel`'e iletir.
    * `ProgressFragment`: Haftalık görev özetini (pasta grafik), bahçe istatistiklerini ve günlük odaklanma süresini gösterir.
* **Room Veritabanı (`AppDatabase`):** `Task`, `Tree`, `Drop` varlıklarını saklar.
* **3D Sahne (`garden_scene.html`):** Three.js kullanarak 3D bahçeyi oluşturur ve Android tarafından sağlanan verilerle güncellenir. `WebAppInterface` aracılığıyla Android ile iletişim kurar.
* **Navigation (`main_nav.xml`):** Uygulama içindeki Fragment'lar arası geçişleri yönetir.

