import com.rokupin.model.fix.FixMessage;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixRequest;
import com.rokupin.model.fix.FixResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FixResponseNegativeTests {
    @Test
    public void fixResponseMissingRequiredFieldsTest() {
        String incompleteMessage = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_BODY_LENGTH + "=" + "48" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "8" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "B00001" + "\u0001" +
                FixMessage.TAG_TARGET_SUB_ID + "=" + 1 + "\u0001" +
//                FixMessage.TAG_SYMBOL + "=" + "TEST1" + "\u0001" +
                FixMessage.TAG_SIDE + "=" + FixRequest.SIDE_SELL + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixResponse.TAG_ORD_STATUS + "=" + FixResponse.MSG_ORD_FILLED + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "059" + "\u0001";

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> FixMessage.fromFix(incompleteMessage, new FixResponse())
        );

        Assertions.assertEquals("Missing required tag: 55", exception.getMessage()); // Missing Symbol (55)
    }

    @Test
    public void fixResponseInvalidChecksumTest() {
        String invalidChecksumMessage = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_BODY_LENGTH + "=" + "54" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "8" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "B00001" + "\u0001" +
                FixMessage.TAG_TARGET_SUB_ID + "=" + 1 + "\u0001" +
                FixMessage.TAG_SYMBOL + "=" + "TEST1" + "\u0001" +
                FixMessage.TAG_SIDE + "=" + FixRequest.SIDE_SELL + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixResponse.TAG_ORD_STATUS + "=" + FixResponse.MSG_ORD_FILLED + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "012" + "\u0001";

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> FixMessage.fromFix(invalidChecksumMessage, new FixResponse())
        );

        Assertions.assertEquals("Checksum doesn't match", exception.getMessage());
    }

    @Test
    public void fixResponseInvalidMessageTypeTest() {
        String invalidMessageTypeMessage = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_BODY_LENGTH + "=" + "54" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "D" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "B00001" + "\u0001" +
                FixMessage.TAG_TARGET_SUB_ID + "=" + 1 + "\u0001" +
                FixMessage.TAG_SYMBOL + "=" + "TEST1" + "\u0001" +
                FixMessage.TAG_SIDE + "=" + FixRequest.SIDE_SELL + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixResponse.TAG_ORD_STATUS + "=" + FixResponse.MSG_ORD_FILLED + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "059" + "\u0001";

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> FixMessage.fromFix(invalidMessageTypeMessage, new FixResponse())
        );

        Assertions.assertTrue(exception.getMessage().contains("expected to be '8'"));
    }

    @Test
    public void fixResponseInvalidActionTest() {
        String invalidActionMessage = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_BODY_LENGTH + "=" + "48" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "8" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "B00001" + "\u0001" +
                FixMessage.TAG_TARGET_SUB_ID + "=" + 1 + "\u0001" +
                FixMessage.TAG_SYMBOL + "=" + "TEST1" + "\u0001" +
                FixMessage.TAG_SIDE + "=" + 0 + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixResponse.TAG_ORD_STATUS + "=" + FixResponse.MSG_ORD_FILLED + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "059" + "\u0001";

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> FixMessage.fromFix(invalidActionMessage, new FixResponse())
        );

        Assertions.assertTrue(exception.getMessage().contains("Side (54) should be 1 (Buy) or 2 (Sell)"));
    }
}
