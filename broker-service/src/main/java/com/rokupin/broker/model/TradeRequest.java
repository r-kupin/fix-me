package com.rokupin.broker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Contract;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@AllArgsConstructor
@NoArgsConstructor
@Data
public class TradeRequest implements Serializable {
    private static final int TAG_BEGIN_STRING = 8;
    private static final int TAG_MSG_TYPE = 35;
    private static final int TAG_MSG_SEQ_NUM = 34;
    private static final int TAG_SENDER_COMP_ID = 49;
    private static final int TAG_SENDING_TIME = 52;
    private static final int TAG_SYMBOL = 55;
    private static final int TAG_SIDE = 54;
    private static final int TAG_ORDER_QTY = 38;
    private static final int TAG_EX_DESTINATION = 100;
    private static final int TAG_CHECKSUM = 10;
    // FIX version and message type
    private static final String FIX_VERSION = "FIX.5.0";
    private static final String MSG_TYPE_NEW_ORDER = "D";
    private String broker;       // SenderCompID (49)
    private String instrument;   // Symbol (55)
    private String action;       // Side (54) - 1 = Buy, 2 = Sell
    private int amount;          // OrderQty (38)
    private int exchangeId;      // ExDestination (100)

    public static TradeRequest fromFix(String fixMessage) {
        Map<Integer, String> fixFields = Arrays
                .stream(fixMessage.split("\u0001"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(pair ->
                                parseInt(pair[0]),
                        pair -> pair[1],
                        (oldVal, newVal) -> newVal));

        String broker = getRequiredField(fixFields, TAG_SENDER_COMP_ID, "SenderCompID");
        String instrument = getRequiredField(fixFields, TAG_SYMBOL, "Symbol");
        String action = getRequiredField(fixFields, TAG_SIDE, "Side");
        int amount = parseInt(getRequiredField(fixFields, TAG_ORDER_QTY, "OrderQty"), "OrderQty");
        int exchangeId = parseInt(getRequiredField(fixFields, TAG_EX_DESTINATION, "ExDestination"), "ExDestination");

        String actionStr = getFixSide(action, false);

        return new TradeRequest(broker, instrument, actionStr, amount, exchangeId);
    }

    private static String getRequiredField(Map<Integer, String> fields, int tag, String fieldName) {
        return Optional
                .ofNullable(fields.get(tag))
                .orElseThrow(() ->
                        new IllegalArgumentException("Missing required field: " + fieldName + " (Tag " + tag + ")"));
    }

    private static int parseInt(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value for field: " + fieldName + ". Received: " + value);
        }
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value);
    }

    public String asFix() {
        // TODO increment this with each message
        int seqNum = 1;
        String sendingTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS"));

        StringBuilder fixMessage = new StringBuilder();
        appendTag(fixMessage, TAG_BEGIN_STRING, FIX_VERSION);
        appendTag(fixMessage, TAG_MSG_TYPE, MSG_TYPE_NEW_ORDER);
        appendTag(fixMessage, TAG_MSG_SEQ_NUM, String.valueOf(seqNum));
        appendTag(fixMessage, TAG_SENDER_COMP_ID, broker);
        appendTag(fixMessage, TAG_SENDING_TIME, sendingTime);
        appendTag(fixMessage, TAG_SYMBOL, instrument);
        appendTag(fixMessage, TAG_SIDE, getFixSide(action, true));
        appendTag(fixMessage, TAG_ORDER_QTY, String.valueOf(amount));
        appendTag(fixMessage, TAG_EX_DESTINATION, String.valueOf(exchangeId));

        String fixBody = fixMessage.toString();
        int checksum = calculateChecksum(fixBody);

        appendTag(fixMessage, 10, String.format("%03d", checksum)); // Checksum

        return fixMessage.toString();
    }

    private static String getFixSide(String action, boolean toFix) {
        return toFix ? switch (action.toLowerCase()) {
            case "buy" -> "1";   // Buy
            case "sell" -> "2";  // Sell
            default ->
                    throw new IllegalArgumentException("Invalid action: " + action);
        } : switch (action.toLowerCase()) {
            case "1" -> "buy";   // Buy
            case "2" -> "sell";  // Sell
            default ->
                    throw new IllegalArgumentException("Invalid action: " + action);
        };
    }

    private void appendTag(StringBuilder stringBuilder, int tag, String value) {
        stringBuilder.append(tag).append("=").append(value).append("\u0001");
    }

    @Contract(pure = true)
    private int calculateChecksum(String message) {
        byte[] bytes = message.getBytes(StandardCharsets.US_ASCII);
        int sum = 0;
        for (byte b : bytes) {
            sum += b;
        }
        return sum % 256;
    }
}