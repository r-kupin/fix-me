package com.rokupin.model.fix;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class FixIdAssignation extends FixMessage {
    // 1st custom message type - ID assignation message
    private static final String MSG_ID_ASSIGNATION = "U1";

    private String msgType; // MsgType (35)
    private String sender;  // SenderCompID (49)
    private String target;  // TargetCompID (56) >> assigned ID

    public FixIdAssignation(String sender, String target) {
        this.msgType = MSG_ID_ASSIGNATION;
        this.sender = sender;
        this.target = target;
    }

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws FixMessageMisconfiguredException {
        this.msgType = getRequiredField(fixFields, TAG_MSG_TYPE);
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws FixMessageMisconfiguredException {
        appendTag(fixMessage, TAG_MSG_TYPE, msgType);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_TARGET_COMP_ID, target);
    }

    @Override
    protected void validateFields() throws FixMessageMisconfiguredException {
        if (!msgType.equals(MSG_ID_ASSIGNATION))
            throw new FixMessageMisconfiguredException(
                    "'message type' [MsgType (35)] for this type of message is " +
                            "expected to be 'U1'. Provided: '" + msgType + "'");
    }
}
