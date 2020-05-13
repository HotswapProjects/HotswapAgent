package org.hotswap.agent.plugin.spring.mvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.hotswap.agent.plugin.spring.mvcHotswap.SampleRestController2;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@WebAppConfiguration
@ContextHierarchy({ @ContextConfiguration(classes = SpringMvcApplication.class) })
public class SpringMvcTest {
    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }

    @Test
    public void baseCase() throws Exception {
        mockMvc.perform(get("/hello")).andExpect(status().isOk()).andExpect(content().string("Hello World"));
    }

    @Test
    public void changeMappingAndMethodBody() throws Exception {
        swappingRule.swapClasses(SampleRestController.class, SampleRestController2.class);
        mockMvc.perform(get("/hello")).andExpect(status().isNotFound());
        mockMvc.perform(get("/helloSwapped")).andExpect(status().isOk())
                .andExpect(content().string("Hello World Swapped"));
    }

}
