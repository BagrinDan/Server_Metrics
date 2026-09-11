package org.example.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JournalLogDto(
        @JsonProperty("_HOSTNAME") String hostname,
        @JsonProperty("_SYSTEMD_UNIT") String unit,
        @JsonProperty("MESSAGE") String message,
        @JsonProperty("PRIORITY") String priority,
        @JsonProperty("__REALTIME_TIMESTAMP") String timestamp
) {}
