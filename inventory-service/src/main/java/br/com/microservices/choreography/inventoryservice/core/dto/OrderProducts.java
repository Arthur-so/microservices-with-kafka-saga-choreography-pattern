package br.com.microservices.choreography.inventoryservice.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OrderProducts {
    private Product product;
    private int quantity;
}
