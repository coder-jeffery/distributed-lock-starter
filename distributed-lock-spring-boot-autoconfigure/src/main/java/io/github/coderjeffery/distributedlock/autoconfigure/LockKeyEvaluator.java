package io.github.coderjeffery.distributedlock.autoconfigure;

import io.github.coderjeffery.distributedlock.LockException;
import io.github.coderjeffery.distributedlock.annotation.DistributedLocked;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

final class LockKeyEvaluator {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    String resolve(DistributedLocked annotation, Method method, Object[] arguments, Object target) {
        String expression = firstNonBlank(annotation.key(), annotation.value());
        if (expression == null) {
            return method.getDeclaringClass().getName() + "." + method.getName();
        }
        try {
            MethodBasedEvaluationContext context =
                    new MethodBasedEvaluationContext(target, method, arguments, names);
            Object value = cache.computeIfAbsent(expression, parser::parseExpression).getValue(context);
            if (value == null || value.toString().isBlank()) {
                throw new LockException("SpEL lock key evaluated to blank: " + expression);
            }
            return value.toString();
        } catch (LockException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new LockException("Failed to evaluate lock key [" + expression + "]", ex);
        }
    }

    private static String firstNonBlank(String key, String value) {
        if (key != null && !key.isBlank()) {
            return key;
        }
        if (value != null && !value.isBlank()) {
            return value;
        }
        return null;
    }
}
