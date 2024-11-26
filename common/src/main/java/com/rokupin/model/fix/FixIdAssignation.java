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
public class FixIdAssignation extends FixMessage {
    // 1st custom message type - ID assignation message
    private static final String MSG_ID_ASSIGNATION = "U1";

    private String sender;  // SenderCompID (49)
    private String target;  // TargetCompID (56) >> assigned ID

    @Override
    protected void parseFields(Map<Integer, String> fixFields) throws FixMessageMisconfiguredException {
        this.sender = getRequiredField(fixFields, TAG_SOURCE_COMP_ID);
        this.target = getRequiredField(fixFields, TAG_TARGET_COMP_ID);
    }

    @Override
    protected void appendFields(StringBuilder fixMessage) throws FixMessageMisconfiguredException {
        appendTag(fixMessage, TAG_MSG_TYPE, MSG_ID_ASSIGNATION);
        appendTag(fixMessage, TAG_SOURCE_COMP_ID, sender);
        appendTag(fixMessage, TAG_TARGET_COMP_ID, target);
    }
}
