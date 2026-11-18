package com.example.rag.config;

import java.util.regex.Pattern;

/**
 * Tenant ids end up inside vector-store filter expressions, so they are strictly
 * validated to rule out filter injection.
 */
public final class Tenants {

    public static final String HEADER = "X-Tenant-Id";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private Tenants() {
    }

    public static String require(String tenantId) {
        if (tenantId == null || !VALID.matcher(tenantId).matches()) {
            throw new IllegalArgumentException(
                    "Invalid tenant id: use 1-64 letters, digits, '_' or '-'");
        }
        return tenantId;
    }
}
