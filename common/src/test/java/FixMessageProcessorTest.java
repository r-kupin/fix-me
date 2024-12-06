import com.rokupin.model.fix.FixMessageProcessor;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

public class FixMessageProcessorTest {
    @Test
    void testSingleCompleteMessage() {
        FixMessageProcessor processor = new FixMessageProcessor();
        String input = "8=FIX.5.0\u000135=D\u000110=123\u0001";

        StepVerifier.create(processor.getFlux())
                .then(() -> processor.processInput(input))
                .expectNext(input)
                .then(processor::complete)
                .verifyComplete();
    }

    @Test
    void testSingleTwoMessages() {
        FixMessageProcessor processor = new FixMessageProcessor();
        String part1 = "8=FIX.5.0\u000135=D\u000110=123\u00018=FIX.5.0\u000135=A\u000110=123";
        String part2 = "\u0001";

        StepVerifier.create(processor.getFlux())
                .then(() -> processor.processInput(part1))
                .expectNextCount(0)
                .then(() -> processor.processInput(part2))
                .expectNext("8=FIX.5.0\u000135=D\u000110=123\u0001")
                .expectNext("8=FIX.5.0\u000135=A\u000110=123\u0001")
                .then(processor::complete)
                .verifyComplete();
    }

    @Test
    void testSplitMessages() {
        FixMessageProcessor processor = new FixMessageProcessor();

        String part1 = "8=FIX.5.0\u000135=D\u0001";
        String part2 = "10=123\u00018=FIX.5.0\u000135=F\u0001";
        String part3 = "10=456\u0001";

        StepVerifier.create(processor.getFlux())
                .then(() -> processor.processInput(part1))
                .expectNextCount(0)
                .then(() -> processor.processInput(part2))
                .expectNext(part1 + part2.substring(0, 7)) // First complete message
                .then(() -> processor.processInput(part3))
                .expectNext(part2.substring(7) + part3) // Second complete message
                .then(processor::complete) // Complete the processor
                .verifyComplete();
    }

    @Test
    void testIncompleteMessage() {
        FixMessageProcessor processor = new FixMessageProcessor();

        String input = "8=FIX.5.0\u000135=D\u0001"; // Incomplete message

        StepVerifier.create(processor.getFlux())
                .then(() -> processor.processInput(input))
                .expectNextCount(0)
                .then(processor::complete)
                .verifyComplete();
    }
}
