package com.hmdp.servicedata.application.service;

import com.hmdp.servicedata.domain.model.Consumer;
import com.hmdp.servicedata.domain.model.ConsumerAlias;
import com.hmdp.servicedata.domain.model.ScopeRef;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Limited identity policy: aliases merge only inside one source system and source scope. */
@Component
public class ConsumerIdentityResolutionPolicy {

    public String normalizedAliasHash(String displayAlias) {
        return ConsumerAlias.normalizedHashOf(displayAlias);
    }

    public String aliasScopeForConversation(String conversationSourceScope) {
        String scope = ScopeRef.requireText(conversationSourceScope, "sourceScope");
        int delimiter = scope.indexOf(':');
        return delimiter < 0 ? scope : scope.substring(0, delimiter);
    }

    public Consumer newConsumer(ScopeRef scope, String sourceSystem, String sourceScope,
                                String normalizedAliasHash, String displayAlias) {
        return new Consumer(stableId("consumer", scope, sourceSystem, sourceScope,
                normalizedAliasHash), scope, displayAlias, Consumer.MERGE_POLICY_LIMITED, 0);
    }

    public ConsumerAlias newAlias(ScopeRef scope, String sourceSystem, String sourceScope,
                                  String displayAlias, String consumerId, String provenanceJson,
                                  String batchId) {
        return new ConsumerAlias(stableId("alias", scope, sourceSystem, sourceScope,
                normalizedAliasHash(displayAlias)), scope, consumerId, sourceSystem, sourceScope,
                displayAlias, ConsumerAlias.CONFIDENCE_LIMITED, provenanceJson, batchId);
    }

    public Consumer newConversationScopedConsumer(ScopeRef scope, String sourceSystem,
                                                   String sourceConversationId) {
        return new Consumer(stableId("consumer", scope, sourceSystem, "conversation",
                sourceConversationId), scope, "unknown-consumer",
                Consumer.MERGE_POLICY_LIMITED, 0);
    }

    public String stableId(String type, ScopeRef scope, String... identityParts) {
        StringBuilder source = new StringBuilder(type)
                .append('|').append(scope.getTenantId())
                .append('|').append(scope.getWorkspaceId());
        for (String part : identityParts) {
            source.append('|').append(part == null ? "" : part);
        }
        return type + "-" + sha256(source.toString()).substring(0, 48);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            char[] alphabet = "0123456789abcdef".toCharArray();
            char[] encoded = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int current = digest[i] & 0xff;
                encoded[i * 2] = alphabet[current >>> 4];
                encoded[i * 2 + 1] = alphabet[current & 0x0f];
            }
            return new String(encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
