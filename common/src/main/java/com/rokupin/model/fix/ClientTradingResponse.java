package com.rokupin.model.fix;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@ToString
@Data
public class ClientTradingResponse {
    private String sender;
    private String instrument;
    private String action;
    private String ordStatus;
    private int amount;

    public ClientTradingResponse(FixResponse fix) throws FixMessageMisconfiguredException {
        this.sender = fix.getSender();
        this.instrument = fix.getInstrument();
        this.amount = fix.getAmount();
        this.ordStatus = switch (fix.getOrdStatus()) {
            case 0 -> "new";
            case 2 -> "filled";
            case 8 -> "rejected";
            default -> throw new FixMessageMisconfiguredException(
                    "OrderStatus parameter should be either '0', '8' or '2' but '" +
                            fix.getOrdStatus() + "' provided");
        };
        this.action = switch (fix.getAction()) {
            case 1 -> "buy";
            case 2 -> "sell";
            default -> throw new FixMessageMisconfiguredException(
                    "Side parameter should be either '1' or '2' but '" +
                            fix.getAction() + "' provided");
        };
    }
}
