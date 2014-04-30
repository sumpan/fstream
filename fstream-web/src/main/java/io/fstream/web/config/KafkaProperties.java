/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.web.config;

import static com.google.common.collect.Lists.newArrayList;

import java.util.List;
import java.util.Properties;

import lombok.Getter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kafka")
@Getter
public class KafkaProperties {

  private final Properties consumerProperties = new Properties() {

    {
      put("zookeeper.connect", "localhost:21818");
      put("zookeeper.connection.timeout.ms", "1000000");
      put("group.id", "1");
      put("broker.id", "0");
    }

  };

  private final List<String> topics = newArrayList("test");

}