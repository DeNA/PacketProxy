package packetproxy.util;

import java.util.function.Predicate;

@FunctionalInterface
public interface ThrowingPredicate<T> extends Predicate<T> {

	@Override
	default boolean test(final T e) {
		try {

			return test0(e);
		} catch (Throwable ex) {

			Throwing.sneakyThrow(ex);
		}
		return true;
	}

	boolean test0(T e) throws Throwable;
}
