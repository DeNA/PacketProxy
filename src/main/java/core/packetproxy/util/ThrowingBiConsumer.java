package packetproxy.util;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface ThrowingBiConsumer<T,R> extends BiConsumer<T,R> {

    @Override
    default void accept(final T t, final R r) {
        try {
            accept0(t, r);
        } catch (Throwable ex) {
            Throwing.sneakyThrow(ex);
        }
    }

    void accept0(T t, R r) throws Throwable;
}