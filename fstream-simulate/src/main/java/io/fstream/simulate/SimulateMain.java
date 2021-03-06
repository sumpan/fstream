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
import io.fstream.core.config.Main;
import io.fstream.simulate.core.Simulator;
import lombok.val;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Application entry point.
 */
@Main
public class SimulateMain {

  @Autowired(required = false)
  Simulator simulator;

  public static void main(String... args) throws Exception {
    SpringApplication.run(SimulateMain.class, args);

    out.println("\n\n*** Running simulate. Press CTLR+C to shutdown\n\n");
  }

  @EventListener
  public void start(ApplicationReadyEvent ready) {
    val active = simulator != null;
    if (active) {
      simulator.simulate();
    }
  }

}