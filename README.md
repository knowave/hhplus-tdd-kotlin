# 동시성 제어 - 과제 1주차 분석 문서

## 1. 개요

이 문서는 포인트 충전/사용 동시성 제어를 위해 채택한 방식과 그 이유, 그리고 다른 방법 대비 장단점을 설명합니다.

## 2. 채택한 방식

### 2.1 최종 방식: User-Level Locking

사용자별 **리엔트런트 락(User-Level Locking)** 방식을 최종 선택했으며, Java의 `ReentrantLock`과 `ConcurrentHashMap`을 활용하여 구현했습니다.

#### 구현 위치

- `LockConfig.kt:12` - 락 매니저 Bean 설정
- `PointService.kt:6,13,26,45` - 락 매니저 사용

### 2.2 핵심 구현 코드

```kotlin
// ConcurrentHashMap<사용자 ID, 리엔트런트 락>
private val userLock: ConcurrentHashMap<Long, ReentrantLock>

// 락 매니저를 통한 락 획득
val lock = userLock.computeIfAbsent(id) { ReentrantLock() }
lock.lock()
try {
    // 비즈니스 로직: 포인트 조회 및 업데이트
    val currentPoint = userPointTable.selectById(id)
    val newPoint = currentPoint.point + amount
    return userPointTable.insertOrUpdate(id, newPoint)
} finally {
    lock.unlock()
}
```

#### 구현 핵심

1. **ConcurrentHashMap 사용**
   - Key: 사용자 ID (Long)
   - Value: 해당 사용자 전용 ReentrantLock
   - Thread-safe한 락 관리 보장

2. **computeIfAbsent 활용**
   - 락 획득 시 사용자별 락 생성
   - 해당 사용자가 없으면 락 생성
   - Race condition 없이 락 생성 보장

3. **try-finally 패턴**
   - 예외 상황에서도 반드시 락 해제
   - 데드락 방지

## 3. 채택한 이유 및 최종 해결

### 3.1 Race Condition 문제

채택한 이유는 동시성 이슈로 인한 다음 시나리오를 방지하기 위함입니다:

```
[시간] [스레드1]              [스레드2]              [DB 상태]
T1    현재 포인트 조회: 1000                      1000
T2                          현재 포인트 조회: 1000  1000
T3    1000 + 500 = 1500                         1000
T4                          1000 + 300 = 1300   1000
T5    포인트 업데이트: 1500                          1500
T6                          포인트 업데이트: 1300    1300 (갱신 유실 발생)

기대 결과: 1800 (1000 + 500 + 300)
실제 결과: 1300 (업데이트 유실 발생)
```

### 3.2 업데이트 유실 시나리오가 발생한 근본 원인

이 프로젝트의 `UserPointTable`과 `PointHistoryTable`은 실제 데이터베이스가 아닌 인메모리 자료구조를 사용하며, 다음과 같은 특성이 있습니다:

- **예상 지연시간 시뮬레이션**
  - Read: 0-200ms 랜덤 지연 (`UserPointTable.kt:14`)
  - Write: 0-300ms 랜덤 지연 (`UserPointTable.kt:19`)

- **인메모리 데이터의 동시성 보장 없음**
  - HashMap 기반 구조로 ACID 속성 없음
  - DB 수준 락(Row Lock, Table Lock) 미지원
  - 따라서 애플리케이션 수준의 동시성 제어 필수

### 3.3 동시성 검증 테스트

#### 충전 동시성 검증

- **검증 위치**: `PointControllerIntegrationTest.kt:230`
- **시나리오**: 10개 스레드가 동시에 100포인트씩 충전
- **검증**: 1000포인트 최종 잔액 확인 (업데이트 유실 방지)

#### 사용 동시성 검증

- **검증 위치**: `PointControllerIntegrationTest.kt:267`
- **시나리오**: 5개 스레드가 동시에 100포인트씩 사용
- **검증**: 500포인트 최종 잔액 확인 (음수 잔액 방지)

## 4. 다른 방식과의 비교

### 4.1 장점

#### 세밀한 동시성 제어 가능

- 사용자 포인트 작업이 독립적이므로, 동시에 실행 가능
- 사용자별 락으로 락 경합 범위가 작음 - 성능 향상

#### 높은 성능 및 확장성 제공

- 사용자 단위로 동시 작업 가능
- 서로 다른 사용자의 포인트 작업은 동시 처리 가능

#### 구현 단순성

- 복잡한 락 관리 코드가 간결함
- 데드락 발생 가능성이 낮음

### 4.2 단점

#### 코드 복잡도 증가

- 사용자별 락 관리 로직이 코드에 추가됨
- 사용자 수가 많을수록 ConcurrentHashMap 메모리 증가
- **해결 방법**: 필요시 락 객체 정리 코드 추가 가능

#### 분산 환경 지원 불가

- 단일 JVM 프로세스에서만 동작함
- 서버 확장 시 각자의 락 컨테이너로 동작
- **해결 방법**: Redis 분산 락(Redisson) 또는 DB Lock 사용

#### Starvation 가능성 존재

- 특정 사용자의 요청이 많을 경우 락 경합 발생 가능
- **현재 프로젝트 특성**: DB 지연시간 시뮬레이션 최대 300ms로 낮음

## 5. 기타 방식 검토

### 5.1 전역 락 (Global Lock)

```kotlin
private val globalLock = ReentrantLock()

fun chargeUserPoint(id: Long, amount: Long): UserPoint {
    globalLock.lock()
    try {
        // 모든 사용자의 포인트 연산 직렬화
    } finally {
        globalLock.unlock()
    }
}
```

**장점**: 구현 간단, 데드락 없음
**단점**: 동시성 제어 수준 낮음 (모든 요청 직렬화)
**평가**: 실무 시스템에서는 사용 불가, 성능 병목지점 발생

### 5.2 낙관적 락 (Optimistic Locking)

```kotlin
data class UserPoint(
    val id: Long,
    val point: Long,
    val version: Long // 버전 컬럼 추가
)

// 업데이트 시 버전 체크
if (currentVersion != expectedVersion) {
    throw OptimisticLockException()
}
```

**장점**: 충돌이 적은 환경에서 성능 우수
**단점**: 충돌 시 재시도 로직 필요, 재시도 오버헤드 발생
**평가**: 포인트 시스템은 충돌이 빈번한 경우가 많아 비효율적

### 5.3 인메모리 락

```sql
SELECT * FROM user_point WHERE id = ? FOR UPDATE
```

**장점**: 트랜잭션 보장, 강력한 격리 수준
**단점**: DB 의존성 높음, 서버 프로세스 확장에서 효과적
**평가**: 실제 DB 도입 시 활용 가능

## 6. 향후 시스템 확장 고려사항

### 6.1 인메모리 락 교체

현재 프로세스별 메모리에 락이 존재하므로, 실제 DB 도입 시:

- **트랜잭션 보장**: `@Transactional` 로 처리
- **데드락 방지**: DB 수준 데드락 감지 로직 추가
- **쿼리 최적화**: `user_id` 인덱스 쿼리

### 6.2 분산 환경 확장 고려

서버 확장 시 다음을 고려:

- **Redis 분산 락**: Redisson의 RLock 사용
- **인메모리 락**: Pessimistic Lock 사용
- **혼합 아키텍처**: 코드 변경을 최소화한 연산 보장

### 6.3 성능 모니터링 추가

- **락 경합 빈도 측정**: 의존성 추가 필요
- **락 대기 시간 모니터링**: 평균 시간 측정 로직
- **데드락 감지**: 예방 조건 설정

## 7. 결론

이 프로젝트는 최종적으로 **사용자별 ReentrantLock 방식**이:

1. 프로세스별 환경에서 인메모리 데이터 보호
2. 충돌 가능성 높은 동시성 제어 수준 (사용자별 독립, 성능)
3. 구현 복잡도가 낮음
4. 동시성 검증 테스트 통과 확인 (10개 충전, 5개 사용)

이 방법은 현재 시스템의 요구사항을 만족하지만, 분산 환경 시 실제 데이터베이스 도입 또는 Redis 사용을 고려해야 할 필요가 있습니다.

---

**작성일**: 2025-10-22
**프로젝트**: hhplus-tdd-kotlin
**기술 스택**: Kotlin 1.9.25, Spring Boot 3.5.6, ReentrantLock
