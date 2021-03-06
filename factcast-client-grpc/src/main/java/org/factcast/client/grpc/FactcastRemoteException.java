/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.client.grpc;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import java.lang.reflect.Constructor;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FactcastRemoteException {

  public static RuntimeException from(StatusRuntimeException e) {

    try {
      Metadata md = e.getTrailers();
      assert md != null;

      byte[] msgBytes = md.get(Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER));
      byte[] excBytes = md.get(Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER));

      if (excBytes != null) {
        Class<?> exc = Class.forName(new String(excBytes));
        Constructor<?> constructor = exc.getConstructor(String.class);
        String msg = new String(Objects.requireNonNull(msgBytes));
        return (RuntimeException) constructor.newInstance(msg);
      }

    } catch (Throwable ex) {
      log.warn("Something went wrong materializing an exception", ex);
      // but throw e anyway
    }
    return e;
  }
}
