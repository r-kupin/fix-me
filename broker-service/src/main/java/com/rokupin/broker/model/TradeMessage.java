package com.rokupin.broker.model;


import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class TradeMessage implements Serializable {

    protected static final int TAG_BEGIN_STRING = 8;
    protected static final int TAG_MSG_TYPE = 35;
    protected static final int TAG_SOURCE_COMP_ID = 49;
    protected static final int TAG_SYMBOL = 55;
    protected static final int TAG_SIDE = 54;
    protected static final int TAG_CHECKSUM = 10;
    protected static final int TAG_ORDER_QTY = 38;
    protected static final int TAG_TARGET_COMP_ID = 56;

    public static <T extends TradeMessage> T fromFix(String fixMessage, T message) throws MissingRequiredTagException {
        Map<Integer, String> fixFields = Arrays.stream(fixMessage.split("\u0001"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(
                        pair -> Integer.parseInt(pair[0]),
                        pair -> pair[1]
                ));

        message.parseFields(fixFields);
        return message;
    }

    protected static String getRequiredField(Map<Integer, String> fields,
                                             int tag) throws MissingRequiredTagException {
        return Optional.ofNullable(fields.get(tag))
                .orElseThrow(() -> new MissingRequiredTagException("Missing required tag: " + tag));
    }

    protected abstract void parseFields(Map<Integer, String> fixFields) throws MissingRequiredTagException;

    protected abstract void appendFields(StringBuilder fixMessage) throws MissingRequiredTagException;

    public String asFix() throws MissingRequiredTagException {
        StringBuilder fixMessage = new StringBuilder();
        appendFields(fixMessage);

        // Calculate checksum
        int checksum = calculateChecksum(fixMessage.toString());
        appendTag(fixMessage, TAG_CHECKSUM, String.format("%03d", checksum));

        return fixMessage.toString();
    }

    protected void appendTag(StringBuilder stringBuilder, int tag, String value) throws MissingRequiredTagException {
        if (value.isEmpty())
            throw new MissingRequiredTagException("Missing value for tag: " + tag);
        stringBuilder.append(tag).append("=").append(value).append("\u0001");
    }

    private int calculateChecksum(String message) {
        byte[] bytes = message.getBytes(StandardCharsets.US_ASCII);
        int sum = 0;
        for (byte b : bytes) {
            sum += b;
        }
        return sum % 256;
    }
}
