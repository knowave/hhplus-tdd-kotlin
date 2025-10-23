package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

class PointServiceTest : DescribeSpec({
    lateinit var pointService: PointService
    lateinit var userPointTable: UserPointTable
    lateinit var pointHistoryTable: PointHistoryTable
    lateinit var userLock: ConcurrentHashMap<Long, ReentrantLock>

    beforeEach {
        userPointTable = mockk()
        pointHistoryTable = mockk()
        userLock = ConcurrentHashMap()
        pointService = PointService(userPointTable, pointHistoryTable, userLock)
    }

    describe("사용자 포인트 조회") {
        it("사용자 포인트를 조회한다") {
            // given
            val userId = 1L
            val expectedPoint = UserPoint(id = userId, point = 1000L, updateMillis = System.currentTimeMillis())
            every { userPointTable.selectById(userId) } returns expectedPoint

            // when
            val result = pointService.getUserPoint(userId)

            // then
            result.id shouldBe userId
            result.point shouldBe 1000L
            verify(exactly = 1) { userPointTable.selectById(userId) }
        }
    }

    describe("사용자 포인트 내역 조회") {
        it("사용자 포인트 내역을 조회한다") {
            // given
            val userId = 1L
            val expectedHistories = listOf(
                PointHistory(id = 1L, userId = userId, amount = 500L, type = TransactionType.CHARGE, timeMillis = System.currentTimeMillis()),
                PointHistory(id = 2L, userId = userId, amount = 200L, type = TransactionType.USE, timeMillis = System.currentTimeMillis())
            )
            every { pointHistoryTable.selectAllByUserId(userId) } returns expectedHistories

            // when
            val result = pointService.getUserPointHistory(userId)

            // then
            result.size shouldBe 2
            result[0].type shouldBe TransactionType.CHARGE
            result[1].type shouldBe TransactionType.USE
            verify(exactly = 1) { pointHistoryTable.selectAllByUserId(userId) }
        }
    }

    describe("포인트 충전") {
        it("포인트를 충전하고 DB를 업데이트한다") {
            // given
            val userId = 1L
            val chargeAmount = 1000L
            val currentPoint = UserPoint(id = userId, point = 0L, updateMillis = System.currentTimeMillis())
            val updatedPoint = UserPoint(id = userId, point = 1000L, updateMillis = System.currentTimeMillis())

            every { userPointTable.selectById(userId) } returns currentPoint
            every { userPointTable.insertOrUpdate(userId, 1000L) } returns updatedPoint

            // when
            val result = pointService.chargeUserPoint(userId, chargeAmount)

            // then
            result.id shouldBe userId
            result.point shouldBe chargeAmount
            verify(exactly = 1) { userPointTable.selectById(userId) }
            verify(exactly = 1) { userPointTable.insertOrUpdate(userId, 1000L) }
        }
    }

    describe("포인트 사용") {
        it("포인트를 사용하고 DB를 업데이트하며 히스토리를 기록한다") {
            // given
            val userId = 1L
            val useAmount = 500L
            val currentTime = System.currentTimeMillis()
            val currentPoint = UserPoint(id = userId, point = 1000L, updateMillis = currentTime)
            val updatedPoint = UserPoint(id = userId, point = 500L, updateMillis = currentTime)
            val expectedHistory = PointHistory(id = 1L, userId = userId, amount = useAmount, type = TransactionType.USE, timeMillis = currentTime)

            every { userPointTable.selectById(userId) } returns currentPoint
            every { userPointTable.insertOrUpdate(userId, 500L) } returns updatedPoint
            every { pointHistoryTable.insert(userId, useAmount, TransactionType.USE, any()) } returns expectedHistory

            // when
            val result = pointService.useUserPoint(userId, useAmount)

            // then
            result.id shouldBe userId
            result.point shouldBe 500L
            verify(exactly = 1) { userPointTable.selectById(userId) }
            verify(exactly = 1) { userPointTable.insertOrUpdate(userId, 500L) }
            verify(exactly = 1) { pointHistoryTable.insert(userId, useAmount, TransactionType.USE, any()) }
        }
    }
})
