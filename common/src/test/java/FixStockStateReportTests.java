import com.rokupin.model.fix.FixMessage;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixStockStateReport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FixStockStateReportTests {
    @Test
    public void fixStockStateReportValidTest() throws FixMessageMisconfiguredException {
        String expected = FixMessage.TAG_BEGIN_STRING + "=FIX.5.0" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=U2" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=EXCHANGE1" + "\u0001" +
                FixMessage.TAG_STOCK_STATE_JSON + "={\"AAPL\":100,\"GOOG\":50}" + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=056" + "\u0001";

        FixStockStateReport report = new FixStockStateReport(
                "EXCHANGE1",
                "{\"AAPL\":100,\"GOOG\":50}"
        );

        Assertions.assertEquals(expected, report.asFix());
    }

    @Test
    public void fixStockStateReportFromFixTest() throws FixMessageMisconfiguredException {
        String validFixMessage = FixMessage.TAG_BEGIN_STRING + "=FIX.5.0" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=U2" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=EXCHANGE1" + "\u0001" +
                FixMessage.TAG_STOCK_STATE_JSON + "={\"AAPL\":100,\"GOOG\":50}" + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=056" + "\u0001";

        FixStockStateReport report = FixMessage.fromFix(validFixMessage, new FixStockStateReport());

        Assertions.assertEquals("U2", report.getMsgType());
        Assertions.assertEquals("EXCHANGE1", report.getSender());
        Assertions.assertEquals("{\"AAPL\":100,\"GOOG\":50}", report.getStockJson());
    }

    @Test
    public void fixStockStateReportMissingFieldsTest() {
        String incompleteMessage = FixMessage.TAG_BEGIN_STRING + "=FIX.5.0" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=U2" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=EXCHANGE1" + "\u0001" +
//                FixMessage.TAG_STOCK_STATE_JSON + "={\"AAPL\":100,\"GOOG\":50}" + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=056" + "\u0001";

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> FixMessage.fromFix(incompleteMessage, new FixStockStateReport())
        );

        Assertions.assertEquals("Missing required tag: 0", exception.getMessage()); // TAG_STOCK_STATE_JSON
    }

    @Test
    public void fixStockStateReportInvalidChecksumTest() {
        String invalidChecksumMessage = FixMessage.TAG_BEGIN_STRING + "=FIX.5.0" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=U2" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=EXCHANGE1" + "\u0001" +
                FixMessage.TAG_STOCK_STATE_JSON + "={\"AAPL\":100,\"GOOG\":50}" + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=999" + "\u0001";

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> FixMessage.fromFix(invalidChecksumMessage, new FixStockStateReport())
        );

        Assertions.assertEquals("Checksum doesn't match", exception.getMessage());
    }

    @Test
    public void fixStockStateReportInvalidMessageTypeTest() {
        String invalidMessageTypeMessage = FixMessage.TAG_BEGIN_STRING + "=FIX.5.0" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=D" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=EXCHANGE1" + "\u0001" +
                FixMessage.TAG_STOCK_STATE_JSON + "={\"AAPL\":100,\"GOOG\":50}" + "\u0001" +
                FixMessage.TAG_CHECKSUM + "=199" + "\u0001";

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> FixMessage.fromFix(invalidMessageTypeMessage, new FixStockStateReport())
        );

        Assertions.assertTrue(exception.getMessage().contains("expected to be 'U2'"));
    }

    @Test
    public void fixStockStateReportMissingChecksumTest() {
        String messageWithoutChecksum = FixMessage.TAG_BEGIN_STRING + "=FIX.5.0" + "\u0001" +
                FixMessage.TAG_MSG_TYPE + "=U2" + "\u0001" +
                FixMessage.TAG_SOURCE_COMP_ID + "=EXCHANGE1" + "\u0001" +
                FixMessage.TAG_STOCK_STATE_JSON + "={\"AAPL\":100,\"GOOG\":50}" + "\u0001"; // Missing checksum

        FixMessageMisconfiguredException exception = Assertions.assertThrows(
                FixMessageMisconfiguredException.class,
                () -> FixMessage.fromFix(messageWithoutChecksum, new FixStockStateReport())
        );

        Assertions.assertTrue(exception.getMessage().contains("Missing required tag: 10")); // TAG_CHECKSUM
    }

}
