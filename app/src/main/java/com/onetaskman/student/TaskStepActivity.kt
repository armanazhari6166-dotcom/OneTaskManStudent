package com.onetaskman.student

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.onetaskman.student.data.Task
import com.onetaskman.student.databinding.ActivityTaskStepBinding
import com.onetaskman.student.utils.FirebaseHelper
import com.onetaskman.student.utils.XPManager

class TaskStepActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTaskStepBinding
    private val db = FirebaseHelper.db
    private lateinit var task: Task
    private lateinit var classId: String
    private lateinit var taskId: String
    private lateinit var studentUid: String
    private var currentStep = 0
    private val skippedSteps = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskStepBinding.inflate(layoutInflater)
        setContentView(binding.root)

        studentUid = getSharedPreferences("session", MODE_PRIVATE)
            .getString("studentUid", "") ?: ""
        classId = intent.getStringExtra("classId") ?: ""
        taskId = intent.getStringExtra("taskId") ?: ""
        task = intent.getStringExtra("taskJson")?.let { Task.fromJson(it) } ?: return
        currentStep = task.CurrentStep

        binding.btnNext.setOnClickListener { nextStep() }
        binding.btnSkip.setOnClickListener { skipStep() }
        binding.btnPrev.setOnClickListener { prevStep() }
        binding.btnSubmit.setOnClickListener { finishTask() }
        binding.btnBackToTasks.setOnClickListener { exit_task_context() }

        renderStep()
    }

    private fun renderStep() {
        if (currentStep >= task.Steps.size) {
            showReviewScreen()
        } else {
            showStepScreen()
        }
    }

    private fun showStepScreen() {
        binding.tvStepNumber.text = "Step ${currentStep + 1} of ${task.Steps.size}"
        binding.tvStepContent.text = task.Steps[currentStep]
        binding.btnPrev.isEnabled = currentStep > 0

        binding.stepNavigation.visibility = View.VISIBLE
        binding.btnNext.visibility = View.VISIBLE
        binding.btnSkip.visibility = View.VISIBLE
        binding.btnSubmit.visibility = View.GONE
    }

    private fun showReviewScreen() {
        binding.tvStepNumber.text = "Mission Review"
        binding.tvStepContent.text = "You have completed all steps. Ready to submit?"
        
        binding.stepNavigation.visibility = View.VISIBLE // Keep parent visible
        binding.btnNext.visibility = View.GONE
        binding.btnSkip.visibility = View.GONE
        binding.btnPrev.visibility = View.VISIBLE // Ensure Prev is visible
        binding.btnPrev.isEnabled = true

        binding.btnSubmit.visibility = View.VISIBLE
    }

    private fun nextStep() {
        currentStep++
        db.child("Queue").child(classId).child(studentUid).child(taskId)
            .child("CurrentStep").setValue(currentStep)
        renderStep()
    }

    private fun skipStep() {
        skippedSteps.add(currentStep + 1)
        currentStep++
        db.child("Queue").child(classId).child(studentUid).child(taskId)
            .child("CurrentStep").setValue(currentStep)
        db.child("Queue").child(classId).child(studentUid).child(taskId)
            .child("SkippedSteps").setValue(skippedSteps)
        renderStep()
    }

    private fun prevStep() {
        if (currentStep > 0) {
            currentStep--
            db.child("Queue").child(classId).child(studentUid).child(taskId)
            .child("CurrentStep").setValue(currentStep)
            renderStep()
        }
    }

    private fun exit_task_context() {
        db.child("Queue").child(classId).child(studentUid).child(taskId)
            .child("CurrentStep").setValue(currentStep).addOnCompleteListener { 
                finish() // Go back to the DashboardActivity
            }
    }

    private fun finishTask() {
        val finishedTask = task.copy(
            Status = "Completed",
            FinishedAt = System.currentTimeMillis(),
            SkippedSteps = skippedSteps.toList()
        )

        db.child("History").child(classId).child(studentUid).child(taskId)
            .setValue(finishedTask.toMap())
            .addOnSuccessListener {
                db.child("Queue").child(classId).child(studentUid).child(taskId).removeValue()
                    .addOnSuccessListener {
                        val xp = if (skippedSteps.isNotEmpty()) 80 else 100
                        XPManager.awardXP(db, studentUid, xp)

                        val intent = Intent(this, CompletionActivity::class.java).apply {
                            putExtra("taskName", task.TaskName)
                            putExtra("xpAwarded", xp)
                            putExtra("stepsTotal", task.Steps.size)
                            putExtra("skipped", skippedSteps.size)
                        }
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("TaskStepActivity", "Failed to remove task from queue", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("TaskStepActivity", "Failed to write task to history", e)
            }
    }
}