package org.hotswap.agent.plugin.deltaspike;

import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

public class EntityManagerProducer {

    @Inject
    private EntityManagerFactory emf;

    @Produces
    public EntityManager create() {
        return emf.createEntityManager();
    }

    public void close(@Disposes EntityManager em) {
        if (em.isOpen()) {
            em.close();
        }
    }

}

