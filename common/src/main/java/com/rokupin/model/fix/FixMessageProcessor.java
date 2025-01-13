package com.rokupin.model.fix;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public class FixMessageProcessor {
    private StringBuilder buffer;
    private final Sinks.Many<String> sink;

    public FixMessageProcessor() {
        this.buffer = new StringBuilder();
        this.sink = Sinks.many().unicast().onBackpressureBuffer();
    }

    public void processInput(String input) {
        buffer.append(input);

        StringBuilder currentMessage = new StringBuilder();
        String[] parts = buffer.toString().split("\u0001");

        for (int i = 0; i < parts.length; i++) {
            currentMessage.append(parts[i]);
            if (parts[i].startsWith("10=")) { // last part of msg
                if (parts[i].length() == 6) { // message ends
                    currentMessage.append("\u0001");
                    sink.tryEmitNext(currentMessage.toString());
                    currentMessage.setLength(0);
                    if (i == parts.length - 1)
                        buffer.setLength(0);
                } else {
                    buffer = currentMessage;
                }
            } else if (i < parts.length - 1) { // not the last part of string
                currentMessage.append("\u0001");
            } else { // last part of the string, but not last part of the message
                if (input.endsWith("\u0001"))
                    currentMessage.append("\u0001");
                buffer = currentMessage;
            }
        }
    }

    public Flux<String> getFlux() {
        return sink.asFlux();
    }

    public void complete() {
        sink.tryEmitComplete();
    }
}
