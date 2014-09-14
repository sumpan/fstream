/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.compute.service;

import static com.google.common.collect.Maps.newConcurrentMap;
import io.fstream.compute.storm.StormJobExecutor;
import io.fstream.compute.storm.StormJobFactory;
import io.fstream.core.model.definition.Alert;
import io.fstream.core.model.definition.Metric;
import io.fstream.core.model.state.State;
import io.fstream.core.model.state.StateListener;
import io.fstream.core.service.StateService;

import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Compute job submission entry point.
 */
@Slf4j
@Service
public class ComputeService implements StateListener {

  /**
   * Dependencies.
   */
  @Autowired
  private StateService stateService;
  @Autowired
  private StormJobFactory jobFactory;
  @Autowired
  private StormJobExecutor jobExecutor;

  /**
   * State.
   */
  private final Map<Integer, Alert> alerts = newConcurrentMap();
  private final Map<Integer, Metric> metrics = newConcurrentMap();

  @PostConstruct
  @SneakyThrows
  public void initialize() {
    log.info("Registering for state updates...");
    stateService.initialize();
    stateService.addListener(this);

    // Bootstrap initial job
    val state = stateService.getState();
    val job = jobFactory.createJob(state);

    // TODO: Submitting more than one topology per topic seems to stop the flow of events. Not sure why this works in
    // simulation mode...
    // onUpdate(state);
    jobExecutor.execute(job);
  }

  @Override
  @SneakyThrows
  public void onUpdate(@NonNull State nextState) {
    // TODO: Remove, see above
    val todo = true;
    if (todo) {
      log.warn("**** IGNORING UPDATE !!!");
      return;
    }

    // TODO: Support removal of definitions
    log.info("Updating state...");
    val symbols = nextState.getSymbols();
    val common = nextState.getStatements();

    log.info("Submitting alerts...");
    submitAlerts(nextState.getAlerts(), symbols, common);

    log.info("Submitting metrics...");
    submitMetrics(nextState.getMetrics(), symbols, common);
  }

  private void submitAlerts(List<Alert> alerts, List<String> symbols, List<String> common) {
    for (val alert : alerts) {
      if (alertExists(alert)) {
        // Skip
        continue;
      }

      submitAlert(alert, symbols, common);
    }
  }

  private void submitAlert(Alert alert, List<String> symbols, List<String> common) {
    val alertJob = jobFactory.createAlertJob(alert, symbols, common);

    log.info("Submitting storm alert topology: '{}'...", alert.getName());
    jobExecutor.execute(alertJob);

    alerts.put(alert.getId(), alert);
  }

  private void submitMetrics(List<Metric> metrics, List<String> symbols, List<String> common) {
    for (val metric : metrics) {
      if (metricExists(metric)) {
        // Skip
        continue;
      }

      submitMetric(metric, symbols, common);
    }
  }

  private void submitMetric(Metric metric, List<String> symbols, List<String> common) {
    val metricJob = jobFactory.createMetricJob(metric, symbols, common);

    log.info("Submitting storm metric topology: '{}'...", metric.getName());
    jobExecutor.execute(metricJob);

    metrics.put(metric.getId(), metric);
  }

  private boolean alertExists(Alert alert) {
    return alerts.containsKey(alert.getId());
  }

  private boolean metricExists(Metric metric) {
    return metrics.containsKey(metric.getId());
  }

}
