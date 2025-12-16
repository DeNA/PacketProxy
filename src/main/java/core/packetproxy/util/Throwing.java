package packetproxy.util;

import java.io.UncheckedIOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class Throwing {

	private Throwing() {
	}

	public static <T> Consumer<T> rethrow(final ThrowingConsumer<T> consumer) {
		return consumer;
	}

	public static <T> Predicate<T> rethrowP(final ThrowingPredicate<T> predicate) {
		return predicate;
	}

	public static <T, R> Function<T, R> rethrowF(final ThrowingFunction<T, R> function) {
		return function;
	}

	public static void sneakyThrow(Throwable ex) {
		if (ex instanceof RuntimeException runtimeException) {
			throw runtimeException;
		}
		if (ex instanceof Error error) {
			throw error;
		}
		if (ex instanceof java.io.IOException ioException) {
			throw new UncheckedIOException(ioException);
		}
		throw new RuntimeException(ex);
	}
}
