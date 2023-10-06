package org.hotswap.agent.plugin.spring.mvc;

import org.hotswap.agent.plugin.spring.BaseTestUtil;
import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.hotswap.agent.plugin.spring.mvcHotswap.SampleRestController2;
import org.hotswap.agent.plugin.spring.reload.BeanFactoryAssistant;
import org.hotswap.agent.plugin.spring.reload.SpringChangedAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({ @ContextConfiguration(classes = SpringMvcApplication.class) })
public class SpringMvcTest {
    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        BaseTestUtil.configMaxReloadTimes();
        mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
        swappingRule.setBeanFactory(beanFactory);
        BeanFactoryAssistant.getBeanFactoryAssistant(beanFactory).reset();
        System.out.println("SpringMvcTest.setup." + beanFactory);
        SpringChangedAgent.getInstance((DefaultListableBeanFactory) beanFactory);
    }

    @After
    public void after() {
        SpringChangedAgent.destroyBeanFactory((DefaultListableBeanFactory) beanFactory);
    }

    @Test
    public void baseCase() throws Exception {
        System.out.println("SpringMvcTest.baseCase");
        mockMvc.perform(get("/hello")).andExpect(status().isOk()).andExpect(content().string("Hello World"));
        mockMvc.perform(get("/helloRequestMapping")).andExpect(status().isOk())
                .andExpect(content().string("Hello World2"));
    }

    @Test
    public void changeGetMappingAndMethodBody() throws Exception {
        System.out.println("SpringMvcTest.changeGetMappingAndMethodBody");
        // warm up to fill all caches
        mockMvc.perform(get("/hello")).andExpect(status().isOk());

        int reloadTimes = 1;
        // swap classes
        swappingRule.swapClasses(SampleRestController.class, SampleRestController2.class, reloadTimes++);

        // make sure classes are swapped
        mockMvc.perform(get("/hello")).andExpect(status().isNotFound());
        mockMvc.perform(get("/helloSwapped")).andExpect(status().isOk())
                .andExpect(content().string("Hello World Swapped"));
        swappingRule.swapClasses(SampleRestController.class, SampleRestController.class, reloadTimes++);
    }

    @Test
    public void changeRequestMappingAndMethodBody() throws Exception {
        System.out.println("SpringMvcTest.changeRequestMappingAndMethodBody");
        // warm up to fill all caches
        mockMvc.perform(get("/helloRequestMapping")).andExpect(status().isOk());

        // swap classes
        int reloadTimes = 1;
        swappingRule.swapClasses(SampleRestController.class, SampleRestController2.class, reloadTimes);

        // make sure classes are swapped
        mockMvc.perform(get("/helloRequestMapping")).andExpect(status().isNotFound());
        mockMvc.perform(get("/helloRequestMappingSwapped")).andExpect(status().isOk())
                .andExpect(content().string("Hello World2 Swapped"));
    }

}
