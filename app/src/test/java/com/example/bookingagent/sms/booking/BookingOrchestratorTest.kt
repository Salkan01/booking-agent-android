package com.example.bookingagent.sms.booking

import com.example.bookingagent.sms.calendar.CalendarWriteResult
import com.example.bookingagent.sms.calendar.CalendarWriter
import com.example.bookingagent.sms.data.db.BookingDao
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.model.BookingStatus
import com.example.bookingagent.sms.data.repo.BookingRepository
import com.example.bookingagent.sms.settings.AppSettings
import com.example.bookingagent.sms.sms.DebugSmsSimulator
import com.example.bookingagent.sms.sms.SmsGateway
import com.example.bookingagent.sms.sms.SmsGatewayResult
import com.example.bookingagent.sms.sms.ShiftInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingOrchestratorTest {
    @Test
    fun duplicateProtectionPreventsDuplicateSideEffects() = runBlocking {
        val bookingDao = FakeBookingDao()
        val realSmsGateway = RecordingSmsGateway()
        val dryRunSmsGateway = RecordingSmsGateway()
        val orchestrator = BookingOrchestrator(
            bookingRepository = BookingRepository(bookingDao),
            realSmsGateway = realSmsGateway,
            dryRunSmsGateway = dryRunSmsGateway,
            calendarWriter = RecordingCalendarWriter(),
            appSettingsProvider = { AppSettings(dryRunMode = false) },
        )
        val message =
            "Hej! Ledigt pass 2026-03-07 07:00 - 19:00 A Skänninge Alla. " +
                "Vill du anmäla intresse för passet svara: J annars N"

        orchestrator.handleIncomingSms(sender = "0700000000", messageBody = message)
        orchestrator.handleIncomingSms(sender = "0700000000", messageBody = message)

        assertEquals(1, realSmsGateway.sentMessages.size)
        assertEquals("J", realSmsGateway.sentMessages.single().messageBody)
        assertEquals(0, dryRunSmsGateway.sentMessages.size)

        val latestBooking = bookingDao.getStoredBookings().first()
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
                intendedReply = null,
                status = BookingStatus.REPLIED_N,
                eventId = null,
                errorMessage = null,
                dryRun = false,
                createdAt = 1L,
            ),
        )
        val calendarWriter = RecordingCalendarWriter()
        val orchestrator = BookingOrchestrator(
            bookingRepository = bookingRepository,
            realSmsGateway = RecordingSmsGateway(),
            dryRunSmsGateway = RecordingSmsGateway(),
            calendarWriter = calendarWriter,
            appSettingsProvider = { AppSettings(dryRunMode = false) },
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

    @Test
    fun dryRunOfferProcessingDoesNotCallRealSmsSending() = runBlocking {
        val bookingDao = FakeBookingDao()
        val realSmsGateway = RecordingSmsGateway()
        val dryRunSmsGateway = RecordingSmsGateway()
        val orchestrator = BookingOrchestrator(
            bookingRepository = BookingRepository(bookingDao),
            realSmsGateway = realSmsGateway,
            dryRunSmsGateway = dryRunSmsGateway,
            calendarWriter = RecordingCalendarWriter(),
            appSettingsProvider = { AppSettings(dryRunMode = true) },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = "Hej! Ledigt pass 2026-03-07 07:00 - 19:00 A Skänninge Alla. Vill du anmäla intresse för passet svara: J annars N",
        )

        assertEquals(0, realSmsGateway.sentMessages.size)
        assertEquals(1, dryRunSmsGateway.sentMessages.size)
    }

    @Test
    fun dryRunOfferProcessingStoresIntendedReply() = runBlocking {
        val bookingDao = FakeBookingDao()
        val orchestrator = BookingOrchestrator(
            bookingRepository = BookingRepository(bookingDao),
            realSmsGateway = RecordingSmsGateway(),
            dryRunSmsGateway = RecordingSmsGateway(),
            calendarWriter = RecordingCalendarWriter(),
            appSettingsProvider = { AppSettings(dryRunMode = true) },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = "Hej! Ledigt pass 2026-03-07 07:00 - 19:00 A Skänninge Alla. Vill du anmäla intresse för passet svara: J annars N",
        )

        val savedBooking = bookingDao.getStoredBookings().first()
        assertTrue(savedBooking.dryRun)
        assertEquals("J", savedBooking.intendedReply)
        assertNull(savedBooking.replySent)
        assertEquals("Dry run mode: no real SMS was sent", savedBooking.errorMessage)
    }

    @Test
    fun nonDryRunModeCanStillUseRealSenderAbstraction() = runBlocking {
        val bookingDao = FakeBookingDao()
        val realSmsGateway = RecordingSmsGateway()
        val dryRunSmsGateway = RecordingSmsGateway()
        val orchestrator = BookingOrchestrator(
            bookingRepository = BookingRepository(bookingDao),
            realSmsGateway = realSmsGateway,
            dryRunSmsGateway = dryRunSmsGateway,
            calendarWriter = RecordingCalendarWriter(),
            appSettingsProvider = { AppSettings(dryRunMode = false) },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = "Hej! Ledigt pass 2026-03-07 07:00 - 19:00 A Skänninge Alla. Vill du anmäla intresse för passet svara: J annars N",
        )

        val savedBooking = bookingDao.getStoredBookings().first()
        assertEquals(1, realSmsGateway.sentMessages.size)
        assertEquals(0, dryRunSmsGateway.sentMessages.size)
        assertEquals("J", savedBooking.replySent)
        assertNull(savedBooking.intendedReply)
        assertEquals(BookingStatus.REPLIED_J, savedBooking.status)
    }

    @Test
    fun debugSimulationRespectsDryRunMode() = runBlocking {
        val bookingDao = FakeBookingDao()
        val realSmsGateway = RecordingSmsGateway()
        val dryRunSmsGateway = RecordingSmsGateway()
        val orchestrator = BookingOrchestrator(
            bookingRepository = BookingRepository(bookingDao),
            realSmsGateway = realSmsGateway,
            dryRunSmsGateway = dryRunSmsGateway,
            calendarWriter = RecordingCalendarWriter(),
            appSettingsProvider = { AppSettings(dryRunMode = true) },
        )
        val simulator = DebugSmsSimulator(orchestrator)

        simulator.simulateIncomingSms(
            sender = "0700000000",
            messageBody = "Hej! Ledigt pass 2026-03-07 07:00 - 19:00 A Skänninge Alla. Vill du anmäla intresse för passet svara: J annars N",
        )

        assertEquals(0, realSmsGateway.sentMessages.size)
        assertEquals(1, dryRunSmsGateway.sentMessages.size)
        assertTrue(bookingDao.getStoredBookings().first().dryRun)
    }
}

private class RecordingSmsGateway : SmsGateway {
    val sentMessages = mutableListOf<SentMessage>()

    override fun send(destination: String, messageBody: String): SmsGatewayResult {
        sentMessages += SentMessage(destination = destination, messageBody = messageBody)
        return SmsGatewayResult.Success
    }
}

private data class SentMessage(
    val destination: String,
    val messageBody: String,
)

private class RecordingCalendarWriter : CalendarWriter() {
    var createEventCalls: Int = 0

    override fun createEvent(
        shiftInfo: ShiftInfo,
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
