package com.queuesystem.queuesystem.controller;

import com.queuesystem.queuesystem.dto.*;
import com.queuesystem.queuesystem.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class UserQueueController {

    private final UserQueueService userQueueService;

    @PostMapping("")
    public Mono<RegisterUserResponse> registerUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                   @RequestParam(name = "user_id") Long userId) {
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
    public Mono<AllowedIUserResponse> isAllowedUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                    @RequestParam(name = "user_id") Long userId){
       return userQueueService.isAllowed(queue, userId)
               .map(AllowedIUserResponse::new);
    }

    @GetMapping("/requeue")
    public Mono<RequeueUser> requeueUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                         @RequestParam(name = "user_id") Long userId) {
        return userQueueService.isAllowedWithRequeue(queue, userId)
                .map(RequeueUser::new);
    }

    @GetMapping("/rank")
    public Mono<RankNumberResponse> getRankUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                @RequestParam(name = "user_id") Long userId) {
        return userQueueService.getRank(queue, userId)
                .map(RankNumberResponse::new);
    }


}
