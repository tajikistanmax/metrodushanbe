package tj.metro.dushanbe.featureflag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "feature_flag")
public class FeatureFlag {

    @Id
    @Column(name = "flag_key", nullable = false, length = 100)
    private String flagKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    protected FeatureFlag() {
    }

    public FeatureFlag(String flagKey, boolean enabled, String description,
                       OffsetDateTime updatedAt, String updatedBy) {
        this.flagKey = flagKey;
        this.enabled = enabled;
        this.description = description;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
