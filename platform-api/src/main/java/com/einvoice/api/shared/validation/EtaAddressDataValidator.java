package com.einvoice.api.shared.validation;

import com.einvoice.core.error.InvalidAddressDataException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates ETA address data contains all required keys.
 */
public final class EtaAddressDataValidator {

    private static final Set<String> REQUIRED_KEYS = Set.of(
            "country", "governorate", "regionCity", "street", "buildingNumber");

    private EtaAddressDataValidator() {
    }

    /**
     * Validates that addressData contains all required ETA address keys.
     *
     * @param addressData the address map to validate
     */
    public static void validate(Map<String, Object> addressData) {
        if (addressData == null) {
            throw new InvalidAddressDataException("addressData is required",
                    new ArrayList<>(REQUIRED_KEYS));
        }
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            Object value = addressData.get(key);
            if (value == null || value.toString().isBlank()) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidAddressDataException(
                    "addressData missing required keys: " + missing, missing);
        }
    }
}
