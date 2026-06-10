package ch.heigvd.iict.dma.labo5

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import ch.heigvd.iict.dma.labo5.databinding.ActivityMainBinding
import ch.heigvd.iict.dma.labo5.viewmodel.ChatViewModel
import androidx.appcompat.widget.Toolbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: ChatViewModel by viewModels()

    // Demande toutes les permissions nécessaires au démarrage
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            askUserNameAndStart()
        } else {
            // En prod on afficherait un message, pour l'instant on essaie quand même
            askUserNameAndStart()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        requestPermissions()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        setSupportActionBar(binding.toolbar)
        setupActionBarWithNavController(navController)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
            android.Manifest.permission.RECORD_AUDIO
        )
        // ACCESS_FINE_LOCATION seulement sur Android 12 et moins
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun askUserNameAndStart() {
        val editText = android.widget.EditText(this).apply {
            hint = "Ton prénom"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Comment tu t'appelles ?")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                val name = editText.text.toString().trim().ifEmpty { "Anonyme" }
                viewModel.setUserName(name)
                viewModel.startChat()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController.navigateUp() || super.onSupportNavigateUp()
    }
}