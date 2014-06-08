/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.web.controller;

import io.fstream.core.model.definition.Alert;
import io.fstream.core.service.StateService;
import lombok.Setter;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class RegistrationController {

  /**
   * Dependencies.
   */
  @Setter
  @Autowired
  protected StateService stateService;

  @MessageMapping("/register")
  @SendTo("/topic/commands")
  public boolean register(Alert alert) throws Exception {
    log.info("Registering '{}'", alert);
    val state = stateService.getState();
    state.getAlerts().add(alert);

    stateService.setState(state);

    return true;
  }

}
