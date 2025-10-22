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
        it("포인트를 충전한다") {
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

        it("충전 금액이 0 이하인 경우 예외가 발생한다") {
            // given
            val userId = 1L
            val invalidAmount = -100L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                pointService.chargeUserPoint(userId, invalidAmount)
            }
            exception.message shouldBe "충전 금액은 양수여야 합니다."
        }

        it("충전 금액이 0인 경우 예외가 발생한다") {
            // given
            val userId = 1L
            val invalidAmount = 0L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                pointService.chargeUserPoint(userId, invalidAmount)
            }
            exception.message shouldBe "충전 금액은 양수여야 합니다."
        }
    }

    describe("포인트 사용") {
        it("포인트를 사용한다") {
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

        it("사용 금액이 0 이하인 경우 예외가 발생한다") {
            // given
            val userId = 1L
            val invalidAmount = -100L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                pointService.useUserPoint(userId, invalidAmount)
            }
            exception.message shouldBe "사용 금액은 양수여야 합니다."
        }

        it("포인트가 부족한 경우 예외가 발생한다") {
            // given
            val userId = 1L
            val useAmount = 600L
            val currentPoint = UserPoint(id = userId, point = 500L, updateMillis = System.currentTimeMillis())

            every { userPointTable.selectById(userId) } returns currentPoint

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                pointService.useUserPoint(userId, useAmount)
            }
            exception.message shouldBe "포인트가 부족합니다."
            verify(exactly = 1) { userPointTable.selectById(userId) }
        }

        it("포인트 사용이 100 단위가 아닌 경우 예외가 발생한다") {
            // given
            val userId = 1L
            val invalidAmount = 550L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                pointService.useUserPoint(userId, invalidAmount)
            }
            exception.message shouldBe "포인트 사용은 100 단위로만 가능합니다."
        }

        it("포인트 사용이 1000을 초과하는 경우 예외가 발생한다") {
            // given
            val userId = 1L
            val invalidAmount = 1100L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                pointService.useUserPoint(userId, invalidAmount)
            }
            exception.message shouldBe "포인트 출금은 1000 이하로 할 수 없습니다."
        }
    }
})
