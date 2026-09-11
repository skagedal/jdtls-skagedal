/*******************************************************************************
 * Copyright (c) 2026 Simon Kågedal Reimer and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.ls.core.internal.RuntimeEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Settings that clients can send over LSP must also be settable as JVM system properties, for
 * clients that have no way to send settings at all.
 */
public class SystemPropertyPreferencesTest {

	private final List<String> setProperties = new ArrayList<>();

	@AfterEach
	public void clearProperties() {
		setProperties.forEach(System::clearProperty);
		setProperties.clear();
	}

	private void givenProperty(String key, String value) {
		setProperties.add(key);
		System.setProperty(key, value);
	}

	@Test
	public void appliesScalarPropertyWhenClientSendsNothing() {
		givenProperty(Preferences.GRADLE_VERSION, "9.4.1");

		Preferences preferences = Preferences.createFrom(new HashMap<>());

		assertEquals("9.4.1", preferences.getGradleVersion());
	}

	@Test
	public void parsesJsonPropertyIntoStructuredPreference() {
		givenProperty(Preferences.JAVA_CONFIGURATION_RUNTIMES,
				"[{\"name\": \"JavaSE-21\", \"path\": \"/tmp/jdk-21\", \"default\": true}]");

		Preferences preferences = Preferences.createFrom(new HashMap<>());

		assertEquals(1, preferences.getRuntimes().size());
		RuntimeEnvironment runtime = preferences.getRuntimes().iterator().next();
		assertEquals("JavaSE-21", runtime.getName());
		assertTrue(runtime.isDefault());
	}

	/**
	 * A version like "9.4" must not be parsed as a number, which the string accessors would then
	 * refuse.
	 */
	@Test
	public void leavesNumericLookingScalarsAsStrings() {
		givenProperty(Preferences.GRADLE_VERSION, "9.4");

		Preferences preferences = Preferences.createFrom(new HashMap<>());

		assertEquals("9.4", preferences.getGradleVersion());
	}

	@Test
	public void propertyWinsOverClientSetting() {
		givenProperty(Preferences.GRADLE_VERSION, "9.4.1");
		Map<String, Object> configuration = new HashMap<>();
		configuration.put(Preferences.GRADLE_VERSION, "8.5");

		Preferences preferences = Preferences.createFrom(configuration);

		assertEquals("9.4.1", preferences.getGradleVersion());
	}

	@Test
	public void clientSettingSurvivesWhenNoPropertyIsSet() {
		Map<String, Object> configuration = new HashMap<>();
		configuration.put(Preferences.GRADLE_VERSION, "8.5");

		Preferences preferences = Preferences.createFrom(configuration);

		assertEquals("8.5", preferences.getGradleVersion());
	}

	/**
	 * A client that sends no settings at all never reaches {@link Preferences#createFrom}, so the
	 * preference manager has its own seam.
	 */
	@Test
	public void appliesToPreferencesBuiltWithoutAnyClientSettings() {
		givenProperty(Preferences.JAVA_FORMATTER_URL, "config/spotless/eclipse-formatter.xml");

		Preferences preferences = new PreferenceManager().getPreferences();

		assertNotNull(preferences);
		assertEquals("config/spotless/eclipse-formatter.xml", preferences.getFormatterUrl());
	}
}
