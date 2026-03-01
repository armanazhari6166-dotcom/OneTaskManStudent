package com.onetaskman.student

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.onetaskman.student.databinding.ActivityClassManagerBinding
import com.onetaskman.student.databinding.ItemClassBinding
import com.onetaskman.student.utils.FirebaseHelper

class ClassManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassManagerBinding
    private val db = FirebaseHelper.db
    private lateinit var prefs: SharedPreferences
    private val enrolledClasses = mutableListOf<String>()
    private lateinit var classAdapter: ClassAdapter
    private lateinit var studentUid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("session", MODE_PRIVATE)
        studentUid = prefs.getString("studentUid", "") ?: ""

        setupUI()
        loadEnrolledClasses()
    }

    private fun setupUI() {
        classAdapter = ClassAdapter(enrolledClasses) { classId -> leaveClassAction(classId) }
        binding.rvEnrolledClasses.adapter = classAdapter
        binding.rvEnrolledClasses.layoutManager = LinearLayoutManager(this)

        binding.btnJoinClass.setOnClickListener {
            val inviteCode = binding.etInviteCode.text.toString().trim()
            if (inviteCode.isNotEmpty()) {
                joinClass(inviteCode)
            }
        }

        binding.btnTasksNav.setOnClickListener { 
            finish() // Go back to Dashboard
        }
        binding.btnClassesNav.setOnClickListener { /* Already here */ }
    }

    private fun joinClass(inviteCode: String) {
        val studentName = prefs.getString("studentName", "") ?: return
        val studentPin = prefs.getString("studentPin", "") ?: return

        db.child("InviteCodes").child(inviteCode.uppercase()).get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) { showAlert("Invalid invite code."); return@addOnSuccessListener }

                val classId = snap.child("classID").getValue(String::class.java) ?: return@addOnSuccessListener
                val className = snap.child("className").getValue(String::class.java) ?: ""

                if (enrolledClasses.contains(classId)) {
                    showAlert("You are already in this class."); return@addOnSuccessListener
                }

                val studentEntry = mapOf(
                    "displayName" to studentName,
                    "pin" to studentPin,
                    "joinedAt" to System.currentTimeMillis()
                )
                db.child("Classes").child(classId).child("students").child(studentUid).setValue(studentEntry)

                val currentClasses = prefs.getStringSet("enrolledClasses", emptySet())?.toMutableSet() ?: mutableSetOf()
                currentClasses.add(classId)
                prefs.edit().putStringSet("enrolledClasses", currentClasses).apply()
                enrolledClasses.add(classId)

                showAlert("Joined $className successfully!")
                loadEnrolledClasses()
            }
    }

    private fun leaveClassAction(classId: String) {
        AlertDialog.Builder(this)
            .setTitle("Leave Class")
            .setMessage("Are you sure you want to leave this class?")
            .setPositiveButton("Yes") { _, _ ->
                db.child("Classes").child(classId).child("students").child(studentUid).removeValue()
                val currentClasses = prefs.getStringSet("enrolledClasses", emptySet())?.toMutableSet() ?: mutableSetOf()
                currentClasses.remove(classId)
                prefs.edit().putStringSet("enrolledClasses", currentClasses).apply()
                enrolledClasses.remove(classId)
                loadEnrolledClasses()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun loadEnrolledClasses() {
        val currentClasses = prefs.getStringSet("enrolledClasses", emptySet())?.toList() ?: emptyList()
        enrolledClasses.clear()
        enrolledClasses.addAll(currentClasses)
        classAdapter.notifyDataSetChanged()
    }

    private fun showAlert(msg: String) {
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show()
    }
}

class ClassAdapter(
    private val classIds: List<String>,
    private val onLeaveClick: (String) -> Unit
) : RecyclerView.Adapter<ClassAdapter.ClassViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val binding = ItemClassBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClassViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        val classId = classIds[position]
        holder.bind(classId, onLeaveClick)
    }

    override fun getItemCount(): Int = classIds.size

    class ClassViewHolder(private val binding: ItemClassBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(classId: String, onLeaveClick: (String) -> Unit) {
            FirebaseHelper.db.child("Classes").child(classId).child("className").get()
                .addOnSuccessListener { 
                    binding.tvClassName.text = it.getValue(String::class.java) ?: "Unknown Class"
                }
            binding.btnLeave.setOnClickListener { onLeaveClick(classId) }
        }
    }
}