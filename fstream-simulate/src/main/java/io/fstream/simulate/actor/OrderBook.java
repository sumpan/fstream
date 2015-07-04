package io.fstream.simulate.actor;

import static com.google.common.base.Preconditions.checkState;
import static io.fstream.simulate.util.OrderBookFormatter.formatOrderBook;
import static java.util.Collections.reverseOrder;
import io.fstream.core.model.event.Order;
import io.fstream.core.model.event.Order.OrderSide;
import io.fstream.core.model.event.Order.OrderType;
import io.fstream.core.model.event.Quote;
import io.fstream.core.model.event.Trade;
import io.fstream.simulate.config.SimulateProperties;
import io.fstream.simulate.message.Command;
import io.fstream.simulate.util.LimitOrderTimeComparator;

import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.joda.time.Seconds;

/**
 * A price,time ordered implementation of a central limit order book. The principle data structure is a List of
 * {@code Order}s. By definition only limit orders can live in the book. Market orders are accepted and trigger a trade
 * immediately if liquidity is available.
 */
@Slf4j
@Getter
public class OrderBook extends BaseActor {

  /**
   * Configuration.
   */
  @NonNull
  private final String symbol;

  /**
   * State.
   */
  private final NavigableMap<Float, NavigableSet<Order>> bids = new TreeMap<>(reverseOrder()); // Non-natural
  private final NavigableMap<Float, NavigableSet<Order>> asks = new TreeMap<>();

  /**
   * Aggregates.
   */
  private float bestBid = Float.MIN_VALUE;
  private float bestAsk = Float.MIN_VALUE;

  private long bidDepth;
  private long askDepth;

  private int orderCount = 0;
  private int tradeCount = 0;

  public OrderBook(SimulateProperties properties, String symbol) {
    super(properties);
    this.symbol = symbol;
  }

  public void printBook() {
    log.info("\n{}", formatOrderBook(this));
  }

  @Override
  public void onReceive(Object message) throws Exception {
    log.debug("Order book message received {}", message);

    if (message instanceof Order) {
      onReceiveOrder((Order) message);
    } else if (message instanceof Command) {
      onReceiveCommand((Command) message);
    } else {
      unhandled(message);
    }
  }

  private void onReceiveOrder(Order order) {
    checkState(order.getSymbol() == this.symbol);

    // Book keeping
    orderCount += 1;

    if (orderCount % 100_000 == 0) {
      log.info("[{}] ask count = {}, bid count = {}, ask depth = {}, bid depth = {}",
          symbol, calculateAskCount(), calculateBidCount(), askDepth, bidDepth);
    }

    log.debug("Processing {} order: {}", order.getOrderType(), order);
    order.setProcessedTime(getSimulationTime());

    if (order.getOrderType() == OrderType.MARKET) {
      // TODO: Explain the need for this
      if (order.getSide() == OrderSide.ASK) {
        order.setPrice(Float.MIN_VALUE);
      } else {
        order.setPrice(Float.MAX_VALUE);
      }

      processMarketOrder(order);
    } else {
      processLimitOrder(order);
    }
  }

  private void onReceiveCommand(Command command) {
    if (command == Command.PRINT_ORDER_BOOK) {
      printBook();

      // TODO: Explain what does sending "true" achieve
      sender().tell(true, self());
    } else if (command == Command.PRINT_SUMMARY) {
      log.info("{} orders processed={}, trades processed={}, biddepth={}, askdepth={} bestask={} bestbid={} spread={}",
          symbol, orderCount, tradeCount, bidDepth, askDepth, bestAsk, bestBid, bestAsk - bestBid);
    }
  }

  /**
   * Accepts {@code Order} and executes it against available depth. Returns unfilled amount.
   * <p>
   * TODO: Currently MarketOrders are mimicked via Orders where trigger price is best ask/bid. Need to add marketable
   * order implementation
   */
  private int processMarketOrder(Order order) {
    NavigableMap<Float, NavigableSet<Order>> book;
    if (order.getSide() == OrderSide.ASK) {
      if (this.bids.isEmpty()) {
        log.debug("No depth. Order not filled {}", order);
        return order.getAmount();
      }
      book = this.bids;
    } else {
      if (this.asks.isEmpty()) {
        log.debug("No depth. Order not filled {}", order);
        return order.getAmount();
      }
      book = this.asks;
    }

    int unfilledSize = order.getAmount();
    int executedSize = 0;
    int totalExecutedsize = 0;
    val bookIterator = book.entrySet().iterator();
    while (bookIterator.hasNext()) {
      val priceLevel = bookIterator.next();
      val orderIterator = priceLevel.getValue().iterator();
      while (orderIterator.hasNext()) {
        val passiveOrder = orderIterator.next();

        if (unfilledSize <= 0) {
          break;
        }
        if (order.getSide() == OrderSide.ASK) {
          // Limit price exists, respect bounds
          if (order.getPrice() > passiveOrder.getPrice()) {
            log.debug("Breaking price crossed on active ASK (SELL) MO for {} order price={} passive order={}",
                this.getSymbol(), order.getPrice(), passiveOrder.getPrice());
            this.updateDepth(order.getSide(), totalExecutedsize);
            this.updateBestPrices();
            return unfilledSize; // price has crossed
          }
        } else {
          if (order.getPrice() < passiveOrder.getPrice()) {
            log.debug("Breaking price crossed on active BID (BUY) MO for {} order price={} passive order={}",
                this.getSymbol(), order.getPrice(), passiveOrder.getPrice());
            this.updateDepth(order.getSide(), totalExecutedsize);
            this.updateBestPrices();
            return unfilledSize; // price has crossed
          }
        }
        unfilledSize = unfilledSize - passiveOrder.getAmount();

        if (unfilledSize == 0) {
          // Nothing else to do.
          executedSize = order.getAmount();
          totalExecutedsize = totalExecutedsize + executedSize;

          executeTrade(order, passiveOrder, executedSize);

          // Remove the passive order (last one returned by iterator)
          orderIterator.remove();
        } else if (unfilledSize < 0) {
          // Incoming was smaller than first order in queue. Repost remainder
          executedSize = order.getAmount();
          totalExecutedsize = totalExecutedsize + executedSize;
          passiveOrder.setAmount(Math.abs(unfilledSize));

          executeTrade(order, passiveOrder, executedSize);
        } else {
          // Incoming larger than the first order in current level. Keep on iterating.
          executedSize = passiveOrder.getAmount();
          totalExecutedsize = totalExecutedsize + executedSize;
          order.setAmount(order.getAmount() - executedSize);

          executeTrade(order, passiveOrder, executedSize);

          // Remove the passive order (last one returned by iterator)
          orderIterator.remove();
        }
      }
      if (book.get(priceLevel.getKey()).isEmpty()) {
        // Removes price level if order queue in it is empty (last one returned by iterator)
        bookIterator.remove();
      }
    }

    this.updateDepth(order.getSide(), totalExecutedsize);
    this.updateBestPrices();

    return unfilledSize;
  }

  private void processLimitOrder(Order order) {
    if (order.getOrderType() == OrderType.ADD) {
      log.debug("Order added");
      addLimitOrder(order);
    } else if (order.getOrderType() == OrderType.AMEND) {
      log.debug("Order amended");
    } else if (order.getOrderType() == OrderType.CANCEL) {
      log.debug("Cancelling order {}", order);
      cancelOrder(order);
    } else {
      checkState(false);
    }
  }

  /**
   * Updates best ask/bid
   */
  private void updateBestPrices() {
    val prevBestAsk = this.bestAsk;
    val prevBestBid = this.bestBid;
    this.bestAsk = this.asks.isEmpty() ? Float.MAX_VALUE : this.asks.firstKey();
    this.bestBid = this.bids.isEmpty() ? Float.MIN_VALUE : this.bids.firstKey();

    if (this.bestAsk != prevBestAsk || this.bestBid != prevBestBid) {
      val quote = new Quote(getSimulationTime(), this.getSymbol(), this.getBestAsk(), this.getBestBid(),
          getDepthAtLevel(bestAsk, OrderSide.ASK),
          getDepthAtLevel(bestBid, OrderSide.BID));

      if (!isValidQuote(this.bestBid, this.bestAsk)) {
        log.error("Invalid quote {}", quote);
        return;
      }

      exchange().tell(quote, self());
      publisher().tell(quote, self());
    }
  }

  private int getDepthAtLevel(float price, OrderSide side) {
    int depth = 0;

    NavigableSet<Order> book;
    if (side == OrderSide.ASK) {
      book = this.asks.get(price);
    } else {
      book = this.bids.get(price);
    }

    if (book != null) {
      for (val order : book) {
        depth += order.getAmount();
      }
    }

    return depth;
  }

  private boolean isValidQuote(float bid, float ask) {
    return ask > bid;
  }

  /**
   * Updates bid depth / ask depth based on executed size
   */
  private void updateDepth(OrderSide side, int executedSize) {
    if (side == OrderSide.ASK) {
      this.bidDepth -= executedSize;
    } else {
      this.askDepth -= executedSize;
    }
  }

  /**
   * Checks the validity of the book by inspecting actual depth in the book and comparing it to maintained bid depth /
   * ask depth variables
   */
  private boolean assertBookDepth() {
    val bidDepth = calculateBidDepth();
    if (bidDepth != this.bidDepth) {
      log.error("Bid depth does not add up record = {} actual = {}", this.bidDepth, bidDepth);
      return false;
    }

    val askDepth = calculateAskDepth();
    if (askDepth != this.askDepth) {
      log.error("Ask depth does not add up record = {} actual = {}", this.askDepth, askDepth);
      return false;
    }

    return true;
  }

  private int calculateAskDepth() {
    int askDepth = 0;
    for (val values : asks.values()) {
      for (val asks : values) {
        askDepth += asks.getAmount();
      }
    }

    return askDepth;
  }

  private int calculateBidDepth() {
    int bidDepth = 0;
    for (val values : bids.values()) {
      for (val bids : values) {
        bidDepth += bids.getAmount();
      }
    }

    return bidDepth;
  }

  private int calculateAskCount() {
    int askCount = 0;
    for (val values : asks.values()) {
      askCount += values.size();
    }

    return askCount;
  }

  private int calculateBidCount() {
    int bidCount = 0;
    for (val values : bids.values()) {
      bidCount += values.size();
    }

    return bidCount;
  }

  /**
   * Registers a trade
   */
  private void executeTrade(Order active, Order passive, int executedSize) {
    tradeCount += 1;
    val trade = new Trade(getSimulationTime(), active, passive, executedSize);

    exchange().tell(trade, self());
    publisher().tell(trade, self());

    val latency = Seconds.secondsBetween(active.getDateTime(), trade.getDateTime()).getSeconds();
    val delayed = latency > 5;
    if (delayed) {
      log.debug("Order took more than 5 seconds to be processed {}", active);
    }
  }

  /**
   * Adds LimitOrder to the order book
   */
  private void addLimitOrder(Order order) {
    long availableDepth = 0;
    if (order.getSide() == OrderSide.ASK) {
      // Executing against bid side of the book.
      availableDepth = this.bidDepth;
    } else {
      availableDepth = this.askDepth;
    }

    int unfilledSize;
    if (crossesSpread(order) && availableDepth > 0) { // if limitprice
      // Crosses spread, treat as market order
      unfilledSize = processMarketOrder(order);
      order.setAmount(unfilledSize);

      // Any unfilled amount added to order book
      if (unfilledSize > 0) {
        insertOrder(order);
      }
    } else {
      // Mot crossing spread or no depth available. So add to limit book
      insertOrder(order);
    }

    if (log.isDebugEnabled()) {
      if (!assertBookDepth()) {
        System.exit(1);
      }
    }
  }

  /**
   * Inserts limit order in the TreeMap<Float,TreeSet<LimitOrder>> data strucuture
   */
  private void insertOrder(Order order) {
    boolean isBid;
    NavigableMap<Float, NavigableSet<Order>> sideBook;
    if (order.getSide() == OrderSide.ASK) {
      isBid = false;
      sideBook = this.asks;
    } else {
      isBid = true;
      sideBook = this.bids;
    }

    if (sideBook.isEmpty() || sideBook.get(order.getPrice()) == null) {
      // Add order to order book
      val orderList = new TreeSet<Order>(LimitOrderTimeComparator.INSTANCE);
      orderList.add(order);
      sideBook.put(order.getPrice(), orderList);
    } else {
      // Order at same price exists, queue it by time
      val orderList = sideBook.get(order.getPrice());
      orderList.add(order);
      sideBook.put(order.getPrice(), orderList);
    }

    // Set best price and depth attributes
    if (isBid) {
      if (this.bidDepth == 0 || order.getPrice() > this.bestBid) {
        this.bestBid = order.getPrice();
      }
      this.bidDepth = this.bidDepth + order.getAmount();
    } else {
      if (this.askDepth == 0 || order.getPrice() < this.bestAsk) {
        this.bestAsk = order.getPrice();
      }
      this.askDepth = this.askDepth + order.getAmount();
    }

    order.setProcessedTime(getSimulationTime());
    if (Seconds.secondsBetween(order.getProcessedTime(), order.getDateTime()).getSeconds() > 5) {
      log.debug("Rrder took more than 5 seconds to be processed: {}", order);
    }

    // publish to tape
    publisher().tell(order, self());
  }

  /**
   * Determines if a limit order crosses the spread i.e. LimitBuy is better priced than bestask or LimitSell is better
   * priced than best bid
   */
  private boolean crossesSpread(Order order) {
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
   * Cancels and removes an order from this order book.
   */
  private boolean cancelOrder(Order order) {
    val priceLevel = getPriceLevel(order);

    val empty = priceLevel == null;
    if (empty) {
      return false;
    }

    val missing = !priceLevel.remove(order);
    if (missing) {
      return false;
    }

    if (order.getSide() == OrderSide.ASK) {
      this.askDepth -= order.getAmount();
    } else {
      this.bidDepth -= order.getAmount();
    }

    if (log.isDebugEnabled() && !assertBookDepth()) {
      System.exit(1);
    }

    log.debug("Cancelled order");
    publisher().tell(order, self());

    return true;
  }

  private NavigableSet<Order> getPriceLevel(Order order) {
    val orders = order.getSide() == OrderSide.ASK ? this.asks : this.bids;

    return orders.get(order.getPrice());
  }

}
