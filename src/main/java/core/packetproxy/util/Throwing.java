package packetproxy.util;

import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

public class Throwing {

	private Throwing() {
	}

	@Nonnull
	public static <T> Consumer<T> rethrow(@Nonnull final ThrowingConsumer<T> consumer) {
		return consumer;
	}

	@Nonnull
	public static <T> Predicate<T> rethrowP(@Nonnull final ThrowingPredicate<T> predicate) {
		return predicate;
	}

	@SuppressWarnings("unchecked")
	@Nonnull
	public static <E extends Throwable> void sneakyThrow(@Nonnull Throwable ex) throws E {
		throw (E) ex;
	}
}
