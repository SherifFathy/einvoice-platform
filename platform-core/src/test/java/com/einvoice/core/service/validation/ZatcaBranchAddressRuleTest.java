package com.einvoice.core.service.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.Authority;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZatcaBranchAddressRuleTest {

    private final ZatcaBranchAddressRule rule = new ZatcaBranchAddressRule();

    private Branch buildCompleteAddressBranch() {
        Branch b = new Branch();
        b.setStreet("King Fahad Rd");
        b.setCity("Riyadh");
        b.setDistrict("Al Olaya");
        b.setPostalCode("12211");
        b.setBuildingNumber("1234");
        b.setCountryCode("SA");
        return b;
    }

    @Test
    void completeBranchAddress_noError() {
        Invoice invoice = new Invoice();
        invoice.setBranch(buildCompleteAddressBranch());

        List<ValidationError> errors = rule.validate(invoice, Authority.ZATCA);
        assertTrue(errors.isEmpty());
    }

    @Test
    void branchWithNullCity_returnsError() {
        Branch b = buildCompleteAddressBranch();
        b.setCity(null);
        Invoice invoice = new Invoice();
        invoice.setBranch(b);

        List<ValidationError> errors = rule.validate(invoice, Authority.ZATCA);
        assertEquals(1, errors.size());
        assertEquals("BR-BRANCH-ADDR-01", errors.get(0).ruleId());
    }

    @Test
    void etaInvoice_noErrorEvenWithoutAddress() {
        Invoice invoice = new Invoice();
        Branch b = new Branch();
        invoice.setBranch(b);

        List<ValidationError> errors = rule.validate(invoice, Authority.ETA);
        assertTrue(errors.isEmpty());
    }
}
