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
public class FixResponse extends FixMessage {
    public static final int MSG_ORD_FILLED = 2;
    public static final int MSG_ORD_REJECTED = 8;
    private static final int TAG_EXEC_ID = 17;
    private static final int TAG_LAST_PX = 31;
    private static final int TAG_LAST_SHARES = 32;
    private static final int TAG_ORDER_ID = 37;
    private static final int TAG_ORD_STATUS = 39;
    private static final int TAG_TARGET_SUB_ID = 57;
    private static final int TAG_EXEC_TYPE = 150;
    private static final String MSG_EXECUTION_REPORT = "8";

    private String sender;      // SenderCompID (49)
    private String target;      // TargetCompID (56)    >> assigned ID
    private String targetSubId; // TargetSubID (57)     >> WS session ID
    private String instrument;  // Symbol (55)
    private int action;         // Side (54) - 1 = Buy, 2 = Sell
    private int amount;         // OrderQty (38)
    private int ordStatus;      // OrdStatus (39) - 0 = New, 2 = Filled, 8 = Rejected

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws FixMessageMisconfiguredException {
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
        this.targetSubId = getRequiredField(fixFields, TAG_TARGET_SUB_ID);
        this.instrument = getRequiredField(fixFields, TAG_SYMBOL);
        this.action = Integer.parseInt(getRequiredField(fixFields, TAG_SIDE));
        this.amount = Integer.parseInt(getRequiredField(fixFields, TAG_ORDER_QTY));
        this.ordStatus = Integer.parseInt(getRequiredField(fixFields, TAG_ORD_STATUS));
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws FixMessageMisconfiguredException {
        appendTag(fixMessage, TAG_MSG_TYPE, MSG_EXECUTION_REPORT);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_TARGET_COMP_ID, target);
        appendTag(fixMessage, TAG_TARGET_SUB_ID, targetSubId);
        appendTag(fixMessage, TAG_SYMBOL, instrument);
        appendTag(fixMessage, TAG_SIDE, String.valueOf(action));
        appendTag(fixMessage, TAG_ORDER_QTY, String.valueOf(amount));
        appendTag(fixMessage, TAG_ORD_STATUS, String.valueOf(ordStatus));
    }

    @Override
    protected void validate() throws FixMessageMisconfiguredException {
        if (action != 1 && action != 2)
            throw new FixMessageMisconfiguredException(
                    "Side (54) should be 1 (Buy) or 2 (Sell). Provided: '" +
                            action + "'");
        if (amount < 1)
            throw new FixMessageMisconfiguredException(
                    "OrderQty (38) should be a positive integer. Provided: '" +
                            amount + "'");
        if (ordStatus != 0 && ordStatus != 2 && ordStatus != 8)
            throw new FixMessageMisconfiguredException(
                    "OrdStatus (39) should be 0 (New), 2 (Filled) or 8 (Rejected)." +
                            " Provided: '" + ordStatus + "'");
    }
}
