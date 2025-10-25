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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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

    describe("동시성 테스트 - 포인트 충전") {
        it("동시에 여러 스레드가 포인트를 충전해도 정확한 최종 포인트를 보장한다") {
            // given
            val realUserPointTable = UserPointTable()
            val realPointHistoryTable = PointHistoryTable()
            val realUserLock = ConcurrentHashMap<Long, ReentrantLock>()
            val concurrentPointService = PointService(realUserPointTable, realPointHistoryTable, realUserLock)

            val userId = 1L
            val threadCount = 10
            val chargeAmount = 100L
            val expectedFinalPoint = threadCount * chargeAmount

            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)

            // when
            repeat(threadCount) {
                executor.execute {
                    try {
                        concurrentPointService.chargeUserPoint(userId, chargeAmount)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then
            val finalPoint = concurrentPointService.getUserPoint(userId)
            finalPoint.point shouldBe expectedFinalPoint
        }
    }

    describe("동시성 테스트 - 포인트 사용") {
        it("동시에 여러 스레드가 포인트를 사용해도 정확한 최종 포인트를 보장한다") {
            // given
            val realUserPointTable = UserPointTable()
            val realPointHistoryTable = PointHistoryTable()
            val realUserLock = ConcurrentHashMap<Long, ReentrantLock>()
            val concurrentPointService = PointService(realUserPointTable, realPointHistoryTable, realUserLock)

            val userId = 1L
            val initialPoint = 10000L
            val threadCount = 10
            val useAmount = 100L
            val expectedFinalPoint = initialPoint - (threadCount * useAmount)

            // 초기 포인트 설정
            concurrentPointService.chargeUserPoint(userId, initialPoint)

            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)

            // when
            repeat(threadCount) {
                executor.execute {
                    try {
                        concurrentPointService.useUserPoint(userId, useAmount)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then
            val finalPoint = concurrentPointService.getUserPoint(userId)
            finalPoint.point shouldBe expectedFinalPoint

            // 히스토리도 정확히 기록되었는지 확인
            val histories = concurrentPointService.getUserPointHistory(userId)
            val useHistories = histories.filter { it.type == TransactionType.USE }
            useHistories.size shouldBe threadCount
        }
    }

    describe("동시성 테스트 - 포인트 충전과 사용 혼합") {
        it("동시에 충전과 사용이 발생해도 정확한 최종 포인트를 보장한다") {
            // given
            val realUserPointTable = UserPointTable()
            val realPointHistoryTable = PointHistoryTable()
            val realUserLock = ConcurrentHashMap<Long, ReentrantLock>()
            val concurrentPointService = PointService(realUserPointTable, realPointHistoryTable, realUserLock)

            val userId = 1L
            val initialPoint = 5000L
            val chargeThreadCount = 5
            val useThreadCount = 5
            val chargeAmount = 200L
            val useAmount = 100L
            val expectedFinalPoint = initialPoint + (chargeThreadCount * chargeAmount) - (useThreadCount * useAmount)

            // 초기 포인트 설정
            concurrentPointService.chargeUserPoint(userId, initialPoint)

            val totalThreadCount = chargeThreadCount + useThreadCount
            val executor = Executors.newFixedThreadPool(totalThreadCount)
            val latch = CountDownLatch(totalThreadCount)

            // when - 충전과 사용을 동시에 실행
            repeat(chargeThreadCount) {
                executor.execute {
                    try {
                        concurrentPointService.chargeUserPoint(userId, chargeAmount)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            repeat(useThreadCount) {
                executor.execute {
                    try {
                        concurrentPointService.useUserPoint(userId, useAmount)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then
            val finalPoint = concurrentPointService.getUserPoint(userId)
            finalPoint.point shouldBe expectedFinalPoint

            // 히스토리도 정확히 기록되었는지 확인
            val histories = concurrentPointService.getUserPointHistory(userId)
            val useHistories = histories.filter { it.type == TransactionType.USE }
            useHistories.size shouldBe useThreadCount
        }
    }

    describe("동시성 테스트 - 서로 다른 사용자") {
        it("서로 다른 사용자의 동시 요청은 서로 영향을 주지 않는다") {
            // given
            val realUserPointTable = UserPointTable()
            val realPointHistoryTable = PointHistoryTable()
            val realUserLock = ConcurrentHashMap<Long, ReentrantLock>()
            val concurrentPointService = PointService(realUserPointTable, realPointHistoryTable, realUserLock)

            val userCount = 5
            val chargePerUser = 1000L
            val executor = Executors.newFixedThreadPool(userCount)
            val latch = CountDownLatch(userCount)

            // when - 서로 다른 사용자가 동시에 충전
            repeat(userCount) { index ->
                executor.execute {
                    try {
                        concurrentPointService.chargeUserPoint(index.toLong(), chargePerUser)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then - 각 사용자의 포인트가 정확한지 확인
            repeat(userCount) { index ->
                val userPoint = concurrentPointService.getUserPoint(index.toLong())
                userPoint.point shouldBe chargePerUser
            }
        }
    }
})
