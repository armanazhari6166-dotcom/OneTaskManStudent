package com.onetaskman.student

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import com.onetaskman.student.auth.LoginActivity
import com.onetaskman.student.auth.RegisterActivity
import com.onetaskman.student.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        // **THE DEFINITIVE FIX IS HERE**
        // This runs every time the screen becomes visible, ensuring no stale user is ever logged in
        // when the user is on the welcome screen.
        if (auth.currentUser != null) {
            auth.signOut()
        }
    }
}