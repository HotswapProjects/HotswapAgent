package org.hotswap.agent.plugin.proxy.hscglib;

/**
 * @author Erki Ehtla
 * 
 */
public class GeneratorParams {
	public GeneratorParams(Object generator, Object params) {
		super();
		this.generator = generator;
		this.param = params;
	}
	
	private Object generator;
	private Object param;
	
	public Object getGenerator() {
		return generator;
	}
	
	public void setGenerator(Object generator) {
		this.generator = generator;
	}
	
	public Object getParam() {
		return param;
	}
	
	public void setParam(Object params) {
		this.param = params;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((generator == null) ? 0 : generator.hashCode());
		result = prime * result + ((param == null) ? 0 : param.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GeneratorParams other = (GeneratorParams) obj;
		if (generator == null) {
			if (other.generator != null)
				return false;
		} else if (!generator.equals(other.generator))
			return false;
		if (param == null) {
			if (other.param != null)
				return false;
		} else if (!param.equals(other.param))
			return false;
		return true;
	}
}