package com.example.bookingagent.sms.data.repo

import com.example.bookingagent.sms.data.db.BookingDao
import com.example.bookingagent.sms.data.model.BookingEntity

class BookingRepository(
    private val bookingDao: BookingDao,
) {
    suspend fun insert(booking: BookingEntity): Long = bookingDao.insert(booking)

    suspend fun update(booking: BookingEntity) {
        bookingDao.update(booking)
    }

    suspend fun findLatestAcceptedOffer(
        sender: String,
        shiftDate: String,
        startTime: String,
        endTime: String,
        details: String,
    ): BookingEntity? =
        bookingDao.findLatestAcceptedOffer(
            sender = sender,
            shiftDate = shiftDate,
            startTime = startTime,
            endTime = endTime,
            details = details,
        )
}
