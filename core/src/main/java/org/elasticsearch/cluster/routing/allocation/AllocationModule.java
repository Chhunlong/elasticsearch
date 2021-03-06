/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cluster.routing.allocation;

import org.elasticsearch.cluster.routing.allocation.allocator.BalancedShardsAllocator;
import org.elasticsearch.cluster.routing.allocation.allocator.ShardsAllocator;
import org.elasticsearch.cluster.routing.allocation.decider.AllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.AllocationDeciders;
import org.elasticsearch.cluster.routing.allocation.decider.AwarenessAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.ClusterRebalanceAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.ConcurrentRebalanceAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.DisableAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.DiskThresholdDecider;
import org.elasticsearch.cluster.routing.allocation.decider.EnableAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.FilterAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.NodeVersionAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.RebalanceOnlyWhenActiveAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.ReplicaAfterPrimaryActiveAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.SameShardAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.ShardsLimitAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.SnapshotInProgressAllocationDecider;
import org.elasticsearch.cluster.routing.allocation.decider.ThrottlingAllocationDecider;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.multibindings.Multibinder;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.gateway.GatewayAllocator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A module to setup classes related to shard allocation.
 *
 * There are two basic concepts for allocation.
 * <ul>
 * <li>An {@link AllocationDecider} decides *when* an allocation should be attempted.</li>
 * <li>A {@link ShardsAllocator} determins *how* an allocation takes place</li>
 * </ul>
 */
public class AllocationModule extends AbstractModule {

    public static final String EVEN_SHARD_COUNT_ALLOCATOR = "even_shard";
    public static final String BALANCED_ALLOCATOR = "balanced"; // default
    public static final String SHARDS_ALLOCATOR_TYPE_KEY = "cluster.routing.allocation.type";

    public static final List<Class<? extends AllocationDecider>> DEFAULT_ALLOCATION_DECIDERS =
        Collections.unmodifiableList(Arrays.asList(
            SameShardAllocationDecider.class,
            FilterAllocationDecider.class,
            ReplicaAfterPrimaryActiveAllocationDecider.class,
            ThrottlingAllocationDecider.class,
            RebalanceOnlyWhenActiveAllocationDecider.class,
            ClusterRebalanceAllocationDecider.class,
            ConcurrentRebalanceAllocationDecider.class,
            EnableAllocationDecider.class, // new enable allocation logic should proceed old disable allocation logic
            DisableAllocationDecider.class,
            AwarenessAllocationDecider.class,
            ShardsLimitAllocationDecider.class,
            NodeVersionAllocationDecider.class,
            DiskThresholdDecider.class,
            SnapshotInProgressAllocationDecider.class));

    private final Settings settings;
    private final Map<String, Class<? extends ShardsAllocator>> shardsAllocators = new HashMap<>();
    private final Set<Class<? extends AllocationDecider>> allocationDeciders = new HashSet<>();

    public AllocationModule(Settings settings) {
        this.settings = settings;
        this.allocationDeciders.addAll(DEFAULT_ALLOCATION_DECIDERS);
        registerShardAllocator(BALANCED_ALLOCATOR, BalancedShardsAllocator.class);
        registerShardAllocator(EVEN_SHARD_COUNT_ALLOCATOR, BalancedShardsAllocator.class);
    }

    /** Register a custom allocation decider */
    public void registerAllocationDecider(Class<? extends AllocationDecider> allocationDecider) {
        boolean isNew = allocationDeciders.add(allocationDecider);
        if (isNew == false) {
            throw new IllegalArgumentException("Cannot register AllocationDecider " + allocationDecider.getName() + " twice");
        }
    }

    /** Register a custom shard allocator with the given name */
    public void registerShardAllocator(String name, Class<? extends ShardsAllocator> clazz) {
        Class<? extends ShardsAllocator> existing = shardsAllocators.put(name, clazz);
        if (existing != null) {
            throw new IllegalArgumentException("Cannot register ShardAllocator [" + name + "] to " + clazz.getName() + ", already registered to " + existing.getName());
        }
    }

    @Override
    protected void configure() {

        // bind ShardsAllocator
        final String shardsAllocatorType = settings.get(AllocationModule.SHARDS_ALLOCATOR_TYPE_KEY, AllocationModule.BALANCED_ALLOCATOR);
        final Class<? extends ShardsAllocator> shardsAllocator = shardsAllocators.get(shardsAllocatorType);
        if (shardsAllocator == null) {
            throw new IllegalArgumentException("Unknown ShardsAllocator type [" + shardsAllocatorType + "]");
        } else if (shardsAllocatorType.equals(EVEN_SHARD_COUNT_ALLOCATOR)) {
            final ESLogger logger = Loggers.getLogger(getClass(), settings);
            logger.warn("{} allocator has been removed in 2.0 using {} instead", AllocationModule.EVEN_SHARD_COUNT_ALLOCATOR, AllocationModule.BALANCED_ALLOCATOR);
        }
        bind(ShardsAllocator.class).to(shardsAllocator).asEagerSingleton();

        // bind AllocationDeciders
        Multibinder<AllocationDecider> allocationMultibinder = Multibinder.newSetBinder(binder(), AllocationDecider.class);
        for (Class<? extends AllocationDecider> allocation : allocationDeciders) {
            allocationMultibinder.addBinding().to(allocation).asEagerSingleton();
        }

        bind(GatewayAllocator.class).asEagerSingleton();
        bind(AllocationDeciders.class).asEagerSingleton();
        bind(AllocationService.class).asEagerSingleton();
    }
}
