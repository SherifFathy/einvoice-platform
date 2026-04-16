package com.einvoice.core.service.importing;

public record ImportError(int row, String field, String message) {
}
