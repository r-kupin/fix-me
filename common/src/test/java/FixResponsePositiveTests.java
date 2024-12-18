import com.rokupin.model.fix.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FixResponsePositiveTests {
    @Test
    public void fixResponseNewArgumentsTest() throws FixMessageMisconfiguredException {

        String expected = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_BODY_LENGTH + "=" + "54" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "8" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "B00001" + "\u0001" +
                FixMessage.TAG_TARGET_SUB_ID + "=" + 1 + "\u0001" +
                FixMessage.TAG_SYMBOL + "=" + "TEST1" + "\u0001" +
                FixMessage.TAG_SIDE + "=" + FixRequest.SIDE_SELL + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixResponse.TAG_ORD_STATUS + "=" + FixResponse.MSG_ORD_FILLED + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "029" + "\u0001";

        FixResponse response = new FixResponse(
                "E00000",
                "B00001",
                "1",
                "TEST1",
                FixRequest.SIDE_SELL,
                1,
                FixResponse.MSG_ORD_FILLED,
                0
        );

        Assertions.assertEquals(response.asFix(), expected);
    }

    @Test
    public void fixResponseFromFixTest() throws FixMessageMisconfiguredException {

        String expected = FixMessage.TAG_BEGIN_STRING + "=" + "FIX.5.0" + "\u0001" +
                FixMessage.TAG_BODY_LENGTH + "=" + "54" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=" + "8" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=" + "E00000" + "\u0001" +
                FixMessage.TAG_TARGET_COMP_ID + "=" + "B00001" + "\u0001" +
                FixMessage.TAG_TARGET_SUB_ID + "=" + 1 + "\u0001" +
                FixMessage.TAG_SYMBOL + "=" + "TEST1" + "\u0001" +
                FixMessage.TAG_SIDE + "=" + FixRequest.SIDE_SELL + "\u0001" +
                FixMessage.TAG_ORDER_QTY + "=" + 1 + "\u0001" +
                FixResponse.TAG_ORD_STATUS + "=" + FixResponse.MSG_ORD_FILLED + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=" + "029" + "\u0001";

        FixResponse response = FixMessage.fromFix(expected, new FixResponse());

        Assertions.assertEquals(response.asFix(), expected);
    }

    @Test
    public void fixClientResponseFromFixTest() throws FixMessageMisconfiguredException {

        FixResponse fixResponse = new FixResponse(
                "E00000",
                "B00001",
                "1",
                "TEST1",
                FixRequest.SIDE_SELL,
                1,
                FixResponse.MSG_ORD_REJECTED,
                3
        );

        ClientTradingResponse clientTradingResponse = new ClientTradingResponse(fixResponse);

        Assertions.assertEquals(clientTradingResponse.getAction(), "sell");
        Assertions.assertEquals(clientTradingResponse.getOrdStatus(), "rejected");
        Assertions.assertEquals(clientTradingResponse.getRejectionReason(),
                "Target exchange doesn't operate with requested instrument");
    }
}
