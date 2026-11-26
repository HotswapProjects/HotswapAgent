package org.hotswap.agent.plugin.jackson3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.Collections;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.jackson3.model.Jackson3Record;
import org.hotswap.agent.plugin.jackson3.model.Jackson3RecordWithProbe;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 keeps the serializer and deserializer of a reloaded class in its caches (issue #750).
 */
public class Jackson3PluginTest {

    @Test
    public void recordComponentAddedOnReload() throws Exception {
        // as Spring Boot creates it
        final JsonMapper mapper = JsonMapper.builder().build();
        // a writer keeps the caches of its mapper, not the mapper
        JsonMapper collectedMapper = JsonMapper.builder().build();
        WeakReference<JsonMapper> collected = new WeakReference<>(collectedMapper);
        final ObjectWriter writer = collectedMapper.writer();
        collectedMapper = null;

        final Jackson3Record record = new Jackson3Record("user", Collections.singletonList("admin"));
        String json = mapper.writeValueAsString(record);
        assertTrue(json, json.contains("\"login\":\"user\""));
        assertFalse(json, json.contains("probe"));
        assertFalse(writer.writeValueAsString(record).contains("probe"));
        // keeps its serialization context, with a read-only copy of the serializers
        StringWriter generatorOutput = new StringWriter();
        JsonGenerator generator = mapper.createGenerator(generatorOutput);
        generator.writePOJO(record);
        // cache the deserializer as well
        mapper.readValue("{\"login\":\"user\",\"roles\":[]}", Jackson3Record.class);
        garbageCollect(collected);

        Jackson3Plugin.reloadFlag = true;
        HotSwapper.swapClasses(Jackson3Record.class, Jackson3RecordWithProbe.class.getName());
        // the caches are cleared one by one, a (de)serializer used meanwhile may be the old one
        assertTrue("caches cleared", WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() {
                return !Jackson3Plugin.reloadFlag;
            }
        }, 5000));

        json = mapper.writeValueAsString(record);
        assertTrue(json, json.contains("\"probe\":null"));
        json = writer.writeValueAsString(record);
        assertTrue(json, json.contains("\"probe\":null"));
        generator.writePOJO(record);
        generator.close();
        assertTrue(generatorOutput.toString(), generatorOutput.toString().contains("\"probe\":null"));
        Jackson3Record read = mapper.readValue("{\"login\":\"user\",\"roles\":[],\"probe\":\"new\"}",
                Jackson3Record.class);
        assertEquals("new", Jackson3Record.class.getMethod("probe").invoke(read));
    }

    // only a hint, the writer is then checked with its mapper alive
    private static void garbageCollect(WeakReference<?> reference) throws InterruptedException {
        for (int i = 0; i < 50 && reference.get() != null; i++) {
            System.gc();
            Thread.sleep(10);
        }
    }
}
