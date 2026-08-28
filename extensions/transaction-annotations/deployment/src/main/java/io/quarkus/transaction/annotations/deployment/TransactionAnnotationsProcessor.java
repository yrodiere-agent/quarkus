package io.quarkus.transaction.annotations.deployment;

import java.util.Collection;
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import io.quarkus.arc.deployment.InterceptorBindingRegistrarBuildItem;
import io.quarkus.arc.deployment.TransformedAnnotationsBuildItem;
import io.quarkus.arc.processor.InterceptorBindingRegistrar;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;

class TransactionAnnotationsProcessor {

    private static final DotName READ_ONLY = DotName.createSimple("io.quarkus.transaction.annotations.ReadOnly");
    private static final DotName TRANSACTIONAL = DotName.createSimple("jakarta.transaction.Transactional");

    @BuildStep
    InterceptorBindingRegistrarBuildItem registerReadOnlyAsInterceptorBinding() {
        return new InterceptorBindingRegistrarBuildItem(new InterceptorBindingRegistrar() {
            @Override
            public List<InterceptorBinding> getAdditionalBindings() {
                return List.of(InterceptorBinding.of(READ_ONLY));
            }
        });
    }

    @BuildStep
    @Produce(ServiceStartBuildItem.class)
    void validateReadOnlyAnnotation(CombinedIndexBuildItem index, TransformedAnnotationsBuildItem transformedAnnotations) {
        Collection<AnnotationInstance> readOnlyAnnotations = index.getIndex().getAnnotations(READ_ONLY);
        if (readOnlyAnnotations.isEmpty()) {
            return;
        }
        for (AnnotationInstance readOnly : readOnlyAnnotations) {
            AnnotationTarget target = readOnly.target();
            if (target.kind() == AnnotationTarget.Kind.METHOD) {
                MethodInfo method = target.asMethod();
                AnnotationInstance transactional = transformedAnnotations.getAnnotation(method, TRANSACTIONAL);
                if (transactional == null) {
                    transactional = transformedAnnotations.getAnnotation(method.declaringClass(), TRANSACTIONAL);
                }
                if (transactional == null) {
                    throw new IllegalStateException(
                            "@ReadOnly is only supported on methods or classes also annotated with @Transactional. "
                                    + "Offending method: " + method.declaringClass().name() + "#" + method.name());
                }
                rejectIncompatibleTxType(transactional,
                        method.declaringClass().name() + "#" + method.name());
            } else if (target.kind() == AnnotationTarget.Kind.CLASS) {
                ClassInfo clazz = target.asClass();
                AnnotationInstance transactional = transformedAnnotations.getAnnotation(clazz, TRANSACTIONAL);
                if (transactional != null) {
                    rejectIncompatibleTxType(transactional, clazz.name().toString());
                    for (MethodInfo method : clazz.methods()) {
                        AnnotationInstance methodTransactional = transformedAnnotations.getAnnotation(method,
                                TRANSACTIONAL);
                        if (methodTransactional != null) {
                            rejectIncompatibleTxType(methodTransactional,
                                    clazz.name() + "#" + method.name());
                        }
                    }
                } else {
                    boolean hasTransactionalMethod = false;
                    for (MethodInfo method : clazz.methods()) {
                        AnnotationInstance methodTransactional = transformedAnnotations.getAnnotation(method,
                                TRANSACTIONAL);
                        if (methodTransactional != null) {
                            hasTransactionalMethod = true;
                            rejectIncompatibleTxType(methodTransactional,
                                    clazz.name() + "#" + method.name());
                        }
                    }
                    if (!hasTransactionalMethod) {
                        throw new IllegalStateException(
                                "@ReadOnly is only supported on methods or classes also annotated with @Transactional. "
                                        + "Offending class: " + clazz.name());
                    }
                }
            }
        }
    }

    private static void rejectIncompatibleTxType(AnnotationInstance transactional, String location) {
        var txTypeValue = transactional.value();
        if (txTypeValue == null) {
            return;
        }
        String txType = txTypeValue.asEnum();
        if ("NEVER".equals(txType) || "NOT_SUPPORTED".equals(txType)) {
            throw new IllegalStateException(
                    "@ReadOnly cannot be combined with @Transactional(" + txType
                            + ") because this transaction type does not start a transaction. "
                            + "Offending element: " + location);
        }
    }
}
