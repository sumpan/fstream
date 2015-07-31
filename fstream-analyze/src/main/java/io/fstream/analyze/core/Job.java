/*
 * Copyright (c) 2015 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.analyze.core;

import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Strings.repeat;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.stream.Collectors.toMap;
import io.fstream.core.model.event.MetricEvent;
import io.fstream.core.model.topic.Topic;

import java.util.Map;
import java.util.Set;

import kafka.serializer.StringDecoder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.Time;
import org.apache.spark.streaming.api.java.JavaPairReceiverInputDStream;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.joda.time.DateTime;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Represents streaming analytical job.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class Job {

  /** The topics to read from. */
  @NonNull
  protected final Set<Topic> topics;

  /** Handle to context the job is running in. */
  @NonNull
  protected JobContext jobContext;

  public void register() {
    log.info(repeat("-", 100));
    log.info("Creating DStream from topics '{}'...", topics);
    log.info(repeat("-", 100));

    // Setup the input to the job
    val kafkaStream = createKafkaStream();

    // Plan the job behavior
    plan(kafkaStream);
  }

  /**
   * Template method
   */
  protected abstract void plan(JavaPairReceiverInputDStream<String, String> kafkaStream);

  /**
   * @see https://spark.apache.org/docs/1.4.1/streaming-kafka-integration.html
   */
  private JavaPairReceiverInputDStream<String, String> createKafkaStream() {
    // Short-hands
    val keyTypeClass = String.class;
    val valueTypeClass = String.class;
    val keyDecoderClass = StringDecoder.class;
    val valueDecoderClass = StringDecoder.class;
    val storageLevel = StorageLevel.MEMORY_AND_DISK_SER_2();

    // Resolve
    val kafkaParams = resolveKafkaParams();
    val partitions = resolveTopicPartitions();

    return KafkaUtils.createStream(jobContext.getStreamingContext(),
        keyTypeClass, valueTypeClass, keyDecoderClass, valueDecoderClass, kafkaParams, partitions, storageLevel);
  }

  private Map<String, Integer> resolveTopicPartitions() {
    return topics.stream().collect(toMap(Topic::getId, (x) -> 1));
  }

  private Map<String, String> resolveKafkaParams() {
    val consumerProperties = jobContext.getKafka().getConsumerProperties();
    val overrideGroupId = resolveKafkaConsumerGroupId(consumerProperties);

    log.info("Creating kafka params with group id: {}", overrideGroupId);
    val kafkaParams = newHashMap(consumerProperties);
    kafkaParams.put(ConsumerConfig.GROUP_ID_CONFIG, overrideGroupId);
    return kafkaParams;
  }

  private String resolveKafkaConsumerGroupId(Map<String, String> consumerProperties) {
    // Ensure Kafka consumers from different jobs are namespaced and therefore isolated
    val groupId = consumerProperties.get(ConsumerConfig.GROUP_ID_CONFIG);
    val jobId = UPPER_CAMEL.to(LOWER_HYPHEN, getClass().getSimpleName());

    return groupId + "-" + jobId;
  }

  /**
   * Utilities.
   */

  protected static Set<Topic> topics(@NonNull Topic... topics) {
    return ImmutableSet.copyOf(topics);
  }

  protected static Map<String, Object> record(String k1, Object v1, String k2, Object v2) {
    return ImmutableMap.of(k1, v1, k2, v2);
  }

  protected static MetricEvent metric(Time time, int id, Object data) {
    return new MetricEvent(new DateTime(time.milliseconds()), id, data);
  }

}