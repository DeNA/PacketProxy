package packetproxy.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

public class WithCounter {

	private WithCounter() {
	}

	@Nonnull
	public static <T> Consumer<T> withCounter(@Nonnull final ThrowingBiConsumer<Integer, T> consumer) {
		AtomicInteger counter = new AtomicInteger(0);
		return (T item) -> {
			consumer.accept(counter.getAndIncrement(), item);
		};
	}
}
