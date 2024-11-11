package com.rokupin.broker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class InitialStockStateMessage implements Serializable {
    private String assignedId;
    // StockId : {Instrument : AmountAvailable}
    private Map<String, Map<String, Integer>> stocks;
}
