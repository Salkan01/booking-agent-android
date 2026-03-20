package com.example.bookingagent.sms.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val rawSms: String,
    val messageType: String,
    val shiftDate: String?,
    val startTime: String?,
    val endTime: String?,
    val details: String?,
    val replySent: String?,
    val recommendedReply: String?,
    val recommendationFromRules: Boolean,
    val intendedReply: String?,
    val reviewState: ReviewState,
    val status: BookingStatus,
    val matchedBookingId: Long?,
    val eventId: Long?,
    val errorMessage: String?,
    val dryRun: Boolean,
    val createdAt: Long,
)
