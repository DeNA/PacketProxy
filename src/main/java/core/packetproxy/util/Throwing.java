package packetproxy.util;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class Throwing {

    private Throwing() {}

    @Nonnull
    public static <T> Consumer<T> rethrow(@Nonnull final ThrowingConsumer<T> consumer) {
        return consumer;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    public static <E extends Throwable> void sneakyThrow(@Nonnull Throwable ex) throws E {
        throw (E) ex;
    }
}
