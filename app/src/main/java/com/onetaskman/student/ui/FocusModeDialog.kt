package com.onetaskman.student.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.onetaskman.student.TaskStepActivity
import com.onetaskman.student.ble.BleScanActivity
import com.onetaskman.student.ble.StudentBleManager
import com.onetaskman.student.data.TaskItem

class FocusModeDialog(
    private val taskItem: TaskItem,
    private val bleManager: StudentBleManager?
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("Choose Focus Mode")
            .setMessage("How do you want to complete this task?")
            .setPositiveButton("📱 App Mode") { _, _ ->
                // Start task execution in app (no BLE needed)
                val intent = Intent(requireContext(), TaskStepActivity::class.java).apply {
                    putExtra("classId", taskItem.classId)
                    putExtra("taskId", taskItem.taskId)
                    putExtra("taskJson", taskItem.task.toJson())
                }
                startActivity(intent)
            }
            .setNegativeButton("🔵 Device Mode") { _, _ ->
                if (bleManager?.isConnected == true) {
                    // Delegate task to BLE device
                    bleManager.startDeviceMode(taskItem)
                } else {
                    // Send to BLE scan screen first
                    startActivity(Intent(requireContext(), BleScanActivity::class.java))
                }
            }
            .create()
    }
}