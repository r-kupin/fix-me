package com.rokupin.broker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class StocksStateMessage implements Serializable {
    private Map<String, Map<String, Integer>> stocks;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StocksStateMessage that = (StocksStateMessage) o;
        return Objects.equals(stocks, that.stocks);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(stocks);
    }
}
