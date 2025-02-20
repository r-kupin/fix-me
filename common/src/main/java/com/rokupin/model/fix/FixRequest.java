package com.rokupin.model.fix;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Objects;


@NoArgsConstructor
@Data
public class FixRequest extends FixMessage {
    public static final int SIDE_BUY = 1;
    public static final int SIDE_SELL = 2;

    private static final String MSG_TYPE_NEW_ORDER = "D";

    private String msgType;     // MsgType (35)
    private String sender;      // SenderCompID (49)
    private String senderSubId; // SenderSubId (50)
    private String target;      // TargetCompID (56)
    private String instrument;  // Symbol (55)
    private int action;         // Side (54) - 1 = Buy, 2 = Sell
    private int amount;         // OrderQty (38)

    public FixRequest(String sender,
                      String senderSubId,
                      String target,
                      String instrument,
                      int action,
                      int amount) throws FixMessageMisconfiguredException {
        if (Objects.nonNull(sender) &&
                Objects.nonNull(senderSubId) &&
                Objects.nonNull(target) &&
                Objects.nonNull(instrument)) {
            this.msgType = MSG_TYPE_NEW_ORDER;
            this.sender = sender;
            this.senderSubId = senderSubId;
            this.target = target;
            this.instrument = instrument;
            this.action = action;
            this.amount = amount;
            validateFields();
        } else {
            throw new FixMessageMisconfiguredException("No fields can be null.");
        }
    }

    public FixRequest(ClientTradingRequest clientMsg) throws FixMessageMisconfiguredException {
        if (Objects.nonNull(clientMsg.getTarget()) &&
                Objects.nonNull(clientMsg.getInstrument()) &&
                Objects.nonNull(clientMsg.getAction())) {
            this.msgType = MSG_TYPE_NEW_ORDER;
            this.target = clientMsg.getTarget();
            this.instrument = clientMsg.getInstrument();
            this.action = getSide(clientMsg.getAction());
            this.amount = clientMsg.getAmount();
            validateFields();
        } else {
            throw new FixMessageMisconfiguredException("No fields of JSON clientMsg can be null.");
        }
    }

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws FixMessageMisconfiguredException {
        this.msgType = getRequiredField(fixFields, TAG_MSG_TYPE);
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.senderSubId = fixFields.get(TAG_SOURCE_SUB_ID);
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
        this.instrument = getRequiredField(fixFields, TAG_SYMBOL);
        this.action = Integer.parseInt(getRequiredField(fixFields, TAG_SIDE));
        this.amount = Integer.parseInt(getRequiredField(fixFields, TAG_ORDER_QTY));
        validateFields();
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws FixMessageMisconfiguredException {
        appendTag(fixMessage, TAG_MSG_TYPE, msgType);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_SOURCE_SUB_ID, senderSubId);
        appendTag(fixMessage, TAG_SYMBOL, instrument);
        appendTag(fixMessage, TAG_SIDE, String.valueOf(action));
        appendTag(fixMessage, TAG_ORDER_QTY, String.valueOf(amount));
        appendTag(fixMessage, TAG_TARGET_COMP_ID, String.valueOf(target));
    }

    @Override
    protected void validateFields() throws FixMessageMisconfiguredException {
        if (action != SIDE_BUY && action != SIDE_SELL)
            throw new FixMessageMisconfiguredException(
                    "'action' [Side (54)] should be either 'buy' or 'sell'. Provided: '" +
                            action + "'");
        if (amount < 1)
            throw new FixMessageMisconfiguredException(
                    "'amount' [OrderQty (38)] should be a positive integer. Provided: '" +
                            amount + "'");

        if (!msgType.equals(MSG_TYPE_NEW_ORDER))
            throw new FixMessageMisconfiguredException(
                    "'message type' [MsgType (35)] for this type of message is " +
                            "expected to be 'D'. Provided: '" + msgType + "'");
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixRequest request)) return false;

        return action == request.action &&
                amount == request.amount &&
                msgType.equals(request.msgType) &&
                Objects.equals(sender, request.sender) &&
                Objects.equals(senderSubId, request.senderSubId) &&
                Objects.equals(target, request.target) &&
                instrument.equals(request.instrument);
    }

    @Override
    public int hashCode() {
        int result = msgType.hashCode();
        result = 31 * result + Objects.hashCode(sender);
        result = 31 * result + Objects.hashCode(senderSubId);
        result = 31 * result + Objects.hashCode(target);
        result = 31 * result + instrument.hashCode();
        result = 31 * result + action;
        result = 31 * result + amount;
        return result;
    }
}
