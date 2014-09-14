/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.compute.config;

import static com.google.common.collect.Maps.newHashMap;

import java.util.Map;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Storm specific configuration properties.
 */
@Data
@Configuration
@ConfigurationProperties("storm")
public class StormProperties {

  private boolean local;

  private Map<String, String> properties = newHashMap();

}
