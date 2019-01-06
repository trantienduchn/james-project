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

package org.apache.james.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.file.FileMailRepository;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryUrlStore;
import org.apache.james.modules.server.MailStoreRepositoryModule;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;

public class InMemoryMailRepositoryStoreTest {
    private static final MailRepositoryUrl FILE_REPO = MailRepositoryUrl.from("file://repo");
    private static final MailRepositoryUrl MEMORY_REPO = MailRepositoryUrl.from("memory://repo");
    private static final MailRepositoryPath PATH_REPO = MailRepositoryPath.from("repo");

    private MemoryMailRepositoryUrlStore urlStore;

    private static class MemoryMailRepositoryProvider implements MailRepositoryProvider {
        @Override
        public String canonicalName() {
            return MemoryMailRepository.class.getCanonicalName();
        }

        @Override
        public MailRepository provide(MailRepositoryUrl url) {
            return new MemoryMailRepository();
        }
    }

    private InMemoryMailRepositoryStore repositoryStore;
    private FileSystemImpl fileSystem;
    private Configuration configuration;

    @Before
    public void setUp() throws Exception {
        configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        fileSystem = new FileSystemImpl(configuration.directories());
        urlStore = new MemoryMailRepositoryUrlStore();
        MailStoreRepositoryModule.FileMailRepositoryProvider fileProvider = new MailStoreRepositoryModule.FileMailRepositoryProvider(fileSystem);
        repositoryStore = new InMemoryMailRepositoryStore(urlStore, Sets.newHashSet(
            fileProvider,
                new MemoryMailRepositoryProvider()));
        repositoryStore.configure(new FileConfigurationProvider(fileSystem, configuration)
            .getConfiguration("mailrepositorystore"));
        repositoryStore.init();
    }

    @Test(expected = MailRepositoryStore.MailRepositoryStoreException.class)
    public void selectingANonRegisteredProtocolShouldFail() {
        repositoryStore.select(MailRepositoryUrl.from("proto://repo"));
    }

    @Test
    public void selectingARegisteredProtocolShouldWork() {
        assertThat(repositoryStore.select(FILE_REPO))
            .isInstanceOf(FileMailRepository.class);
    }

    @Test
    public void selectingTwiceARegisteredProtocolWithSameDestinationShouldReturnTheSameResult() {
        assertThat(repositoryStore.select(FILE_REPO))
            .isEqualTo(repositoryStore.select(FILE_REPO));
    }

    @Test
    public void selectingTwiceARegisteredProtocolWithDifferentDestinationShouldReturnDifferentResults() {
        assertThat(repositoryStore.select(FILE_REPO))
            .isNotEqualTo(repositoryStore.select(MailRepositoryUrl.from("file://repo1")));
    }

    @Test
    public void configureShouldThrowWhenNonValidClassesAreProvided() throws Exception {
        repositoryStore = new InMemoryMailRepositoryStore(urlStore, Sets.newHashSet(
            new MailStoreRepositoryModule.FileMailRepositoryProvider(
                fileSystem)));
        repositoryStore.configure(new FileConfigurationProvider(fileSystem, configuration).getConfiguration("fakemailrepositorystore"));

        assertThatThrownBy(() -> repositoryStore.init())
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    public void configureShouldNotThrowOnEmptyConfiguration() throws Exception {
        repositoryStore = new InMemoryMailRepositoryStore(urlStore, Sets.newHashSet(
            new MailStoreRepositoryModule.FileMailRepositoryProvider(
                fileSystem)));
        repositoryStore.configure(new HierarchicalConfiguration());

        repositoryStore.init();
    }

    @Test
    public void getUrlsShouldBeEmptyIfNoSelectWerePerformed() {
        assertThat(repositoryStore.getUrls()).isEmpty();
    }

    @Test
    public void getUrlsShouldReturnUsedUrls() {
        MailRepositoryUrl url1 = MailRepositoryUrl.from("file://repo1");
        MailRepositoryUrl url2 = MailRepositoryUrl.from("file://repo2");
        MailRepositoryUrl url3 = MailRepositoryUrl.from("file://repo3");
        repositoryStore.select(url1);
        repositoryStore.select(url2);
        repositoryStore.select(url3);
        assertThat(repositoryStore.getUrls()).containsOnly(url1, url2, url3);
    }

    @Test
    public void getUrlsResultsShouldNotBeDuplicated() {
        repositoryStore.select(FILE_REPO);
        repositoryStore.select(FILE_REPO);
        assertThat(repositoryStore.getUrls()).containsExactly(FILE_REPO);
    }

    @Test
    public void getPathsShouldBeEmptyIfNoSelectWerePerformed() {
        assertThat(repositoryStore.getPaths()).isEmpty();
    }

    @Test
    public void getPathsShouldReturnUsedUrls() {
        MailRepositoryPath path1 = MailRepositoryPath.from("repo1");
        MailRepositoryPath path2 = MailRepositoryPath.from("repo1");
        MailRepositoryPath path3 = MailRepositoryPath.from("repo1");
        repositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(path1, "file"));
        repositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(path2, "file"));
        repositoryStore.select(MailRepositoryUrl.fromPathAndProtocol(path3, "file"));
        assertThat(repositoryStore.getPaths()).containsOnly(path1, path2, path3);
    }

    @Test
    public void getPathsResultsShouldNotBeDuplicatedWithTheSameProtocol() {
        repositoryStore.select(FILE_REPO);
        repositoryStore.select(FILE_REPO);
        assertThat(repositoryStore.getPaths()).containsExactly(PATH_REPO);
    }

    @Test
    public void getPathsResultsShouldNotBeDuplicatedWithDifferentProtocols() {
        repositoryStore.select(FILE_REPO);
        repositoryStore.select(MEMORY_REPO);
        assertThat(repositoryStore.getPaths()).containsExactly(PATH_REPO);
    }

    @Test
    public void getShouldReturnEmptyWhenUrlNotInUse() {
        assertThat(repositoryStore.get(FILE_REPO))
            .isEmpty();
    }

    @Test
    public void getShouldReturnRepositoryWhenUrlExists() {
        urlStore.add(FILE_REPO);

        assertThat(repositoryStore.get(FILE_REPO))
            .isNotEmpty();
    }

    @Test
    public void getByPathShouldReturnRepositoryWhenUrlExists() {
        urlStore.add(FILE_REPO);

        assertThat(repositoryStore.getByPath(FILE_REPO.getPath()))
            .isNotEmpty();
    }

    @Test
    public void getShouldReturnPreviouslyCreatedMailRepository() {
        MailRepository mailRepository = repositoryStore.select(FILE_REPO);

        assertThat(repositoryStore.get(FILE_REPO))
            .contains(mailRepository);
    }

    @Test
    public void getByPathShouldReturnEmptyWhenUrlNotInUse() {
        assertThat(repositoryStore.getByPath(PATH_REPO))
            .isEmpty();
    }

    @Test
    public void getByPathShouldReturnPreviouslyCreatedMatchingMailRepository() {
        MailRepository mailRepository = repositoryStore.select(FILE_REPO);

        assertThat(repositoryStore.getByPath(PATH_REPO))
            .contains(mailRepository);
    }

    @Test
    public void getByPathShouldReturnPreviouslyCreatedMatchingMailRepositories() {
        MailRepository mailRepositoryFile = repositoryStore.select(FILE_REPO);
        MailRepository mailRepositoryArbitrary = repositoryStore.select(MEMORY_REPO);

        assertThat(repositoryStore.getByPath(PATH_REPO))
            .contains(mailRepositoryFile)
            .contains(mailRepositoryArbitrary);
    }

    @Test
    public void getByPathShouldReturnEmptyWhenNoMailRepositoriesAreMatching() {
        repositoryStore.select(FILE_REPO);

        assertThat(repositoryStore.getByPath(MailRepositoryPath.from("unknown")))
            .isEmpty();
    }

    @Test
    public void selectShouldNotReturnDifferentResultsWhenUsedInAConcurrentEnvironment() throws Exception {
        MailRepositoryUrl url = MailRepositoryUrl.from("memory://repo");
        int threadCount = 10;

        ConcurrentTestRunner concurrentTestRunner = ConcurrentTestRunner.builder()
            .threadCount(10)
            .build((threadNb, operationNb) -> repositoryStore.select(url)
                .store(FakeMail.builder()
                    .name("name" + threadNb)
                    .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .setText("Any body"))
                    .build()));
        concurrentTestRunner.run().awaitTermination(1, TimeUnit.MINUTES);
        concurrentTestRunner.assertNoException();

        long actualSize = repositoryStore.get(url).get().size();

        assertThat(actualSize).isEqualTo(threadCount);
    }

}
