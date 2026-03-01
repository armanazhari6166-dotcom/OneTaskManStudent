package com.onetaskman.student.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.onetaskman.student.databinding.ActivityRegisterBinding
import com.onetaskman.student.utils.FirebaseHelper

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.registerButton.setOnClickListener { handleRegister() }
    }

    private fun handleRegister() {
        val displayName = binding.etDisplayName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val pin = binding.etPin.text.toString().trim()

        if (displayName.isEmpty() || email.isEmpty() || password.isEmpty() || pin.isEmpty()) {
            showToast("All fields are required."); return
        }

        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            showToast("PIN must be exactly 4 digits"); return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser!!
                    val uid = user.uid

                    val profile = mapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "pin" to pin,
                        "emailVerified" to false,
                        "enrolledClasses" to emptyList<String>(), // Correctly create an empty list
                        "stats" to mapOf(
                            "TotalXP" to 0,
                            "CurrentStreak" to 0,
                            "LastLoginDate" to "",
                            "Rank" to "Civilian"
                        )
                    )
                    FirebaseHelper.db.child("Users").child("Students").child(uid).setValue(profile)

                    user.sendEmailVerification()

                    AlertDialog.Builder(this)
                        .setTitle("Account Created")
                        .setMessage("Please check your email to verify your account before logging in.")
                        .setPositiveButton("OK") { _, _ ->
                            val intent = Intent(this, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
                        .show()

                } else {
                    showAlert("Error: ${task.exception?.message}")
                }
            }
    }

    private fun showAlert(msg: String) =
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show()

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}