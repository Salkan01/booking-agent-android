package com.example.bookingagent.sms.data.repo

import com.example.bookingagent.sms.data.db.BookingDao
import com.example.bookingagent.sms.data.model.BookingEntity
import kotlinx.coroutines.flow.Flow

class BookingRepository(
    private val bookingDao: BookingDao,
) {
    suspend fun insert(booking: BookingEntity): Long = bookingDao.insert(booking)

    suspend fun update(booking: BookingEntity) {
        bookingDao.update(booking)
    }

    suspend fun findRecentDuplicate(
        sender: String,
        rawSms: String,
        createdAfter: Long,
        createdBefore: Long,
        excludeId: Long,
    ): BookingEntity? =
        bookingDao.findRecentDuplicate(
            sender = sender,
            rawSms = rawSms,
            createdAfter = createdAfter,
            createdBefore = createdBefore,
            excludeId = excludeId,
        )

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

    fun getAll(): Flow<List<BookingEntity>> = bookingDao.getAll()
}
