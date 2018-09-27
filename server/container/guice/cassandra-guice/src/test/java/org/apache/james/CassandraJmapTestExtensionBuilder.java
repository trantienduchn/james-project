/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james;

import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;

import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestESMetricReporterModule;
import org.apache.james.modules.TestJMAPServerModule;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public interface CassandraJmapTestExtensionBuilder {

    class CoreModuleStage {

        private Module coreModule;

        AdditionalModulesStage defaultCoreModule() {
            this.coreModule = ALL_BUT_JMX_CASSANDRA_MODULE;
            return new AdditionalModulesStage(this);
        }
    }

    class AdditionalModulesStage {
        private final CoreModuleStage coreModuleStage;
        private Module overrideModules;

        AdditionalModulesStage(CoreModuleStage coreModuleStage) {
            this.coreModuleStage = coreModuleStage;
        }

        ExtensionsStage defaultModulesOverrideWith(Module... overrides) {
            this.overrideModules = Modules.override(DEFAULT_OVERRIDE_MODULES)
                .with(overrides);
            return new ExtensionsStage(this);
        }
    }

    class ExtensionsStage {
        private final AdditionalModulesStage additionalModulesStage;
        private ImmutableList<GuiceModuleTestExtension> extensions;

        ExtensionsStage(AdditionalModulesStage additionalModulesStage) {
            this.additionalModulesStage = additionalModulesStage;
            this.extensions = EMPTY_EXTENSIONS;
        }

        ReadyToBuild defaultExtensions() {
            this.extensions = EMBEDDED_ES_EXTENSION_ONLY;
            return new ReadyToBuild(this);
        }
    }

    class ReadyToBuild {
        private final ExtensionsStage extensionsStage;

        ReadyToBuild(ExtensionsStage extensionsStage) {
            this.extensionsStage = extensionsStage;
        }

        public CassandraJmapTestExtension build() {
            AdditionalModulesStage additionalModulesStage = extensionsStage.additionalModulesStage;
            CoreModuleStage coreModuleStage = additionalModulesStage.coreModuleStage;
            return new CassandraJmapTestExtension(
                coreModuleStage.coreModule,
                additionalModulesStage.overrideModules,
                extensionsStage.extensions);
        }
    }

    int LIMIT_TO_10_MESSAGES = 10;
    Module DEFAULT_OVERRIDE_MODULES = Modules.combine(
        binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class),
        new TestJMAPServerModule(LIMIT_TO_10_MESSAGES),
        new TestESMetricReporterModule());
    ImmutableList<GuiceModuleTestExtension> EMPTY_EXTENSIONS = ImmutableList.of();
    ImmutableList<GuiceModuleTestExtension> EMBEDDED_ES_EXTENSION_ONLY = ImmutableList.of(new EmbeddedElasticSearchExtension());
}
