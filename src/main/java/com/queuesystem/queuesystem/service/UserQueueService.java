package com.queuesystem.queuesystem.service;

import com.queuesystem.queuesystem.exception.ErrorCode;
import com.queuesystem.queuesystem.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;


@Service
@RequiredArgsConstructor
public class UserQueueService {
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final RedisUtils redisUtils;
    private final String USER_QUEUE_WAIT_KEY = "user_queue:%s:wait";
    private final String USER_QUEUE_PROCEED_KEY = "user_queue:%s:proceed";

    // 대기열 등록
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        String key = USER_QUEUE_WAIT_KEY.formatted(queue);
        double unixTimestamp = Instant.now().getEpochSecond();

        return redisUtils.isUserAlreadyRegistered(key, userId)
                .flatMap(alreadyRegistered -> alreadyRegistered
                        ? Mono.error(ErrorCode.QUEUE_ALREADY_REGISTER_USER.build())
                        : redisUtils.addUserToQueue(key, userId, unixTimestamp));
    }

    // 대기열 진입 가능 여부
    public Mono<Long> allowUser(final String queue, final Long count) {
        return reactiveRedisTemplate.opsForZSet().popMin(USER_QUEUE_WAIT_KEY.formatted(queue), count)
                .flatMap(user -> reactiveRedisTemplate.opsForZSet().add(USER_QUEUE_PROCEED_KEY.formatted(queue), user.getValue(), Instant.now().getEpochSecond()))
                .count();
    }

    // 진입이 가능한 상태인지 조회
    public Mono<Boolean> isAllowed(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(- 1L)
                .map(rank -> rank >= 0);
    }

    public Mono<Long> getRank(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L)
                .map(rank -> rank >= 0 ? rank + 1 : rank);
    }
}
