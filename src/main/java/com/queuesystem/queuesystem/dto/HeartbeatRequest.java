package com.queuesystem.queuesystem.dto;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatRequest {
    private String queue; // 클라이언트에서 보내는 "queue" 필드
    private String userId;  // 클라이언트에서 보내는 "user_id" 필드
}
