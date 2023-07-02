package auto;

import java.lang.annotation.*;

/**
 *
 * Mark an interface that you want automatically transformed into the appropriate client
 * with this interface. Absent this annotation, it is assumed that the user will want to
 * factory their own instance of this client interface.
 *
 * @author Josh Long
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AutoClient {

}
