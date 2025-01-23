package com.queuesystem.queuesystem.service;

import com.queuesystem.queuesystem.exception.ErrorCode;
import com.queuesystem.queuesystem.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

//    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
//        String baseKey = USER_QUEUE_WAIT_KEY.formatted(queue);
//        double unixTimestamp = Instant.now().getEpochSecond();
//
//        // 테스트: 10,000개의 키를 생성하고 각 키에 값을 삽입
//        Flux<String> testKeys = Flux.range(1, 10000)
//                .map(i -> baseKey + ":test-key-" + i);
//
//        return testKeys
//                .flatMap(testKey -> redisUtils.addUserToQueue(testKey, userId, unixTimestamp))
//                .then(Mono.defer(() ->
//                        redisUtils.isUserAlreadyRegistered(baseKey, userId)
//                                .flatMap(alreadyRegistered -> alreadyRegistered
//                                        ? Mono.error(ErrorCode.QUEUE_ALREADY_REGISTER_USER.build())
//                                        : redisUtils.addUserToQueue(baseKey, userId, unixTimestamp))
//                ));
//    }

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

    @Scheduled(initialDelay = 5000, fixedDelay = 10000)
    public void scheduleAllowUser() {

        var maxAllowUserCount = 1L;
        reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                        .match(USER_QUEUE_WAIT_FOR_SCAN)
                        .count(100)
                        .build())
                .map(key -> key.split(":")[2])
                .flatMap(queue -> allowUser(queue, maxAllowUserCount).map(allowed -> Tuples.of(queue, allowed)))
//                .doOnNext(tuple -> log.info("Tried %d and allowed %d members of %s queue".formatted(maxAllowUserCount, tuple.getT2(), tuple.getT1())))
                .subscribe();
    }

    // 하트비트로 TTL 갱신
    public Mono<Boolean> updateHeartbeat(String queue, String userId) {
        String waitKey = USER_QUEUE_WAIT_KEY.formatted(queue);

        // TTL 갱신 (점수는 변경하지 않음)
        return reactiveRedisTemplate.expire(waitKey, Duration.ofSeconds(10));
    }

    // 대기열에서 사용자 제거
    public Mono<Void> removeUserFromQueue(String queue, String userId) {
        String waitKey = USER_QUEUE_WAIT_KEY.formatted(queue);
        return reactiveRedisTemplate.opsForZSet()
                .remove(waitKey, userId)
                .then();
    }
}
