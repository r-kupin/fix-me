package com.rokupin.broker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TradeResponse extends TradeMessage {
    public static final int MSG_ORD_FILLED = 2;
    public static final int MSG_ORD_REJECTED = 8;
    private static final int TAG_EXEC_ID = 17;
    private static final int TAG_LAST_PX = 31;
    private static final int TAG_LAST_SHARES = 32;
    private static final int TAG_ORDER_ID = 37;
    private static final int TAG_ORD_STATUS = 39;
    private static final int TAG_TARGET_SUB_ID = 57;
    private static final int TAG_EXEC_TYPE = 150;
    private static final int MSG_EXECUTION_REPORT = 8;
    private int sender;      // SenderCompID (49)
    private String target;         // TargetCompID (56)    >> assigned ID
    private String targetSubId;    // TargetSubID (57)     >> WS session ID
    private String instrument;  // Symbol (55)
    private String action;      // Side (54) - 1 = Buy, 2 = Sell
    private int amount;         // OrderQty (38)
    private int ordStatus;      // OrdStatus (39) - 0 = New, 2 = Filled, 8 = Rejected

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws MissingRequiredTagException {
        this.sender = Integer.parseInt(getRequiredField(fixFields, TAG_SOURCE_COMP_ID));
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
        this.targetSubId = getRequiredField(fixFields, TAG_TARGET_SUB_ID);
        this.instrument = getRequiredField(fixFields, TAG_SYMBOL);
        this.action = fixFields.get(TAG_SIDE);
        this.amount = Integer.parseInt(getRequiredField(fixFields, TAG_ORDER_QTY));
        this.ordStatus = Integer.parseInt(getRequiredField(fixFields, TAG_ORD_STATUS));
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws MissingRequiredTagException {
        appendTag(fixMessage, TAG_BEGIN_STRING, "FIX.5.0");
        appendTag(fixMessage, TAG_MSG_TYPE, String.valueOf(MSG_EXECUTION_REPORT));
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, String.valueOf(sender));
        appendTag(fixMessage, TAG_TARGET_COMP_ID, String.valueOf(target));
        appendTag(fixMessage, TAG_TARGET_SUB_ID, String.valueOf(targetSubId));
        appendTag(fixMessage, TAG_SYMBOL, instrument);
        appendTag(fixMessage, TAG_SIDE, action);
        appendTag(fixMessage, TAG_ORDER_QTY, String.valueOf(amount));
        appendTag(fixMessage, TAG_ORD_STATUS, String.valueOf(ordStatus));
    }
}
