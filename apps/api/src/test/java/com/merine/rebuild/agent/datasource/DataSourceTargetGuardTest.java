package com.merine.rebuild.agent.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 目标地址边界的纯函数回归：只接受部署配置允许的端口与解析后地址。 */
@DisplayName("数据源目标网络边界")
class DataSourceTargetGuardTest {
    private static DataSourceTargetGuard guard(List<String> networks) {
        return new DataSourceTargetGuard(new DataSourceProperties(
                networks, List.of(3306), 1000, 2000, 2, 100, 50,
                DataSourceProperties.TlsMode.REQUIRED));
    }

    @Test
    @DisplayName("CIDR 内的地址通过，范围外地址与端口被拒绝")
    void cidrAndPortBounds() {
        DataSourceTargetGuard guard = guard(List.of("10.0.0.0/8"));
        assertThat(guard.resolve("10.1.2.3", 3306).port()).isEqualTo(3306);
        assertThatThrownBy(() -> guard.resolve("11.0.0.1", 3306))
                .isInstanceOf(DataSourceTargetGuard.TargetDeniedException.class)
                .hasMessageContaining("未授权");
        assertThatThrownBy(() -> guard.resolve("10.1.2.3", 3307))
                .isInstanceOf(DataSourceTargetGuard.TargetDeniedException.class)
                .hasMessageContaining("端口");
    }

    @Test
    @DisplayName("未配置网段时 fail closed，回环地址也要显式允许")
    void emptyAllowlistDeniesEverything() {
        DataSourceTargetGuard guard = guard(List.of());
        assertThatThrownBy(() -> guard.resolve("127.0.0.1", 3306))
                .isInstanceOf(DataSourceTargetGuard.TargetDeniedException.class)
                .hasMessageContaining("未配置");
        assertThat(guard(List.of("127.0.0.0/8")).resolve("127.0.0.1", 3306).loopback()).isTrue();
    }

    @Test
    @DisplayName("IPv6 字面量与单地址配置可用")
    void ipv6AndSingleAddress() {
        assertThat(guard(List.of("::1/128")).resolve("::1", 3306).loopback()).isTrue();
        assertThat(guard(List.of("192.0.2.10")).resolve("192.0.2.10", 3306).port()).isEqualTo(3306);
    }

    @Test
    @DisplayName("配置里只接受 IP 字面量，域名与非法前缀直接拒绝")
    void allowlistRejectsHostnames() {
        assertThatThrownBy(() -> guard(List.of("db.internal")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard(List.of("10.0.0.0/33")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
