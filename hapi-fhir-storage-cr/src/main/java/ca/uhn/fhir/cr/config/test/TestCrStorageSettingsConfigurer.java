/*-
 * #%L
 * HAPI FHIR - Clinical Reasoning
 * %%
 * Copyright (C) 2014 - 2025 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.fhir.cr.config.test;

import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;

@Deprecated(since = "8.1.4", forRemoval = true)
public class TestCrStorageSettingsConfigurer {

	private final JpaStorageSettings myStorageSettings;

	public TestCrStorageSettingsConfigurer(JpaStorageSettings theStorageSettings) {
		myStorageSettings = theStorageSettings;
	}

	public void setUpConfiguration() {
		myStorageSettings.setAllowExternalReferences(true);
		myStorageSettings.setEnforceReferentialIntegrityOnWrite(false);
		myStorageSettings.setEnforceReferenceTargetTypes(false);
		myStorageSettings.setResourceClientIdStrategy(JpaStorageSettings.ClientIdStrategyEnum.ANY);
	}

}
