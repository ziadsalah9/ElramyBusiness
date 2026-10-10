package Elramy.Group.MafroshartElramyz.productcolor;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductColorServiceTest {

    private ProductColorRepository repo;
    private ProductColorService service;

    @BeforeEach
    void setUp() {
        repo = mock(ProductColorRepository.class);
        when(repo.save(any())).thenAnswer(inv -> {
            ProductColor c = inv.getArgument(0);
            c.setId(9L);
            return c;
        });
        service = new ProductColorService(repo, mock(ActivityLogService.class));
    }

    @Test
    void createNormalisesNameAndHex() {
        when(repo.findByName("أزرق فاتح")).thenReturn(Optional.empty());
        var c = service.create("  أزرق   فاتح ", " #A1B2C3 ");
        assertEquals("أزرق فاتح", c.name());
        assertEquals("#a1b2c3", c.hex());
        assertNull(service.create("سماوي", "  ").hex(), "blank swatch = none");
    }

    @Test
    void badHexAndDuplicatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create("سماوي", "blue"));
        assertThrows(IllegalArgumentException.class, () -> service.create("سماوي", "#12345"));
        when(repo.findByName("أبيض")).thenReturn(Optional.of(ProductColor.builder().id(1L).name("أبيض").build()));
        assertThrows(ConflictException.class, () -> service.create("أبيض", null));
    }

    @Test
    void aColourInUseCannotBeDeleted() {
        ProductColor white = ProductColor.builder().id(1L).name("أبيض").active(true).build();
        when(repo.findById(1L)).thenReturn(Optional.of(white));
        when(repo.countVariants("أبيض")).thenReturn(3L);
        assertThrows(ConflictException.class, () -> service.delete(1L));
        verify(repo, never()).delete(any());

        when(repo.countVariants("أبيض")).thenReturn(0L);
        service.delete(1L);
        verify(repo).delete(white);
    }

    @Test
    void hiddenColourOnlyForItemsThatAlreadyHaveIt() {
        ProductColor gold = ProductColor.builder().id(2L).name("ذهبي").active(false).build();
        when(repo.findByName("ذهبي")).thenReturn(Optional.of(gold));
        assertThrows(IllegalArgumentException.class, () -> service.requireUsable("ذهبي", null));
        assertEquals("ذهبي", service.requireUsable("ذهبي", "ذهبي"));
        assertNull(service.requireUsable("  ", null), "blank = no colour");
        assertThrows(IllegalArgumentException.class, () -> service.requireUsable("لون غريب", null));
    }

    @Test
    void oldMultiColourValueMapsToItsLabel() {
        when(repo.findByName("متعدد الألوان")).thenReturn(Optional.of(ProductColor.builder().id(3L).name("متعدد الألوان").active(true).build()));
        assertEquals(Optional.of("متعدد الألوان"), service.resolve("متعدد"));
    }
}
