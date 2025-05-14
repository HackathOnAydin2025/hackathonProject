package com.example.hackathon

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.hackathon.R // Kendi R dosyanızın yolu
import com.example.hackathon.databinding.ActivityMainBinding // View Binding dosyanızın adı

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // View Binding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // BottomNavigationView'ı NavController ile bağla
        binding.bottomNavigationView.setupWithNavController(navController)

        // FAB için tıklama dinleyicisi (isteğe bağlı)
        binding.fab.setOnClickListener {
            // FAB'a tıklandığında yapılacak işlem
            // Örneğin: Yeni bir görev ekleme ekranına gitmek,
            navController.navigate(R.id.action_global_gardenFragment)
            // veya başka bir özel işlem.
            // "masal" ikonu özel bir şeyi çağrıştırıyorsa ona uygun bir işlem.
            // Şimdilik bir Toast mesajı gösterelim:
            android.widget.Toast.makeText(this, "FAB Tıklandı!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}