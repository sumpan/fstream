/*
 * Copyright (c) 2015 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.simulate.actor;

import io.fstream.core.model.event.Order;
import io.fstream.core.model.event.Quote;
import io.fstream.simulate.config.SimulateProperties;
import io.fstream.simulate.message.ActiveInstruments;
import io.fstream.simulate.message.Command;
import io.fstream.simulate.message.QuoteRequest;
import io.fstream.simulate.message.SubscriptionQuoteRequest;
import io.fstream.simulate.model.DelayedQuote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Setter;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.Props;

@Slf4j
@Setter
public class Exchange extends BaseActor {

  /**
   * Configuration.
   */
  private final float tickSize;
  private final FiniteDuration quoteDelayDuration;

  /**
   * State.
   */
  private static final AtomicInteger currentOrderId = new AtomicInteger(0);

  private final Map<String, ActorRef> orderBooks = new HashMap<>();
  private final Map<String, Quote> lastValidQuote = new HashMap<>();

  private final List<ActorRef> premiumSubscribers = new ArrayList<>();
  private final List<ActorRef> quotesSubscribers = new ArrayList<>();
  private final List<ActorRef> quoteAndOrdersSubscribers = new ArrayList<>();

  /**
   * Global order ID generator.
   */
  // TODO: Static method prevents distributed actors across JVMs.
  public static int nextOrderId() {
    return currentOrderId.incrementAndGet();
  }

  public Exchange(SimulateProperties properties) {
    super(properties);
    this.activeInstruments = properties.getInstruments();
    this.tickSize = properties.getTickSize();
    this.quoteDelayDuration = FiniteDuration.create(properties.getNonPremiumQuoteDelay(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void preStart() throws Exception {
    // On market open, initialize quotes to random numbers.

    val random = new Random();
    val minBid = properties.getMinPrice();
    val minAsk = properties.getMaxPrice();
    val riskDistance = properties.getRiskDistance();

    for (val symbol : activeInstruments) {
      val bid = minBid - (random.nextInt(riskDistance) * tickSize);
      val ask = minAsk + (random.nextInt(riskDistance) * tickSize);
      val quote = new Quote(getSimulationTime(), symbol, ask, bid, 0, 0);

      lastValidQuote.put(symbol, quote);
    }
  }

  @Override
  public void onReceive(Object message) throws Exception {
    log.debug("Exchange message received {}", message);

    if (message instanceof Order) {
      onReceiveOrder((Order) message);
    } else if (message instanceof Command) {
      onReceiveCommand((Command) message);
    } else if (message instanceof QuoteRequest) {
      onReceiveQuoteRequest((QuoteRequest) message);
    } else if (message instanceof ActiveInstruments) {
      onReceiveActiveInstruments();
    } else if (message instanceof SubscriptionQuoteRequest) {
      onReceiveSubscriptionQuoteRequest((SubscriptionQuoteRequest) message);
    } else if (message instanceof Quote) {
      onReceiveQuote((Quote) message);
    } else {
      unhandled(message);
    }
  }

  private void onReceiveOrder(Order order) {
    val symbol = order.getSymbol();
    if (!isActive(symbol)) {
      log.error("Order sent for inactive symbol {}", symbol);
    }

    val orderBook = resolveOrderBook(symbol);
    orderBook.tell(order, self());
  }

  private void onReceiveQuote(Quote quote) {
    if (quote instanceof DelayedQuote) {
      notifyQuoteAndOrderSubscribers(quote);
      notifyQuoteSubscribers(quote);
    } else {
      lastValidQuote.put(quote.getSymbol(), quote);

      // Notify premium subscribers immediately.
      notifyPremiumSubscribers(quote);

      // Notify non-premium with latency
      val delayedQuote = new DelayedQuote(quote.getDateTime(), quote.getSymbol(),
          quote.getAsk(), quote.getBid(), quote.getAskAmount(),
          quote.getBidAmount());

      scheduleSelfOnce(delayedQuote, quoteDelayDuration);
    }
  }

  private void onReceiveSubscriptionQuoteRequest(SubscriptionQuoteRequest subscriptionQuote) {
    // TODO: Check to make sure Agent is requesting subscription
    val subscription = subscribeForQuote(sender(), subscriptionQuote);
    sender().tell(subscription, self());
  }

  private void onReceiveActiveInstruments() {
    sender().tell(new ActiveInstruments(activeInstruments), self());
  }

  private void onReceiveQuoteRequest(QuoteRequest quoteRequest) {
    val quote = lastValidQuote.get(quoteRequest.getSymbol());
    sender().tell(quote, self());
  }

  private void onReceiveCommand(Command command) {
    val recognized = command == Command.PRINT_BOOK || command == Command.PRINT_SUMMARY;
    if (recognized) {
      for (val orderBook : orderBooks.values()) {
        orderBook.tell(command, self());
      }
    }
  }

  private SubscriptionQuoteRequest subscribeForQuote(ActorRef agent, SubscriptionQuoteRequest message) {
    val level = message.getLevel();
    if (level.equals(Command.SUBSCRIBE_QUOTES.name())) {
      message.setSuccess(this.quotesSubscribers.add(agent));
    } else if (level.equals(Command.SUBSCRIBE_QUOTES_ORDERS.name())) {
      message.setSuccess(this.quoteAndOrdersSubscribers.add(agent));
    } else if (level.equals(Command.SUBSCRIBE_QUOTES_PREMIUM.name())) {
      message.setSuccess(this.premiumSubscribers.add(agent));
    } else {
      message.setSuccess(false);
      log.error("Subscription request not recognized");
    }

    return message;
  }

  private void notifyQuoteSubscribers(Quote quote) {
    notifyAgents(quote, quotesSubscribers);
  }

  private void notifyQuoteAndOrderSubscribers(Quote quote) {
    notifyAgents(quote, quoteAndOrdersSubscribers);
  }

  private void notifyPremiumSubscribers(Quote quote) {
    notifyAgents(quote, premiumSubscribers);
  }

  private void notifyAgents(Quote quote, List<ActorRef> agents) {
    for (val agent : agents) {
      agent.tell(quote, self());
    }
  }

  private ActorRef resolveOrderBook(String symbol) {
    // Create book on demand, as needed
    ActorRef orderBook = orderBooks.get(symbol);
    if (orderBook == null) {
      orderBook = createOrderBook(symbol);

      orderBooks.put(symbol, orderBook);
    }

    return orderBook;
  }

  private ActorRef createOrderBook(String symbol) {
    val props = Props.create(OrderBook.class, properties, symbol);
    return context().actorOf(props, symbol);
  }

}
