package com.rokupin.model.fix;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public abstract class FixMessage implements Serializable {
    protected static final int TAG_STOCK_STATE_JSON = 0;
    protected static final int TAG_BEGIN_STRING = 8;
    protected static final int TAG_CHECKSUM = 10;
    protected static final int TAG_MSG_TYPE = 35;
    protected static final int TAG_ORDER_QTY = 38;
    protected static final int TAG_SOURCE_COMP_ID = 49;
    protected static final int TAG_SOURCE_SUB_ID = 50;
    protected static final int TAG_SIDE = 54;
    protected static final int TAG_SYMBOL = 55;
    protected static final int TAG_TARGET_COMP_ID = 56;
    protected static final int TAG_TARGET_SUB_ID = 57;
    protected static final int TAG_ORD_REJ_REASON = 103;

    public static <T extends FixMessage> T fromFix(String fixMessage, T message) throws FixMessageMisconfiguredException {
        Map<Integer, String> fixFields = Arrays.stream(fixMessage.split("\u0001"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(
                        pair -> Integer.parseInt(pair[0]),
                        pair -> pair[1]
                ));

        message.parseFields(fixFields);
        message.validate();
        return message;
    }

    protected static String getRequiredField(Map<Integer, String> fields,
                                             int tag) throws FixMessageMisconfiguredException {
        return Optional.ofNullable(fields.get(tag))
                .orElseThrow(() ->
                        new FixMessageMisconfiguredException("Missing required tag: " + tag)
                );
    }

    protected abstract void parseFields(Map<Integer, String> fixFields) throws FixMessageMisconfiguredException;

    protected abstract void appendFields(StringBuilder fixMessage) throws FixMessageMisconfiguredException;

    protected void validate() throws FixMessageMisconfiguredException {
    }

    public String asFix() throws FixMessageMisconfiguredException {
        StringBuilder fixMessage = new StringBuilder();

        appendTag(fixMessage, TAG_BEGIN_STRING, "FIX.5.0");
        appendFields(fixMessage);

        // Calculate checksum
        int checksum = calculateChecksum(fixMessage.toString());
        appendTag(fixMessage, TAG_CHECKSUM, String.format("%03d", checksum));

        return fixMessage.toString();
    }

    protected void appendTag(StringBuilder stringBuilder, int tag, String value) throws FixMessageMisconfiguredException {
        if (value.isEmpty())
            throw new FixMessageMisconfiguredException("Missing value for tag: " + tag);
        stringBuilder.append(tag).append("=").append(value).append("\u0001");
    }

    protected int getSide(String action) throws FixMessageMisconfiguredException {
        return switch (action) {
            case "buy" -> 1;
            case "sell" -> 2;
            default -> throw new FixMessageMisconfiguredException(
                    "Side (54) should be 1 (Buy) or 2 (Sell). Provided: '" +
                            action + "'");
        };
    }

    private int calculateChecksum(String message) {
        byte[] bytes = message.getBytes(StandardCharsets.US_ASCII);
        int sum = 0;
        for (byte b : bytes) {
            sum += b;
        }
        return sum % 256;
    }

    public static List<String> splitFixMessages(String messages) {
        List<String> fixMessages = new ArrayList<>();
        StringBuilder currentMessage = new StringBuilder();
        String[] parts = messages.split("\u0001"); // Split by the SOH character

        for (String part : parts) {
            currentMessage.append(part).append("\u0001"); // Re-add the delimiter
            if (part.startsWith("10=")) { // Detect the end of a FIX message
                fixMessages.add(currentMessage.toString());
                currentMessage.setLength(0); // Reset for the next message
            }
        }
        return fixMessages;
    }
}
