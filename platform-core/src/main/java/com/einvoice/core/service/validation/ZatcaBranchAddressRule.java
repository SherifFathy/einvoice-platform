package com.einvoice.core.service.validation;

import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.Authority;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Validation rule ensuring branch address is complete for ZATCA invoices. */
@Component
public class ZatcaBranchAddressRule implements ValidationRule {

    @Override
    public List<ValidationError> validate(Invoice invoice, Authority authority) {
        List<ValidationError> errors = new ArrayList<>();
        if (authority != Authority.ZATCA) {
            return errors;
        }
        Branch branch = invoice.getBranch();
        if (branch == null) {
            return errors;
        }
        boolean incomplete = isBlank(branch.getStreet())
                || isBlank(branch.getCity())
                || isBlank(branch.getDistrict())
                || isBlank(branch.getPostalCode())
                || isBlank(branch.getBuildingNumber())
                || isBlank(branch.getCountryCode());
        if (incomplete) {
            errors.add(new ValidationError(
                    ValidationLayer.COMPLIANCE,
                    Authority.ZATCA,
                    "BR-BRANCH-ADDR-01",
                    "branch.address",
                    "Branch address required for ZATCA submission",
                    ValidationSeverity.ERROR));
        }
        return errors;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
