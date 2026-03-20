package com.example.bookingagent.sms.booking

import com.example.bookingagent.sms.calendar.CalendarWriteResult
import com.example.bookingagent.sms.calendar.CalendarWriter
import com.example.bookingagent.sms.data.db.BookingDao
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.model.BookingStatus
import com.example.bookingagent.sms.data.repo.BookingRepository
import com.example.bookingagent.sms.settings.AppSettings
import com.example.bookingagent.sms.sms.SmsSendResult
import com.example.bookingagent.sms.sms.SmsSender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingOrchestratorTest {
    @Test
    fun duplicateProtectionPreventsDuplicateSideEffects() = runBlocking {
        val bookingDao = FakeBookingDao()
        val bookingRepository = BookingRepository(bookingDao)
        val smsSender = RecordingSmsSender()
        val orchestrator = BookingOrchestrator(
            bookingRepository = bookingRepository,
            smsSender = smsSender,
            calendarWriter = RecordingCalendarWriter(),
            appSettingsProvider = { AppSettings() },
        )
        val message =
            "Hej! Ledigt pass 2026-03-07 07:00 - 19:00 A Skänninge Alla. " +
                "Vill du anmäla intresse för passet svara: J annars N"

        orchestrator.handleIncomingSms(sender = "0700000000", messageBody = message)
        orchestrator.handleIncomingSms(sender = "0700000000", messageBody = message)

        assertEquals(1, smsSender.sentMessages.size)
        assertEquals("J", smsSender.sentMessages.single().messageBody)

        val allBookings = bookingDao.getStoredBookings()
        assertEquals(2, allBookings.size)
        val latestBooking = allBookings.first()
        assertEquals(BookingStatus.DUPLICATE_IGNORED, latestBooking.status)
        assertEquals("Duplicate SMS detected; side effects skipped", latestBooking.errorMessage)
        assertNull(latestBooking.replySent)
    }

    @Test
    fun confirmationMatchingOnlyWorksForRepliedJOffers() = runBlocking {
        val bookingDao = FakeBookingDao()
        val bookingRepository = BookingRepository(bookingDao)
        bookingRepository.insert(
            BookingEntity(
                sender = "0700000000",
                rawSms = "previous offer",
                messageType = "offer",
                shiftDate = "2026-04-12",
                startTime = "07:00",
                endTime = "19:00",
                details = "A Skänninge Alla",
                replySent = "N",
                status = BookingStatus.REPLIED_N,
                eventId = null,
                errorMessage = null,
                createdAt = 1L,
            ),
        )
        val calendarWriter = RecordingCalendarWriter()
        val orchestrator = BookingOrchestrator(
            bookingRepository = bookingRepository,
            smsSender = RecordingSmsSender(),
            calendarWriter = calendarWriter,
            appSettingsProvider = { AppSettings() },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = "Du är nu bokad på pass 2026-04-12 07:00 - 19:00 A Skänninge Alla",
        )

        assertEquals(0, calendarWriter.createEventCalls)

        val savedConfirmation = bookingDao.getStoredBookings().first()
        assertEquals("confirmation", savedConfirmation.messageType)
        assertEquals(BookingStatus.FAILED, savedConfirmation.status)
        assertEquals("No matching accepted offer found", savedConfirmation.errorMessage)
        assertNull(savedConfirmation.eventId)
    }
}

private class RecordingSmsSender : SmsSender() {
    val sentMessages = mutableListOf<SentMessage>()

    override fun send(destination: String, messageBody: String): SmsSendResult {
        sentMessages += SentMessage(destination = destination, messageBody = messageBody)
        return SmsSendResult.Success
    }
}

private data class SentMessage(
    val destination: String,
    val messageBody: String,
)

private class RecordingCalendarWriter : CalendarWriter() {
    var createEventCalls: Int = 0

    override fun createEvent(
        shiftInfo: com.example.bookingagent.sms.sms.ShiftInfo,
        rawSms: String,
        calendarName: String,
        eventTitle: String,
    ): CalendarWriteResult {
        createEventCalls += 1
        return CalendarWriteResult.Success(eventId = 99L)
    }
}

private class FakeBookingDao : BookingDao {
    private val storedBookings = mutableListOf<BookingEntity>()
    private var nextId = 1L

    override suspend fun insert(booking: BookingEntity): Long {
        val assignedId = nextId++
        storedBookings += booking.copy(id = assignedId)
        return assignedId
    }

    override suspend fun update(booking: BookingEntity) {
        val index = storedBookings.indexOfFirst { stored -> stored.id == booking.id }
        storedBookings[index] = booking
    }

    override fun getAll(): Flow<List<BookingEntity>> = flowOf(getStoredBookings())

    override suspend fun findRecentDuplicate(
        sender: String,
        rawSms: String,
        createdAfter: Long,
        createdBefore: Long,
        excludeId: Long,
    ): BookingEntity? =
        storedBookings
            .filter { booking ->
                booking.sender == sender &&
                    booking.rawSms == rawSms &&
                    booking.createdAt >= createdAfter &&
                    booking.createdAt <= createdBefore &&
                    booking.id != excludeId
            }
            .sortedWith(compareByDescending<BookingEntity> { it.createdAt }.thenByDescending { it.id })
            .firstOrNull()

    override suspend fun findLatestAcceptedOffer(
        sender: String,
        shiftDate: String,
        startTime: String,
        endTime: String,
        details: String,
    ): BookingEntity? =
        storedBookings
            .filter { booking ->
                booking.sender == sender &&
                    booking.shiftDate == shiftDate &&
                    booking.startTime == startTime &&
                    booking.endTime == endTime &&
                    booking.details == details &&
                    booking.status == BookingStatus.REPLIED_J
            }
            .sortedWith(compareByDescending<BookingEntity> { it.createdAt }.thenByDescending { it.id })
            .firstOrNull()

    override fun findAllPendingConfirmations(): Flow<List<BookingEntity>> =
        flowOf(
            storedBookings
                .filter { booking -> booking.status == BookingStatus.REPLIED_J }
                .sortedWith(compareByDescending<BookingEntity> { it.createdAt }.thenByDescending { it.id }),
        )

    fun getStoredBookings(): List<BookingEntity> =
        storedBookings.sortedWith(compareByDescending<BookingEntity> { it.createdAt }.thenByDescending { it.id })
}
