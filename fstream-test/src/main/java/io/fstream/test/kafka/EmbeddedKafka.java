/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.test.kafka;

import java.util.Map;
import java.util.Properties;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class EmbeddedKafka {

  /**
   * Configuration.
   */
  @NonNull
  private final Map<String, String> brokerProperties;

  /**
   * State.
   */
  private KafkaServerStartable server;

  public void startUp() throws Exception {
    val properties = createProperties();
    server = new KafkaServerStartable(new KafkaConfig(properties));

    log.info("Starting up server using logDir '{}'...", properties.get("log.dirs"));
    server.startup();
    log.info("Finished startup");
  }

  public void shutDown() throws Exception {
    log.info("Shutting down server...");
    server.shutdown();
    log.info("Awaiting shutdown...");
    server.awaitShutdown();
    log.info("Finished shutdown");
  }

  /**
   * @see https://kafka.apache.org/08/configuration.html
   */
  private Properties createProperties() {
    log.info("Creating properties {}", brokerProperties);
    val properties = new Properties();
    properties.putAll(brokerProperties);

    return properties;
  }

}