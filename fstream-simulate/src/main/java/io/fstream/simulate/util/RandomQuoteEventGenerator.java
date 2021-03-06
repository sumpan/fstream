/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.simulate.util;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import io.fstream.core.model.event.Quote;

import java.util.Random;

import lombok.RequiredArgsConstructor;
import lombok.val;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.joda.time.DateTime;

/**
 * {@link QuoteEvent} generator used to create a simulated quote feed when running in simulation mode.
 */
@RequiredArgsConstructor
public class RandomQuoteEventGenerator implements Processor {

  /**
   * Configuration.
   */
  private final String symbol;
  private final float minMid;
  private final float maxMid;

  /**
   * State.
   */
  private final Random random = new Random();

  @Override
  public void process(Exchange exchange) throws Exception {
    // Random pricing
    val mid = generatePrice(minMid, maxMid);
    val spread = generateSpread(minMid);

    val ask = mid + spread / 2;
    val bid = mid - spread / 2;
    val event = new Quote(new DateTime(), symbol, ask, bid);

    // Random timing
    val delay = generateDelay(0, 2000);
    MILLISECONDS.sleep(delay);

    // Simulated event
    exchange.getOut().setBody(event);
  }

  private float generatePrice(float min, float max) {
    return min + (max - min) * random.nextFloat();
  }

  private int generateDelay(int min, int max) {
    return (int) (min + (max - min) * random.nextFloat());
  }

  private float generateSpread(float price) {
    return price * 0.10f;
  }

}