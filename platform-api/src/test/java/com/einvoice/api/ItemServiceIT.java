package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Item;
import com.einvoice.core.domain.enums.AuthorityScope;
import com.einvoice.core.domain.enums.VatCategory;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.ItemRepository;
import com.einvoice.core.service.ItemService;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ItemServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("einvoice_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private ItemService itemService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company company;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        company = new Company();
        company.setNameAr("شركة اختبار الأصناف");
        company.setNameEn("Item Test Company");
        company.setVatNumber("660000000000006");
        company.setCrNumber("CR006");
        company = companyRepository.save(company);

        Long superUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE is_super_user = true LIMIT 1",
                Long.class);
        if (superUserId == null) {
            superUserId = jdbcTemplate.queryForObject(
                    "INSERT INTO users (name, email, password_hash, is_active, "
                            + "is_super_user) VALUES ('Super','super-item@test.com',"
                            + "'x',true,true) RETURNING id",
                    Long.class);
        }

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(superUserId, "credentials"));

        TenantContext.setCurrentTenantId(company.getId());
        TenantContext.setLovContextId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        itemRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void testCreateItem_success() {
        Item item = Item.builder()
                .code("ITM-001")
                .nameEn("Test Item")
                .nameAr("صنف اختبار")
                .unitOfMeasure("EA")
                .unitPrice(new BigDecimal("100.0000"))
                .vatCategory(VatCategory.S)
                .vatRate(new BigDecimal("15.00"))
                .authorityScope(AuthorityScope.BOTH)
                .build();

        Item saved = itemService.create(item);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).isEqualTo("ITM-001");
        assertThat(saved.getLovContextId()).isEqualTo(1L);
        assertThat(saved.getCompany().getId()).isEqualTo(company.getId());
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    void testCreateItem_lovContextIdSetFromTenantContext() {
        TenantContext.setLovContextId(2L);

        Item item = Item.builder()
                .code("ITM-CTX")
                .nameEn("Context Item")
                .unitOfMeasure("KG")
                .unitPrice(new BigDecimal("50.0000"))
                .vatCategory(VatCategory.S)
                .vatRate(new BigDecimal("15.00"))
                .build();

        Item saved = itemService.create(item);

        assertThat(saved.getLovContextId()).isEqualTo(2L);
    }

    @Test
    void testCreateItem_noLovContext_throws() {
        TenantContext.setLovContextId(null);

        Item item = Item.builder()
                .code("ITM-NOCTX")
                .nameEn("No Context Item")
                .unitOfMeasure("EA")
                .unitPrice(BigDecimal.ONE)
                .vatCategory(VatCategory.S)
                .vatRate(new BigDecimal("15"))
                .build();

        assertThatThrownBy(() -> itemService.create(item))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active LOV context");
    }

    @Test
    void testCreateItem_duplicateCode_throws() {
        Item item1 = Item.builder()
                .code("ITM-DUP")
                .nameEn("First Item")
                .unitOfMeasure("EA")
                .unitPrice(BigDecimal.ONE)
                .vatCategory(VatCategory.S)
                .vatRate(new BigDecimal("15"))
                .build();
        itemService.create(item1);

        Item item2 = Item.builder()
                .code("ITM-DUP")
                .nameEn("Second Item")
                .unitOfMeasure("EA")
                .unitPrice(BigDecimal.TEN)
                .vatCategory(VatCategory.S)
                .vatRate(new BigDecimal("15"))
                .build();

        assertThatThrownBy(() -> itemService.create(item2))
                .isInstanceOf(ItemService.DuplicateItemCodeException.class);
    }
}
