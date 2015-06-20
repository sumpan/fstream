package io.fstream.simulate.model;

import io.fstream.simulate.model.Order.OrderSide;
import lombok.Data;
import lombok.EqualsAndHashCode;

import org.joda.time.DateTime;

@Data
@EqualsAndHashCode
public class Trade {

  public enum TradeSide {
    ACTIVE, PASSIVE
  };

  private String buyuser;
  private String selluser;
  private float price;
  private boolean activebuy;
  private String symbol;
  private DateTime time;
  private DateTime ordertime;
  private int amount;

  // TODO ordertime needs to be initialized
  public Trade(DateTime tradetime, Order active, Order passive, int executedsize) {
    this.setPrice(passive.getPrice());
    this.setSymbol(active.getSymbol());
    this.setTime(tradetime);

    // use active orders timestamp as tradetime. Simplifying assumption
    this.setAmount(executedsize);
    if (active.getSide() == OrderSide.ASK) {
      // active seller
      this.selluser = active.getUserId();
      this.activebuy = false;
      this.buyuser = passive.getUserId();
    } else {
      // active buy
      this.selluser = passive.getUserId();
      this.activebuy = true;
      this.buyuser = active.getUserId();
    }
  }

}