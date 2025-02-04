package com.queuesystem.queuesystem.service;

import com.queuesystem.queuesystem.exception.ErrorCode;
import com.queuesystem.queuesystem.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;


@Service
@RequiredArgsConstructor
@Slf4j
public class UserQueueService {
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final RedisUtils redisUtils;
    private final String USER_QUEUE_WAIT_KEY = "user:queue:%s:wait";
    private final String USER_QUEUE_WAIT_FOR_SCAN = "user:queue:*:wait";
    private final String USER_QUEUE_PROCEED_KEY = "user:queue:%s:proceed";

    // 대기열 등록
    public Mono<Long> registerWaitQueue(final String queue, final String userId) {
        String key = USER_QUEUE_WAIT_KEY.formatted(queue);
        double unixTimestamp = Instant.now().getEpochSecond();

        return redisUtils.isUserAlreadyRegistered(key, userId)
                .flatMap(alreadyRegistered -> alreadyRegistered
                        ? Mono.error(ErrorCode.QUEUE_ALREADY_REGISTER_USER.build())
                        : redisUtils.addUserToQueue(key, userId, unixTimestamp));
    }

    public Mono<Long> allowUser(final String queue, final Long count) {
        return reactiveRedisTemplate.opsForZSet()
                .popMin(USER_QUEUE_WAIT_KEY.formatted(queue), count)
                .flatMap(member -> reactiveRedisTemplate.opsForZSet()
                        .add(USER_QUEUE_PROCEED_KEY.formatted(queue), member.getValue(), Instant.now().getEpochSecond()))
                .count()
                .flatMap(addedCount -> {
                    // Proceed 키의 TTL 설정 (10분)
                    // 사용자의 동작을 감지해서(Health Check) TTL을 갱신하는 방법
                    // 접속 이후, 모든 요청에 상태 체크?
                    String proceedKey = USER_QUEUE_PROCEED_KEY.formatted(queue);
                    return reactiveRedisTemplate.expire(proceedKey, Duration.ofMinutes(10))
                            .thenReturn(addedCount);
                });
    }

    // 진입이 가능한 상태인지 조회
    public Mono<Boolean> isAllowed(final String queue, final String userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId)
                .defaultIfEmpty(- 1L)
                .map(rank -> rank >= 0);
    }

    public Mono<Long> getRank(final String queue, final String userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId)
                .defaultIfEmpty(-1L)
                .map(rank -> rank >= 0 ? rank + 1 : rank);
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 3000)
    public void scheduleAllowUser() {
        // 부하율 계산
        double loadRate = calculateLoadRate();

        // 동적으로 처리량 조정
        long maxAllowUserCount = calculateMaxAllowUserCount(loadRate);

        reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                        .match(USER_QUEUE_WAIT_FOR_SCAN)
                        .count(100)
                        .build())
                .map(key -> key.split(":")[2])
                .flatMap(queue -> allowUser(queue, maxAllowUserCount).map(allowed -> Tuples.of(queue, allowed)))
                .subscribe();
    }

    private double calculateLoadRate() {
        // 서버의 CPU 및 메모리 사용량 측정
        double cpuLoad = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage(); // CPU 부하율
        double memoryUsage = getMemoryUsage(); // 메모리 사용률 (사용량 / 최대 메모리)

        // 부하율 계산 (CPU와 메모리 사용률 평균)
        return Math.min(1.0, (cpuLoad + memoryUsage) / 2);
    }

    private double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        double usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return usedMemory / runtime.maxMemory();
    }

    private Long calculateMaxAllowUserCount(double loadRate) {
        // 최대 처리량 설정 (예: 10명)
        long maxUserCapacity = 10;

        // 선형 모델로 처리량 조정
        return Math.max(1, (long) (maxUserCapacity * (1 - loadRate)));
    }

    public Mono<Boolean> updateHeartbeat(String queue, String userId) {
        String waitKey = USER_QUEUE_WAIT_KEY.formatted(queue);

        return reactiveRedisTemplate.expire(waitKey, Duration.ofSeconds(10));
    }

    // 대기열에서 사용자 제거
    public Mono<Void> removeUserFromQueue(String queue, String userId) {
        String waitKey = USER_QUEUE_WAIT_KEY.formatted(queue);
        return reactiveRedisTemplate.opsForZSet()
                .remove(waitKey, userId)
                .then();
    }

    public Mono<ResponseEntity<String>> removeAllUserFromQueue() {
        return reactiveRedisTemplate.execute(connection -> connection.serverCommands().flushAll())
                .then(Mono.just(ResponseEntity.ok("Redis 데이터가 초기화되었습니다.")))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("초기화 실패: " + e.getMessage())));
    }
}
