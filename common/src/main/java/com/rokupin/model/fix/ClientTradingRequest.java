package com.rokupin.model.fix;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ClientTradingRequest implements Serializable {
    private String target;
    private String instrument;
    private String action;
    private int amount;
}
