package org.hotswap.agent.it.service;

import org.hotswap.agent.it.model.TestEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

/**
 * Experiment with this service in debug mode to check Spring reloading.
 *
 * @author Jiri Bubnik
 */
@Service
@Transactional
public class TestEntityService {

    @Autowired
    TestRepository testRepository2;

    @PersistenceContext
    EntityManager entityManager;


    public void addTestEntity(TestEntity entity) {
        entityManager.persist(entity);
    }

    @SuppressWarnings("unchecked")
    public List<TestEntity> loadTestEntities() {
        return entityManager.createQuery("select e from TestEntity e").getResultList();
    }

    // return text from repository and ExamplePlugin values.
    public String helloWorld() {
        String hello = testRepository2.helloWorld();

        if (examplePluginResourceText != null)
            hello += "\n" + examplePluginResourceText;

        if (loadedClasses > 0)
            hello += "\n xLoaded classes since plugin initialization: " + loadedClasses;

        if (reloadedClasses > 0)
            hello += "\n xReloaded classes since plugin initialization: " + reloadedClasses;

        return hello;
    }

    /**
     * See ExamplePlugin
     */
    String examplePluginResourceText = "";
    int loadedClasses = 0;
    int reloadedClasses = 0;

    public void setExamplePluginResourceText(String examplePluginResourceText) {
        this.examplePluginResourceText = examplePluginResourceText;
    }

    public void setLoadedClasses(Integer loadedClasses) {
        this.loadedClasses = loadedClasses;
    }

    public void addReloadedClass() {
        reloadedClasses++;
    }
}
