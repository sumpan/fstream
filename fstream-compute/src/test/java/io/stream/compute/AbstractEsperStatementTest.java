/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.stream.compute;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.io.Resources.readLines;
import static org.assertj.core.util.Lists.newArrayList;
import io.fstream.core.model.event.TickEvent;

import java.io.File;
import java.util.List;

import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPAdministrator;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

@Slf4j
public abstract class AbstractEsperStatementTest implements UpdateListener {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
      .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

  /**
   * Esper state.
   */
  private EPServiceProvider provider;
  private EPRuntime runtime;
  private EPAdministrator admin;

  /**
   * Intermediate results.
   */
  private List<Object> results;

  @Before
  public void setUp() {
    val configuration = new Configuration();
    configuration.addEventType("Rate", TickEvent.class.getName());

    this.provider = EPServiceProviderManager.getProvider(this.toString(), configuration);
    this.provider.initialize();

    this.runtime = provider.getEPRuntime();
    this.admin = provider.getEPAdministrator();
  }

  @SneakyThrows
  protected List<?> execute(File eplFile, Iterable<?> events) {
    return execute(Resources.toString(eplFile.toURI().toURL(), UTF_8), events);
  }

  @SneakyThrows
  protected List<?> execute(File eplFile, File tickEventFile) {
    return execute(Resources.toString(eplFile.toURI().toURL(), UTF_8), getTicketEvents(tickEventFile));
  }

  protected List<?> execute(String statement, TickEvent... events) {
    return execute(statement, events);
  }

  protected List<?> execute(String statement, Iterable<?> events) {
    log.info("Executing: {}", statement);
    val epl = admin.createEPL(statement);

    epl.addListener(this);
    for (val event : events) {
      runtime.sendEvent(event);
    }

    for (val result : results) {
      log.info("Result: {}", result);
    }

    return results;
  }

  @Override
  public void update(EventBean[] newEvents, EventBean[] oldEvents) {
    this.results = newArrayList();
    if (newEvents != null) {
      for (val newEvent : newEvents) {
        results.add(newEvent.getUnderlying());
      }
    }
  }

  @After
  public void tearDown() {
    if (provider != null) {
      provider.destroy();
    }
  }

  @SneakyThrows
  private static Iterable<TickEvent> getTicketEvents(File tickEventFile) {
    val lines = readLines(tickEventFile.toURI().toURL(), UTF_8);

    val builder = ImmutableList.<TickEvent> builder();
    for (val line : lines) {
      val tickEvent = MAPPER.readValue(line, TickEvent.class);
      builder.add(tickEvent);
    }

    return builder.build();
  }

  protected static List<?> givenEvents(Object... events) {
    return ImmutableList.copyOf(events);
  }

  protected static String epl(String epl) {
    return epl;
  }

  protected static File eplFile(String fileName) {
    return new File("src/test/resources/epl", fileName);
  }

  protected static File tickEventFile(String fileName) {
    return new File("src/test/resources/tick-events", fileName);
  }

  protected static TickEvent tickEvent(long time, String symbol, double ask, double bid) {
    return new TickEvent(new DateTime(time), symbol, (float) ask, (float) bid);
  }

  protected static CurrentTimeEvent timeEvent(long time) {
    return new CurrentTimeEvent(time);
  }

}