package com.example.bookingagent.sms.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.model.BookingStatus

@Database(
    entities = [BookingEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(BookingStatusConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookingDao(): BookingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "booking-agent.db",
                ).build().also { instance = it }
            }
    }
}

class BookingStatusConverters {
    @TypeConverter
    fun fromBookingStatus(value: BookingStatus): String = value.name

    @TypeConverter
    fun toBookingStatus(value: String): BookingStatus = BookingStatus.valueOf(value)
}
