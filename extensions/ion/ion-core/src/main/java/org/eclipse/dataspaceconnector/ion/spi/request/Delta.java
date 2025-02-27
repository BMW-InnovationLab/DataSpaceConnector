package org.eclipse.dataspaceconnector.ion.spi.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

/**
 * Delta object as per sidetree spec
 */
@JsonPropertyOrder(alphabetic = true)
public class Delta {
    private final String updateCommitment;
    private final List<Map<String, Object>> patches;

    public Delta(@JsonProperty("updateCommitment") String updateCommitment, @JsonProperty("patches") List<Map<String, Object>> patches) {
        this.updateCommitment = updateCommitment;
        this.patches = patches;
    }

    public String getUpdateCommitment() {
        return updateCommitment;
    }

    public List<Map<String, Object>> getPatches() {
        return patches;
    }
}
