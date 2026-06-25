package io.quarkus.transaction.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a transactional method or class as read-only.
 * <p>
 * When applied to a method annotated with {@code @Transactional}, the transaction will be configured
 * for read-only access. This has the following effects:
 * <ul>
 * <li>The JDBC connection is set to read-only mode ({@code Connection.setReadOnly(true)}),
 * enabling potential optimizations in the JDBC driver (e.g., routing to read replicas).</li>
 * <li>The Hibernate ORM session is configured with {@code defaultReadOnly = true}
 * and {@code FlushMode.MANUAL}, disabling dirty checking and automatic flushing.</li>
 * </ul>
 * <p>
 * This annotation must be placed on the entry method of the transaction, not on a method that
 * joins an existing transaction. When defined on a class, it is equivalent to defining it on all
 * the methods of the class marked with {@code @Transactional}.
 * The configuration defined on a method takes precedence over the configuration defined on a class.
 *
 * <p>
 * This annotation is expected to be replaced by {@code @Transactional(readOnly = true)} once
 * Jakarta Transactions adds the {@code readOnly} attribute to {@code @Transactional}
 * and Narayana implements it. See <a href="https://github.com/jakartaee/transactions/pull/222">Jakarta Transactions PR
 * #222</a>.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface ReadOnly {
}
