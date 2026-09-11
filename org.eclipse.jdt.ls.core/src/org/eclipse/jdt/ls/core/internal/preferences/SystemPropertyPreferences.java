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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.MapFlattener;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Lets any preference normally sent by the client as an LSP setting be supplied instead as a JVM
 * system property on the server's own command line:
 *
 * <pre>
 * jdtls --jvm-arg=-Djava.import.gradle.version=9.4.1
 * </pre>
 *
 * <p>
 * Some clients have no way to express LSP settings at all - Claude Code, for instance, just runs a
 * {@code jdtls} found on {@code PATH} - so without this the server runs with defaults there no
 * matter what the user wants. It also stops
 * {@code java.import.generatesMetadataFilesAtProjectRoot} being a special case, that one having
 * always been read straight from the system properties and settable over LSP by no client at all.
 * </p>
 *
 * <p>
 * A property wins over the same key sent by the client, so dotfile-managed config.ini settings
 * override per-editor ones - the same precedence the hand-written overrides in {@link Preferences}
 * used before this generalised them.
 * </p>
 *
 * <p>
 * Values are strings, which the {@link MapFlattener} accessors already coerce to booleans, ints and
 * comma-separated lists. A value starting with <code>{</code> or <code>[</code> is parsed as JSON
 * first, so structured preferences work too:
 * </p>
 *
 * <pre>
 * -Djava.configuration.runtimes=[{"name":"JavaSE-26","path":"/path/to/jdk","default":true}]
 * </pre>
 */
public final class SystemPropertyPreferences {

	private static Set<String> preferenceKeys;

	private SystemPropertyPreferences() {
		//Utility class
	}

	/**
	 * Returns a configuration with every preference set as a system property applied over it. The
	 * argument is never modified; it is returned as-is when no such property is set.
	 */
	public static Map<String, Object> withOverrides(Map<String, Object> configuration) {
		Map<String, Object> overrides = collectProperties();
		if (overrides.isEmpty()) {
			return configuration;
		}
		Map<String, Object> merged = new HashMap<>(configuration);
		//Flat, not nested: MapFlattener resolves the dotted key before descending, so a flat entry
		//wins whichever shape the client sent the same setting in.
		merged.putAll(overrides);
		return merged;
	}

	/**
	 * Applies system properties to preferences built without the client sending any settings at
	 * all - the case this exists for, since then nothing else runs them through
	 * {@link Preferences#updateFrom}.
	 */
	public static Preferences applyTo(Preferences preferences) {
		return Preferences.updateFrom(preferences, new HashMap<>());
	}

	private static Map<String, Object> collectProperties() {
		Map<String, Object> overrides = new HashMap<>();
		for (String key : preferenceKeys()) {
			String property = System.getProperty(key);
			if (property != null) {
				overrides.put(key, parse(key, property));
			}
		}
		return overrides;
	}

	private static Object parse(String key, String property) {
		String trimmed = property.trim();
		if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
			//Leave scalars as strings: the MapFlattener accessors coerce them, and JSON parsing
			//would turn a version like "9.4" into a number that getString() then refuses.
			return property;
		}
		try {
			return new Gson().fromJson(trimmed, Object.class);
		} catch (JsonSyntaxException e) {
			JavaLanguageServerPlugin.logError("System property " + key + " is not valid JSON, using it as a string: " + e.getMessage());
			return property;
		}
	}

	/**
	 * Every preference key declared by {@link Preferences}, discovered reflectively so that keys
	 * added upstream are supported without touching this class.
	 */
	private static synchronized Set<String> preferenceKeys() {
		if (preferenceKeys == null) {
			preferenceKeys = Collections.unmodifiableSet(
					Arrays.stream(Preferences.class.getDeclaredFields())
							.filter(field -> Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()))
							.filter(field -> field.getType() == String.class)
							.map(SystemPropertyPreferences::valueOf)
							.filter(value -> value != null && value.startsWith("java."))
							.collect(Collectors.toCollection(TreeSet::new)));
		}
		return preferenceKeys;
	}

	private static String valueOf(Field field) {
		try {
			field.setAccessible(true);
			return (String) field.get(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
