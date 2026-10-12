package com.merine.rebuild.agent.datasource;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据源目标的网络边界：连接本身具有服务端网络访问能力，不能让表单用户随意探测任意地址。
 *
 * <p>允许范围完全来自部署配置（{@link DataSourceProperties#allowedNetworks()} 与
 * {@link DataSourceProperties#allowedPorts()}），表单用户改不了。校验对象是 DNS 解析后的
 * <strong>每个</strong>地址，而不是输入字符串；通过后连接使用解析出的数字地址，
 * 不在连接时重新解析，避免校验与连接之间的 DNS 重绑定。
 */
@Component
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceTargetGuard {
    private final List<IpNetwork> networks;
    private final DataSourceProperties properties;

    public DataSourceTargetGuard(DataSourceProperties properties) {
        this.properties = properties;
        this.networks = properties.allowedNetworks().stream()
                .filter(text -> text != null && !text.isBlank())
                .map(IpNetwork::parse)
                .toList();
    }

    /** 已解析并验证过的目标：连接必须使用这里的数字地址，不得再次解析主机名。 */
    public record ResolvedTarget(InetAddress address, int port, boolean loopback, String host) {
        @Override
        public String toString() {
            return "ResolvedTarget[redacted]";
        }
    }

    /** 目标被部署范围拒绝；消息不含凭据，可以直接回给管理员。 */
    public static class TargetDeniedException extends RuntimeException {
        public TargetDeniedException(String message) {
            super(message);
        }
    }

    public ResolvedTarget resolve(String host, int port) {
        if (!properties.allowedPorts().contains(port)) {
            throw new TargetDeniedException("目标端口不在服务端允许范围内");
        }
        if (networks.isEmpty()) {
            throw new TargetDeniedException("服务端未配置数据源允许访问的网段，请联系部署管理员");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException error) {
            throw new TargetDeniedException("无法解析数据库主机地址");
        }
        if (addresses.length == 0) {
            throw new TargetDeniedException("无法解析数据库主机地址");
        }
        for (InetAddress address : addresses) {
            if (networks.stream().noneMatch(network -> network.contains(address))) {
                throw new TargetDeniedException("目标主机解析到未授权的网络地址");
            }
        }
        InetAddress chosen = addresses[0];
        return new ResolvedTarget(chosen, port, chosen.isLoopbackAddress(), host);
    }

    /** IPv4/IPv6 网段；配置项只接受 IP 字面量或 CIDR，不接受域名，避免配置期隐式解析。 */
    private static final class IpNetwork {
        private final byte[] network;
        private final int prefixBits;

        private IpNetwork(byte[] network, int prefixBits) {
            this.network = network;
            this.prefixBits = prefixBits;
        }

        static IpNetwork parse(String raw) {
            String text = raw.trim();
            int slash = text.indexOf('/');
            String addressPart = slash < 0 ? text : text.substring(0, slash);
            byte[] bytes = literal(addressPart);
            int prefix = bytes.length * 8;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(text.substring(slash + 1).trim());
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("允许网段配置不合法：" + raw);
                }
                if (prefix < 0 || prefix > bytes.length * 8) {
                    throw new IllegalArgumentException("允许网段配置不合法：" + raw);
                }
            }
            return new IpNetwork(bytes, prefix);
        }

        boolean contains(InetAddress candidate) {
            byte[] bytes = normalize(candidate.getAddress());
            if (bytes.length != network.length) return false;
            for (int index = 0; index < prefixBits / 8; index += 1) {
                if (network[index] != bytes[index]) return false;
            }
            int remainder = prefixBits % 8;
            if (remainder > 0) {
                int mask = 0xFF << (8 - remainder);
                if ((network[prefixBits / 8] & mask) != (bytes[prefixBits / 8] & mask)) return false;
            }
            return true;
        }

        /** 只解析 IP 字面量：IPv6 含冒号；IPv4 逐段校验，避免 InetAddress 把纯数字当 IP 或触发 DNS。 */
        private static byte[] literal(String text) {
            if (text.contains(":")) {
                try {
                    InetAddress address = InetAddress.getByName(text);
                    if (!(address instanceof Inet6Address)) {
                        throw new IllegalArgumentException("允许网段配置必须是 IP 字面量：" + text);
                    }
                    return address.getAddress();
                } catch (UnknownHostException error) {
                    throw new IllegalArgumentException("允许网段配置必须是 IP 字面量：" + text);
                }
            }
            String[] parts = text.split("\\.", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("允许网段配置必须是 IP 字面量：" + text);
            }
            byte[] bytes = new byte[4];
            for (int index = 0; index < 4; index += 1) {
                String part = parts[index];
                if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                    throw new IllegalArgumentException("允许网段配置必须是 IP 字面量：" + text);
                }
                int value = Integer.parseInt(part);
                if (value > 255) {
                    throw new IllegalArgumentException("允许网段配置必须是 IP 字面量：" + text);
                }
                bytes[index] = (byte) value;
            }
            return bytes;
        }

        /** 少数 JDK 会把 IPv4-mapped IPv6 保留为 16 字节，比较前还原成 IPv4。 */
        private static byte[] normalize(byte[] bytes) {
            if (bytes.length == 16 && isMapped(bytes)) {
                return new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]};
            }
            return bytes;
        }

        private static boolean isMapped(byte[] bytes) {
            for (int index = 0; index < 10; index += 1) {
                if (bytes[index] != 0) return false;
            }
            return bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF;
        }
    }
}
