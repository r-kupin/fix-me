package com.rokupin.model.fix;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;


@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class FixRequest extends FixMessage {
    private static final int TAG_SOURCE_SUB_ID = 50;
    private static final String MSG_TYPE_NEW_ORDER = "D";

    private String sender;      // SenderCompID (49)
    private String senderSubId; // SenderSubId (50)
    private String target;      // TargetCompID (56)
    private String instrument;  // Symbol (55)
    private String action;      // Side (54) - 1 = Buy, 2 = Sell
    private int amount;         // OrderQty (38)

    public FixRequest(ClientTradingRequest clientMsg) {
        this.target = clientMsg.getTarget();
        this.instrument = clientMsg.getInstrument();
        this.action = clientMsg.getAction();
        this.amount = clientMsg.getAmount();
    }

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws MissingRequiredTagException {
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.senderSubId = fixFields.get(TAG_SOURCE_SUB_ID);
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
        this.instrument = getRequiredField(fixFields, TAG_SYMBOL);
        this.action = fixFields.get(TAG_SIDE);
        this.amount = Integer.parseInt(getRequiredField(fixFields, TAG_ORDER_QTY));
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws MissingRequiredTagException {
        appendTag(fixMessage, TAG_BEGIN_STRING, "FIX.5.0");
        appendTag(fixMessage, TAG_MSG_TYPE, MSG_TYPE_NEW_ORDER);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_SOURCE_SUB_ID, senderSubId);
        appendTag(fixMessage, TAG_SYMBOL, instrument);
        appendTag(fixMessage, TAG_SIDE, action);
        appendTag(fixMessage, TAG_ORDER_QTY, String.valueOf(amount));
        appendTag(fixMessage, TAG_TARGET_COMP_ID, String.valueOf(target));
    }
}
