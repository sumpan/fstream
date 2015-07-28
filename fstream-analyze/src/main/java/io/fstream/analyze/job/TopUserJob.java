/*
 * Copyright (c) 2015 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.analyze.job;

import static io.fstream.core.model.topic.Topic.METRICS;
import io.fstream.analyze.core.Job;
import io.fstream.analyze.core.JobContext;
import io.fstream.analyze.kafka.KafkaProducer;
import io.fstream.core.model.topic.Topic;

import java.util.Set;

import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.pool2.ObjectPool;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.streaming.Time;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaPairReceiverInputDStream;
import org.springframework.stereotype.Component;

/**
 * Base class of "Top N User by <fact>" calculation jobs.
 */
@Slf4j
@Component
public abstract class TopUserJob<T extends Comparable<T>> extends Job {

  /**
   * Output metric identifier.
   */
  private final int metricId;

  /**
   * Top N.
   */
  private final int n;

  public TopUserJob(int metricId, Set<Topic> topics, int n, JobContext jobContext) {
    super(topics, jobContext);
    this.metricId = metricId;
    this.n = n;
  }

  @Override
  protected void plan(JavaPairReceiverInputDStream<String, String> kafkaStream) {
    log.info("[{}:{}] DStream element count: {}", metricId, topics, kafkaStream.count());
    val calculation = planCalculation(kafkaStream);
    planBatches(calculation);
  }

  /**
   * Template method.
   * <p>
   * To filled in by sub-classes.
   */
  protected abstract JavaPairDStream<String, T> planCalculation(JavaPairReceiverInputDStream<String, String> kafkaStream);

  protected void planBatches(JavaPairDStream<String, T> calculation) {
    // Closure safety
    val metricId = this.metricId;
    val topics = this.topics;
    val n = this.n;
    val pool = jobContext.getPool();

    // Sort and top
    calculation.foreachRDD((rdd, time) -> {
      log.info("[{}:{}] RDD partition count: {}, RDD element count: {}",
          metricId, topics, rdd.partitions().size(), rdd.count());

      analyzeBatch(rdd, time, metricId, n, pool);
      return null;
    });
  }

  @SneakyThrows
  protected static <T extends Comparable<T>> void analyzeBatch(JavaPairRDD<String, T> rdd, Time time,
      int metricId, int n, Broadcast<ObjectPool<KafkaProducer>> pool) {
    // Find top N by value descending
    val tuples = rdd.top(n, valueDescending());

    // We use a pool here to amortize the cost of the Kafka socket connections over the entire job.
    val producer = pool.getValue().borrowObject();
    try {
      val metric = metricEvent(metricId, time, tuples);
      producer.send(METRICS, metric);
    } finally {
      pool.getValue().returnObject(producer);
    }
  }

}
