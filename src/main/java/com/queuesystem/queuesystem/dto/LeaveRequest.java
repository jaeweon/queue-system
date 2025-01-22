package com.queuesystem.queuesystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequest {
    private String queue;  // 대기열 이름
    private Long userId;   // 사용자 ID
}