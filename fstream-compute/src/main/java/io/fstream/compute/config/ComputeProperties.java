/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.compute.config;

import static com.google.common.collect.Lists.newArrayList;
import io.fstream.core.model.definition.Alert;
import io.fstream.core.model.definition.Metric;

import java.util.List;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("compute")
public class ComputeProperties {

  private List<String> statements = newArrayList();
  private List<Alert> alerts = newArrayList();
  private List<Metric> metrics = newArrayList();

}