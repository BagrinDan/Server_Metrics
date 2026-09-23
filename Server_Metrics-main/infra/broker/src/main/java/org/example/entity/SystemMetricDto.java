package org.example.entity;

import org.example.entity.enums.EventTypeEnum;

public record SystemMetricDto(
        EventTypeEnum eventType,
        long activeSubscribers,
        double cpuUsageMb,
        long timestamp
) {}
