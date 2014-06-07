/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */
package io.fstream.test;

import static java.lang.System.out;
import io.fstream.test.config.TestConfig;

import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Application entry point.
 */
public class TestMain {

  public static void main(String... args) throws Exception {
    new SpringApplicationBuilder()
        .sources(TestConfig.class)
        .run(args);

    out.println("\n\n*** Running test. Press CTLR+C to shutdown\n\n");
    Thread.sleep(Long.MAX_VALUE);
  }

}
