package com.example.bookingagent.sms.booking

import com.example.bookingagent.sms.calendar.CalendarWriteResult
import com.example.bookingagent.sms.calendar.CalendarWriter
import com.example.bookingagent.sms.data.db.BookingDao
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.model.BookingStatus
import com.example.bookingagent.sms.data.model.ReviewState
import com.example.bookingagent.sms.data.repo.BookingRepository
import com.example.bookingagent.sms.settings.AppSettings
import com.example.bookingagent.sms.sms.DebugSmsSimulator
import com.example.bookingagent.sms.sms.ShiftInfo
import com.example.bookingagent.sms.sms.SmsGateway
import com.example.bookingagent.sms.sms.SmsGatewayResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingOrchestratorTest {
    @Test
    fun manualReviewModeTruePreventsAutomaticSideEffects() = runBlocking {
        val bookingDao = FakeBookingDao()
        val bookingRepository = BookingRepository(bookingDao)
        val acceptedOfferId = bookingRepository.insert(
            testBooking(
                sender = "0700000000",
                rawSms = "accepted offer",
                messageType = "offer",
                shiftDate = "2026-04-12",
                startTime = "07:00",
                endTime = "19:00",
                details = "A Skänninge Alla",
                replySent = "J",
                recommendedReply = "J",
                recommendationFromRules = true,
                reviewState = ReviewState.AUTO_PROCESSED,
                status = BookingStatus.REPLIED_J,
                createdAt = 1L,
            ),
        )
        val realSmsGateway = RecordingSmsGateway()
        val dryRunSmsGateway = RecordingSmsGateway()
        val calendarWriter = RecordingCalendarWriter()
        val orchestrator = BookingOrchestrator(
            bookingRepository = bookingRepository,
            realSmsGateway = realSmsGateway,
            dryRunSmsGateway = dryRunSmsGateway,
            calendarWriter = calendarWriter,
            appSettingsProvider = {
                AppSettings(
                    automationEnabled = true,
                    dryRunMode = false,
                    manualReviewMode = true,
                )
            },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = offerMessage("2026-04-12", "07:00", "19:00", "A Skänninge Alla"),
        )
        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = confirmationMessage("2026-04-12", "07:00", "19:00", "A Skänninge Alla"),
        )

        assertEquals(0, realSmsGateway.sentMessages.size)
        assertEquals(0, dryRunSmsGateway.sentMessages.size)
        assertEquals(0, calendarWriter.createEventCalls)

        val storedOffer = bookingDao.getStoredBookings().first { it.rawSms.startsWith("Hej! Ledigt pass") }
        assertEquals(ReviewState.PENDING_REVIEW, storedOffer.reviewState)
        assertEquals("J", storedOffer.recommendedReply)
        assertNull(storedOffer.replySent)
        assertNull(storedOffer.intendedReply)

        val storedConfirmation = bookingDao.getStoredBookings().first { it.messageType == "confirmation" }
        assertEquals(ReviewState.PENDING_REVIEW, storedConfirmation.reviewState)
        assertEquals(acceptedOfferId, storedConfirmation.matchedBookingId)
        assertEquals(BookingStatus.CONFIRMED, storedConfirmation.status)
    }

    @Test
    fun offerGetsRecommendedReplyBasedOnExistingRuleLogic() = runBlocking {
        val bookingDao = FakeBookingDao()
        val orchestrator = BookingOrchestrator(
            bookingRepository = BookingRepository(bookingDao),
            realSmsGateway = RecordingSmsGateway(),
            dryRunSmsGateway = RecordingSmsGateway(),
            calendarWriter = RecordingCalendarWriter(),
            appSettingsProvider = {
                AppSettings(
                    automationEnabled = true,
                    dryRunMode = false,
                    manualReviewMode = true,
                )
            },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = offerMessage("2026-03-07", "07:00", "19:00", "A Skänninge Alla"),
        )

        val savedBooking = bookingDao.getStoredBookings().first()
        assertEquals("J", savedBooking.recommendedReply)
        assertTrue(savedBooking.recommendationFromRules)
        assertEquals(ReviewState.PENDING_REVIEW, savedBooking.reviewState)
    }

    @Test
    fun manualApproveStoresUsesJCorrectly() = runBlocking {
        val bookingDao = FakeBookingDao()
        val realSmsGateway = RecordingSmsGateway()
        val orchestrator = BookingOrchestrator(
            bookingRepository = BookingRepository(bookingDao),
            realSmsGateway = realSmsGateway,
            dryRunSmsGateway = RecordingSmsGateway(),
            calendarWriter = RecordingCalendarWriter(),
            appSettingsProvider = {
                AppSettings(
                    automationEnabled = true,
                    dryRunMode = false,
                    manualReviewMode = true,
                )
            },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = offerMessage("2026-03-07", "07:00", "19:00", "A Skänninge Alla"),
        )
        val bookingId = bookingDao.getStoredBookings().first().id

        orchestrator.approveOffer(bookingId)

        val updatedBooking = bookingDao.getStoredBookings().first()
        assertEquals(1, realSmsGateway.sentMessages.size)
        assertEquals("J", realSmsGateway.sentMessages.single().messageBody)
        assertEquals("J", updatedBooking.replySent)
        assertNull(updatedBooking.intendedReply)
        assertEquals(ReviewState.APPROVED, updatedBooking.reviewState)
        assertEquals(BookingStatus.REPLIED_J, updatedBooking.status)
    }

    @Test
    fun manualRejectStoresUsesNCorrectly() = runBlocking {
        val bookingDao = FakeBookingDao()
        val realSmsGateway = RecordingSmsGateway()
        val orchestrator = BookingOrchestrator(
            bookingRepository = BookingRepository(bookingDao),
            realSmsGateway = realSmsGateway,
            dryRunSmsGateway = RecordingSmsGateway(),
            calendarWriter = RecordingCalendarWriter(),
            appSettingsProvider = {
                AppSettings(
                    automationEnabled = true,
                    dryRunMode = false,
                    manualReviewMode = true,
                )
            },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = offerMessage("2026-03-07", "07:00", "19:00", "A Skänninge Alla"),
        )
        val bookingId = bookingDao.getStoredBookings().first().id

        orchestrator.rejectOffer(bookingId)

        val updatedBooking = bookingDao.getStoredBookings().first()
        assertEquals(1, realSmsGateway.sentMessages.size)
        assertEquals("N", realSmsGateway.sentMessages.single().messageBody)
        assertEquals("N", updatedBooking.replySent)
        assertNull(updatedBooking.intendedReply)
        assertEquals(ReviewState.REJECTED, updatedBooking.reviewState)
        assertEquals(BookingStatus.REPLIED_N, updatedBooking.status)
    }

    @Test
    fun confirmationPreviewFindsMatchingAcceptedOfferOnlyWhenStatusIsRepliedJ() = runBlocking {
        val bookingDao = FakeBookingDao()
        val bookingRepository = BookingRepository(bookingDao)
        bookingRepository.insert(
            testBooking(
                sender = "0700000001",
                rawSms = "rejected offer",
                messageType = "offer",
                shiftDate = "2026-04-12",
                startTime = "07:00",
                endTime = "19:00",
                details = "A Skänninge Alla",
                replySent = "N",
                recommendedReply = "J",
                recommendationFromRules = true,
                reviewState = ReviewState.REJECTED,
                status = BookingStatus.REPLIED_N,
                createdAt = 1L,
            ),
        )
        val acceptedOfferId = bookingRepository.insert(
            testBooking(
                sender = "0700000002",
                rawSms = "accepted offer",
                messageType = "offer",
                shiftDate = "2026-04-12",
                startTime = "07:00",
                endTime = "19:00",
                details = "A Skänninge Alla",
                replySent = "J",
                recommendedReply = "J",
                recommendationFromRules = true,
                reviewState = ReviewState.APPROVED,
                status = BookingStatus.REPLIED_J,
                createdAt = 2L,
            ),
        )
        val orchestrator = BookingOrchestrator(
            bookingRepository = bookingRepository,
            realSmsGateway = RecordingSmsGateway(),
            dryRunSmsGateway = RecordingSmsGateway(),
            calendarWriter = RecordingCalendarWriter(),
            appSettingsProvider = {
                AppSettings(
                    automationEnabled = true,
                    dryRunMode = false,
                    manualReviewMode = true,
                )
            },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000001",
            messageBody = confirmationMessage("2026-04-12", "07:00", "19:00", "A Skänninge Alla"),
        )
        orchestrator.handleIncomingSms(
            sender = "0700000002",
            messageBody = confirmationMessage("2026-04-12", "07:00", "19:00", "A Skänninge Alla"),
        )

        val savedForRejectedOffer =
            bookingDao.getStoredBookings().first { it.sender == "0700000001" && it.messageType == "confirmation" }
        val savedForAcceptedOffer =
            bookingDao.getStoredBookings().first { it.sender == "0700000002" && it.messageType == "confirmation" }

        assertNull(savedForRejectedOffer.matchedBookingId)
        assertEquals("No matching accepted offer found", savedForRejectedOffer.errorMessage)
        assertEquals(acceptedOfferId, savedForAcceptedOffer.matchedBookingId)
        assertNull(savedForAcceptedOffer.errorMessage)
    }

    @Test
    fun dryRunManualEventCreationDoesNotCreateRealEventButRecordsIntendedBehavior() = runBlocking {
        val bookingDao = FakeBookingDao()
        val bookingRepository = BookingRepository(bookingDao)
        val acceptedOfferId = bookingRepository.insert(
            testBooking(
                sender = "0700000000",
                rawSms = "accepted offer",
                messageType = "offer",
                shiftDate = "2026-04-12",
                startTime = "07:00",
                endTime = "19:00",
                details = "A Skänninge Alla",
                replySent = "J",
                recommendedReply = "J",
                recommendationFromRules = true,
                reviewState = ReviewState.APPROVED,
                status = BookingStatus.REPLIED_J,
                createdAt = 1L,
            ),
        )
        val calendarWriter = RecordingCalendarWriter()
        val orchestrator = BookingOrchestrator(
            bookingRepository = bookingRepository,
            realSmsGateway = RecordingSmsGateway(),
            dryRunSmsGateway = RecordingSmsGateway(),
            calendarWriter = calendarWriter,
            appSettingsProvider = {
                AppSettings(
                    targetCalendarName = "Test Calendar",
                    eventTitle = "Test Shift",
                    automationEnabled = true,
                    dryRunMode = true,
                    manualReviewMode = true,
                )
            },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = confirmationMessage("2026-04-12", "07:00", "19:00", "A Skänninge Alla"),
        )
        val confirmationId = bookingDao.getStoredBookings().first { it.messageType == "confirmation" }.id

        orchestrator.createEventManually(confirmationId)

        val updatedBooking = bookingDao.getStoredBookings().first { it.id == confirmationId }
        assertEquals(0, calendarWriter.createEventCalls)
        assertEquals(BookingStatus.EVENT_CREATED, updatedBooking.status)
        assertEquals(ReviewState.APPROVED, updatedBooking.reviewState)
        assertEquals(acceptedOfferId, updatedBooking.matchedBookingId)
        assertNull(updatedBooking.eventId)
        assertTrue(updatedBooking.dryRun)
        assertEquals(
            "Dry run mode: would create event \"Test Shift\" in calendar \"Test Calendar\"",
            updatedBooking.errorMessage,
        )
    }

    @Test
    fun duplicateManualActionsDoNotCreateDuplicateSideEffects() = runBlocking {
        val bookingDao = FakeBookingDao()
        val realSmsGateway = RecordingSmsGateway()
        val calendarWriter = RecordingCalendarWriter()
        val orchestrator = BookingOrchestrator(
            bookingRepository = BookingRepository(bookingDao),
            realSmsGateway = realSmsGateway,
            dryRunSmsGateway = RecordingSmsGateway(),
            calendarWriter = calendarWriter,
            appSettingsProvider = {
                AppSettings(
                    automationEnabled = true,
                    dryRunMode = false,
                    manualReviewMode = true,
                )
            },
        )

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = offerMessage("2026-04-12", "07:00", "19:00", "A Skänninge Alla"),
        )
        val offerId = bookingDao.getStoredBookings().first { it.messageType == "offer" }.id

        orchestrator.approveOffer(offerId)
        orchestrator.approveOffer(offerId)

        orchestrator.handleIncomingSms(
            sender = "0700000000",
            messageBody = confirmationMessage("2026-04-12", "07:00", "19:00", "A Skänninge Alla"),
        )
        val confirmationId = bookingDao.getStoredBookings().first { it.messageType == "confirmation" }.id

        orchestrator.createEventManually(confirmationId)
        orchestrator.createEventManually(confirmationId)

        val updatedOffer = bookingDao.getStoredBookings().first { it.id == offerId }
        val updatedConfirmation = bookingDao.getStoredBookings().first { it.id == confirmationId }
        assertEquals(1, realSmsGateway.sentMessages.size)
        assertEquals("J", updatedOffer.replySent)
        assertEquals(1, calendarWriter.createEventCalls)
        assertEquals(99L, updatedConfirmation.eventId)
        assertEquals(BookingStatus.EVENT_CREATED, updatedConfirmation.status)
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
            appSettingsProvider = {
                AppSettings(
                    automationEnabled = true,
                    dryRunMode = true,
                    manualReviewMode = false,
                )
            },
        )
        val simulator = DebugSmsSimulator(orchestrator)

        simulator.simulateIncomingSms(
            sender = "0700000000",
            messageBody = offerMessage("2026-04-12", "07:00", "19:00", "A Skänninge Alla"),
        )

        val savedBooking = bookingDao.getStoredBookings().first()
        assertEquals(0, realSmsGateway.sentMessages.size)
        assertEquals(1, dryRunSmsGateway.sentMessages.size)
        assertTrue(savedBooking.dryRun)
        assertEquals("J", savedBooking.intendedReply)
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

    override suspend fun getById(id: Long): BookingEntity? = storedBookings.firstOrNull { it.id == id }

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

private fun offerMessage(
    date: String,
    start: String,
    end: String,
    details: String,
): String =
    "Hej! Ledigt pass $date $start - $end $details. Vill du anmäla intresse för passet svara: J annars N"

private fun confirmationMessage(
    date: String,
    start: String,
    end: String,
    details: String,
): String = "Du är nu bokad på pass $date $start - $end $details"

private fun testBooking(
    sender: String,
    rawSms: String,
    messageType: String,
    shiftDate: String? = null,
    startTime: String? = null,
    endTime: String? = null,
    details: String? = null,
    replySent: String? = null,
    recommendedReply: String? = null,
    recommendationFromRules: Boolean = false,
    intendedReply: String? = null,
    reviewState: ReviewState = ReviewState.AUTO_PROCESSED,
    status: BookingStatus,
    matchedBookingId: Long? = null,
    eventId: Long? = null,
    errorMessage: String? = null,
    dryRun: Boolean = false,
    createdAt: Long = System.currentTimeMillis(),
): BookingEntity =
    BookingEntity(
        sender = sender,
        rawSms = rawSms,
        messageType = messageType,
        shiftDate = shiftDate,
        startTime = startTime,
        endTime = endTime,
        details = details,
        replySent = replySent,
        recommendedReply = recommendedReply,
        recommendationFromRules = recommendationFromRules,
        intendedReply = intendedReply,
        reviewState = reviewState,
        status = status,
        matchedBookingId = matchedBookingId,
        eventId = eventId,
        errorMessage = errorMessage,
        dryRun = dryRun,
        createdAt = createdAt,
    )
