/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.olingo.commons.core.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.apache.olingo.commons.api.Constants;

public class JSONODataErrorDeserializer extends AbstractJsonDeserializer<JSONODataErrorImpl> {

  @Override
  protected JSONODataErrorImpl doDeserialize(final JsonParser parser, final DeserializationContext ctxt)
          throws IOException, JsonProcessingException {

    final JSONODataErrorImpl error = new JSONODataErrorImpl();

    final ObjectNode tree = parser.getCodec().readTree(parser);
    if (tree.has(jsonError)) {
      final JsonNode errorNode = tree.get(jsonError);

      if (errorNode.has(Constants.ERROR_CODE)) {
        error.setCode(errorNode.get(Constants.ERROR_CODE).textValue());
      }
      if (errorNode.has(Constants.ERROR_MESSAGE)) {
        final JsonNode message = errorNode.get(Constants.ERROR_MESSAGE);
        if (message.isValueNode()) {
          error.setMessage(message.textValue());
        } else if (message.isObject()) {
          error.setMessage(message.get(Constants.VALUE).asText());
        }
      }
      if (errorNode.has(Constants.ERROR_TARGET)) {
        error.setTarget(errorNode.get(Constants.ERROR_TARGET).textValue());
      }
    }

    return error;
  }

}
