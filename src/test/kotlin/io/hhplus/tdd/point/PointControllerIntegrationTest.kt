package io.hhplus.tdd.point

import com.fasterxml.jackson.databind.ObjectMapper
import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("PointController 통합 테스트")
class PointControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userPointTable: UserPointTable

    @Autowired
    private lateinit var pointHistoryTable: PointHistoryTable

    @Test
    @DisplayName("GET /point/{id} - 특정 유저의 포인트를 조회한다")
    fun getUserPoint() {
        // given
        val userId = 1L
        userPointTable.insertOrUpdate(userId, 1000L)

        // when & then
        mockMvc.perform(get("/point/$userId"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(1000))
    }

    @Test
    @DisplayName("GET /point/{id} - 존재하지 않는 유저의 포인트를 조회하면 0 포인트를 반환한다")
    fun getUserPoint_nonExistentUser() {
        // given
        val userId = 999L

        // when & then
        mockMvc.perform(get("/point/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(0))
    }

    @Test
    @DisplayName("GET /point/{id}/histories - 특정 유저의 포인트 내역을 조회한다")
    fun getUserPointHistories() {
        // given
        val userId = 2L
        pointHistoryTable.insert(userId, 500L, TransactionType.CHARGE, System.currentTimeMillis())
        pointHistoryTable.insert(userId, 200L, TransactionType.USE, System.currentTimeMillis())

        // when & then
        mockMvc.perform(get("/point/$userId/histories"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].userId").value(userId))
            .andExpect(jsonPath("$[0].amount").value(500))
            .andExpect(jsonPath("$[0].type").value("CHARGE"))
            .andExpect(jsonPath("$[1].userId").value(userId))
            .andExpect(jsonPath("$[1].amount").value(200))
            .andExpect(jsonPath("$[1].type").value("USE"))
    }

    @Test
    @DisplayName("GET /point/{id}/histories - 내역이 없는 유저는 빈 배열을 반환한다")
    fun getUserPointHistories_emptyHistory() {
        // given
        val userId = 998L

        // when & then
        mockMvc.perform(get("/point/$userId/histories"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    @DisplayName("PATCH /point/{id}/charge - 특정 유저의 포인트를 충전한다")
    fun chargeUserPoint() {
        // given
        val userId = 3L
        val chargeAmount = 1000L

        // when & then
        mockMvc.perform(
            patch("/point/$userId/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(chargeAmount.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(1000))
    }

    @Test
    @DisplayName("PATCH /point/{id}/charge - 여러 번 충전하면 포인트가 누적된다")
    fun chargeUserPoint_multiple() {
        // given
        val userId = 4L

        // when & then - 첫 번째 충전
        mockMvc.perform(
            patch("/point/$userId/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("500")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point").value(500))

        // when & then - 두 번째 충전
        mockMvc.perform(
            patch("/point/$userId/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("300")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point").value(800))
    }

    @Test
    @DisplayName("PATCH /point/{id}/charge - 0 이하의 금액으로 충전하면 400 에러가 발생한다")
    fun chargeUserPoint_invalidAmount() {
        // given
        val userId = 5L
        val invalidAmount = -100L

        // when & then
        mockMvc.perform(
            patch("/point/$userId/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidAmount.toString())
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("PATCH /point/{id}/use - 특정 유저의 포인트를 사용한다")
    fun useUserPoint() {
        // given
        val userId = 6L
        userPointTable.insertOrUpdate(userId, 1000L)
        val useAmount = 500L

        // when & then
        mockMvc.perform(
            patch("/point/$userId/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(useAmount.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.point").value(500))
    }

    @Test
    @DisplayName("PATCH /point/{id}/use - 포인트가 부족하면 400 에러가 발생한다")
    fun useUserPoint_insufficientBalance() {
        // given
        val userId = 7L
        userPointTable.insertOrUpdate(userId, 300L)
        val useAmount = 500L

        // when & then
        mockMvc.perform(
            patch("/point/$userId/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(useAmount.toString())
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("PATCH /point/{id}/use - 100 단위가 아닌 금액으로 사용하면 400 에러가 발생한다")
    fun useUserPoint_invalidUnit() {
        // given
        val userId = 8L
        userPointTable.insertOrUpdate(userId, 1000L)
        val invalidAmount = 550L

        // when & then
        mockMvc.perform(
            patch("/point/$userId/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidAmount.toString())
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("PATCH /point/{id}/use - 1000을 초과하는 금액으로 사용하면 400 에러가 발생한다")
    fun useUserPoint_excessiveAmount() {
        // given
        val userId = 9L
        userPointTable.insertOrUpdate(userId, 5000L)
        val excessiveAmount = 1100L

        // when & then
        mockMvc.perform(
            patch("/point/$userId/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(excessiveAmount.toString())
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("동시성 테스트 - 동일 유저에 대한 동시 충전 요청이 올바르게 처리된다")
    fun concurrentChargeRequests() {
        // given
        val userId = 10L
        val numberOfThreads = 10
        val chargeAmount = 100L
        val latch = CountDownLatch(numberOfThreads)
        val successCount = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(numberOfThreads)

        // when
        repeat(numberOfThreads) {
            executor.submit {
                try {
                    mockMvc.perform(
                        patch("/point/$userId/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chargeAmount.toString())
                    )
                        .andExpect(status().isOk)
                    successCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then
        val finalPoint = userPointTable.selectById(userId)
        assertEquals(numberOfThreads, successCount.get())
        assertEquals(chargeAmount * numberOfThreads, finalPoint.point)
    }

    @Test
    @DisplayName("동시성 테스트 - 동일 유저에 대한 동시 사용 요청이 올바르게 처리된다")
    fun concurrentUseRequests() {
        // given
        val userId = 11L
        val initialPoint = 2000L
        userPointTable.insertOrUpdate(userId, initialPoint)

        val numberOfThreads = 5
        val useAmount = 100L
        val latch = CountDownLatch(numberOfThreads)
        val successCount = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(numberOfThreads)

        // when
        repeat(numberOfThreads) {
            executor.submit {
                try {
                    mockMvc.perform(
                        patch("/point/$userId/use")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(useAmount.toString())
                    )
                        .andExpect(status().isOk)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    // 예외 발생 시에도 카운트다운
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then
        val finalPoint = userPointTable.selectById(userId)
        assertEquals(numberOfThreads, successCount.get())
        assertEquals(initialPoint - (useAmount * numberOfThreads), finalPoint.point)
    }

    @Test
    @DisplayName("통합 시나리오 - 충전, 사용, 조회가 연속적으로 올바르게 동작한다")
    fun integratedScenario() {
        // given
        val userId = 12L

        // when & then - 1. 초기 포인트 조회
        mockMvc.perform(get("/point/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point").value(0))

        // 2. 포인트 충전
        mockMvc.perform(
            patch("/point/$userId/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("2000")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point").value(2000))

        // 3. 포인트 사용
        mockMvc.perform(
            patch("/point/$userId/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content("500")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point").value(1500))

        // 4. 포인트 조회 확인
        mockMvc.perform(get("/point/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point").value(1500))

        // 5. 포인트 내역 조회
        mockMvc.perform(get("/point/$userId/histories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("USE"))
            .andExpect(jsonPath("$[0].amount").value(500))
    }
}
