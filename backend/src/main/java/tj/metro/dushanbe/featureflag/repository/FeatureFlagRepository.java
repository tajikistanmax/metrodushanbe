package tj.metro.dushanbe.featureflag.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.featureflag.domain.FeatureFlag;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {

    List<FeatureFlag> findByEnabledTrue();
}
