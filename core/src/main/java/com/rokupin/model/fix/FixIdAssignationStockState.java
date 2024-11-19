package com.rokupin.model.fix;

import java.util.Map;

public class FixIdAssignationStockState extends FixMessage {
    // 3rd custom message type - ID assignation message with stock states
    private static final String MSG_ID_ASSIGNATION_WITH_STOCKS = "U3";

    private String sender;      // SenderCompID (49)
    private String target;      // TargetCompID (56)    >> assigned ID
    private Map<String, Map<String, Integer>> stocks;

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws MissingRequiredTagException {
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws MissingRequiredTagException {
        appendTag(fixMessage, TAG_MSG_TYPE, MSG_ID_ASSIGNATION_WITH_STOCKS);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_TARGET_COMP_ID, target);
    }
}
