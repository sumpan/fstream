/*
 * Copyright (c) 2015 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.simulate.message;

import lombok.Data;

/**
 * Message for subscribing to quotes.
 * <p>
 * Success flag is set to true on success by exchange a sent back to sender.
 */
@Data
public class SubscriptionQuoteRequest {

  String level;
  boolean success;

  public SubscriptionQuoteRequest(String level) {
    this.level = level;
    this.success = false;
  }

}
