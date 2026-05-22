package com.midaas.moonwalk.dto;

public record WalkInResponse(
    boolean isSeated,
    Long tableId,
    String message,
    Integer waitlistEtaSeconds
) {}