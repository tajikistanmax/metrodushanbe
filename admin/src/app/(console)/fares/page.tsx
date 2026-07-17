import SectionHeader from "@/components/SectionHeader";
import FaresManager from "@/components/operations/FaresManager";
import { getFareProducts } from "@/lib/admin-actions";

export const dynamic = "force-dynamic";

export default async function FaresPage() {
  const fares = await getFareProducts();
  return (
    <>
      <SectionHeader section="fares" />
      <FaresManager data={fares.data} error={fares.error} />
    </>
  );
}
