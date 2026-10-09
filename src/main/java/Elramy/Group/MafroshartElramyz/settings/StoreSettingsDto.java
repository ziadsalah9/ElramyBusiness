package Elramy.Group.MafroshartElramyz.settings;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Store settings as read by every user and edited by admins. */
public record StoreSettingsDto(
        @NotBlank(message = "اسم المحل مطلوب") @Size(max = 200, message = "اسم المحل طويل جداً") String storeName,
        @Size(max = 100, message = "رقم الهاتف طويل جداً") String phone,
        @Size(max = 300, message = "العنوان طويل جداً") String address,
        @Size(max = 300, message = "تذييل الإيصال طويل جداً") String receiptFooter,
        @NotNull(message = "حد خصم الموظف مطلوب")
        @DecimalMin(value = "0", message = "حد الخصم لا يمكن أن يكون سالباً")
        @DecimalMax(value = "100", message = "حد الخصم لا يتجاوز 100%") BigDecimal employeeMaxDiscountPercent) {
}
