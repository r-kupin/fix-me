import com.rokupin.model.fix.ClientTradingRequest;
import com.rokupin.model.fix.FixMessage;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FixRequestPositiveTests {
    @Test
    public void fixRequestNewArgumentsTest() throws FixMessageMisconfiguredException {

        String expected = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "D" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "B00000" + "\u0001" +
                FixMessage.TAG_SOURCE_SUB_ID + "=" + 0 + "\u0001" +
                FixMessage.TAG_SYMBOL + "=" + "TEST" + "\u0001" +
                FixMessage.TAG_SIDE + "=" + FixRequest.SIDE_BUY + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "048" + "\u0001";

        FixRequest request = new FixRequest(
                "B00000",
                "0",
                "E00000",
                "TEST",
                FixRequest.SIDE_BUY,
                1
        );

        Assertions.assertEquals("B00000", request.getSender());
        Assertions.assertEquals("0", request.getSenderSubId());
        Assertions.assertEquals("E00000", request.getTarget());
        Assertions.assertEquals("TEST", request.getInstrument());
        Assertions.assertEquals(FixRequest.SIDE_BUY, request.getAction());
        Assertions.assertEquals(1, request.getAmount());

        Assertions.assertEquals(request.asFix(), expected);
    }

    @Test
    public void fixRequestFromClientTradingRequestTest() throws FixMessageMisconfiguredException {

        String expected = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "D" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "B00000" + "\u0001" +
                FixMessage.TAG_SOURCE_SUB_ID + "=" + 0 + "\u0001" +
                FixMessage.TAG_SYMBOL + "=" + "TEST" + "\u0001" +
                FixMessage.TAG_SIDE + "=" + FixRequest.SIDE_BUY + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "048" + "\u0001";

        FixRequest request = new FixRequest(new ClientTradingRequest(
                "E00000",
                "TEST",
                "buy",
                1
        ));
        // Set in broker service: client doesn't need to know how it's designated
        request.setSender("B00000");
        request.setSenderSubId("0");
        Assertions.assertEquals(request.asFix(), expected);
    }

    @Test
    public void fixRequestFromMessageStringTest() throws FixMessageMisconfiguredException {

        String expected = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "D" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "B00000" + "\u0001" +
                FixMessage.TAG_SOURCE_SUB_ID + "=" + 0 + "\u0001" +
                FixMessage.TAG_SYMBOL + "=" + "TEST" + "\u0001" +
                FixMessage.TAG_SIDE + "=" + FixRequest.SIDE_BUY + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "048" + "\u0001";

        FixRequest request = FixMessage.fromFix(expected, new FixRequest());
        Assertions.assertEquals(request.asFix(), expected);
    }
}
