package com.onetaskman.student

import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference

class StudentLoginActivity : AppCompatActivity() {
    var mAuth: FirebaseAuth? = null
    var db: DatabaseReference? = null

    var nameInput: EditText? = null
    var pinInput: EditText? = null
    var loginButton: Button? = null
}