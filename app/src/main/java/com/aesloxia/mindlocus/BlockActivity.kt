package com.aesloxia.mindlocus

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aesloxia.mindlocus.databinding.ActivityBlockBinding

class BlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Removed btnUnlock listener as the button is removed from layout to avoid temptation.
        // The user must use system navigation (Home/Back) to leave this screen.
    }

    override fun onBackPressed() {
        // When back is pressed, send user to the Home screen
        val startMain = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(startMain)
    }
}
