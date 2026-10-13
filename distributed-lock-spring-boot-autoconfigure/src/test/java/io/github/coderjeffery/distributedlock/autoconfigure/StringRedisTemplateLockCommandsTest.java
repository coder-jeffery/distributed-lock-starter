package io.github.coderjeffery.distributedlock.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class StringRedisTemplateLockCommandsTest {

    @Mock
    private StringRedisTemplate template;

    @Test
    void evalDelegatesToTemplate() {
        when(template.execute(any(RedisScript.class), eq(List.of("k")), eq("a"))).thenReturn(1L);

        StringRedisTemplateLockCommands commands = new StringRedisTemplateLockCommands(template);

        assertEquals(1L, commands.eval("return 1", List.of("k"), "a"));
        verify(template).execute(any(RedisScript.class), eq(List.of("k")), eq("a"));
    }

    @Test
    void nullRedisResultBecomesZero() {
        when(template.execute(any(RedisScript.class), eq(List.of("k")))).thenReturn(null);

        StringRedisTemplateLockCommands commands = new StringRedisTemplateLockCommands(template);

        assertEquals(0L, commands.eval("return nil", List.of("k")));
    }

    @Test
    void sameScriptIsCompiledOnce() {
        when(template.execute(any(RedisScript.class), eq(List.of("k")), eq("1"))).thenReturn(1L);

        StringRedisTemplateLockCommands commands = new StringRedisTemplateLockCommands(template);
        commands.eval("return tonumber(ARGV[1])", List.of("k"), "1");
        commands.eval("return tonumber(ARGV[1])", List.of("k"), "1");

        verify(template, times(2)).execute(any(RedisScript.class), eq(List.of("k")), eq("1"));
    }
}
