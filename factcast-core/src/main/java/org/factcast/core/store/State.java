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
package org.factcast.core.store;

import java.util.List;
import lombok.Data;
import org.factcast.core.spec.FactSpec;

@Data
public class State {
  List<FactSpec> specs;

  long serialOfLastMatchingFact;

  public static State of(List<FactSpec> specs, long lastSerial) {
    return new State().serialOfLastMatchingFact(lastSerial).specs(specs);
  }
}
