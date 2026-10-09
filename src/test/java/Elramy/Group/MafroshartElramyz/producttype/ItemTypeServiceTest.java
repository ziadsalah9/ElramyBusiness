package Elramy.Group.MafroshartElramyz.producttype;

import Elramy.Group.MafroshartElramyz.activity.ActivityLogService;
import Elramy.Group.MafroshartElramyz.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Admin-managed product types: unique names, no deleting a type in use, hidden types not for new products. */
class ItemTypeServiceTest {

    private ItemTypeRepository repo;
    private ItemTypeService service;
    private ItemType quilt;

    @BeforeEach
    void setUp() {
        repo = mock(ItemTypeRepository.class);
        service = new ItemTypeService(repo, mock(ActivityLogService.class));
        quilt = ItemType.builder().id(1L).name("لحاف").active(true).build();
        when(repo.findById(1L)).thenReturn(Optional.of(quilt));
        when(repo.findByName("لحاف")).thenReturn(Optional.of(quilt));
        when(repo.save(any())).thenAnswer(inv -> { ItemType t = inv.getArgument(0); t.setId(9L); return t; });
    }

    @Test
    void namesAreTrimmedAndUnique() {
        assertEquals("كنب سرير", service.create("  كنب   سرير ").name());
        assertThrows(ConflictException.class, () -> service.create(" لحاف "));
        assertThrows(IllegalArgumentException.class, () -> service.create("   "));
    }

    @Test
    void aTypeInUseCannotBeDeletedOnlyHidden() {
        when(repo.countProducts("لحاف")).thenReturn(6L);
        ConflictException ex = assertThrows(ConflictException.class, () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("6"));
        verify(repo, never()).delete(any());

        assertFalse(service.toggle(1L).active());
        when(repo.countProducts("لحاف")).thenReturn(0L);
        service.delete(1L);
        verify(repo).delete(quilt);
    }

    @Test
    void hiddenTypesAreNotForNewProductsButAProductKeepsItsOwn() {
        quilt.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.requireUsable("لحاف", null));
        assertEquals("لحاف", service.requireUsable("لحاف", "لحاف"));
        assertThrows(IllegalArgumentException.class, () -> service.requireUsable("كرسي", null));
    }

    @Test
    void renameKeepsStocktakeScopesInStep() {
        service.rename(1L, "لحافات");
        assertEquals("لحافات", quilt.getName());
        verify(repo).renameInStocktakeScopes("لحاف", "لحافات");
    }
}
