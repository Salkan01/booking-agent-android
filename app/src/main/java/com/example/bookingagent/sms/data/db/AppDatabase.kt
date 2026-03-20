package com.example.bookingagent.sms.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.model.BookingStatus
import com.example.bookingagent.sms.data.model.ReviewState

@Database(
    entities = [BookingEntity::class],
    version = 4,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE bookings ADD COLUMN errorMessage TEXT")
                }
            }

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE bookings ADD COLUMN intendedReply TEXT")
                    database.execSQL("ALTER TABLE bookings ADD COLUMN dryRun INTEGER NOT NULL DEFAULT 0")
                }
            }

        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE bookings ADD COLUMN recommendedReply TEXT")
                    database.execSQL("ALTER TABLE bookings ADD COLUMN recommendationFromRules INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE bookings ADD COLUMN reviewState TEXT NOT NULL DEFAULT 'AUTO_PROCESSED'")
                    database.execSQL("ALTER TABLE bookings ADD COLUMN matchedBookingId INTEGER")
                }
            }
    }
}

class BookingStatusConverters {
    @TypeConverter
    fun fromBookingStatus(value: BookingStatus): String = value.name

    @TypeConverter
    fun toBookingStatus(value: String): BookingStatus = BookingStatus.valueOf(value)

    @TypeConverter
    fun fromReviewState(value: ReviewState): String = value.name

    @TypeConverter
    fun toReviewState(value: String): ReviewState = ReviewState.valueOf(value)
}
