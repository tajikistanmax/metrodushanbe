import SectionHeader from "@/components/SectionHeader";
import FeatureFlagsManager from "@/components/operations/FeatureFlagsManager";
import { getFeatureFlags } from "@/lib/admin-actions";

export const dynamic = "force-dynamic";

export default async function FeaturesPage() {
  const flags = await getFeatureFlags();
  return (
    <>
      <SectionHeader section="features" />
      <FeatureFlagsManager data={flags.data} error={flags.error} />
    </>
  );
}
