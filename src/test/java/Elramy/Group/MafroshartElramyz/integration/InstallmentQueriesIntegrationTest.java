package Elramy.Group.MafroshartElramyz.integration;

import Elramy.Group.MafroshartElramyz.installment.InstallmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** The installment overview queries run on PostgreSQL (fetch joins over plans, customers, invoices, schedules). */
@SpringBootTest
class InstallmentQueriesIntegrationTest {

    @Autowired private InstallmentService installments;

    @Test
    void customerOverviewQueriesRun() {
        assertDoesNotThrow(() -> installments.customers());
        assertDoesNotThrow(() -> installments.forCustomer(1L));
    }
}
