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

package org.elasticsearch.cache.recycler;

import org.elasticsearch.common.Classes;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;

/**
 */
public class PageCacheRecyclerModule extends AbstractModule {

    public static final String CACHE_IMPL = "cache.recycler.page_cache_impl";

    private final Settings settings;

    public PageCacheRecyclerModule(Settings settings) {
        this.settings = settings;
    }

    @Override
    protected void configure() {
        String impl = settings.get(CACHE_IMPL);
        if (impl == null) {
            bind(PageCacheRecycler.class).asEagerSingleton();
        } else {
            Class<? extends PageCacheRecycler> implClass = Classes.loadClass(getClass().getClassLoader(), impl);
            bind(PageCacheRecycler.class).to(implClass).asEagerSingleton();
        }
    }
}
