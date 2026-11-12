package com.wu.pharos.uam.ingestion.dto;

import java.util.UUID;

public record EventResult(UUID eventId, String status) {}
