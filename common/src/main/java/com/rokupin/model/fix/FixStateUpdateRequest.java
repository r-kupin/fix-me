package com.rokupin.model.fix;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
@Data
public class FixStateUpdateRequest extends FixMessage {
    // 4th custom message type - Stock state update request
    private static final String MSG_STATE_UPDATE_REQUEST = "U4";

    private String msgType; // MsgType (35)
    private String sender;  // SenderCompID (49)
    private String target;  // TargetCompID (56)

    public FixStateUpdateRequest(String sender, String target) {
        this.msgType = MSG_STATE_UPDATE_REQUEST;
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
        if (!msgType.equals(MSG_STATE_UPDATE_REQUEST))
            throw new FixMessageMisconfiguredException(
                    "'message type' [MsgType (35)] for this type of message is " +
                            "expected to be 'U4'. Provided: '" + msgType + "'");
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixStateUpdateRequest that)) return false;

        return msgType.equals(that.msgType) &&
                sender.equals(that.sender) &&
                target.equals(that.target);
    }

    @Override
    public int hashCode() {
        int result = msgType.hashCode();
        result = 31 * result + sender.hashCode();
        result = 31 * result + target.hashCode();
        return result;
    }
}
