package io.hhplus.tdd.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Configuration
class LockConfig {

    @Bean
    fun userLock(): ConcurrentHashMap<Long, ReentrantLock> {
        return ConcurrentHashMap()
    }
}
