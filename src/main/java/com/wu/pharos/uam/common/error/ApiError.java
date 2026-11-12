package com.wu.pharos.uam.common.error;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String code, String message, String correlationId) {}
