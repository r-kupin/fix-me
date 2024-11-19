package com.rokupin.model.fix;

import java.util.Map;

public class FixStockStateReport extends FixMessage {
    // 2nd custom message type - stocks state report
    private static final String MSG_STOCK_REPORT = "U2";

    private String sender;      // SenderCompID (49)
    private Map<String, Map<String, Integer>> stocks;

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws MissingRequiredTagException {
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws MissingRequiredTagException {
        appendTag(fixMessage, TAG_MSG_TYPE, MSG_STOCK_REPORT);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
    }
}
