package com.onetaskman.student

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.onetaskman.student.databinding.ActivityCompletionBinding

class CompletionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompletionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompletionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val taskName = intent.getStringExtra("taskName")
        val xpAwarded = intent.getIntExtra("xpAwarded", 0)
        val stepsTotal = intent.getIntExtra("stepsTotal", 0)
        val skipped = intent.getIntExtra("skipped", 0)

        binding.tvTaskName.text = taskName
        binding.tvXpAwarded.text = "+$xpAwarded XP"
        binding.tvCompletionMessage.text = "You completed $stepsTotal steps (${stepsTotal - skipped} done, $skipped skipped)."

        binding.btnBackToDashboard.setOnClickListener {
            finish()
        }
    }
}