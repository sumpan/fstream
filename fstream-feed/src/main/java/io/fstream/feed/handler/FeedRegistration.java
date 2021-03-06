/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.feed.handler;

import static quickfix.field.MDEntryType.BID;
import static quickfix.field.MDEntryType.OFFER;
import static quickfix.field.MDUpdateType.FULL_REFRESH;
import static quickfix.field.SubscriptionRequestType.SNAPSHOT_PLUS_UPDATES;
import io.fstream.core.model.state.State;
import lombok.Setter;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.camel.Handler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import quickfix.field.MDEntryType;
import quickfix.field.MDReqID;
import quickfix.field.MDUpdateType;
import quickfix.field.MarketDepth;
import quickfix.field.SubscriptionRequestType;
import quickfix.field.Symbol;
import quickfix.fix44.MarketDataRequest;
import quickfix.fix44.Message;

/**
 * Bean that registers for quote subscriptions.
 */
@Slf4j
@Setter
@Component
public class FeedRegistration {

  @Autowired
  private State state;

  @Handler
  public Message register() {
    log.info("Registering {}...", state.getFxSymbols());

    // All these fields are required
    val message = new MarketDataRequest(
        new MDReqID("fstream-feed"),
        new SubscriptionRequestType(SNAPSHOT_PLUS_UPDATES),
        new MarketDepth(1));

    message.set(new MDUpdateType(FULL_REFRESH));

    // Entry types
    val entryTypes = new MarketDataRequest.NoMDEntryTypes();
    entryTypes.set(new MDEntryType(BID));
    message.addGroup(entryTypes);
    entryTypes.set(new MDEntryType(OFFER));

    message.addGroup(entryTypes);

    // Symbols
    for (val symbol : state.getFxSymbols()) {
      val relatedSymbols = new MarketDataRequest.NoRelatedSym();
      relatedSymbols.set(new Symbol(symbol));
      message.addGroup(relatedSymbols);
    }

    return message;
  }

}