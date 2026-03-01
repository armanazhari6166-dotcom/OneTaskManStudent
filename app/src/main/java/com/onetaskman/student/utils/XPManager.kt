package com.onetaskman.student.utils

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.onetaskman.student.data.HeroRank

object XPManager {
    fun awardXP(db: DatabaseReference, studentUid: String, amount: Int) {
        val statsRef = db.child("Users").child("Students").child(studentUid).child("stats")

        statsRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val totalXP = currentData.child("TotalXP").getValue(Int::class.java) ?: 0
                val newXP = totalXP + amount
                val newRank = HeroRank.calculate(newXP)

                currentData.child("TotalXP").value = newXP
                currentData.child("Rank").value = newRank
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (!committed) {
                    Log.e("XPManager", "XP transaction failed: ${error?.message}")
                }
            }
        })
    }
}