package com.onetaskman.student

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.onetaskman.student.ble.BleService
import com.onetaskman.student.data.Task
import com.onetaskman.student.data.TaskItem
import com.onetaskman.student.databinding.ActivityDashboardBinding
import com.onetaskman.student.databinding.ItemMonthHeaderBinding
import com.onetaskman.student.databinding.ItemTaskBinding
import com.onetaskman.student.ui.FocusModeDialog
import com.onetaskman.student.utils.FirebaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Add a sealed class to represent the different item types
sealed class ListItem {
    data class TaskListItem(val taskItem: TaskItem) : ListItem()
    data class HeaderItem(val month: String) : ListItem()
}

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val db = FirebaseHelper.db
    private val listItems = mutableListOf<ListItem>()
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var studentUid: String
    private lateinit var studentName: String
    private lateinit var enrolledClasses: List<String>
    private var currentViewMode = "pending"

    private var bleService: BleService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshTasks()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("session", MODE_PRIVATE)
        studentUid = prefs.getString("studentUid", "") ?: ""
        studentName = prefs.getString("studentName", "") ?: ""
        enrolledClasses = prefs.getStringSet("enrolledClasses", emptySet())?.toList() ?: emptyList()

        setupUI()

        // Start and bind to the service
        val serviceIntent = Intent(this, BleService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        LocalBroadcastManager.getInstance(this).registerReceiver(refreshReceiver, IntentFilter("com.onetaskman.student.REFRESH_TASKS"))
    }

    override fun onResume() {
        super.onResume()
        refreshTasks()
    }

    private fun refreshTasks() {
        if (currentViewMode == "pending") {
            loadPendingTasks()
        } else {
            loadHistoryTasks()
        }
        loadStats()
    }

    private fun setupUI() {
        binding.tvHeaderTitle.text = "Hi, $studentName"
        taskAdapter = TaskAdapter(listItems) { taskItem ->
            if (currentViewMode == "pending") {
                showFocusModeDialog(taskItem)
            } else {
                val intent = Intent(this, HistoryDetailActivity::class.java).apply {
                    putExtra("taskJson", taskItem.task.toJson())
                }
                startActivity(intent)
            }
        }
        binding.rvTasks.adapter = taskAdapter
        binding.rvTasks.layoutManager = LinearLayoutManager(this)

        binding.btnPending.setOnClickListener {
            currentViewMode = "pending"
            loadPendingTasks()
        }
        binding.btnHistory.setOnClickListener {
            currentViewMode = "history"
            loadHistoryTasks()
        }
        binding.btnClassesNav.setOnClickListener {
            startActivity(Intent(this, ClassManagerActivity::class.java))
        }
        binding.btnTasksNav.setOnClickListener { /* Already here */ }
        binding.btnLogout.setOnClickListener { logout() }
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshReceiver)
    }

    private fun loadPendingTasks() {
        binding.btnPending.isSelected = true
        binding.btnHistory.isSelected = false

        listItems.clear()
        taskAdapter.notifyDataSetChanged()
        var loadedCount = 0

        if (enrolledClasses.isEmpty()) return

        for (classId in enrolledClasses) {
            db.child("Queue").child(classId).child(studentUid).get()
                .addOnSuccessListener { snap ->
                    for (taskSnap in snap.children) {
                        if (taskSnap.key == "Stats") continue
                        val task = taskSnap.getValue(Task::class.java) ?: continue
                        if (task.Status == "Pending") {
                            listItems.add(ListItem.TaskListItem(TaskItem(classId, taskSnap.key!!, task)))
                        }
                    }
                    loadedCount++
                    if (loadedCount == enrolledClasses.size) {
                        listItems.sortBy { (it as ListItem.TaskListItem).taskItem.task.DueDate }
                        runOnUiThread { taskAdapter.notifyDataSetChanged() }
                    }
                }
        }
    }

    private fun loadHistoryTasks() {
        binding.btnPending.isSelected = false
        binding.btnHistory.isSelected = true

        listItems.clear()
        taskAdapter.notifyDataSetChanged()
        var loadedCount = 0

        if (enrolledClasses.isEmpty()) return

        val allTaskItems = mutableListOf<TaskItem>()

        for (classId in enrolledClasses) {
            db.child("History").child(classId).child(studentUid).get()
                .addOnSuccessListener { snap ->
                    for (taskSnap in snap.children) {
                        if (taskSnap.key == "Stats") continue

                        val taskMap = taskSnap.value as? Map<String, Any> ?: continue

                        val finishedAtValue = taskMap["FinishedAt"]
                        val finishedAtLong = when (finishedAtValue) {
                            is Long -> finishedAtValue
                            is String -> {
                                finishedAtValue.toLongOrNull() ?:
                                try {
                                    val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
                                    sdf.parse(finishedAtValue)?.time ?: 0L
                                } catch (e: java.text.ParseException) {
                                    0L
                                }
                            }
                            else -> 0L
                        }

                        val currentStepInt = (taskMap["CurrentStep"] as? Long)?.toInt() ?: 0
                        @Suppress("UNCHECKED_CAST")
                        val skippedStepsList = taskMap["SkippedSteps"] as? List<Any> ?: emptyList()
                        val skippedStepsIntList = skippedStepsList.mapNotNull { (it as? Long)?.toInt() }


                        val task = Task(
                            TaskName = taskMap["TaskName"] as? String ?: "",
                            TeacherName = taskMap["TeacherName"] as? String ?: "",
                            DueDate = taskMap["DueDate"] as? String ?: "",
                            Status = taskMap["Status"] as? String ?: "Completed",
                            CurrentStep = currentStepInt,
                            Steps = taskMap["Steps"] as? List<String> ?: emptyList(),
                            FinishedAt = finishedAtLong,
                            SkippedSteps = skippedStepsIntList
                        )
                        allTaskItems.add(TaskItem(classId, taskSnap.key!!, task))
                    }

                    loadedCount++
                    if (loadedCount == enrolledClasses.size) {
                        allTaskItems.sortByDescending { it.task.FinishedAt }

                        listItems.clear()
                        val groupedTasks = allTaskItems.groupBy {
                            val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                            sdf.format(Date(it.task.FinishedAt))
                        }

                        for ((month, tasks) in groupedTasks) {
                            listItems.add(ListItem.HeaderItem(month))
                            tasks.forEach { taskItem ->
                                listItems.add(ListItem.TaskListItem(taskItem))
                            }
                        }

                        runOnUiThread { taskAdapter.notifyDataSetChanged() }
                    }
                }
                .addOnFailureListener {
                    loadedCount++
                    if (loadedCount == enrolledClasses.size) {
                        runOnUiThread { taskAdapter.notifyDataSetChanged() }
                    }
                }
        }
    }


    private fun loadStats() {
        db.child("Users").child("Students").child(studentUid).child("stats").get()
            .addOnSuccessListener { snap ->
                val xp = snap.child("TotalXP").getValue(Int::class.java) ?: 0
                val streak = snap.child("CurrentStreak").getValue(Int::class.java) ?: 0
                val rank = snap.child("Rank").getValue(String::class.java) ?: "Civilian"
                binding.tvRankStreak.text = "🏅 $rank | 🔥 $streak-Day Streak"
                binding.lblXp.text = "XP: $xp"
            }
    }

    private fun showFocusModeDialog(taskItem: TaskItem) {
        if (!isBound || bleService == null) {
            Toast.makeText(this, "BLE Service not connected", Toast.LENGTH_SHORT).show()
            return
        }
        val bleManager = bleService!!.getBleManager()
        val dialog = FocusModeDialog(taskItem, bleManager)
        dialog.show(supportFragmentManager, "FocusModeDialog")
    }
}

class TaskAdapter(
    private val items: List<ListItem>,
    private val onClick: (TaskItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TASK = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.HeaderItem -> TYPE_HEADER
            is ListItem.TaskListItem -> TYPE_TASK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val binding = ItemMonthHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            TaskViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.HeaderItem -> (holder as HeaderViewHolder).bind(item)
            is ListItem.TaskListItem -> {
                (holder as TaskViewHolder).bind(item.taskItem)
                holder.itemView.setOnClickListener { onClick(item.taskItem) }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class TaskViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(taskItem: TaskItem) {
            binding.tvTaskName.text = taskItem.task.TaskName
            binding.tvDueDate.text = "Due: ${taskItem.task.DueDate}"
        }
    }

    class HeaderViewHolder(private val binding: ItemMonthHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(headerItem: ListItem.HeaderItem) {
            binding.tvMonthHeader.text = headerItem.month
        }
    }
}