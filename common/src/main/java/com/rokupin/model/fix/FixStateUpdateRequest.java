package com.rokupin.model.fix;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class FixStateUpdateRequest extends FixMessage {
    // 4th custom message type - Stock state update request
    private static final String MSG_STATE_UPDATE_REQUEST = "U4";

    private String msgType; // MsgType (35)
    private String sender;  // SenderCompID (49)

    public FixStateUpdateRequest(String sender) {
        this.msgType = MSG_STATE_UPDATE_REQUEST;
        this.sender = sender;
    }

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws FixMessageMisconfiguredException {
        this.msgType = getRequiredField(fixFields, TAG_MSG_TYPE);
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws FixMessageMisconfiguredException {
        appendTag(fixMessage, TAG_MSG_TYPE, msgType);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
    }

    @Override
    protected void validateFields() throws FixMessageMisconfiguredException {
        if (!msgType.equals(MSG_STATE_UPDATE_REQUEST))
            throw new FixMessageMisconfiguredException(
                    "'message type' [MsgType (35)] for this type of message is " +
                            "expected to be 'U4'. Provided: '" + msgType + "'");
    }
}
