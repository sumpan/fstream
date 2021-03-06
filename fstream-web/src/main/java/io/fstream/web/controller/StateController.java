/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.web.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import io.fstream.core.model.state.State;
import io.fstream.core.service.StateService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/state")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class StateController {

  /**
   * Dependencies.
   */
  @NonNull
  private final StateService stateService;

  @RequestMapping(method = GET)
  public @ResponseBody State getState() {
    return stateService.getState();
  }

}
