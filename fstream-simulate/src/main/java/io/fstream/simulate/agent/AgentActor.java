package io.fstream.simulate.agent;

import io.fstream.simulate.orders.Order.OrderSide;
import io.fstream.simulate.orders.Order.OrderType;

import java.util.Random;

import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.util.Timeout;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AgentActor extends UntypedActor implements Agent {

  Random random;
  String name;
  int sleep; // agent sleep time
  int max_trade_size = 100;
  Timeout bookquerytimeout;
  protected ActorRef exchange;

  public AgentActor(String name, ActorRef exchange) {
    this.name = name;
    this.exchange = exchange;
    init();
  }

  protected void init() {
    random = new Random();
    this.sleep = random.nextInt(5) + 1;
    bookquerytimeout = new Timeout(Duration.create(5, "seconds"));
  }

  abstract public void executeAction();

  /**
   * Return orderside preferred with the given probability. E.g. prob=0.7, side=BUY returns BUY 70% of the time
   * 
   * @param prob
   * @param side
   * @return
   */
  protected OrderSide decideSide(float prob, OrderSide side) {
    if (random.nextFloat() <= prob) {
      return side;
    } else {
      if (side == OrderSide.BID) {
        return OrderSide.ASK;
      } else {
        return OrderSide.BID;
      }
    }
  }

  /**
   * With a given probbest will simply return the best price. Otherwise will return a random price within the min/max
   * bounds
   * 
   * @param min
   * @param max
   * @param best
   * @param probbest
   * @return
   */
  protected float decidePrice(float min, float max, float best, float probbest) {
    if (random.nextFloat() <= probbest) {
      return best;
    } else {
      return min + (random.nextFloat() * (max - min));
    }
  }

  /**
   * return a market order with a given probability otherwise limit
   * 
   * @param probmarket
   */
  protected OrderType decideOrderType(float probmarket) {
    if (random.nextFloat() <= probmarket) {
      return OrderType.MO;
    } else {
      return OrderType.ADD;
    }
  }
}