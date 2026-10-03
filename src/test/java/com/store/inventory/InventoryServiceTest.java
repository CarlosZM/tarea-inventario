package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {

    private InventoryService service;

    @BeforeEach
    void setUp() {

        service = Inventory.create(Clock.systemUTC(), (sku, available) -> System.out.println("Stock alert onLowStock for sku: " + sku + " availableUnits: " + available + " and email send"));
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.registerProduct("SKU-2", ProductCategory.FLASH_SALE);
    }

    @Test
    void reservingReducesAvailableUnits() {
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 3);
        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void availableUnitsForNonExistingSku() {
        assertEquals(0, service.available("SKU-4"));
    }

    @Test
    void addingStockIncreasesAvailableUnits() {
        service.addStock("SKU-1", 10);
        service.addStock("SKU-1", 5);

        assertEquals(15, service.available("SKU-1"));
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        service.addStock("SKU-1", 2);
        assertThrows(InsufficientStockException.class, () -> service.reserve("ORDER-1", "SKU-1", 3));
    }

    @Test
    void cannotReserveMoreThanAllowedByCategory() {
        service.addStock("SKU-2", 10);
        assertThrows(OrderLimitExceededException.class, () -> service.reserve("ORDER-1", "SKU-2", 3));
    }

    @Test
    void cannotReserveLowerThanZero() {
        service.addStock("SKU-2", 10);
        assertThrows(IllegalArgumentException.class, () -> service.reserve("ORDER-1", "SKU-2", -1));
    }

    @Test
    void confirmedUnitsStaySold() {
        service.addStock("SKU-1", 5);
        service.reserve("ORDER-1", "SKU-1", 2);
        service.confirm("ORDER-1");
        assertEquals(3, service.available("SKU-1"));
    }

    @Test
    void confirmIllegalExceptionWhenNoActiveReserve() throws InterruptedException {
        service.addStock("SKU-2", 5);
        service.reserve("ORDER-1", "SKU-2", 2);
        Thread.sleep(120000);
        assertThrows(IllegalStateException.class, () -> service.confirm("ORDER-1"));
        assertEquals(5, service.available("SKU-2"));
    }

    @Test
    void reserveAfterTimePassedForCategory() throws InterruptedException {
        service.addStock("SKU-2", 5);
        service.reserve("ORDER-1", "SKU-2", 2);
        //Thread.sleep(120000);
        Thread.sleep(2000);
        service.reserve("ORDER-1", "SKU-2", 2);
        //Thread.sleep(120000);
        Thread.sleep(2000);
        service.reserve("ORDER-1", "SKU-2", 2);
        Thread.sleep(2000);
        assertThrows(IllegalStateException.class, () -> service.confirm("ORDER-1"));
        assertEquals(5, service.available("SKU-2"));

    }
}
