package com.rokupin.model.fix;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class FixIdAssignationStockState extends FixMessage {
    // 3rd custom message type - ID assignation message with stock states
    private static final String MSG_ID_ASSIGNATION_WITH_STOCKS = "U3";

    private String msgType;     // MsgType (35)
    private String sender;      // SenderCompID (49)
    private String target;      // TargetCompID (56)    >> assigned ID
    private String stockJson;

    public FixIdAssignationStockState(String sender, String target, String stockJson) {
        this.msgType = MSG_ID_ASSIGNATION_WITH_STOCKS;
        this.sender = sender;
        this.target = target;
        this.stockJson = stockJson;
    }

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws FixMessageMisconfiguredException {
        this.msgType = getRequiredField(fixFields, TAG_MSG_TYPE);
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
        this.stockJson = getRequiredField(fixFields, TAG_STOCK_STATE_JSON);
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws FixMessageMisconfiguredException {
        appendTag(fixMessage, TAG_MSG_TYPE, msgType);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_TARGET_COMP_ID, target);
        appendTag(fixMessage, TAG_STOCK_STATE_JSON, stockJson);
    }

    @Override
    protected void validateFields() throws FixMessageMisconfiguredException {
        if (!msgType.equals(MSG_ID_ASSIGNATION_WITH_STOCKS))
            throw new FixMessageMisconfiguredException(
                    "'message type' [MsgType (35)] for this type of message is " +
                            "expected to be 'U3'. Provided: '" + msgType + "'");
    }
}
