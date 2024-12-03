package com.rokupin.model.fix;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Objects;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class FixResponse extends FixMessage {
    public static final int MSG_ORD_FILLED = 2;
    public static final int MSG_ORD_REJECTED = 8;
    private static final int TAG_EXEC_ID = 17;
    private static final int TAG_LAST_PX = 31;
    private static final int TAG_LAST_SHARES = 32;
    private static final int TAG_ORDER_ID = 37;
    private static final int TAG_EXEC_TYPE = 150;

    public static final String MSG_EXECUTION_REPORT = "8";
    public static final int TAG_ORD_STATUS = 39;

    public static final int UNSPECIFIED = 0;
    public static final int UNSUPPORTED_FORMAT = 1;
    public static final int EXCHANGE_IS_NOT_AVAILABLE = 2;
    public static final int INSTRUMENT_NOT_SUPPORTED = 3;
    public static final int EXCHANGE_LACKS_REQUESTED_AMOUNT = 4;

    private String msgType;         // MsgType (35)
    private String sender;          // SenderCompID (49)
    private String target;          // TargetCompID (56)    >> assigned ID
    private String targetSubId;     // TargetSubID (57)     >> WS session ID
    private String instrument;      // Symbol (55)
    private int action;             // Side (54) - 1 = Buy, 2 = Sell
    private int amount;             // OrderQty (38)
    private int ordStatus;          // OrdStatus (39) - 0 = New, 2 = Filled, 8 = Rejected
    private int rejectionReason;    // OrdRejReason (103)

    public FixResponse(String sender,
                       String target,
                       String targetSubId,
                       String instrument,
                       int action,
                       int amount,
                       int ordStatus,
                       int rejectionReason) throws FixMessageMisconfiguredException {
        if (Objects.nonNull(sender) &&
                Objects.nonNull(target) &&
                Objects.nonNull(targetSubId) &&
                Objects.nonNull(instrument)) {
            this.msgType = MSG_EXECUTION_REPORT;
            this.sender = sender;
            this.target = target;
            this.targetSubId = targetSubId;
            this.instrument = instrument;
            this.action = action;
            this.amount = amount;
            this.ordStatus = ordStatus;
            this.rejectionReason = rejectionReason;
            validateFields();
        } else {
            throw new FixMessageMisconfiguredException("No fields can be null.");
        }
    }

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws FixMessageMisconfiguredException {
        this.msgType = getRequiredField(fixFields, TAG_MSG_TYPE);
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
        this.targetSubId = getRequiredField(fixFields, TAG_TARGET_SUB_ID);
        this.instrument = getRequiredField(fixFields, TAG_SYMBOL);
        this.action = Integer.parseInt(getRequiredField(fixFields, TAG_SIDE));
        this.amount = Integer.parseInt(getRequiredField(fixFields, TAG_ORDER_QTY));
        this.ordStatus = Integer.parseInt(getRequiredField(fixFields, TAG_ORD_STATUS));
        this.rejectionReason = 0;
        if (fixFields.containsKey(TAG_ORD_REJ_REASON))
            this.rejectionReason = Integer.parseInt(fixFields.get(TAG_ORD_REJ_REASON));
        validateFields();
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws FixMessageMisconfiguredException {
        appendTag(fixMessage, TAG_MSG_TYPE, msgType);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_TARGET_COMP_ID, target);
        appendTag(fixMessage, TAG_TARGET_SUB_ID, targetSubId);
        appendTag(fixMessage, TAG_SYMBOL, instrument);
        appendTag(fixMessage, TAG_SIDE, String.valueOf(action));
        appendTag(fixMessage, TAG_ORDER_QTY, String.valueOf(amount));
        appendTag(fixMessage, TAG_ORD_STATUS, String.valueOf(ordStatus));
        if (rejectionReason > 0)
            appendTag(fixMessage, TAG_ORD_REJ_REASON, String.valueOf(rejectionReason));
    }

    @Override
    protected void validateFields() throws FixMessageMisconfiguredException {
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

        if (rejectionReason < 0 || rejectionReason > 5)
            throw new FixMessageMisconfiguredException(
                    "OrdRejReason (103) should be >= 0 and <= 4 Provided: '" +
                            rejectionReason + "'");

        if (!msgType.equals(MSG_EXECUTION_REPORT))
            throw new FixMessageMisconfiguredException(
                    "'message type' [MsgType (35)] for this type of message is " +
                            "expected to be '8'. Provided: '" + msgType + "'");
    }

    public String getDescription() {
        return switch (rejectionReason) {
            case 1 -> "Unsupported format";
            case 2 -> "Target exchange is unavailable";
            case 3 -> "Target exchange doesn't operate with requested instrument";
            case 4 -> "Target exchange doesn't possess requested quantity";
            default -> "Reason unknown";
        };
    }
}