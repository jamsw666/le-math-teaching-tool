package com.javalearning.engineering;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.javalearning.engineering.UnitTestDemo.OrderService;
import com.javalearning.engineering.UnitTestDemo.PaymentGateway;
import com.javalearning.engineering.UnitTestDemo.Product;
import com.javalearning.engineering.UnitTestDemo.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 9-3 真实的 JUnit 5 + Mockito 测试。运行：mvn test
 *
 * <p>对照 {@link UnitTestDemo} 中的 MiniJUnit / MiniMock 阅读。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 下单")
class OrderServiceTest {

    @Mock ProductRepository repo;
    @Mock PaymentGateway payment;
    @InjectMocks OrderService service; // 自动用上面两个 mock 调用构造器

    private final Product apple = new Product("A1", new BigDecimal("10.00"), 100);

    /** MockitoExtension 默认 STRICT_STUBS：没被用到的打桩会报 UnnecessaryStubbingException，所以只在需要的地方打桩。 */
    private void stubApple() {
        when(repo.findBySku("A1")).thenReturn(Optional.of(apple));
    }

    @Nested
    @DisplayName("正常路径")
    class HappyPath {

        @BeforeEach
        void setUp() {
            stubApple();
        }

        @Test
        @DisplayName("支付成功则扣减库存并返回金额")
        void placeOrder_success_savesReducedStock() {
            when(payment.charge(eq("u1"), any())).thenReturn(true);

            BigDecimal total = service.placeOrder("u1", "A1", 3);

            ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
            verify(repo).save(saved.capture());
            assertAll(
                    () -> assertEquals(new BigDecimal("30.00"), total),
                    () -> assertEquals(97, saved.getValue().stock()),
                    () -> assertEquals("A1", saved.getValue().sku()));
        }

        @ParameterizedTest(name = "购买 {0} 件应付 {1}")
        @CsvSource({"1, 10.00", "9, 90.00", "10, 90.000", "20, 180.000"})
        void placeOrder_discountRule(int qty, BigDecimal expected) {
            when(payment.charge(anyString(), any())).thenReturn(true);

            assertEquals(expected, service.placeOrder("u1", "A1", qty));
        }
    }

    @Nested
    @DisplayName("异常路径")
    class Failures {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        void placeOrder_nonPositiveQty_throwsAndNeverTouchesDependencies(int qty) {
            assertThrows(IllegalArgumentException.class, () -> service.placeOrder("u1", "A1", qty));
            verify(repo, never()).findBySku(any());
            verify(payment, never()).charge(any(), any());
        }

        @Test
        void placeOrder_unknownSku_throws() {
            when(repo.findBySku("ZZ")).thenReturn(Optional.empty());

            var e = assertThrows(IllegalArgumentException.class, () -> service.placeOrder("u1", "ZZ", 1));

            assertEquals("unknown sku ZZ", e.getMessage());
            verify(payment, never()).charge(any(), any());
        }

        @Test
        void placeOrder_insufficientStock_throws() {
            stubApple();
            var e = assertThrows(IllegalStateException.class, () -> service.placeOrder("u1", "A1", 101));
            assertEquals("insufficient stock", e.getMessage());
        }

        @Test
        void placeOrder_paymentDeclined_doesNotSave() {
            stubApple();
            when(payment.charge(anyString(), any())).thenReturn(false);

            assertThrows(IllegalStateException.class, () -> service.placeOrder("u1", "A1", 1));

            verify(repo, never()).save(any());
        }
    }
}
