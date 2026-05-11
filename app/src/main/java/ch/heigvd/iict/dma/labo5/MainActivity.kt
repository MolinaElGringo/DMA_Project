package ch.heigvd.iict.dma.labo5

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ch.heigvd.iict.dma.labo5.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

}
