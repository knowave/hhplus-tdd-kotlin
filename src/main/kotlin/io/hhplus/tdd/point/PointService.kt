package io.hhplus.tdd.point

import io.hhplus.tdd.database.UserPointTable
import io.hhplus.tdd.database.PointHistoryTable
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable,
    private val userLock: ConcurrentHashMap<Long, ReentrantLock>
) {
        fun getUserPoint(id: Long): UserPoint {
            return userPointTable.selectById(id)
        }

        fun getUserPointHistory(id: Long): List<PointHistory> {
            return pointHistoryTable.selectAllByUserId(id)
        }

        fun chargeUserPoint(id: Long, amount: Long): UserPoint {
            val lock = userLock.computeIfAbsent(id) { ReentrantLock() }
            lock.lock()

            try {
                val currentPoint = userPointTable.selectById(id)
                val newPoint = currentPoint.charge(amount)
                return userPointTable.insertOrUpdate(id, newPoint)
            } catch(e: Exception) {
                throw e
            } finally {
                lock.unlock()
            }
        }

        fun useUserPoint(userId: Long, amount: Long): UserPoint {
            val lock = userLock.computeIfAbsent(userId) { ReentrantLock() }

            lock.lock()
            try {
                val currentPoint = userPointTable.selectById(userId)
                val newPoint = currentPoint.deduct(amount)

                val result = userPointTable.insertOrUpdate(userId, newPoint)
                pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis())

                return result
            } finally {
                lock.unlock()
            }
        }
}
