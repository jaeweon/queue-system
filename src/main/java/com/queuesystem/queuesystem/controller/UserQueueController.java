package com.queuesystem.queuesystem.controller;

import com.queuesystem.queuesystem.dto.*;
import com.queuesystem.queuesystem.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class UserQueueController {

    private final UserQueueService userQueueService;

    @PostMapping("")
    public Mono<RegisterUserResponse> registerUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                   @RequestParam(name = "user_id") String userId) {
        return userQueueService.registerWaitQueue(queue, userId)
                .map(RegisterUserResponse::new);
    }

    @PostMapping("/allow")
    public Mono<AllowUserResponse> allowUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                             @RequestParam(name = "count") Long count){
        return userQueueService.allowUser(queue, count)
                .map(allowedUser -> new AllowUserResponse(count, allowedUser));
    }

    @GetMapping("/allowed")
    public Mono<AllowedUserResponse> isAllowedUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                   @RequestParam(name = "user_id") String userId) {
        return userQueueService.isAllowed(queue, userId)
                .map(AllowedUserResponse::new);
    }

    @GetMapping("/rank")
    public Mono<RankNumberResponse> getRankUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                @RequestParam(name = "user_id") String userId) {
        return userQueueService.getRank(queue, userId)
                .map(RankNumberResponse::new);
    }
    @PostMapping("/leave")
    public Mono<ResponseEntity<Void>> leaveQueue(@RequestBody HeartbeatRequest request) {
        return userQueueService.removeUserFromQueue(request.getQueue(), request.getUserId())
                .thenReturn(ResponseEntity.ok().build());
    }

    @PostMapping("/heartbeat")
    public Mono<ResponseEntity<Void>> heartbeat(@RequestBody HeartbeatRequest request) {
        return userQueueService.updateHeartbeat(request.getQueue(), request.getUserId())
                .thenReturn(ResponseEntity.ok().build());
    }
}
