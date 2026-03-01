package com.onetaskman.student

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.onetaskman.student.data.Task
import com.onetaskman.student.databinding.ActivityHistoryDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val taskJson = intent.getStringExtra("taskJson")
        val task = taskJson?.let { Task.fromJson(it) } ?: return

        binding.tvTaskName.text = task.TaskName
        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        val finishedAtTimestamp = (task.FinishedAt as? Number)?.toLong() ?: 0L
        val finishedAtDate = Date(finishedAtTimestamp)
        binding.tvFinishedAt.text = "Finished: ${sdf.format(finishedAtDate)}"

        renderSteps(task)

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun renderSteps(task: Task) {
        binding.stepsContainer.removeAllViews()
        for ((index, stepText) in task.Steps.withIndex()) {
            val stepView = TextView(this).apply {
                val isSkipped = task.SkippedSteps.contains(index + 1)
                val stepStatus = if (isSkipped) "✘" else "✓"
                text = "Step ${index + 1}: $stepStatus"
                textSize = 18f
                setPadding(0, 12, 0, 12)
                if (isSkipped) {
                    setTextColor(android.graphics.Color.parseColor("#F44336"))
                } else {
                    setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                }
            }
            binding.stepsContainer.addView(stepView)
        }
    }
}