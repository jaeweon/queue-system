package com.queuesystem.queuesystem.service;

import com.queuesystem.queuesystem.exception.ErrorCode;
import com.queuesystem.queuesystem.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        String key = USER_QUEUE_WAIT_KEY.formatted(queue);
        double unixTimestamp = Instant.now().getEpochSecond();

        return redisUtils.isUserAlreadyRegistered(key, userId)
                .flatMap(alreadyRegistered -> alreadyRegistered
                        ? Mono.error(ErrorCode.QUEUE_ALREADY_REGISTER_USER.build())
                        : redisUtils.addUserToQueue(key, userId, unixTimestamp));
    }


    public Mono<Long> allowUser(final String queue, final Long count) {
        return reactiveRedisTemplate.opsForZSet().popMin(USER_QUEUE_WAIT_KEY.formatted(queue), count)
                .flatMap(member -> reactiveRedisTemplate.opsForZSet().add(USER_QUEUE_PROCEED_KEY.formatted(queue), member.getValue(), Instant.now().getEpochSecond()))
                .count();
    }

    // 진입이 가능한 상태인지 조회
    public Mono<Boolean> isAllowed(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(- 1L)
                .map(rank -> rank >= 0);
    }

    // 새로운 로직: proceed에서 제거하고 waiting으로 이동
    public Mono<Boolean> isAllowedWithRequeue(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet()
                .rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L)
                .flatMap(rank -> {
                    if (rank >= 0) {
                        // 유저를 proceed에서 제거하고 waiting으로 재등록
                        return requeueUser(queue, userId).thenReturn(false);
                    }
                    return Mono.just(false);
                });
    }


    // 사용자 대기열 재등록
    private Mono<Void> requeueUser(String queue, Long userId) {
        String proceedKey = USER_QUEUE_PROCEED_KEY.formatted(queue);
        String waitKey = USER_QUEUE_WAIT_KEY.formatted(queue);

        return reactiveRedisTemplate.opsForZSet().remove(proceedKey, userId.toString())
                .then(reactiveRedisTemplate.opsForZSet()
                        .add(waitKey, userId.toString(), Instant.now().getEpochSecond()))
                .then();
    }

    public Mono<Long> getRank(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L)
                .map(rank -> rank >= 0 ? rank + 1 : rank);
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 10000)
    public void scheduleAllowUser() {
//        log.info("접속 허용 중...");

        var maxAllowUserCount = 1L;
        reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                        .match(USER_QUEUE_WAIT_FOR_SCAN)
                        .count(100)
                        .build())
                .map(key -> key.split(":")[2])
                .flatMap(queue -> allowUser(queue, maxAllowUserCount).map(allowed -> Tuples.of(queue, allowed)))
                .doOnNext(tuple -> log.info("Tried %d and allowed %d members of %s queue".formatted(maxAllowUserCount, tuple.getT2(), tuple.getT1())))
                .subscribe();
    }
}
