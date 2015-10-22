/*                                      
 * Copyright (c) 1999-2015 Touch Tecnologia e Informatica Ltda.
 * Gomes de Carvalho, 1666, 3o. Andar, Vila Olimpia, Sao Paulo, SP, Brasil.
 * Todos os direitos reservados.
 *                              
 * Este software e confidencial e de propriedade da Touch Tecnologia e 
 * Informatica Ltda. (Informacao Confidencial). As informacoes contidas neste
 * arquivo nao podem ser publicadas, e seu uso esta limitado de acordo com os 
 * termos do contrato de licenca.
 */


package org.hotswap.agent.plugin.tomcat;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

import sun.misc.Resource;
import sun.misc.URLClassPath;


/**
 * Quando alguém tenta carregar todos os resources do classpath com um determinado nome, usa uma blacklist para excluir
 * determinados recursos do extraClasspath.
 * <p>
 * Isto é feito porque, quando a aplicação sobe por fora do IDE, haverá no classpath um JAR empacotado com os mesmos
 * resources do extraClasspath e esta duplicação causa problemas em alguns casos (Spring pode duplicar beans, por
 * exemplo).
 * 
 * @author eduardomarubayashi
 */
@SuppressWarnings("restriction")
class BlacklistClassPath extends URLClassPath {

    private static final List<Pattern> BLACKLIST;

    static {
	List<Pattern> b = new ArrayList<Pattern>();
	b.add(Pattern.compile("applicationContext.xml"));
	b.add(Pattern.compile("META-INF/persistence.xml"));
	b.add(Pattern.compile("META-INF/spring.*xml"));
	b.add(Pattern.compile("spring-config.xml"));
	b.add(Pattern.compile("struts.*xml"));
	BLACKLIST = b;
    }

    private final URLClassPath delegate;

    @SuppressWarnings("javadoc")
    public BlacklistClassPath(URLClassPath delegate) {
	super(new URL[0]);
	this.delegate = delegate;
    }

    @Override
    public Enumeration<URL> findResources(String name, boolean check) {
	for (Pattern p : BLACKLIST) {
	    if (p.matcher(name).matches()) {
		return Collections.emptyEnumeration();
	    }
	}
	return delegate.findResources(name, check);
    }


    @Override
    public synchronized void addURL(URL url) {
	delegate.addURL(url);
    }

    @Override
    public URL checkURL(URL url) {
	return delegate.checkURL(url);
    }

    @Override
    public synchronized List<IOException> closeLoaders() {
	return delegate.closeLoaders();
    }


    @Override
    public URL findResource(String name, boolean check) {
	return delegate.findResource(name, check);
    }

    @Override
    public Resource getResource(String name) {
	return delegate.getResource(name);
    }

    @Override
    public Resource getResource(String name, boolean check) {
	return delegate.getResource(name, check);
    }

    @Override
    public Enumeration<Resource> getResources(String name) {
	return delegate.getResources(name);
    }

    @Override
    public Enumeration<Resource> getResources(String name, boolean check) {
	return delegate.getResources(name, check);
    }

    @Override
    public URL[] getURLs() {
	return delegate.getURLs();
    }
}
