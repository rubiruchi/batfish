package org.batfish.topology;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.graph.ValueGraph;
import java.util.concurrent.ExecutionException;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.topology.IpOwners;
import org.batfish.common.topology.TopologyProvider;
import org.batfish.datamodel.BgpPeerConfigId;
import org.batfish.datamodel.BgpSessionProperties;
import org.batfish.datamodel.bgp.BgpTopologyUtils;

@ParametersAreNonnullByDefault
public final class TopologyProviderImpl implements TopologyProvider {
  private static final int MAX_CACHED_SNAPSHOTS = 3;

  private final IBatfish _batfish;
  private final Cache<NetworkSnapshot, IpOwners> _ipOwners;
  private final Cache<NetworkSnapshot, ValueGraph<BgpPeerConfigId, BgpSessionProperties>>
      _inferredBgpTopology;

  /** Create a new topology provider for a given instance of {@link IBatfish} */
  public TopologyProviderImpl(IBatfish batfish) {
    _batfish = batfish;
    _ipOwners = CacheBuilder.newBuilder().maximumSize(MAX_CACHED_SNAPSHOTS).build();
    _inferredBgpTopology = CacheBuilder.newBuilder().maximumSize(MAX_CACHED_SNAPSHOTS).build();
  }

  /** Return the {@link IpOwners} for a given snapshot. */
  @Override
  public IpOwners getIpOwners(NetworkSnapshot snapshot) {
    try {
      return _ipOwners.get(snapshot, () -> new IpOwners(_batfish.loadConfigurations(snapshot)));
    } catch (ExecutionException e) {
      return new IpOwners(_batfish.loadConfigurations(snapshot));
    }
  }

  /**
   * Return the BGP topology inferred by Batfish based on configured BGP peers.
   *
   * <p>This topology performs basic peer compatibility checks but is unaware of dataplane
   * reachability for actual session establishment.
   */
  public ValueGraph<BgpPeerConfigId, BgpSessionProperties> getInferredBgpTopology(
      NetworkSnapshot snapshot) {
    try {
      return _inferredBgpTopology.get(
          snapshot,
          () ->
              BgpTopologyUtils.initBgpTopology(
                  _batfish.loadConfigurations(snapshot),
                  getIpOwners(snapshot).getActiveIpToOwnerHostnames(),
                  false));
    } catch (ExecutionException e) {
      return BgpTopologyUtils.initBgpTopology(
          _batfish.loadConfigurations(snapshot),
          getIpOwners(snapshot).getActiveIpToOwnerHostnames(),
          false);
    }
  }
}
