package com.store.inventory.api;


public record ProductConfiguration(String sku, int stock, int reserved, int confirmed, ProductCategory category) {
}
