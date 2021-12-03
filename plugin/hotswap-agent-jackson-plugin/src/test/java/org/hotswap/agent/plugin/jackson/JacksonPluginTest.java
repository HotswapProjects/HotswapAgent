package org.hotswap.agent.plugin.jackson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.jackson.model.TestModel1;
import org.hotswap.agent.plugin.jackson.model.TestModel2;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author liuzhengyang
 * 2021/12/3
 */
public class JacksonPluginTest {
    // create object mapper, add field, check object mappers to json from json


    @Test
    public void testAddNewField() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TestModel1 testModel1 = new TestModel1();
        testModel1.setAge(1);
        String json = objectMapper.writeValueAsString(testModel1);
        assertTrue(json.contains("\"age\":1"));

        // save cache
        objectMapper.readValue(json, TestModel1.class);

        swapClasses();

        String jsonWithName = "{\"age\":1, \"name\": \"test\"}";
        TestModel1 readValue = objectMapper.readValue(jsonWithName, TestModel1.class);
        Field nameField = TestModel1.class.getDeclaredField("name");
        nameField.setAccessible(true);
        assertEquals("test", nameField.get(readValue));
        String value = objectMapper.writeValueAsString(readValue);
        assertTrue(value.contains("name"));
    }

    private void swapClasses() throws Exception {
        assertTrue(Arrays.stream(TestModel1.class.getDeclaredFields()).noneMatch(field -> "name".equals(field.getName())));

        JacksonPlugin.reloadFlag = true;
        HotSwapper.swapClasses(TestModel1.class, TestModel2.class.getName());
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !JacksonPlugin.reloadFlag;
            }
        }));
        assertTrue(Arrays.stream(TestModel1.class.getDeclaredFields()).anyMatch(field -> "name".equals(field.getName())));
    }
}
