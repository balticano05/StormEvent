package com.workspace.storm.event.dto.offer;

import java.math.BigDecimal;
import java.util.Currency;

public class Price {

    private BigDecimal amount;
    private Currency currency;
    private BigDecimal amountByn;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }
    public BigDecimal getAmountByn() { return amountByn; }
    public void setAmountByn(BigDecimal amountByn) { this.amountByn = amountByn; }

    public static Price ofByn(BigDecimal amount) {
        Price price = new Price();
        price.setAmount(amount);
        price.setCurrency(Currency.getInstance("BYN"));
        price.setAmountByn(amount);
        return price;
    }

    public static Price of(BigDecimal amount, Currency currency, BigDecimal amountByn) {
        Price price = new Price();
        price.setAmount(amount);
        price.setCurrency(currency);
        price.setAmountByn(amountByn);
        return price;
    }
}