package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@DisplayName("PointService 단위 테스트")
class PointServiceTest {

    private lateinit var pointService: PointService
    private lateinit var userPointTable: UserPointTable
    private lateinit var pointHistoryTable: PointHistoryTable
    private lateinit var userLock: ConcurrentHashMap<Long, ReentrantLock>

    @BeforeEach
    fun setUp() {
        userPointTable = UserPointTable()
        pointHistoryTable = PointHistoryTable()
        userLock = ConcurrentHashMap()
        pointService = PointService(userPointTable, pointHistoryTable, userLock)
    }

    @Test
    @DisplayName("사용자 포인트를 조회한다")
    fun getUserPoint() {
        // given
        val userId = 1L
        userPointTable.insertOrUpdate(userId, 1000L)

        // when
        val result = pointService.getUserPoint(userId)

        // then
        assertEquals(userId, result.id)
        assertEquals(1000L, result.point)
    }

    @Test
    @DisplayName("사용자 포인트 내역을 조회한다")
    fun getUserPointHistory() {
        // given
        val userId = 1L
        pointHistoryTable.insert(userId, 500L, TransactionType.CHARGE, System.currentTimeMillis())
        pointHistoryTable.insert(userId, 200L, TransactionType.USE, System.currentTimeMillis())

        // when
        val result = pointService.getUserPointHistory(userId)

        // then
        assertEquals(2, result.size)
        assertEquals(TransactionType.CHARGE, result[0].type)
        assertEquals(TransactionType.USE, result[1].type)
    }

    @Test
    @DisplayName("포인트를 충전한다")
    fun chargeUserPoint() {
        // given
        val userId = 1L
        val chargeAmount = 1000L

        // when
        val result = pointService.chargeUserPoint(userId, chargeAmount)

        // then
        assertEquals(userId, result.id)
        assertEquals(chargeAmount, result.point)
    }

    @Test
    @DisplayName("충전 금액이 0 이하인 경우 예외가 발생한다")
    fun chargeUserPoint_withNegativeAmount() {
        // given
        val userId = 1L
        val invalidAmount = -100L

        // when & then
        val exception = assertThrows<IllegalArgumentException> {
            pointService.chargeUserPoint(userId, invalidAmount)
        }
        assertEquals("충전 금액은 양수여야 합니다.", exception.message)
    }

    @Test
    @DisplayName("충전 금액이 0인 경우 예외가 발생한다")
    fun chargeUserPoint_withZeroAmount() {
        // given
        val userId = 1L
        val invalidAmount = 0L

        // when & then
        val exception = assertThrows<IllegalArgumentException> {
            pointService.chargeUserPoint(userId, invalidAmount)
        }
        assertEquals("충전 금액은 양수여야 합니다.", exception.message)
    }

    @Test
    @DisplayName("포인트를 사용한다")
    fun useUserPoint() {
        // given
        val userId = 1L
        userPointTable.insertOrUpdate(userId, 1000L)
        val useAmount = 500L

        // when
        val result = pointService.useUserPoint(userId, useAmount)

        // then
        assertEquals(userId, result.id)
        assertEquals(500L, result.point)
    }

    @Test
    @DisplayName("사용 금액이 0 이하인 경우 예외가 발생한다")
    fun useUserPoint_withNegativeAmount() {
        // given
        val userId = 1L
        userPointTable.insertOrUpdate(userId, 1000L)
        val invalidAmount = -100L

        // when & then
        val exception = assertThrows<IllegalArgumentException> {
            pointService.useUserPoint(userId, invalidAmount)
        }
        assertEquals("사용 금액은 양수여야 합니다.", exception.message)
    }

    @Test
    @DisplayName("포인트가 부족한 경우 예외가 발생한다")
    fun useUserPoint_withInsufficientBalance() {
        // given
        val userId = 1L
        userPointTable.insertOrUpdate(userId, 500L)
        val useAmount = 600L

        // when & then
        val exception = assertThrows<IllegalArgumentException> {
            pointService.useUserPoint(userId, useAmount)
        }
        assertEquals("포인트가 부족합니다.", exception.message)
    }

    @Test
    @DisplayName("포인트 사용이 100 단위가 아닌 경우 예외가 발생한다")
    fun useUserPoint_withInvalidUnit() {
        // given
        val userId = 1L
        userPointTable.insertOrUpdate(userId, 1000L)
        val invalidAmount = 550L

        // when & then
        val exception = assertThrows<IllegalArgumentException> {
            pointService.useUserPoint(userId, invalidAmount)
        }
        assertEquals("포인트 사용은 100 단위로만 가능합니다.", exception.message)
    }

    @Test
    @DisplayName("포인트 사용이 1000을 초과하는 경우 예외가 발생한다")
    fun useUserPoint_withExcessiveAmount() {
        // given
        val userId = 1L
        userPointTable.insertOrUpdate(userId, 5000L)
        val invalidAmount = 1100L

        // when & then
        val exception = assertThrows<IllegalArgumentException> {
            pointService.useUserPoint(userId, invalidAmount)
        }
        assertEquals("포인트 출금은 1000 이하로 할 수 없습니다.", exception.message)
    }
}
