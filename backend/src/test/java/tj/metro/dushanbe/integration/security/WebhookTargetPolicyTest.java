package tj.metro.dushanbe.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class WebhookTargetPolicyTest {

    @Test
    void acceptsPublicHttpsDestination() throws Exception {
        WebhookTargetPolicy policy = new WebhookTargetPolicy(host ->
                new InetAddress[]{InetAddress.getByName("93.184.216.34")});

        assertEquals("https://hooks.example.test/events",
                policy.validateForDispatch("https://hooks.example.test/events").toASCIIString());
    }

    @Test
    void rejectsNonHttpsAndPrivateLiteralDestinations() {
        WebhookTargetPolicy policy = new WebhookTargetPolicy();

        assertThrows(WebhookTargetPolicy.UnsafeTargetException.class,
                () -> policy.validateForConfiguration("http://hooks.example.test/events"));
        assertThrows(WebhookTargetPolicy.UnsafeTargetException.class,
                () -> policy.validateForConfiguration("https://127.0.0.1/events"));
        assertThrows(WebhookTargetPolicy.UnsafeTargetException.class,
                () -> policy.validateForConfiguration("https://169.254.169.254/metadata"));
        assertThrows(WebhookTargetPolicy.UnsafeTargetException.class,
                () -> policy.validateForConfiguration("https://[::1]/events"));
    }

    @Test
    void rejectsHostWhenAnyDnsAnswerIsPrivate() throws Exception {
        WebhookTargetPolicy policy = new WebhookTargetPolicy(host -> new InetAddress[]{
                InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.7")});

        assertThrows(WebhookTargetPolicy.UnsafeTargetException.class,
                () -> policy.validateForDispatch("https://hooks.example.test/events"));
    }

    @Test
    void rejectsDnsFailureAsUnsafeAtDispatchTime() {
        WebhookTargetPolicy policy = new WebhookTargetPolicy(host -> {
            throw new UnknownHostException(host);
        });

        assertThrows(WebhookTargetPolicy.UnsafeTargetException.class,
                () -> policy.validateForDispatch("https://missing.example.test/events"));
    }
}
