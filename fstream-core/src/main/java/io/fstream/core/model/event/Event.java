/*
 * Copyright (c) 2014 fStream. All Rights Reserved.
 *
 * Project and contact information: https://bitbucket.org/fstream/fstream
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and confidential.
 */

package io.fstream.core.model.event;

import java.io.Serializable;

import org.joda.time.DateTime;

/**
 * Central event abstraction in the system.
 */
public interface Event extends Serializable {

  DateTime getDateTime();

  EventType getType();

}
