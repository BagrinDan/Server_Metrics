package org.example.entity;



public record MessageDTO(
        String topic,
        String payload,
        long timestamp
    ) {
        public MessageDTO {
            if (timestamp == 0) {
                timestamp = System.currentTimeMillis();
            }
        }
}
