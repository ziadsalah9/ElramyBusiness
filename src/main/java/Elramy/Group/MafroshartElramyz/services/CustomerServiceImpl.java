package Elramy.Group.MafroshartElramyz.services;

import Elramy.Group.MafroshartElramyz.activity.ActivityAction;
import Elramy.Group.MafroshartElramyz.activity.ActivityEntity;
import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.activity.Changes;
import Elramy.Group.MafroshartElramyz.dto.customer.CreateCustomerRequest;
import Elramy.Group.MafroshartElramyz.dto.customer.CustomerDeleteResponse;
import Elramy.Group.MafroshartElramyz.dto.customer.CustomerResponse;
import Elramy.Group.MafroshartElramyz.dto.customer.UpdateCustomerRequest;
import Elramy.Group.MafroshartElramyz.exception.CustomerNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.CustomerPhoneNotFoundException;
import Elramy.Group.MafroshartElramyz.exception.DuplicateCustomerException;
import Elramy.Group.MafroshartElramyz.mapping.CustomerMapper;
import Elramy.Group.MafroshartElramyz.models.Customer;
import Elramy.Group.MafroshartElramyz.repository.CustomerRepository;
import Elramy.Group.MafroshartElramyz.repository.SalesInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final ActivityLogService activityLog;


    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public CustomerResponse create(
            CreateCustomerRequest request) {

        String phone = normalizePhone(request.phone());
        String secondaryPhone = normalizeOptionalPhone(request.secondaryPhone());

        ensurePhoneIsFree(phone, null);
        ensureSecondaryDiffers(phone, secondaryPhone);

        Customer customer =
                Customer.builder()
                        .name(request.name().trim())
                        .phone(phone)
                        .secondaryPhone(secondaryPhone)
                        .address(request.address())
                        .active(true)
                        .build();

        customer = customerRepository.save(customer);
        activityLog.record(ActivityEntity.CUSTOMER, ActivityAction.CREATE, customer.getId(), label(customer),
                "إضافة عميل «" + customer.getName() + "»",
                Changes.of().set("الاسم", customer.getName()).set("الهاتف", customer.getPhone())
                        .set("هاتف ثانوي", customer.getSecondaryPhone()).set("العنوان", customer.getAddress()));
        return customerMapper.toResponse(customer);
    }


    // =========================================================
    // UPDATE
    // =========================================================

    @Override
    public CustomerResponse update(
            Long id,
            UpdateCustomerRequest request) {

        Customer customer =
                customerRepository.findById(id)
                        .orElseThrow(() ->
                                new CustomerNotFoundException(id)
                        );

        String phone = normalizePhone(request.phone());
        String secondaryPhone = normalizeOptionalPhone(request.secondaryPhone());

        ensurePhoneIsFree(phone, id);
        ensureSecondaryDiffers(phone, secondaryPhone);

        Changes changes = Changes.of()
                .add("الاسم", customer.getName(), request.name().trim())
                .add("الهاتف", customer.getPhone(), phone)
                .add("هاتف ثانوي", customer.getSecondaryPhone(), secondaryPhone)
                .add("العنوان", customer.getAddress(), request.address());
        if (request.active() != null) changes.add("نشط", customer.getActive(), request.active());

        customer.setName(request.name().trim());
        customer.setPhone(phone);
        customer.setSecondaryPhone(secondaryPhone);
        customer.setAddress(request.address());

        if (request.active() != null) {
            customer.setActive(request.active());
        }

        customer = customerRepository.save(customer);
        if (!changes.isEmpty()) {
            boolean restored = Boolean.TRUE.equals(request.active()) && changes.list().stream().anyMatch(c -> c.field().equals("نشط"));
            activityLog.record(ActivityEntity.CUSTOMER, restored ? ActivityAction.STATUS : ActivityAction.UPDATE,
                    customer.getId(), label(customer),
                    (restored ? "استعادة عميل «" : "تعديل عميل «") + customer.getName() + "»", changes);
        }
        return customerMapper.toResponse(customer);
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {

        return customerMapper.toResponse(
                customerRepository.findById(id)
                        .orElseThrow(() ->
                                new CustomerNotFoundException(id)
                        )
        );
    }


    // =========================================================
    // GET BY PHONE
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getByPhone(String phone) {

        String normalized = normalizePhone(phone);

        return customerRepository.findByPhone(normalized)
                .map(customerMapper::toResponse)
                .orElseThrow(() ->
                        new CustomerPhoneNotFoundException(
                                normalized
                        )
                );
    }


    // =========================================================
    // GET ALL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAll() {

        return customerRepository.findAll()
                .stream()
                .map(customerMapper::toResponse)
                .toList();
    }


    // =========================================================
    // TOGGLE STATUS
    // =========================================================

    @Override
    public CustomerResponse toggleStatus(Long id) {

        Customer customer =
                customerRepository.findById(id)
                        .orElseThrow(() ->
                                new CustomerNotFoundException(id)
                        );

        customer.setActive(
                !Boolean.TRUE.equals(customer.getActive())
        );

        customer = customerRepository.save(customer);
        activityLog.record(ActivityEntity.CUSTOMER, ActivityAction.STATUS, customer.getId(), label(customer),
                (Boolean.TRUE.equals(customer.getActive()) ? "تفعيل" : "تعطيل") + " عميل «" + customer.getName() + "»");
        return customerMapper.toResponse(customer);
    }


    // =========================================================
    // DELETE
    // =========================================================

    /**
     * Permanently deletes a customer with no sales invoices. A customer
     * that has invoices is deactivated instead: deleting the row would
     * break (or force deleting) the invoice history.
     */
    @Override
    public CustomerDeleteResponse delete(Long id) {

        Customer customer =
                customerRepository.findById(id)
                        .orElseThrow(() ->
                                new CustomerNotFoundException(id)
                        );

        if (salesInvoiceRepository.existsByCustomerId(id)) {
            customer.setActive(false);
            customerRepository.save(customer);
            activityLog.record(ActivityEntity.CUSTOMER, ActivityAction.STATUS, id, label(customer),
                    "إخفاء عميل «" + customer.getName() + "» (لديه فواتير)");
            return new CustomerDeleteResponse(id, false);
        }

        customerRepository.delete(customer);
        activityLog.record(ActivityEntity.CUSTOMER, ActivityAction.DELETE, id, label(customer),
                "حذف عميل «" + customer.getName() + "» نهائياً",
                Changes.of().add("الهاتف", customer.getPhone(), null).add("العنوان", customer.getAddress(), null));
        return new CustomerDeleteResponse(id, true);
    }


    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    /** Blank means "no secondary phone". */
    private static String label(Customer c) {
        return c.getName() + " (" + c.getPhone() + ")";
    }

    private String normalizeOptionalPhone(String phone) {

        if (phone == null || phone.isBlank()) {
            return null;
        }

        return phone.trim();
    }

    private void ensureSecondaryDiffers(
            String phone,
            String secondaryPhone) {

        if (secondaryPhone != null && secondaryPhone.equals(phone)) {
            throw new IllegalArgumentException(
                    "Secondary phone must be different from the primary phone."
            );
        }
    }

    private String normalizePhone(String phone) {

        if (phone == null) {
            return null;
        }

        return phone.trim();
    }

    private void ensurePhoneIsFree(
            String phone,
            Long currentCustomerId) {

        Optional<Customer> existing =
                customerRepository.findByPhone(phone);

        if (existing.isEmpty()) {
            return;
        }

        if (currentCustomerId != null
                && currentCustomerId.equals(
                        existing.get().getId()
                )) {
            return;
        }

        throw new DuplicateCustomerException(phone);
    }
}