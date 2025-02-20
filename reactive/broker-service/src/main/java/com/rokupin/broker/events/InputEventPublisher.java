package com.rokupin.broker.events;

import org.springframework.context.ApplicationListener;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * 1. Consumes Events
 * 2. Puts them in a BlockingQueue
 * 3. Let WebSocketMessage publisher drain it gradually
 */
@SuppressWarnings("rawtypes")
public class InputEventPublisher<E extends BrokerEvent> implements
        ApplicationListener<E>, Consumer<FluxSink<E>> {

    private final Executor executor;

    /**
     * If a consumer tries to drain an item from the queue, but the queue is
     * empty, the queue will block until such time as a new item has been
     * offered to the queue. This means we can simply loop forever, waiting
     * for the next item to be added to the queue, and when it’s available
     * our code will return, and we can publish the event on the
     * FluxSink<StockUpdateReceivedEvent> sink pointer we’ve been given when the
     * Flux is first created.
     */
    private final BlockingQueue<E> queue = new LinkedBlockingQueue<>();

    public InputEventPublisher(Executor executor) {
        this.executor = executor;
    }

    // will be called when any new events published when a new Profile is created
    @Override
    public void onApplicationEvent(E event) {
        this.queue.offer(event);
    }

    /**
     * Method is only called once when the application starts up, and we try
     * to create the Flux for the first time. In that callback we begin the
     * while loop that will constantly try to drain the BlockingQueue<T>.
     * This infinite and un-ending while-loop blocks! Naturally. That’s the
     * whole point. So, we manage that ourselves using the previously
     * configured java.util.concurrent.Executor instance.
     */
    @Override
    public void accept(FluxSink<E> sink) {
        this.executor.execute(() -> {
            while (true)
                try {
                    E event = queue.take();
                    sink.next(event);
                } catch (InterruptedException e) {
                    ReflectionUtils.rethrowRuntimeException(e);
                }
        });
    }
}