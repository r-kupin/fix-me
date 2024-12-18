package com.rokupin.model.fix;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class FixStockStateReport extends FixMessage {
    // 2nd custom message type - stocks state report
    private static final String MSG_STOCK_REPORT = "U2";

    private String msgType;     // MsgType (35)
    private String sender;      // SenderCompID (49)
    private String target;      // TargetCompID (56)
    private String stockJson;   // Text (58)

    public FixStockStateReport(String sender, String stockJson) {
        this.msgType = MSG_STOCK_REPORT;
        this.sender = sender;
        this.stockJson = stockJson;
    }

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws FixMessageMisconfiguredException {
        this.msgType = getRequiredField(fixFields, TAG_MSG_TYPE);
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.stockJson = getRequiredField(fixFields, TAG_TEXT);
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws FixMessageMisconfiguredException {
        appendTag(fixMessage, TAG_MSG_TYPE, MSG_STOCK_REPORT);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_TEXT, stockJson);
    }

    @Override
    protected void validateFields() throws FixMessageMisconfiguredException {
        if (!msgType.equals(MSG_STOCK_REPORT))
            throw new FixMessageMisconfiguredException(
                    "'message type' [MsgType (35)] for this type of message is " +
                            "expected to be 'U2'. Provided: '" + msgType + "'");
    }
}
