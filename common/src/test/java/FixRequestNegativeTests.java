import com.rokupin.model.fix.ClientTradingRequest;
import com.rokupin.model.fix.FixMessage;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FixRequestNegativeTests {
    @Test
    public void fixRequestInvalidActionTest() {
        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> new FixRequest(
                        "B00000",
                        "0",
                        "E00000",
                        "TEST",
                        3,
                        1
                )
        );
        Assertions.assertEquals("'action' [Side (54)] should be either 'buy' or 'sell'. Provided: '3'", exception.getMessage());
    }

    @Test
    public void fixRequestNegativeAmountTest() {
        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> new FixRequest(
                        "B00000",
                        "0",
                        "E00000",
                        "TEST",
                        FixRequest.SIDE_BUY,
                        -1
                )
        );
        Assertions.assertEquals("'amount' [OrderQty (38)] should be a positive integer. Provided: '-1'", exception.getMessage());
    }

    @Test
    public void fixRequestMissingRequiredTagTest() {
        String invalidMessage = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_BODY_LENGTH + "=" + "48" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "D" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "B00000" + "\u0001" +
                FixMessage.TAG_SOURCE_SUB_ID + "=" + 0 + "\u0001" +
                FixMessage.TAG_SYMBOL + "=" + "TEST" + "\u0001" +
//                FixMessage.TAG_SIDE + "=" + FixRequest.SIDE_BUY + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "048" + "\u0001";

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> FixMessage.fromFix(invalidMessage, new FixRequest())
        );

        Assertions.assertEquals("Missing required tag: 54", exception.getMessage());
    }

    @Test
    public void fixRequestInvalidChecksumTest() {
        String invalidChecksumMessage = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_BODY_LENGTH + "=" + "48" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "D" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "B00000" + "\u0001" +
                FixMessage.TAG_SOURCE_SUB_ID + "=" + 0 + "\u0001" +
                FixMessage.TAG_SYMBOL + "=" + "TEST" + "\u0001" +
                FixMessage.TAG_SIDE + "=" + FixRequest.SIDE_BUY + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "028" + "\u0001";

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> FixMessage.fromFix(invalidChecksumMessage, new FixRequest())
        );

        Assertions.assertEquals("Checksum doesn't match", exception.getMessage());
    }

    @Test
    public void fixRequestEmptyClientTradingRequestFieldsTest() {
        ClientTradingRequest clientTradingRequest = new ClientTradingRequest(null, "TEST", "buy", 1);

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> new FixRequest(clientTradingRequest)
        );

        Assertions.assertEquals("No fields of JSON clientMsg can be null.", exception.getMessage());
    }




}
