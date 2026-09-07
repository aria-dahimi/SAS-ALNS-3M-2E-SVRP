package problem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testsupport.TestConfigSupport;

import static org.junit.jupiter.api.Assertions.*;

class CustomerSetTest {

    private Depot depot;

    @BeforeEach
    void setUp() {
        TestConfigSupport.applyBaseConfiguration();
        depot = new Depot("D1", 0, 0, 0, 0, 1000, 0);
    }

    @Test
    void mutationVersionChangesOnlyWhenMembershipChanges() {
        CustomerSet customers = new CustomerSet();
        Customer customer = new Customer("C1", depot, 0, 0, 0, 100, 10, 10);

        long startVersion = customers.getMutationVersion();
        customers.addCustomer(customer);
        assertEquals(startVersion + 1, customers.getMutationVersion());

        customers.removeCustomer(new Customer("OTHER", depot, 0, 0, 0, 100, 1, 10));
        assertEquals(startVersion + 1, customers.getMutationVersion());

        customers.removeCustomer(customer);
        assertEquals(startVersion + 2, customers.getMutationVersion());
    }

    @Test
    void copyCreatesIndependentCustomerObjects() {
        CustomerSet source = new CustomerSet();
        Customer original = new Customer("C1", depot, 1, 2, 3, 4, 5, 6);
        source.addCustomer(original);

        CustomerSet copy = new CustomerSet();
        copy.copyFrom(source);

        assertEquals(1, copy.getCustomerCount());
        assertNotSame(original, copy.getCustomer(0));
        assertEquals(original.id, copy.getCustomer(0).id);
        assertEquals(original.demand, copy.getCustomer(0).demand, 1e-12);
    }

    @Test
    void deterministicShuffleUsesSuppliedSeed() {
        CustomerSet first = customerSet(8);
        CustomerSet second = customerSet(8);

        first.shuffleCustomers(new java.util.Random(1230));
        second.shuffleCustomers(new java.util.Random(1230));

        for (int i = 0; i < first.getCustomerCount(); i++) {
            assertEquals(first.getCustomer(i).id, second.getCustomer(i).id);
        }
    }

    private CustomerSet customerSet(int count) {
        CustomerSet set = new CustomerSet();
        for (int i = 1; i <= count; i++) {
            set.addCustomer(new Customer("C" + i, depot, i, i, 0, 100, i, 10));
        }
        return set;
    }
}
