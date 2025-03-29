package org.hotswap.agent.plugin.deltaspike_jakarta;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

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

