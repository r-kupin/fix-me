package com.rokupin.broker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ExchangeStateUpdateMessage implements Serializable {
    private List<Map<String, Integer>> exchangeStates;
}
