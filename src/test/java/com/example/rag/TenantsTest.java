package com.example.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.rag.config.Tenants;
import org.junit.jupiter.api.Test;

class TenantsTest {

    @Test
    void acceptsSafeIds() {
        assertThat(Tenants.require("acme-corp_01")).isEqualTo("acme-corp_01");
    }

    @Test
    void rejectsFilterInjection() {
        assertThatThrownBy(() -> Tenants.require("acme' || tenantId != 'x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenants.require(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
