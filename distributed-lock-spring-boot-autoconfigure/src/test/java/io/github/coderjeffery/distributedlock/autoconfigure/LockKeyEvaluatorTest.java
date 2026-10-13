package io.github.coderjeffery.distributedlock.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.coderjeffery.distributedlock.LockException;
import io.github.coderjeffery.distributedlock.annotation.DistributedLocked;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class LockKeyEvaluatorTest {

    private final LockKeyEvaluator evaluator = new LockKeyEvaluator();

    @Test
    void evaluatesSpelFromParameterName() throws Exception {
        Method method = Sample.class.getMethod("pay", String.class);
        String key = evaluator.resolve(
                method.getAnnotation(DistributedLocked.class), method, new Object[] {"9"}, new Sample());
        assertEquals("order:9", key);
    }

    @Test
    void usesValueAlias() throws Exception {
        Method method = Sample.class.getMethod("byValue", String.class);
        String key = evaluator.resolve(
                method.getAnnotation(DistributedLocked.class), method, new Object[] {"u1"}, new Sample());
        assertEquals("user:u1", key);
    }

    @Test
    void blankExpressionFallsBackToClassAndMethod() throws Exception {
        Method method = Sample.class.getMethod("noKey");
        String key = evaluator.resolve(
                method.getAnnotation(DistributedLocked.class), method, new Object[0], new Sample());
        assertEquals(Sample.class.getName() + ".noKey", key);
    }

    @Test
    void blankEvaluationThrows() throws Exception {
        Method method = Sample.class.getMethod("blank", String.class);
        assertThrows(LockException.class, () -> evaluator.resolve(
                method.getAnnotation(DistributedLocked.class), method, new Object[] {""}, new Sample()));
    }

    static class Sample {
        @DistributedLocked(key = "'order:' + #id")
        public void pay(String id) {
        }

        @DistributedLocked(value = "'user:' + #userId")
        public void byValue(String userId) {
        }

        @DistributedLocked
        public void noKey() {
        }

        @DistributedLocked(key = "#id")
        public void blank(String id) {
        }
    }
}
