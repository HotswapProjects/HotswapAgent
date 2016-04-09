package org.hotswap.agent.plugin.hibernate3.en;

/**
 * Model class for Stock
 */
public class Stock2 implements java.io.Serializable {

	private static final long serialVersionUID = 1L;

	private Integer stockId;
	private String stockCode;
	private String stockName;

	public Stock2() {
	}

	public Stock2(String stockCode, String stockName) {
		this.stockCode = stockCode;
		this.stockName = stockName;
	}

	public Integer getStockId() {
		return stockId;
	}

	public void setStockId(Integer stockId) {
		this.stockId = stockId;
	}

	public String getStockCode() {
		return stockCode;
	}

	public void setStockCode(String stockCode) {
		this.stockCode = stockCode;
	}

	public String getStockName() {
		return stockName;
	}

	public void setStockName(String stockName) {
		this.stockName = stockName;
	}


}