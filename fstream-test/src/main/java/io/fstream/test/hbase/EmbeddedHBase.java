/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.test.hbase;

import static io.fstream.core.util.ZooKeepers.parseZkPort;
import static org.apache.hadoop.hbase.HConstants.ZOOKEEPER_CLIENT_PORT;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;

@Slf4j
@RequiredArgsConstructor
public class EmbeddedHBase {

  /**
   * Configuration.
   */
  private final String zkConnect;

  /**
   * State.
   */
  private HBaseTestingUtility utility;

  public void startUp() throws Exception {
    val config = createConfiguration();
    utility = new HBaseTestingUtility(config);

    log.info("Starting mini-cluster...");
    utility.startMiniCluster();
    log.info("Finished starting mini-cluster");
  }

  public void shutDown() throws Exception {
    log.info("Shutting down mini-cluster...");
    utility.shutdownMiniCluster();
    log.info("Finished shutting down mini-cluster");
  }

  private Configuration createConfiguration() {
    val clientPort = parseZkPort(zkConnect);

    log.info("Creating configuation with zkConnect = '{}'", zkConnect);
    val config = HBaseConfiguration.create();
    config.set("test." + ZOOKEEPER_CLIENT_PORT, Integer.toString(clientPort));

    return config;
  }

}
