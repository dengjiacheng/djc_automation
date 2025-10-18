package com.automation.domain.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 验证 LegacyVariableResolver 在设备上对不同 Map 实现的兼容性。
 */
@RunWith(AndroidJUnit4.class)
public class LegacyVariableResolverSmokeTest {

    private static final String TAG = "LegacyResolverSmoke";

    @Test
    public void resolverHandlesNestedMaps() {
        Map<String, Object> nested = new LinkedHashMap<>();
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", "abc");
        session.put("count", 42L);
        nested.put("session", session);

        LegacyVariableResolver resolver = LegacyVariableResolver.from(nested);
        Log.i(TAG, "resolverHandlesNestedMaps -> mapType=" + nested.getClass().getName());

        assertEquals("abc", resolver.resolve("session.id"));
        assertEquals("42", resolver.resolve("session.count"));
        assertNull(resolver.resolve("session.missing"));
        assertNull(resolver.resolve("missing.any"));
    }

    @Test
    public void resolverHandlesUnmodifiableMaps() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("token", "xyz");
        Map<String, Object> wrapped = Collections.unmodifiableMap(inner);
        Map<String, Object> outer = new HashMap<>();
        outer.put("auth", wrapped);
        Map<String, Object> snapshot = Collections.unmodifiableMap(outer);

        LegacyVariableResolver resolver = LegacyVariableResolver.from(snapshot);
        Log.i(TAG, "resolverHandlesUnmodifiableMaps -> mapType=" + snapshot.getClass().getName());

        assertNotNull(resolver);
        assertEquals("xyz", resolver.resolve("auth.token"));
    }

    @Test
    public void resolverHandlesComplexNestedStructure() {
        Map<String, Object> account = new HashMap<>();
        account.put("name", "tester");
        account.put("age", 28);

        Map<String, Object> session = new HashMap<>();
        session.put("id", "session-123");
        session.put("user", account);

        Map<String, Object> root = new HashMap<>();
        root.put("session", session);
        root.put("debug", Boolean.TRUE);

        LegacyVariableResolver resolver = LegacyVariableResolver.from(Collections.unmodifiableMap(root));
        Log.i(TAG, "resolverHandlesComplexNestedStructure -> rootType=" + root.getClass().getName());

        assertEquals("session-123", resolver.resolve("session.id"));
        assertEquals("tester", resolver.resolve("session.user.name"));
        assertEquals("28", resolver.resolve("session.user.age"));
        assertEquals("true", resolver.resolve("debug"));
    }
}
