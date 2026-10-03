package com.store.inventory.api;


import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InventoryServiceImpl implements InventoryService {

    private Clock clock;

    private StockAlertListener stockAlertListener;

    private Map<String, ProductConfiguration> productConfigurationMap;

    private Map<String, Map<String, Reservation>> productReservationMap;

    private Map<String, String> reservationProductMap;

    private Map<ProductCategory, CategoryConfiguration> categoryConfigurationMap;

    public InventoryServiceImpl(Clock clock, StockAlertListener stockAlertListener) {
        this.clock = clock;
        this.stockAlertListener = stockAlertListener;
        this.productConfigurationMap = new HashMap<>();
        this.productReservationMap = new HashMap<>();
        this.categoryConfigurationMap = new EnumMap<>(ProductCategory.class);
        this.reservationProductMap = new HashMap<>();

        this.categoryConfigurationMap.put(ProductCategory.STANDARD, new CategoryConfiguration(true, 15, ChronoUnit.MINUTES, false, 0));
        this.categoryConfigurationMap.put(ProductCategory.PRE_ORDER, new CategoryConfiguration(true, 24, ChronoUnit.HOURS, false, 0));
        this.categoryConfigurationMap.put(ProductCategory.FLASH_SALE, new CategoryConfiguration(true, 2, ChronoUnit.SECONDS, true, 2));
    }

    @Override
    public void registerProduct(String sku, ProductCategory category) {

        if (this.productConfigurationMap.containsKey(sku)) {

        }
        this.productConfigurationMap.put(sku, new ProductConfiguration(sku, 0, 0, 0, category));
        this.productReservationMap.put(sku, new HashMap<>());
    }

    @Override
    public void addStock(String sku, int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        if (!this.productConfigurationMap.containsKey(sku)) {
            throw new IllegalArgumentException("Product must exist");
        }
        this.productConfigurationMap.put(sku, new ProductConfiguration(sku, this.productConfigurationMap.get(sku).stock() + quantity, 0, 0, this.productConfigurationMap.get(sku).category()));
    }

    @Override
    public Reservation reserve(String orderId, String sku, int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        if (!this.productConfigurationMap.containsKey(sku)) {
            throw new IllegalArgumentException("Product must exist");
        }
        this.releaseReservations(sku);

        ProductConfiguration productConfiguration = this.productConfigurationMap.getOrDefault(sku, this.getProductConfiguration());

        CategoryConfiguration categoryConfiguration = this.categoryConfigurationMap.getOrDefault(productConfiguration.category(), new CategoryConfiguration(true, 0, null, false, 0));

        if (categoryConfiguration.purchaseLimit() && quantity > categoryConfiguration.purchaseLimitQuantity()) {
            throw new OrderLimitExceededException(sku, quantity, 2);
        }

        if (productConfiguration.stock() < quantity) {
            throw new InsufficientStockException(sku, quantity, productConfiguration.stock());
        }

        this.productReservationMap.get(sku).put(orderId, new Reservation(orderId, sku, quantity, clock.instant().plus(categoryConfiguration.expiresQuantity(), categoryConfiguration.expiresUnit())));

        this.productConfigurationMap.put(sku, new ProductConfiguration(sku, productConfiguration.stock() - quantity, productConfiguration.reserved() + quantity, productConfiguration.confirmed(), productConfiguration.category()));

        this.reservationProductMap.put(orderId, sku);

        return this.productReservationMap.get(sku).get(orderId);
    }

    @Override
    public void confirm(String orderId) {

        String sku = this.reservationProductMap.get(orderId);

        this.releaseReservations(sku);

        Reservation reservation = this.productReservationMap.get(sku).get(orderId);

        if (reservation == null) {

            throw new IllegalStateException("order has no active reservation");
        }
        ProductConfiguration productConfiguration = this.productConfigurationMap.get(sku);
        this.releaseReservation(sku, orderId, reservation, productConfiguration, true);

        productConfiguration = this.productConfigurationMap.get(sku);

        if (productConfiguration.stock() + productConfiguration.reserved() <= 5) {

            this.stockAlertListener.onLowStock(sku, productConfiguration.stock());
        }

    }

    @Override
    public int available(String sku) {
        return this.productConfigurationMap.getOrDefault(sku, this.getProductConfiguration()).stock();
    }

    private ProductConfiguration getProductConfiguration() {
        return new ProductConfiguration(null, 0, 0, 0, null);
    }

    private void releaseReservations() {
        for (String sku : this.productReservationMap.keySet()) {

            this.releaseReservations(sku);
        }
    }

    private void releaseReservations(String sku) {
        ProductConfiguration productConfiguration = this.productConfigurationMap.get(sku);
        for (String orderId : this.productReservationMap.get(sku).keySet()) {
            Reservation reservation = this.productReservationMap.get(sku).get(orderId);
            if (this.clock.instant().isAfter(reservation.expiresAt())) {
                this.releaseReservation(sku, orderId, reservation, productConfiguration, false);
            }
        }
    }

    private void releaseReservation(String sku, String orderId, Reservation reservation, ProductConfiguration productConfiguration, boolean confirmed) {
        this.productReservationMap.get(sku).remove(orderId);
        this.productConfigurationMap.remove(orderId);

        this.productConfigurationMap.put(sku, new ProductConfiguration(sku, confirmed ? productConfiguration.stock() : productConfiguration.stock() + reservation.quantity(), productConfiguration.reserved() - reservation.quantity(), confirmed ? productConfiguration.confirmed() + reservation.quantity() : productConfiguration.confirmed(), productConfiguration.category()));
    }
}
