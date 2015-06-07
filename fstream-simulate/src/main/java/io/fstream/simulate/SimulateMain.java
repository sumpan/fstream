/* 
 * Copyright (c) 2014 fStream. All Rights Reserved.
 * 
 * Project and contact information: https://bitbucket.org/fstream/fstream
 * 
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */
package io.fstream.simulate;

import static java.lang.System.out;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Application entry point.
 */
@SpringBootApplication
public class SimulateMain {

  public static void main(String... args) throws Exception {
    new SpringApplicationBuilder()
        .sources(SimulateMain.class)
        .run(args);

    out.println("\n\n*** Running simulate. Press CTLR+C to shutdown\n\n");
    Thread.sleep(Long.MAX_VALUE);
  }

}