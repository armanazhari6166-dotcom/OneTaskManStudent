package com.onetaskman.student.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.database.DatabaseReference
import com.onetaskman.student.data.Task
import com.onetaskman.student.data.TaskItem
import com.onetaskman.student.utils.FirebaseHelper
import com.onetaskman.student.utils.NotificationHelper
import com.onetaskman.student.utils.XPManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StudentBleManager(
    private val context: Context,
    private val db: DatabaseReference,
    private val studentUid: String,
    private val studentName: String,
    private val enrolledClasses: List<String>
) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    var isConnected = false
        private set

    private val writeQueue: ArrayDeque<ByteArray> = ArrayDeque()
    private var isWriting = false
    
    // Brace-counting buffer for robust JSON framing
    private val notifyBuffer = StringBuilder()
    private var braceDepth = 0
    private var started = false

    // Cache to resolve class_id for progress updates
    private val taskClassMap = mutableMapOf<String, String>()

    private var lastHeartbeat = 0L
    private var hbActive = false
    private var watchdogStarted = false
    private var inFocusSession = false
    private val watchdogExecutor = Executors.newSingleThreadScheduledExecutor()

    companion object {
        const val ACTION_DEVICE_DONE = "com.onetaskman.student.DEVICE_DONE"
        const val ACTION_DEVICE_ACTIVE = "com.onetaskman.student.DEVICE_ACTIVE"
        const val ACTION_REFRESH_TASKS = "com.onetaskman.student.REFRESH_TASKS"
        const val ACTION_STEP_DONE = "com.onetaskman.student.STEP_DONE"
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBtPermission()) { Log.e("BLE", "BLUETOOTH_CONNECT permission missing"); return }
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun startDeviceMode(taskItem: TaskItem) {
        Log.d("BLE", "startDeviceMode called for ${'$'}{taskItem.taskId} — tasks sent via REQUEST_TASKS flow")
        sendAllPendingTasksToDevice()
    }

    fun markDeviceInactive() {
        db.child("Users").child("Students").child(studentUid)
            .child("LastSeen").setValue(mapOf(
                "status" to "OFFLINE",
                "timestamp" to getCurrentTimestamp()
            ))
    }

    fun bleSend(payload: JSONObject) {
        if (!isConnected || bluetoothGatt == null || characteristic == null) {
            Log.w("BLE", "bleSend skipped — not ready")
            return
        }

        val data = payload.toString().toByteArray(Charsets.UTF_8)
        synchronized(writeQueue) {
            writeQueue.add(data)
        }
        writeNext()
    }

    fun sendAllPendingTasksToDevice() {
        Log.d("BLE", "sendAllPendingTasksToDevice — classes=${'$'}{enrolledClasses.size} ${'$'}enrolledClasses")
        if (enrolledClasses.isEmpty()) {
            Log.e("BLE", "No enrolled classes — cannot send tasks"); return
        }

        val allTasks = mutableListOf<TaskItem>()
        var loadedCount = 0

        for (classId in enrolledClasses) {
            db.child("Queue").child(classId).child(studentUid).get()
                .addOnSuccessListener { snap ->
                    Log.d("BLE", "Queue[${'$'}classId]: ${'$'}{snap.childrenCount} nodes")
                    for (ts in snap.children) {
                        if (ts.key == "Stats") continue
                        val task = ts.getValue(Task::class.java) ?: continue
                        Log.d("BLE", "  ${'$'}{ts.key}: status=${'$'}{task.Status}")
                        if (task.Status == "Pending") allTasks.add(TaskItem(classId, ts.key!!, task))
                    }
                    loadedCount++
                    if (loadedCount == enrolledClasses.size) dispatchTasks(allTasks)
                }
                .addOnFailureListener { e ->
                    Log.e("BLE", "Queue fetch failed [${'$'}classId]: ${'$'}{e.message}")
                    loadedCount++
                    if (loadedCount == enrolledClasses.size) dispatchTasks(allTasks)
                }
        }
    }

    private fun dispatchTasks(allTasks: MutableList<TaskItem>) {
        Log.d("BLE", "Pending tasks found: ${'$'}{allTasks.size}")
        allTasks.sortBy { it.task.DueDate }
        val toSend = allTasks.take(5)
        Log.d("BLE", "Dispatching ${'$'}{toSend.size} tasks to device")
        // bleSend(JSONObject().put("type", "CLEAR_TASKS")) // Removed as device does not handle it
        toSend.forEach { sendTaskToDevice(it) }
    }
    
    @SuppressLint("MissingPermission")
    private fun writeNext() {
        if (isWriting) return
        if (writeQueue.isEmpty()) return
        if (!hasBtPermission()) return

        val data = synchronized(writeQueue) { writeQueue.removeFirst() }
        isWriting = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                characteristic!!,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            characteristic!!.value = data
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(characteristic)
        }
        Log.d("BLE", "📤 queued write (${'$'}{data.size})")
    }

    private fun hasBtPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        else true

    @SuppressLint("MissingPermission")
    private fun hardResetBle() {
        isConnected = false
        hbActive = false
        lastHeartbeat = 0L
        inFocusSession = false
        writeQueue.clear()
        isWriting = false
        notifyBuffer.clear()
        braceDepth = 0
        started = false
        try {
            if (hasBtPermission()) {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
            }
        } catch (e: Exception) {
            Log.w("BLE", "Error during BLE hard reset, ignoring.", e)
        }
        bluetoothGatt = null
        characteristic = null
    }

    private fun sendLoginMessage() {
        val msg = JSONObject().apply {
            put("v", 1)
            put("type", "LOGIN")
            put("student_uid", studentUid)
            put("display_name", studentName)
            put("session_id", "session_${'$'}{studentUid.take(6)}")
        }
        Log.d("BLE", "Sending LOGIN for ${'$'}studentName (${'$'}studentUid)")
        bleSend(msg)
    }

    private fun sendTaskToDevice(item: TaskItem) {
        taskClassMap[item.taskId] = item.classId // ✅ Cache the mapping
        Log.d("BLE", "Queueing task for send: ${'$'}{item.task.TaskName} (${'$'}{item.taskId})")
        val msg = JSONObject().apply {
            put("v", 1)
            put("type", "TASK_ITEM")
            put("task", JSONObject().apply {
                put("task_id", item.taskId)
                put("class_id", item.classId)
                put("name", item.task.TaskName)
                put("teacher", item.task.TeacherName)
                put("due", item.task.DueDate)
                put("steps", JSONArray(item.task.Steps))
                put("current_step", item.task.CurrentStep)
            })
        }
        bleSend(msg)
    }

    private fun startHeartbeatWatchdog() {
        watchdogStarted = true
        watchdogExecutor.scheduleWithFixedDelay({
            if (!isConnected || !hbActive || inFocusSession) return@scheduleWithFixedDelay
            if (lastHeartbeat == 0L) return@scheduleWithFixedDelay
            if (System.currentTimeMillis() - lastHeartbeat > 35_000L) {
                Log.w("BLE", "Heartbeat lost — unlocking app")
                hardResetBle()
                markDeviceInactive()
                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_DEVICE_DONE))
                watchdogExecutor.shutdown()
            }
        }, 2, 2, TimeUnit.SECONDS)
    }

    private fun handleIncomingChunk(chunk: String) {
        for (ch in chunk) {
            if (ch == '{') {
                braceDepth++
                started = true
            }
            if (started) notifyBuffer.append(ch)
            if (ch == '}') {
                braceDepth--
                if (started && braceDepth == 0) {
                    val packet = notifyBuffer.toString()
                    notifyBuffer.clear()
                    started = false
                    Log.d("BLE", "✅ Full packet received: ${'$'}packet")
                    handleBleMessage(packet)
                }
            }
        }
    }

    private fun handleBleMessage(json: String) {
        try {
            val msg = JSONObject(json)
            val type = msg.optString("type", "")
            Log.d("BLE", "📥 Processing: type=${'$'}type")

            when (type) {
                "HEARTBEAT" -> {
                    lastHeartbeat = System.currentTimeMillis()
                    hbActive = true
                    if (!watchdogStarted) startHeartbeatWatchdog()
                }
                "REQUEST_TASKS" -> {
                    if (!inFocusSession) {
                        inFocusSession = true
                        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_DEVICE_ACTIVE))
                    }
                    sendAllPendingTasksToDevice()
                }
                "TASK_PROGRESS" -> {
                    val taskId = msg.optString("task_id")
                    var classId = msg.optString("class_id")
                    val currentStep = msg.optInt("current_step")
                    val skippedArr = msg.optJSONArray("skipped_steps") ?: JSONArray()
                    val skipped = (0 until skippedArr.length()).map { skippedArr.getInt(it) }

                    if (classId.isBlank() && taskId.isNotBlank()) {
                        classId = taskClassMap[taskId].orEmpty()
                        Log.w("BLE", "TASK_PROGRESS missing class_id, using cache=${'$'}classId")
                    }

                    if (classId.isNotEmpty() && taskId.isNotEmpty()) {
                        db.child("Queue").child(classId).child(studentUid).child(taskId)
                            .updateChildren(mapOf(
                                "CurrentStep" to currentStep,
                                "SkippedSteps" to skipped,
                                "LastUpdate" to getCurrentTimestamp()
                            ))
                    } else {
                        Log.e("BLE", "TASK_PROGRESS drop: missing ids taskId=${'$'}taskId classId=${'$'}classId")
                    }
                }
                "STEP_DONE" -> {
                    LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_STEP_DONE).putExtra("data", msg.toString()))
                }
                "TASK_COMPLETE" -> {
                    val taskId = msg.optString("task_id")
                    val classId = msg.optString("class_id")
                    val taskName = msg.optString("task_name")
                    val skipped = (msg.optJSONArray("skipped_steps")?.length() ?: 0) > 0
                    Log.d("BLE", "🏁 TASK_COMPLETE task=${'$'}taskId class=${'$'}classId skipped=${'$'}skipped")
                    handleTaskComplete(taskId, classId, taskName, skipped)
                    
                    NotificationHelper.showTaskComplete(context, taskName.ifEmpty { "Task" }, skipped)

                    LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_REFRESH_TASKS))
                }
                "DEVICE_LOGOUT" -> {
                    inFocusSession = false
                    markDeviceInactive()
                    LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_DEVICE_DONE))
                }
                else -> Log.w("BLE", "⚠️ Unknown type: ${'$'}type")
            }
        } catch (e: Exception) {
            Log.e("BLE", "Parse error: ${'$'}{e.message} | raw=${'$'}json")
        }
    }

    private fun handleTaskComplete(taskId: String, classId: String, taskName: String, skipped: Boolean) {
        if (taskId.isEmpty() || classId.isEmpty()) {
            Log.e("BLE", "handleTaskComplete: missing ids taskId=${'$'}taskId classId=${'$'}classId"); return
        }
        db.child("Queue").child(classId).child(studentUid).child(taskId).get()
            .addOnSuccessListener { snap ->
                val task = snap.getValue(Task::class.java) ?: run {
                    Log.e("BLE", "Task ${'$'}taskId not found in Queue/${'$'}classId"); return@addOnSuccessListener
                }
                val finishedTask = task.copy(Status = "Completed", FinishedAt = getCurrentTimestamp())
                db.child("History").child(classId).child(studentUid).child(taskId).setValue(finishedTask.toMap())
                db.child("Queue").child(classId).child(studentUid).child(taskId).removeValue()
                val xp = if (skipped) 80 else 100
                XPManager.awardXP(db, studentUid, xp)
                Log.d("BLE", "✅ '${'$'}taskName' → History, +${'$'}xp XP")
            }
            .addOnFailureListener { e -> Log.e("BLE", "handleTaskComplete failed: ${'$'}{e.message}") }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BLE", "onConnectionStateChange status=${'$'}status newState=${'$'}newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                if (!hasBtPermission()) return
                Log.d("BLE", "Connected — requesting MTU 512")
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("BLE", "Disconnected (status=${'$'}status)")
                hardResetBle()
                markDeviceInactive()
                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_DEVICE_DONE))
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("BLE", "MTU=${'$'}mtu status=${'$'}status — discovering services")
            if (!hasBtPermission()) return
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("BLE", "onServicesDiscovered status=${'$'}status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BLE", "Service discovery failed: ${'$'}status"); return
            }
            val service = gatt.getService(BleConstants.SERVICE_UUID)
            if (service == null) { Log.e("BLE", "Service not found: ${'$'}{BleConstants.SERVICE_UUID}"); return }

            characteristic = service.getCharacteristic(BleConstants.CHAR_UUID)
            if (characteristic == null) { Log.e("BLE", "Char not found: ${'$'}{BleConstants.CHAR_UUID}"); return }

            val props = characteristic!!.properties
            Log.d("BLE", "Char props = ${'$'}props notify=${'$'}{props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0}")

            Log.d("BLE", "Found service + char — enabling notifications")
            enableNotifications(gatt, characteristic!!)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d("BLE", "onDescriptorWrite uuid=${'$'}{descriptor.uuid} status=${'$'}status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BLE", "Descriptor write failed: ${'$'}status"); return
            }
            if (descriptor.uuid == BleConstants.CCCD) {
                Log.d("BLE", "Notifications enabled — sending LOGIN")
                sendLoginMessage()
            }
        }
        
        @Deprecated("Used for API < 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val chunk = c.value.toString(Charsets.UTF_8)
            Log.d("BLE", "chunk legacy: ${'$'}chunk")
            handleIncomingChunk(chunk)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val chunk = value.toString(Charsets.UTF_8)
            Log.d("BLE", "chunk api33: ${'$'}chunk")
            handleIncomingChunk(chunk)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d("BLE", "onCharacteristicWrite status=${'$'}status")
            isWriting = false
            writeNext()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) {
        if (!hasBtPermission()) return
        gatt.setCharacteristicNotification(c, true)
        val descriptor = c.getDescriptor(BleConstants.CCCD)
        if (descriptor == null) {
            Log.e("BLE", "CCCD not found — notifications disabled"); return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun getCurrentTimestamp() = FirebaseHelper.currentTimestamp()
}
