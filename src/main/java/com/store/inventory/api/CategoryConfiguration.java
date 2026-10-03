package com.store.inventory.api;

import java.time.temporal.TemporalUnit;

public record CategoryConfiguration(boolean expires,
                                    int expiresQuantity,
                                    TemporalUnit expiresUnit,
                                    boolean purchaseLimit,
                                    int purchaseLimitQuantity) {
}

