package com.onetaskman.student.auth

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import com.onetaskman.student.DashboardActivity
import com.onetaskman.student.data.StudentProfile
import com.onetaskman.student.databinding.ActivityLoginBinding
import com.onetaskman.student.utils.FirebaseHelper
import com.onetaskman.student.utils.XPManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var functions: FirebaseFunctions
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        functions = FirebaseFunctions.getInstance("us-central1")
        auth = FirebaseAuth.getInstance()

        binding.btnLogin.setOnClickListener { handleLogin() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun handleLogin() {
        val name = binding.etDisplayName.text.toString().trim()
        val pin = binding.etPin.text.toString().trim()

        if (name.isEmpty() || pin.isEmpty()) {
            showToast("Please enter both name and PIN.")
            return
        }

        binding.loginProgress.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        val data = hashMapOf(
            "name" to name,
            "pin" to pin
        )

        functions
            .getHttpsCallable("loginWithPin")
            .call(data)
            .addOnCompleteListener { task ->
                binding.loginProgress.visibility = View.GONE
                binding.btnLogin.isEnabled = true

                if (!task.isSuccessful) {
                    val e = task.exception
                    showAlert("Login Failed: ${e?.message}")
                    return@addOnCompleteListener
                }

                val result = task.result?.data as? Map<String, Any>
                val token = result?.get("token") as? String

                if (token != null) {
                    auth.signInWithCustomToken(token)
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                val uid = authTask.result?.user?.uid ?: ""
                                fetchUserProfileAndProceed(uid)
                            } else {
                                showAlert("Authentication failed after token validation.")
                            }
                        }
                } else {
                    showAlert("Failed to retrieve authentication token from server.")
                }
            }
    }

    private fun fetchUserProfileAndProceed(uid: String) {
        // Specify the regional URL here as well
        val dbUrl = "https://one-task-man-global-default-rtdb.asia-southeast1.firebasedatabase.app"
        val db = FirebaseDatabase.getInstance(dbUrl).reference
        
        db.child("Users").child("Students").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        showAlert("Profile not found.")
                        return
                    }

                    val profile = snapshot.getValue(StudentProfile::class.java)
                    
                    // JSON check: your DB stores these as a simple list of strings
                    val enrolledClasses = snapshot.child("enrolledClasses").children.mapNotNull { 
                        it.value?.toString() 
                    }

                    if (profile != null) {
                        saveSession(uid, profile, enrolledClasses)
                        processDailyLogin(uid)
                        
                        startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                        finish()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showAlert("DB Error: ${error.message}")
                }
            })
    }


    private fun saveSession(uid: String, profile: StudentProfile, enrolledClasses: List<String>) {
        getSharedPreferences("session", MODE_PRIVATE).edit()
            .putString("studentUid", uid)
            .putString("studentName", profile.displayName)
            .putString("studentPin", profile.pin)
            .putStringSet("enrolledClasses", enrolledClasses.toSet())
            .apply()
    }

    private fun processDailyLogin(uid: String) {
        val db = FirebaseHelper.db
        val statsRef = db.child("Users").child("Students").child(uid).child("stats")

        statsRef.get().addOnSuccessListener { snap ->
            val streak = snap.child("CurrentStreak").getValue(Int::class.java) ?: 0
            val lastLogin = snap.child("LastLoginDate").getValue(String::class.java) ?: ""

            val today = FirebaseHelper.todayDateString()
            if (today == lastLogin) return@addOnSuccessListener

            val yesterday = FirebaseHelper.yesterdayDateString()
            val newStreak = if (lastLogin == yesterday) streak + 1 else 1

            var bonusXP = 100
            if (newStreak == 3) bonusXP += 50
            if (newStreak == 7) bonusXP += 200
            if (newStreak == 14) bonusXP += 500

            XPManager.awardXP(db, uid, bonusXP)

            val updates = mapOf(
                "CurrentStreak" to newStreak,
                "LastLoginDate" to today
            )
            statsRef.updateChildren(updates)
        }
    }

    private fun resetUI() {
        binding.loginProgress.visibility = View.GONE
        binding.btnLogin.isEnabled = true
    }

    private fun showAlert(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Login Message")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}