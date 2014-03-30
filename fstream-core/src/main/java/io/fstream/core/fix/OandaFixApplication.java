/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */
package io.fstream.core.fix;

import static io.fstream.core.fix.Main.FIX_PASSWORD;
import static quickfix.field.MDEntryType.BID;
import static quickfix.field.MDEntryType.OFFER;
import static quickfix.field.MsgType.FIELD;
import static quickfix.field.MsgType.LOGON;
import static quickfix.field.SubscriptionRequestType.SNAPSHOT_PLUS_UPDATES;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import quickfix.Application;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.MessageCracker;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.UnsupportedMessageType;
import quickfix.field.MDEntryType;
import quickfix.field.MDReqID;
import quickfix.field.Password;
import quickfix.field.ResetSeqNumFlag;
import quickfix.field.SubscriptionRequestType;
import quickfix.field.Symbol;
import quickfix.field.TargetSubID;
import quickfix.fix44.MarketDataSnapshotFullRefresh;
import quickfix.fix44.News;

@Slf4j
public class OandaFixApplication extends MessageCracker implements Application {

  @Override
  @SneakyThrows
  public void toAdmin(Message message, SessionID sessionId) {
    // See http://www.quickfixj.org/confluence/display/qfj/User+FAQ
    log.info("toAdmin - message: {}", message);
    val msgType = message.getHeader().getString(FIELD);
    if (LOGON.compareTo(msgType) == 0) {
      message.setField(new TargetSubID("RATES"));
      message.setField(new Password(FIX_PASSWORD));
      message.setField(new ResetSeqNumFlag(true));
    }
  }

  @Override
  public void toApp(Message message, SessionID sessionID) throws DoNotSend {
    log.info("toApp - message: {}", message);
  }

  @Override
  @SneakyThrows
  public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat,
      IncorrectTagValue, RejectLogon {
    log.info("fromAdmin - message: {}", message);
  }

  @Override
  public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat,
      IncorrectTagValue, UnsupportedMessageType {
    log.info("fromApp - message: {}", message);
    crack(message, sessionID);
  }

  @Override
  public void onCreate(SessionID sessionID) {
    log.info("onCreate - sessionId: {}", sessionID);
  }

  @Override
  public void onLogon(SessionID sessionID) {
    log.info("onLogon - sessionId: {}", sessionID);
    register(sessionID);
  }

  @Override
  public void onLogout(SessionID sessionID) {
    log.info("onLogout - sessionId: {}", sessionID);
  }

  public void onMessage(News news, SessionID sessionID) throws FieldNotFound,
      UnsupportedMessageType, IncorrectTagValue {
    log.info("onMessage - news: {}", news);
  }

  @SneakyThrows
  private static void register(SessionID sessionID) {
    log.info("Registering rates");

    val message = new MarketDataSnapshotFullRefresh();
    message.set(new MDReqID("MDQeq"));
    message.setField(new SubscriptionRequestType(SNAPSHOT_PLUS_UPDATES));
    message.addGroup(newBidGroup());
    message.addGroup(newOfferGroup());
    message.setField(new Symbol("EUR/USD"));

    Session.sendToTarget(message, sessionID);
  }

  private static MarketDataSnapshotFullRefresh.NoMDEntries newOfferGroup() {
    val offer = new MarketDataSnapshotFullRefresh.NoMDEntries();
    offer.set(new MDEntryType(OFFER));

    return offer;
  }

  private static MarketDataSnapshotFullRefresh.NoMDEntries newBidGroup() {
    val bid = new MarketDataSnapshotFullRefresh.NoMDEntries();
    bid.set(new MDEntryType(BID));

    return bid;
  }

}
