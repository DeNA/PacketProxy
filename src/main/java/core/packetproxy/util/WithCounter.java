package packetproxy.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class WithCounter {

	private WithCounter() {
	}

	public static <T> Consumer<T> withCounter(final ThrowingBiConsumer<Integer, T> consumer) {
		AtomicInteger counter = new AtomicInteger(0);
		return (T item) -> {
			consumer.accept(counter.getAndIncrement(), item);
		};
	}
}
