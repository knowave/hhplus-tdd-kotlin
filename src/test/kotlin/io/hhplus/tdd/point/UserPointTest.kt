package io.hhplus.tdd.point

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UserPointTest : DescribeSpec({
    describe("포인트 충전") {
        it("포인트를 충전한다") {
            // given
            val userPoint = UserPoint(id = 1L, point = 1000L, updateMillis = System.currentTimeMillis())
            val chargeAmount = 500L

            // when
            val result = userPoint.charge(chargeAmount)

            // then
            result shouldBe 1500L
        }

        it("충전 금액이 0 이하인 경우 예외가 발생한다") {
            // given
            val userPoint = UserPoint(id = 1L, point = 1000L, updateMillis = System.currentTimeMillis())
            val invalidAmount = -100L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                userPoint.charge(invalidAmount)
            }
            exception.message shouldBe "충전 금액은 양수여야 합니다."
        }

        it("충전 금액이 0인 경우 예외가 발생한다") {
            // given
            val userPoint = UserPoint(id = 1L, point = 1000L, updateMillis = System.currentTimeMillis())
            val invalidAmount = 0L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                userPoint.charge(invalidAmount)
            }
            exception.message shouldBe "충전 금액은 양수여야 합니다."
        }
    }

    describe("포인트 사용") {
        it("포인트를 사용한다") {
            // given
            val userPoint = UserPoint(id = 1L, point = 1000L, updateMillis = System.currentTimeMillis())
            val useAmount = 500L

            // when
            val result = userPoint.deduct(useAmount)

            // then
            result shouldBe 500L
        }

        it("사용 금액이 0 이하인 경우 예외가 발생한다") {
            // given
            val userPoint = UserPoint(id = 1L, point = 1000L, updateMillis = System.currentTimeMillis())
            val invalidAmount = -100L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                userPoint.deduct(invalidAmount)
            }
            exception.message shouldBe "사용 금액은 양수여야 합니다."
        }

        it("포인트가 부족한 경우 예외가 발생한다") {
            // given
            val userPoint = UserPoint(id = 1L, point = 500L, updateMillis = System.currentTimeMillis())
            val useAmount = 600L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                userPoint.deduct(useAmount)
            }
            exception.message shouldBe "포인트가 부족합니다."
        }

        it("포인트 사용이 100 단위가 아닌 경우 예외가 발생한다") {
            // given
            val userPoint = UserPoint(id = 1L, point = 1000L, updateMillis = System.currentTimeMillis())
            val invalidAmount = 550L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                userPoint.deduct(invalidAmount)
            }
            exception.message shouldBe "포인트 사용은 100 단위로만 가능합니다."
        }

        it("포인트 사용이 1000을 초과하는 경우 예외가 발생한다") {
            // given
            val userPoint = UserPoint(id = 1L, point = 2000L, updateMillis = System.currentTimeMillis())
            val invalidAmount = 1100L

            // when & then
            val exception = shouldThrow<IllegalArgumentException> {
                userPoint.deduct(invalidAmount)
            }
            exception.message shouldBe "포인트 출금은 1000 이하로 할 수 없습니다."
        }
    }
})
