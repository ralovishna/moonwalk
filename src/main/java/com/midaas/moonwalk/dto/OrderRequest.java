package com.midaas.moonwalk.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrderRequest(
        @NotNull(message = "Restaurant ID is required")
        Long restaurantId,

        @NotNull(message = "Table ID is required to know where to serve the food")
        Long tableId,

        @NotBlank(message = "Customer name is required")
        String customerName,

        @NotNull(message = "Party size is required")
        @Min(value = 1, message = "Party size must be at least 1")
        Integer partySize,

        @NotEmpty(message = "Order must contain at least one dish")
        @Valid
        List<OrderItemRequest> items
) {
        public record OrderItemRequest(
                @NotNull(message = "Menu Item ID is required")
                Long menuItemId
        ) {}
}