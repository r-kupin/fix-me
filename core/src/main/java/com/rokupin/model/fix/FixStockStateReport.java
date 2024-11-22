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
public class FixStockStateReport extends FixMessage {
    // 2nd custom message type - stocks state report
    private static final String MSG_STOCK_REPORT = "U2";

    private String sender;      // SenderCompID (49)
    private String stockJson;

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws MissingRequiredTagException {
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.stockJson = getRequiredField(fixFields, TAG_STOCK_STATE_JSON);
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws MissingRequiredTagException {
        appendTag(fixMessage, TAG_MSG_TYPE, MSG_STOCK_REPORT);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_STOCK_STATE_JSON, stockJson);
    }
}
