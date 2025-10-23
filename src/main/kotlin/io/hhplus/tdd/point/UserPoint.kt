package io.hhplus.tdd.point

data class
UserPoint(
    val id: Long,
    val point: Long,
    val updateMillis: Long,
) {
    fun validateCharge(amount: Long) {
        require(amount > 0) { "충전 금액은 양수여야 합니다." }
    }

    fun validateDeduction(amount: Long) {
        require(amount > 0) { "사용 금액은 양수여야 합니다." }
        require(amount % 100 == 0L) { "포인트 사용은 100 단위로만 가능합니다." }
        require(amount <= 1000) { "포인트 출금은 1000 이하로 할 수 없습니다." }
        require(point >= amount) { "포인트가 부족합니다." }
    }

    fun charge(amount: Long): Long {
        validateCharge(amount)
        return point + amount
    }

    fun deduct(amount: Long): Long {
        validateDeduction(amount)
        return point - amount
    }
}
