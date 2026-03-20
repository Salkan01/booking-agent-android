package com.example.bookingagent.sms.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.bookingagent.sms.data.model.BookingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {
    @Insert
    suspend fun insert(booking: BookingEntity): Long

    @Update
    suspend fun update(booking: BookingEntity)

    @Query("SELECT * FROM bookings ORDER BY createdAt DESC, id DESC")
    fun getAll(): Flow<List<BookingEntity>>

    @Query(
        """
        SELECT * FROM bookings
        WHERE sender = :sender
          AND shiftDate = :shiftDate
          AND startTime = :startTime
          AND endTime = :endTime
          AND details = :details
          AND status = 'REPLIED_J'
        ORDER BY createdAt DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestAcceptedOffer(
        sender: String,
        shiftDate: String,
        startTime: String,
        endTime: String,
        details: String,
    ): BookingEntity?

    @Query(
        """
        SELECT * FROM bookings
        WHERE status = 'REPLIED_J'
        ORDER BY createdAt DESC, id DESC
        """,
    )
    fun findAllPendingConfirmations(): Flow<List<BookingEntity>>
}
