package io.quarkus.hibernate.reactive.transactions.deployment;

import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.InterceptorBindingRegistrarBuildItem;
import io.quarkus.arc.processor.InterceptorBindingRegistrar;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.reactive.transaction.runtime.TransactionalInterceptorMandatory;
import io.quarkus.reactive.transaction.runtime.TransactionalInterceptorNever;
import io.quarkus.reactive.transaction.runtime.TransactionalInterceptorNotSupported;
import io.quarkus.reactive.transaction.runtime.TransactionalInterceptorRequired;
import io.quarkus.reactive.transaction.runtime.TransactionalInterceptorRequiresNew;
import io.quarkus.reactive.transaction.runtime.TransactionalInterceptorSupports;

public class QuarkusReactiveTransactionsProcessor {

    private static final DotName READ_ONLY = DotName.createSimple("io.quarkus.transaction.annotations.ReadOnly");

    @BuildStep
    AdditionalBeanBuildItem produceItems() {
        return new AdditionalBeanBuildItem(
                TransactionalInterceptorMandatory.class,
                TransactionalInterceptorNever.class,
                TransactionalInterceptorNotSupported.class,
                TransactionalInterceptorRequired.class,
                TransactionalInterceptorRequiresNew.class,
                TransactionalInterceptorSupports.class);

    }

    @BuildStep
    InterceptorBindingRegistrarBuildItem registerReadOnlyAsInterceptorBinding() {
        return new InterceptorBindingRegistrarBuildItem(new InterceptorBindingRegistrar() {
            @Override
            public List<InterceptorBinding> getAdditionalBindings() {
                return List.of(InterceptorBinding.of(READ_ONLY));
            }
        });
    }
}
