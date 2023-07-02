package auto;

import java.util.function.Predicate;

/**
 * Strategy pattern to identify which kind of declarative clients we're dealing with:
 * HTTP, RSocket, etc.
 *
 * @author Josh Long
 */
interface ClientDetectionStrategy extends Predicate<Class<?>> {

}
