package io.fstream.simulate.actor;

import static java.util.Collections.reverseOrder;
import io.fstream.simulate.message.Messages;
import io.fstream.simulate.model.LimitOrder;
import io.fstream.simulate.model.Order;
import io.fstream.simulate.model.Order.OrderSide;
import io.fstream.simulate.model.Order.OrderType;
import io.fstream.simulate.model.Quote;
import io.fstream.simulate.model.Trade;
import io.fstream.simulate.util.PrototypeActor;

import java.util.Comparator;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.joda.time.DateTime;
import org.joda.time.Seconds;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;

/**
 * A price,time ordered implementation of a central limit order book. The principle data structure is a List of
 * {@code Order}s. By definition only limit orders can live in the book. Market orders are accepted and trigger a trade
 * immediately if liquidity is available.
 */
@Slf4j
@Getter
@RequiredArgsConstructor
@PrototypeActor
public class OrderBook extends UntypedActor {

  /**
   * Configuration.
   */
  @NonNull
  final String symbol;

  /**
   * Dependencies.
   */
  @NonNull
  final ActorRef exchange;
  @NonNull
  final ActorRef publisher;

  /**
   * State.
   */
  NavigableMap<Float, NavigableSet<LimitOrder>> bids = new TreeMap<>(reverseOrder());
  NavigableMap<Float, NavigableSet<LimitOrder>> asks = new TreeMap<>();

  /**
   * Aggregates.
   */
  float bestBid = Float.MIN_VALUE;
  float bestAsk = Float.MIN_VALUE;

  int bidDepth;
  int askDepth;

  int orderCount = 0;
  int tradeCount = 0;

  public void processOrder(Order order) {
    assert (order.getSymbol() == this.symbol);
    orderCount += 1;
    order.setProcessedTime(DateTime.now());
    if (order.getType() == OrderType.MO) { // process market order
      log.debug(String.format("processing market order %s ", order.toString()));
      LimitOrder limitorder = (LimitOrder) order;

      if (order.getSide() == OrderSide.ASK) {
        limitorder.setPrice(Float.MIN_VALUE);
      } else {
        limitorder.setPrice(Float.MAX_VALUE);
      }
      this.processMarketOrder(limitorder);
    } else { // process limit order
      log.debug(String.format("processing limitorder %s ", order.toString()));
      this.processLimitOrder((LimitOrder) order);
    }
  }

  /**
   * Accepts IOrder and executes it against available depth. Returns unfilled amount TODO currently MarketOrders are
   * mimicked via LimitOrders where trigger price is best ask/bid. Need to add marketable order implementation
   * 
   * @param order
   * @return
   */
  private int processMarketOrder(LimitOrder order) {
    NavigableMap<Float, NavigableSet<LimitOrder>> book;
    if (order.getSide() == OrderSide.ASK) {
      if (this.bids.isEmpty()) {
        log.debug(String.format("No depth. Order not filled %s", order.toString()));
        return order.getAmount();
      }
      book = this.bids;
    } else {
      if (this.asks.isEmpty()) {
        log.debug(String.format("No depth. Order not filled %s", order.toString()));
        return order.getAmount();
      }
      book = this.asks;
    }

    int unfilledsize = order.getAmount();
    int executedsize = 0;
    int totalexecutedsize = 0;
    val bookiterator = book.entrySet().iterator();
    while (bookiterator.hasNext()) {
      val pricelevel = bookiterator.next();
      val orderiterator = pricelevel.getValue().iterator();
      while (orderiterator.hasNext()) {
        val passiveorder = orderiterator.next();

        if (unfilledsize <= 0) {
          break;
        }
        if (order.getSide() == OrderSide.ASK) { // limit price exists,
          // respect bounds
          if (order.getPrice() > passiveorder.getPrice()) {
            log.debug(String.format(
                "breaking price crossed on active ASK (SELL) MO for %s orderprice=%s passiveorder=%s",
                this.getSymbol(), order.getPrice(), passiveorder.getPrice()));
            this.updateDepth(order.getSide(), totalexecutedsize);
            this.updateBestPrices();
            return unfilledsize; // price has crossed
          }
        } else {
          if (order.getPrice() < passiveorder.getPrice()) {
            log.debug(String.format(
                "breaking price crossed on active BID (BUY) MO for %s orderprice=%s passiveorder=%s", this.getSymbol(),
                order.getPrice(), passiveorder.getPrice()));
            this.updateDepth(order.getSide(), totalexecutedsize);
            this.updateBestPrices();
            return unfilledsize; // price has crossed
          }
        }
        unfilledsize = unfilledsize - passiveorder.getAmount();

        if (unfilledsize == 0) {
          // nothing else to do.
          executedsize = order.getAmount();
          totalexecutedsize = totalexecutedsize + executedsize;
          registerTrade(order, passiveorder, executedsize);
          orderiterator.remove(); // remove the passive order (last
          // one returned by iterator)

        } else if (unfilledsize < 0) {
          // incoming was smaller than first order in queue. repost
          // remainder
          executedsize = order.getAmount();
          totalexecutedsize = totalexecutedsize + executedsize;
          passiveorder.setAmount(Math.abs(unfilledsize));
          registerTrade(order, passiveorder, executedsize);

        } else {
          // incoming larger than the first order in current level.
          // keep
          // on iterating.
          executedsize = passiveorder.getAmount();
          order.setAmount(order.getAmount() - executedsize);
          totalexecutedsize = totalexecutedsize + executedsize;
          registerTrade(order, passiveorder, executedsize);
          orderiterator.remove(); // remove the passive order (last
          // one returned by iterator)
        }
      }
      if (book.get(pricelevel.getKey()).isEmpty()) {
        bookiterator.remove(); // removes price level if orderqueue in
        // it is empty (last one returned by
        // iterator)
      }
    }
    this.updateDepth(order.getSide(), totalexecutedsize);
    this.updateBestPrices();
    return unfilledsize;
  }

  /**
   * updates best ask/bid
   */
  private void updateBestPrices() {
    val prevbestaks = this.bestAsk;
    val prevbestbid = this.bestBid;
    this.bestAsk = this.asks.isEmpty() ? Float.MAX_VALUE : this.asks.firstKey();
    this.bestBid = this.bids.isEmpty() ? Float.MIN_VALUE : this.bids.firstKey();
    if (this.bestAsk != prevbestaks || this.bestBid != prevbestbid) {
      val quote =
          new Quote(DateTime.now(), this.getSymbol(), this.getBestAsk(), this.getBestBid(), getDepthAtLevel(bestAsk,
              OrderSide.ASK), getDepthAtLevel(bestBid, OrderSide.BID));
      if (!isValidQuote(this.bestBid, this.bestAsk)) {
        log.info("invalid quote %s", quote.toString());
        return;
      }
      exchange.tell(quote, self());
      publisher.tell(quote, self());
    }
  }

  private int getDepthAtLevel(float price, OrderSide side) {
    int depth = 0;

    NavigableSet<LimitOrder> book;
    if (side == OrderSide.ASK) {
      book = this.asks.get(price);
    }
    else {
      book = this.bids.get(price);
    }
    if (book != null) {
      for (val order : book) {
        depth = depth + order.getAmount();
      }
    }
    return depth;
  }

  private boolean isValidQuote(float bid, float ask) {
    if (ask <= bid) {
      return false;
    }
    return true;
  }

  /**
   * updates biddepth/askdepth based on executed size
   * 
   * @param orderside
   * @param executedsize
   */
  private void updateDepth(OrderSide orderside, int executedsize) {
    if (orderside == OrderSide.ASK) {
      this.bidDepth = this.bidDepth - executedsize;
    } else {
      this.askDepth = this.askDepth - executedsize;
    }
  }

  /**
   * Checks the validity of the book by inspecting actual depth in the book and comparing it to maintained
   * biddepth/askdepth variables
   * 
   * @return
   */
  private boolean assertBookDepth() {
    int biddepth = 0;
    for (val bidsentries : bids.entrySet()) {
      for (val bids : bidsentries.getValue()) {
        biddepth += bids.getAmount();
      }
    }
    if (biddepth != this.bidDepth) {
      log.error(String.format("bid depth does not add up record = %s actual = %s", this.bidDepth, biddepth));
      return false;
    }
    int askdepth = 0;
    for (val asksentries : asks.entrySet()) {
      for (val asks : asksentries.getValue()) {
        askdepth += asks.getAmount();
      }
    }
    if (askdepth != this.askDepth) {
      log.error(String.format("aks depth does not add up record = %s actual = %s", this.askDepth, askdepth));
      return false;
    }
    return true;
  }

  /**
   * Registers a Trade
   * 
   * @param active
   * @param passive
   * @param executedsize
   */
  private void registerTrade(Order active, Order passive, int executedsize) {
    tradeCount += 1;
    Trade trade = new Trade(DateTime.now(), active, passive, executedsize);
    exchange.tell(trade, self());
    publisher.tell(trade, self());
    if (Seconds.secondsBetween(active.getSentTime(), trade.getTime()).getSeconds() > 5) {
      log.debug(String.format("order took more than 5 seconds to be processed %s", active.toString()));
    }
  }

  private void processLimitOrder(LimitOrder order) {
    switch (order.getType()) {
    case AMEND:
      System.out.println("order amended");
    case CANCEL:
      System.out.println("order canceled");
    case ADD:
      addLimitOrder(order);
    default:
      // TODO: Handle?
      break;
    }
  }

  /**
   * Adds LimitOrder to the orderbook
   * 
   * @param order
   */
  private void addLimitOrder(LimitOrder order) {
    int availabledepth = 0;
    if (order.getSide() == OrderSide.ASK) {
      // executing against bid side of the book.
      availabledepth = this.bidDepth;
    } else {
      availabledepth = this.askDepth;
    }
    int unfilledsize;
    if (crossesSpread(order) && availabledepth > 0) { // if limitprice
      // crosses spread,
      // treat as
      // marketorder

      unfilledsize = processMarketOrder(order);
      order.setAmount(unfilledsize); // any unfilled amount added to
      // orderbook
      if (unfilledsize > 0) {
        insertOrder(order);
      }

    } else { // not crossing spread or no depth available. So add to limit
      // book
      insertOrder(order);
    }

    if (!assertBookDepth()) {
      try {
        throw new Exception("book in invalid state");
      } catch (Exception e) {
        // TODO Auto-generated catch block
        this.printBook();
        e.printStackTrace();
        System.exit(1);
      }
    }
  }

  /**
   * inserts limit order in the TreeMap<Float,TreeSet<LimitOrder>> data strucuture
   * 
   * @param order
   */
  private void insertOrder(LimitOrder order) {
    boolean isBid;
    NavigableMap<Float, NavigableSet<LimitOrder>> sidebook;
    if (order.getSide() == OrderSide.ASK) {
      isBid = false;
      sidebook = this.asks;
    } else {
      isBid = true;
      sidebook = this.bids;
    }

    if (sidebook.isEmpty() || sidebook.get(order.getPrice()) == null) {
      // add order to order book
      TreeSet<LimitOrder> orderlist = new TreeSet<LimitOrder>(orderTimeComparator);
      orderlist.add(order);
      sidebook.put(order.getPrice(), orderlist);

    } else { // order at same price exists, queue it by time
      val orderlist = sidebook.get(order.getPrice());
      orderlist.add(order);
      sidebook.put(order.getPrice(), orderlist);
    }
    // set best price and depth attributes
    if (isBid) {
      if (this.bestBid == 0 || order.getPrice() > this.bestBid) {
        this.bestBid = order.getPrice();
      }
      this.bidDepth = this.bidDepth + order.getAmount();
    } else {
      if (this.bestAsk == 0 || order.getPrice() < this.bestAsk) {
        this.bestAsk = order.getPrice();
      }
      this.askDepth = this.askDepth + order.getAmount();
    }
    order.setProcessedTime(DateTime.now());
    if (Seconds.secondsBetween(order.getProcessedTime(), order.getSentTime()).getSeconds() > 5) {
      log.debug(String.format("order took more than 5 seconds to be processed %s", order.toString()));
    }

    // TODO: Add these to the right location
    publisher.tell(order, self());
  }

  /**
   * Determines if a limitorder crosses the spread i.e. LimitBuy is better priced than bestask or LimitSell is better
   * priced than bestbid
   * 
   * @param order
   * @return
   */
  private boolean crossesSpread(LimitOrder order) {
    if (order.getSide() == OrderSide.ASK) {
      if (order.getPrice() <= this.bestBid) {
        return true;
      }
    } else {
      if (order.getPrice() >= this.bestAsk) {
        return true;
      }
    }
    return false;
  }

  /**
   * deletes an order from orderbook TODO untested method.
   * 
   * @param order
   */
  @SuppressWarnings("unused")
  private boolean deleteOrder(LimitOrder order) {
    return getBook(order).get(order.getPrice()).remove(order);
  }

  private NavigableMap<Float, NavigableSet<LimitOrder>> getBook(LimitOrder order) {
    NavigableMap<Float, NavigableSet<LimitOrder>> book;
    if (order.getSide() == OrderSide.ASK) {
      book = this.bids;
    } else {
      book = this.asks;
    }
    return book;
  }

  /**
   * orders limitorders in time priority in the orderbook treeset (Price -> {ordert1, ordert2, ...}
   */
  public static Comparator<LimitOrder> orderTimeComparator = new Comparator<LimitOrder>() {

    @Override
    public int compare(LimitOrder order1, LimitOrder order2) {
      if (order1.equals(order2)) {
        return 0;
      }
      if (order1.getSentTime().getMillis() < order2.getSentTime().getMillis()) {
        return -1;
      } else if (order1.getSentTime().getMillis() > order2.getSentTime().getMillis()) {
        return 1;
      } else {
        return -1; // TODO this is a hack so orders at same time are not
        // ignored. need a better data structure for orders
      }

    }

  };

  public void printBook() {
    log.info(String.format("BOOK = %s", this.getSymbol()));
    String book = new String(String.format("BOOK = %s\n", this.getSymbol()));
    book = book + "------ ASKS -------\n";
    for (val ask : asks.entrySet()) {
      book = book + String.format("%s -> ", ask.getKey());
      for (val firstnode : ask.getValue()) {
        book =
            book
                + String.format("( %s,%s,%s) -> ", firstnode.getSentTime().toString(), firstnode.getPrice(),
                    firstnode.getAmount());
      }
      book = book + "\n";
    }
    book = book + "------ BIDS -------\n";
    for (val bid : bids.entrySet()) {
      book = book + String.format("%s -> ", bid.getKey());
      for (val firstnode : bid.getValue()) {
        book =
            book
                + String.format("( %s,%s,%s) -> ", firstnode.getSentTime().toString(), firstnode.getPrice(),
                    firstnode.getAmount());
      }
      book = book + "\n";
    }
    book = book + String.format("bid depth = %s, ask depth = %s\n", this.bidDepth, this.askDepth);
    book =
        book
            + String.format("best ask = %s, best bid =%s, spread = %s\n", this.bestAsk, this.bestBid, this.bestAsk
                - this.bestBid);
    book = book + "----- END -----\n";
    log.info(book);
  }

  @Override
  public void onReceive(Object message) throws Exception {
    log.debug("exchange message received {}", message);
    if (message instanceof Order) {
      this.processOrder((Order) message);
    } else if (message instanceof String) {
      if (message.equals(Messages.PRINT_ORDER_BOOK)) {
        this.printBook();
        sender().tell(true, self());
      } else if (message.equals(Messages.PRINT_SUMMARY)) {
        log.info(String.format(
            "%s orders processed=%s, trades processed=%s, biddepth=%s, askdepth=%s bestask=%s bestbid=%s spread=%s",
            symbol, orderCount, tradeCount, bidDepth, askDepth, bestAsk, bestBid, bestAsk - bestBid));
      }
    } else {
      unhandled(message);
    }

  }

}