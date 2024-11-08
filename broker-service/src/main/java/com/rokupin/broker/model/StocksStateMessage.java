package com.rokupin.broker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class StocksStateMessage implements Serializable {
    private int stockId;
    private Map<String, Integer> instrumentToAvailableQty;
}
