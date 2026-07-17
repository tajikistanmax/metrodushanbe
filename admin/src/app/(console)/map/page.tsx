import NetworkMapWorkspace from "@/components/dashboard/NetworkMapWorkspace";
import { getLines, getNetworkGeoJson, getStations } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function MetroMapPage() {
  const [lines, stations, network] = await Promise.all([
    getLines(),
    getStations(),
    getNetworkGeoJson(),
  ]);

  return (
    <NetworkMapWorkspace
      network={network.data}
      networkSource={network.source}
      lines={lines.data ?? []}
      stations={stations.data ?? []}
    />
  );
}
