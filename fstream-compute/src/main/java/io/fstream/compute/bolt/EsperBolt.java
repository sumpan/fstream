/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.compute.bolt;

import io.fstream.core.model.definition.Definition;
import io.fstream.core.model.event.AlertEvent;
import io.fstream.core.model.event.Event;
import io.fstream.core.model.event.EventType;
import io.fstream.core.model.event.Order;
import io.fstream.core.model.event.Quote;
import io.fstream.core.model.event.Trade;
import io.fstream.core.util.Codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPAdministrator;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.StatementAwareUpdateListener;
import com.espertech.esper.client.metric.MetricEvent;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.fasterxml.jackson.core.type.TypeReference;

@Slf4j
public abstract class EsperBolt extends BaseRichBolt implements StatementAwareUpdateListener {

  /**
   * Constants.
   */
  private static final String KAFKA_TOPIC_KEY = "1";

  /**
   * Configuration keys.
   */
  public static final String STATEMENTS_CONFIG_KEY = EsperBolt.class.getName();

  /**
   * Esper.
   */
  private transient EPServiceProvider provider;
  private transient EPRuntime runtime;
  private transient EPAdministrator admin;

  /**
   * Storm.
   */
  private transient OutputCollector collector;

  private final boolean externalClock = false;

  @Override
  public void declareOutputFields(OutputFieldsDeclarer declarer) {
    declarer.declare(new Fields(
        FieldNameBasedTupleToKafkaMapper.BOLT_KEY,
        FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE));
  }

  @Override
  public void prepare(@SuppressWarnings("rawtypes") Map conf, TopologyContext context, OutputCollector collector) {
    log.info("Preparing...");
    val configuration = createConfiguration();

    if (externalClock) {
      // Set external clock for esper
      configuration.getEngineDefaults().getThreading().setInternalTimerEnabled(false);
    }

    this.collector = collector;
    this.provider = EPServiceProviderManager.getProvider(this.toString(), configuration);
    this.provider.initialize();
    this.runtime = provider.getEPRuntime();
    this.admin = provider.getEPAdministrator();

    log.info("Creating common statements...");
    for (val statement : getStatements(conf)) {
      log.info("Registering common statement: '{};", statement.replace('\n', ' '));
      val epl = admin.createEPL(statement);

      epl.addListener(this);
    }
    log.info("Finished creating common statements.");

    // Delegate to child
    log.info("Creating '{}' statements...", this.getClass().getSimpleName());
    createStatements(conf, admin);
    log.info("Finished creating '{}' statements.", this.getClass().getSimpleName());
  }

  private Configuration createConfiguration() {
    val configuration = new Configuration();
    configuration.configure();
    configuration.addEventType("Trade", Trade.class.getName());
    configuration.addEventType("Order", Order.class.getName());
    configuration.addEventType("Quote", Quote.class.getName());
    configuration.addEventType("Alert", AlertEvent.class.getName());
    configuration.addEventType("Metric", MetricEvent.class.getName());

    return configuration;
  }

  /**
   * Template method to create a statement.
   */
  protected abstract void createStatements(Map<?, ?> conf, EPAdministrator admin);

  /**
   * Template method to create an event.
   */
  protected abstract Event createEvent(int id, Object data);

  @Override
  @SneakyThrows
  public void execute(Tuple tuple) {
    val value = (String) tuple.getValue(0);
    val event = Codec.decodeText(value, Event.class);

    if (externalClock) {
      // Send external timer event - timestamp of incoming event.
      // Quote are truly external events. Rest are internally generated.
      if (event.getType().equals(EventType.QUOTE)) {
        runtime.sendEvent(new CurrentTimeEvent(event.getDateTime().getMillis()));
      }
    }

    runtime.sendEvent(event);

    collector.ack(tuple);
  }

  @Override
  public void update(EventBean[] newEvents, EventBean[] oldEvents, EPStatement statement,
      EPServiceProvider epServiceProvider) {
    if (newEvents != null) {
      val definition = (Definition) statement.getUserObject();
      for (val newEvent : newEvents) {
        val data = newEvent.getUnderlying();
        val event = createEvent(definition.getId(), data);
        val value = Codec.encodeText(event);

        collector.emit(new Values(KAFKA_TOPIC_KEY, value));
      }
    }
  }

  @Override
  public void cleanup() {
    if (provider != null) {
      provider.destroy();
    }
  }

  @SneakyThrows
  private static List<String> getStatements(Map<?, ?> conf) {
    val value = (String) conf.get(STATEMENTS_CONFIG_KEY);

    return Codec.decodeText(value, new TypeReference<ArrayList<String>>() {});
  }

}