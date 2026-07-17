package tj.metro.dushanbe.integration.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validates outbound webhook destinations at configuration time and immediately before delivery.
 * DNS is deliberately checked again for every delivery because a previously public hostname can
 * later resolve to a private or link-local address.
 */
@Component
public class WebhookTargetPolicy {

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HostResolver hostResolver;

    public WebhookTargetPolicy() {
        this(InetAddress::getAllByName);
    }

    WebhookTargetPolicy(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    /** Parses the URL and rejects unsafe literal destinations before it is persisted. */
    public URI validateForConfiguration(String rawTarget) {
        URI target = parse(rawTarget);
        if (looksLikeIpLiteral(target.getHost())) {
            validateResolvedAddresses(target.getHost());
        }
        return target;
    }

    /** Resolves and validates every address immediately before the HTTP request. */
    public URI validateForDispatch(String rawTarget) {
        URI target = parse(rawTarget);
        validateResolvedAddresses(target.getHost());
        return target;
    }

    private static URI parse(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            throw new UnsafeTargetException("Webhook target is required");
        }
        final URI target;
        try {
            target = new URI(rawTarget.trim()).normalize();
        } catch (URISyntaxException exception) {
            throw new UnsafeTargetException("Webhook target is not a valid URI", exception);
        }
        if (!target.isAbsolute() || !"https".equalsIgnoreCase(target.getScheme())) {
            throw new UnsafeTargetException("Webhook target must use HTTPS");
        }
        if (target.getHost() == null || target.getHost().isBlank()) {
            throw new UnsafeTargetException("Webhook target must contain a host");
        }
        if (target.getUserInfo() != null) {
            throw new UnsafeTargetException("Webhook target must not contain user credentials");
        }
        if (target.getFragment() != null) {
            throw new UnsafeTargetException("Webhook target must not contain a fragment");
        }
        if (target.getPort() != -1 && target.getPort() != 443) {
            throw new UnsafeTargetException("Webhook target must use port 443");
        }
        String host = target.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal") || host.endsWith(".home.arpa")) {
            throw new UnsafeTargetException("Local webhook hosts are not allowed");
        }
        return target;
    }

    private void validateResolvedAddresses(String host) {
        final InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(host);
        } catch (UnknownHostException exception) {
            throw new UnsafeTargetException("Webhook host cannot be resolved", exception);
        }
        if (addresses == null || addresses.length == 0) {
            throw new UnsafeTargetException("Webhook host did not resolve to an address");
        }
        for (InetAddress address : addresses) {
            if (address == null || forbidden(address)) {
                throw new UnsafeTargetException("Webhook host resolves to a non-public address");
            }
        }
    }

    private static boolean looksLikeIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
    }

    private static boolean forbidden(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0 || first == 10 || first == 127 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 198 && (second == 18 || second == 19));
        }
        if (bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            // IPv6 ULA, documentation, Teredo and 6to4 transition ranges are not valid
            // direct webhook destinations. Rejecting transition ranges also prevents a
            // private IPv4 destination from being hidden inside an IPv6 literal.
            return (first & 0xfe) == 0xfc
                    || (first == 0x20 && second == 0x01
                            && Byte.toUnsignedInt(bytes[2]) == 0x0d
                            && Byte.toUnsignedInt(bytes[3]) == 0xb8)
                    || (first == 0x20 && second == 0x01
                            && bytes[2] == 0 && bytes[3] == 0)
                    || (first == 0x20 && second == 0x02);
        }
        return true;
    }

    public static class UnsafeTargetException extends RuntimeException {
        public UnsafeTargetException(String message) {
            super(message);
        }

        public UnsafeTargetException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
