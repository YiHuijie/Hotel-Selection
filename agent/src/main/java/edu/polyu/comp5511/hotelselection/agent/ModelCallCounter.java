package edu.polyu.comp5511.hotelselection.agent;

import java.util.concurrent.atomic.AtomicInteger;

public final class ModelCallCounter {
    private final AtomicInteger calls = new AtomicInteger();

    void increment() {
        calls.incrementAndGet();
    }

    public int count() {
        return calls.get();
    }
}
