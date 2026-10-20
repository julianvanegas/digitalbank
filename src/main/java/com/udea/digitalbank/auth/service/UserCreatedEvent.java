package com.udea.digitalbank.auth.service;

import java.util.UUID;

public record UserCreatedEvent(UUID userId) {
}
