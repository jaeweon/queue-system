package com.queuesystem.queuesystem.utils;

import com.queuesystem.queuesystem.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;
@Component
public class RedisUtils {
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    public RedisUtils(ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    // 사용자 등록 여부 확인
    public Mono<Boolean> isUserAlreadyRegistered(String key, Long userId) {
        return reactiveRedisTemplate.opsForZSet()
                .rank(key, userId.toString())
                .map(Objects::nonNull)
                .defaultIfEmpty(false);
    }

    // 대기열에 사용자 추가
    public Mono<Long> addUserToQueue(String key, Long userId, double score) {
        return reactiveRedisTemplate.opsForZSet()
                .add(key, userId.toString(), score)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(ErrorCode.QUEUE_REGISTRATION_FAILED.build()))
                .flatMap(success -> reactiveRedisTemplate.opsForZSet()
                        .rank(key, userId.toString())
                        .map(rank -> rank + 1));
    }
}