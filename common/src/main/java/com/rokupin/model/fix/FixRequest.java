package com.rokupin.model.fix;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Objects;


@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class FixRequest extends FixMessage {
    public static final int SIDE_BUY = 1;
    public static final int SIDE_SELL = 2;

    private static final String MSG_TYPE_NEW_ORDER = "D";

    private String sender;      // SenderCompID (49)
    private String senderSubId; // SenderSubId (50)
    private String target;      // TargetCompID (56)
    private String instrument;  // Symbol (55)
    private int action;         // Side (54) - 1 = Buy, 2 = Sell
    private int amount;         // OrderQty (38)

    public FixRequest(ClientTradingRequest clientMsg) throws FixMessageMisconfiguredException {
        if (Objects.nonNull(clientMsg.getTarget()) &&
                Objects.nonNull(clientMsg.getInstrument()) &&
                Objects.nonNull(clientMsg.getAction())) {
            this.target = clientMsg.getTarget();
            this.instrument = clientMsg.getInstrument();
            this.action = getSide(clientMsg.getAction());
            this.amount = clientMsg.getAmount();
            validate();
        } else {
            throw new FixMessageMisconfiguredException("No fields of JSON clientMsg can be null.");
        }
    }

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws FixMessageMisconfiguredException {
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.senderSubId = fixFields.get(TAG_SOURCE_SUB_ID);
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
        this.instrument = getRequiredField(fixFields, TAG_SYMBOL);
        this.action = Integer.parseInt(getRequiredField(fixFields, TAG_SIDE));
        this.amount = Integer.parseInt(getRequiredField(fixFields, TAG_ORDER_QTY));
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws FixMessageMisconfiguredException {
        appendTag(fixMessage, TAG_MSG_TYPE, MSG_TYPE_NEW_ORDER);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_SOURCE_SUB_ID, senderSubId);
        appendTag(fixMessage, TAG_SYMBOL, instrument);
        appendTag(fixMessage, TAG_SIDE, String.valueOf(action));
        appendTag(fixMessage, TAG_ORDER_QTY, String.valueOf(amount));
        appendTag(fixMessage, TAG_TARGET_COMP_ID, String.valueOf(target));
    }

    @Override
    protected void validate() throws FixMessageMisconfiguredException {
        if (action != SIDE_BUY && action != SIDE_SELL)
            throw new FixMessageMisconfiguredException(
                    "'action' [Side (54)] should be either 'buy' or 'sell'. Provided: '" +
                            action + "'");
        if (amount < 1)
            throw new FixMessageMisconfiguredException(
                    "'amount' [OrderQty (38)] should be a positive integer. Provided: '" +
                            amount + "'");
    }
}
