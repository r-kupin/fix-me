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
public class FixIdAssignationStockState extends FixMessage {
    // 3rd custom message type - ID assignation message with stock states
    private static final String MSG_ID_ASSIGNATION_WITH_STOCKS = "U3";

    private String sender;      // SenderCompID (49)
    private String target;      // TargetCompID (56)    >> assigned ID
    private String stockJson;

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws MissingRequiredTagException {
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
        this.stockJson = getRequiredField(fixFields, TAG_STOCK_STATE_JSON);
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws MissingRequiredTagException {
        appendTag(fixMessage, TAG_MSG_TYPE, MSG_ID_ASSIGNATION_WITH_STOCKS);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_TARGET_COMP_ID, target);
        appendTag(fixMessage, TAG_STOCK_STATE_JSON, stockJson);
    }
}
