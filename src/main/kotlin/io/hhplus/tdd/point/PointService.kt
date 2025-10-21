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
            require(amount > 0) { "충전 금액은 양수여야 합니다." }

            val lock = userLock.computeIfAbsent(id) { ReentrantLock() }
            lock.lock()

            try {
                val currentPoint = userPointTable.selectById(id)
                val newPoint = currentPoint.point + amount
                return userPointTable.insertOrUpdate(id, newPoint)
            } catch(e: Exception) {
                throw e
            } finally {
                lock.unlock()
            }
        }

        fun useUserPoint(userId: Long, amount: Long): UserPoint {
            require(amount > 0) { "사용 금액은 양수여야 합니다." }
            require(amount % 100 == 0L) { "포인트 사용은 100 단위로만 가능합니다." }
            require(amount <= 1000) { "포인트 출금은 1000 이하로 할 수 없습니다." }
            
            val lock = userLock.computeIfAbsent(userId) { ReentrantLock() }
            
            lock.lock()
            try {
                val currentPoint = userPointTable.selectById(userId)
                require(currentPoint.point >= amount) { "포인트가 부족합니다." }
                
                val newPoint = currentPoint.point - amount
                val result = userPointTable.insertOrUpdate(userId, newPoint)
                pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis())
                
                return result
            } finally {
                lock.unlock()
            }
        }
}
