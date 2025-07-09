package packetproxy.util;

import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<T, R> extends Function<T, R> {

	@Override
	default R apply(T t) {
		try {
			return apply0(t);
		} catch (Throwable ex) {
			Throwing.sneakyThrow(ex);
		}
		return null;
	}

	R apply0(T t) throws Throwable;
}
