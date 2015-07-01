/*
 * Copyright (c) 2015 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.simulate.actor;

import io.fstream.simulate.config.SimulateProperties;
import io.fstream.simulate.message.ActiveInstruments;
import io.fstream.simulate.util.SpringExtension;
import lombok.NonNull;
import lombok.Setter;
import lombok.val;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import scala.concurrent.duration.FiniteDuration;
import akka.actor.UntypedActor;

@Setter
public abstract class BaseActor extends UntypedActor {

  /**
   * Dependencies.
   */
  @Autowired
  protected SimulateProperties properties;
  @Autowired
  protected SpringExtension spring;

  /**
   * State.
   */
  protected ActiveInstruments activeInstruments = new ActiveInstruments();

  protected static DateTime getSimulationTime() {
    return DateTime.now();
  }

  @NonNull
  protected <T> void scheduleSelfOnce(T message, FiniteDuration duration) {
    val scheduler = getContext().system().scheduler();
    scheduler.scheduleOnce(duration, getSelf(), message, getContext().dispatcher(), null);
  }

}